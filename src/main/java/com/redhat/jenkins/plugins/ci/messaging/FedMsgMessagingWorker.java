package com.redhat.jenkins.plugins.ci.messaging;

import hudson.EnvVars;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.Run;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.jenkins.plugins.ci.CIEnvironmentContributingAction;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.messaging.data.FedmsgMessage;
import com.redhat.jenkins.plugins.ci.messaging.data.SendResult;
import com.redhat.jenkins.plugins.ci.provider.data.FedMsgPublisherProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.FedMsgSubscriberProviderData;
import com.redhat.jenkins.plugins.ci.provider.data.ProviderData;
import com.redhat.utils.PluginUtils;

/*
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
public class FedMsgMessagingWorker extends JMSMessagingWorker {

    private static final Logger log = Logger.getLogger(FedMsgMessagingWorker.class.getName());

    public static final String DEFAULT_TOPIC = "org.fedoraproject";
    private final FedMsgMessagingProvider provider;

    private ZMQ.Context context;
    private ZMQ.Poller poller;
    private ZMQ.Socket socket;
    private boolean interrupt = false;

    private boolean pollerClosed = false;

    public FedMsgMessagingWorker(JMSMessagingProvider messagingProvider, MessagingProviderOverrides overrides, String jobname) {
        super(messagingProvider, overrides, jobname);
        this.provider = (FedMsgMessagingProvider) messagingProvider;
    }

    @Override
    public boolean subscribe(String jobname, String selector) {
        if (interrupt) {
            return true;
        }
        this.topic = getTopic(provider);
        if (this.topic != null) {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (!hasPoller() && !connect()) {
                        return false;
                    }
                    if (socket == null) {
                        socket = context.socket(ZMQ.SUB);
                        log.info("Subscribing job '" + jobname + "' to " + this.topic + " topic.");
                        socket.subscribe(this.topic.getBytes());
                        socket.setLinger(0);
                        socket.connect(provider.getHubAddr());
                        poller.register(socket, ZMQ.Poller.POLLIN);
                        log.info("Successfully subscribed job '" + jobname + "' to topic '" + this.topic + "'.");
                    } else {
                        log.info("Already subscribed job '" + jobname + "' to topic '" + this.topic + "'.");
                    }
                    return true;
                } catch (Exception ex) {

                    // Either we were interrupted, or something else went
                    // wrong. If we were interrupted, then we will jump ship
                    // on the next iteration. If something else happened,
                    // then we just unsubscribe here, sleep, so that we may
                    // try again on the next iteration.

                    log.log(Level.SEVERE, "Exception raised while subscribing job '" + jobname + "', retrying in " + RETRY_MINUTES + " minutes.", ex);
                    if (!Thread.currentThread().isInterrupted()) {

                        unsubscribe(jobname);

                        try {
                            Thread.sleep(RETRY_MINUTES * 60 * 1000);
                        } catch (InterruptedException ie) {
                            // We were interrupted while waiting to retry.
                            // We will jump ship on the next iteration.

                            // NB: The interrupt flag was cleared when
                            // InterruptedException was thrown. We have to
                            // re-install it to make sure we eventually
                            // leave this thread.
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void unsubscribe(String jobname) {
        if (interrupt) {
            log.info("We are being interrupted. Skipping unsubscribe...");
            return;
        }
        try {
            if (poller != null) {
                for (Integer i = 0; i < poller.getSize(); i++) {
                    ZMQ.Socket s = poller.getSocket(i);
                    poller.unregister(s);
                    s.disconnect(provider.getHubAddr());
                    log.info("Un-subscribing job '" + jobname + "' from " + this.topic + " topic.");
                    socket.unsubscribe(this.topic.getBytes());
                }
                socket.close();
            }
            if (context != null) {
                context.term();
            }
        } catch (Exception e) {
            log.warning(e.getMessage());
        }
        poller = null;
        context = null;
        socket = null;
        pollerClosed = true;
    }

    @Override
    public void receive(String jobname, ProviderData pdata) {
        FedMsgSubscriberProviderData pd = (FedMsgSubscriberProviderData)pdata;
        int timeoutInMs = (pd.getTimeout() != null ? pd.getTimeout() : FedMsgSubscriberProviderData.DEFAULT_TIMEOUT_IN_MINUTES) * 60 * 1000;
        if (interrupt) {
            log.info("we have been interrupted at start of receive");
            return;
        }
        while (!subscribe(jobname)) {
            if (!Thread.currentThread().isInterrupted()) {
                try {
                    int WAIT_SECONDS = 2;
                    Thread.sleep(WAIT_SECONDS * 1000);
                } catch (InterruptedException e) {
                    // We were interrupted while waiting to retry. We will
                    // jump ship on the next iteration.

                    // NB: The interrupt flag was cleared when
                    // InterruptedException was thrown. We have to
                    // re-install it to make sure we eventually leave this
                    // thread.
                    Thread.currentThread().interrupt();
                }
            }
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
        long lastSeenMessage = new Date().getTime();
        try {
            while ((new Date().getTime() - lastSeenMessage) < timeoutInMs) {
                if (poller.poll(1000) > 0) {
                    pollerClosed = false;
                    if (poller.pollin(0)) {
                        ZMsg z = ZMsg.recvMsg(poller.getSocket(0));
                        // Reset timer
                        lastSeenMessage = new Date().getTime();
                        //
                        String json = z.getLast().toString();
                        FedmsgMessage data = mapper.readValue(json, FedmsgMessage.class);
                        if (provider.verify(data.getBodyJson(), pd.getChecks(), jobname)) {
                            Map<String, String> params = new HashMap<String, String>();
                            params.put("CI_MESSAGE", data.getBodyJson());
                            trigger(jobname, provider.formatMessage(data), params);
                        }
                    }
                } else {
                    if (interrupt) {
                        log.info("We have been interrupted...");
                        pollerClosed = true;
                        break;
                    }
                }
            }
            if (!interrupt) {
                log.info("No message received for the past " + timeoutInMs + " ms, re-subscribing for job '" + jobname + "'.");
                unsubscribe(jobname);
            }
        } catch (Exception e) {
            if (!Thread.currentThread().isInterrupted()) {
                // Something other than an interrupt causes this.
                // Unsubscribe, but stay in our loop and try to reconnect..
                log.log(Level.WARNING, "JMS exception raised, going to re-subscribe for job '" + jobname + "'.", e);
                unsubscribe(jobname); // Try again next time.
            }
        }

    }

    @Override
    public boolean connect() {
        context = ZMQ.context(1);
        poller = context.poller(1);
        return true;
    }

    public boolean hasPoller() {
        return poller != null;
    }

    @Override
    public void disconnect() {
    }

    @Override
    public SendResult sendMessage(Run<?, ?> build, TaskListener listener, ProviderData pdata) {
        FedMsgPublisherProviderData pd = (FedMsgPublisherProviderData)pdata;
        ZMQ.Context context = ZMQ.context(1);
        ZMQ.Socket sock = context.socket(ZMQ.PUB);
        sock.setLinger(0);
        log.fine("pub address: " + provider.getPubAddr());
        sock.connect(provider.getPubAddr());
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        String body = "";
        String msgId = "";
        try {

            EnvVars env = new EnvVars();
            env.putAll(build.getEnvironment(listener));
            env.put("CI_NAME", build.getParent().getName());
            if (!build.isBuilding()) {
                env.put("CI_STATUS", (build.getResult() == Result.SUCCESS ? "passed" : "failed"));
                env.put("BUILD_STATUS", build.getResult().toString());
            }

            FedmsgMessage fm = new FedmsgMessage(PluginUtils.getSubstitutedValue(getTopic(provider), build.getEnvironment(listener)),
                                                 PluginUtils.getSubstitutedValue(pd.getMessageContent(), env));

            fm.setTimestamp(System.currentTimeMillis());

            body = fm.toJson(); // Use toString() instead of getBodyJson so that message ID is included and sent.
            msgId = fm.getMsgId();
            if (!sock.sendMore(fm.getTopic()) && pd.isFailOnError()) {
                log.severe("Unhandled exception in perform: Failed to send message (topic)!");
                return new SendResult(false, msgId, body);
            }
            if (!sock.send(body) && pd.isFailOnError()) {
                log.severe("Unhandled exception in perform: Failed to send message (body)!");
                return new SendResult(false, msgId, body);
            }
            log.fine("JSON message body:\n" + body);
            listener.getLogger().println("JSON message body:\n" + body);

        } catch (Exception e) {
            if (pd.isFailOnError()) {
                log.severe("Unhandled exception in perform: ");
                log.severe(ExceptionUtils.getStackTrace(e));
                listener.fatalError("Unhandled exception in perform: ");
                listener.fatalError(ExceptionUtils.getStackTrace(e));
                return new SendResult(false, msgId, body);
            } else {
                log.warning("Unhandled exception in perform: ");
                log.warning(ExceptionUtils.getStackTrace(e));
                listener.error("Unhandled exception in perform: ");
                listener.error(ExceptionUtils.getStackTrace(e));
                return new SendResult(true, msgId, body);
            }
        } finally {
            sock.close();
            context.term();
        }
        return new SendResult(true, msgId, body);
    }

    @Override
    public String waitForMessage(Run<?, ?> build, TaskListener listener, ProviderData pdata) {
        FedMsgSubscriberProviderData pd = (FedMsgSubscriberProviderData)pdata;
        log.info("Waiting for message.");
        listener.getLogger().println("Waiting for message.");
        for (MsgCheck msgCheck: pd.getChecks()) {
            log.info(" with check: " + msgCheck.toString());
            listener.getLogger().println(" with check: " + msgCheck.toString());
        }
        Integer timeout = (pd.getTimeout() != null ? pd.getTimeout() : FedMsgSubscriberProviderData.DEFAULT_TIMEOUT_IN_MINUTES);
        log.info(" with timeout: " + timeout);
        listener.getLogger().println(" with timeout: " + timeout);

        ZMQ.Context lcontext = ZMQ.context(1);
        ZMQ.Poller lpoller = lcontext.poller(1);
        ZMQ.Socket lsocket = lcontext.socket(ZMQ.SUB);

        String ltopic = getTopic(provider);
        try {
            ltopic = PluginUtils.getSubstitutedValue(getTopic(provider), build.getEnvironment(listener));
        } catch (IOException e) {
            log.warning(e.getMessage());
        } catch (InterruptedException e) {
            log.warning(e.getMessage());
        }

        lsocket.subscribe(ltopic.getBytes());
        lsocket.setLinger(0);
        lsocket.connect(provider.getHubAddr());
        lpoller.register(lsocket, ZMQ.Poller.POLLIN);

        ObjectMapper mapper = new ObjectMapper();
        long startTime = new Date().getTime();

        int timeoutInMs = timeout * 60 * 1000;
        boolean interrupted = false;
        try {
            while ((new Date().getTime() - startTime) < timeoutInMs) {
                if (lpoller.poll(1000) > 0) {
                    if (lpoller.pollin(0)) {
                        ZMsg z = ZMsg.recvMsg(lpoller.getSocket(0));

                        listener.getLogger().println("Received a message");

                        String json = z.getLast().toString();
                        FedmsgMessage data = mapper.readValue(json, FedmsgMessage.class);
                        String body = data.getBodyJson();

                        if (!provider.verify(body, pd.getChecks(), jobname)) {
                            continue;
                        }

                        if (build != null) {
                            if (StringUtils.isNotEmpty(pd.getVariable())) {
                                EnvVars vars = new EnvVars();
                                vars.put(pd.getVariable(), body);
                                build.addAction(new CIEnvironmentContributingAction(vars));
                            }
                        }
                        return body;
                    }
                }
            }
            if (interrupted) {
                return null;
            }
            log.severe("Timed out waiting for message!");
            listener.getLogger().println("Timed out waiting for message!");
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unhandled exception waiting for message.", e);
        } finally {
            try {
                ZMQ.Socket s = lpoller.getSocket(0);
                lpoller.unregister(s);
                s.disconnect(provider.getHubAddr());
                lsocket.unsubscribe(ltopic.getBytes());
                lsocket.close();
                lcontext.term();
            } catch (Exception e) {
                listener.getLogger().println("exception in finally");
            }
        }
        return null;
    }

    public void prepareForInterrupt() {
        interrupt = true;
        try {
            while (!pollerClosed) {
                if (!Thread.currentThread().isAlive()) {
                    log.info("poller not closed yet BUT trigger thread is dead. continuing interrupt");
                    break;
                }
                try {
                    log.info("poller not closed yet. Sleeping for 1 sec...");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // swallow
                }
            }
            if (poller != null) {
                ZMQ.Socket s = poller.getSocket(0);
                poller.unregister(s);
                s.disconnect(provider.getHubAddr());
                log.info("Un-subscribing job '" + jobname + "' from " + this.topic + " topic.");
                socket.unsubscribe(this.topic.getBytes());
                socket.close();
            }
            if (context != null) {
                context.term();
            }
        } catch (Exception e) {
            log.fine(e.getMessage());
        }
        poller = null;
        socket = null;
        interrupt = false;
    }

    public boolean isBeingInterrupted() {
        return interrupt;
    }

    @Override
    public String getDefaultTopic() {
        return DEFAULT_TOPIC;
    }

}
