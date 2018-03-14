package com.redhat.jenkins.plugins.ci.messaging;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.redhat.jenkins.plugins.ci.messaging.data.SendResult;
import com.redhat.utils.OrderedProperties;
import com.redhat.utils.PluginUtils;
import hudson.EnvVars;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.Run;

import java.io.IOException;
import java.io.StringReader;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import net.sf.json.JSONObject;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;
import org.zeromq.jms.selector.ZmqMessageSelector;
import org.zeromq.jms.selector.ZmqSimpleMessageSelector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.redhat.jenkins.plugins.ci.CIEnvironmentContributingAction;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.messaging.data.FedmsgMessage;
import com.redhat.utils.MessageUtils;

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

    private final FedMsgMessagingProvider provider;

    public static final String DEFAULT_PREFIX = "org.fedoraproject";

    private ZMQ.Context context;
    private ZMQ.Poller poller;
    private ZMQ.Socket socket;
    private boolean interrupt = false;

    private String selector;
    private boolean pollerClosed = false;

    public FedMsgMessagingWorker(FedMsgMessagingProvider fedMsgMessagingProvider, MessagingProviderOverrides overrides, String jobname) {
        this.provider = fedMsgMessagingProvider;
        this.overrides = overrides;
        this.jobname = jobname;
    }

    @Override
    public boolean subscribe(String jobname, String selector) {
        if (interrupt) {
            return true;
        }
        this.topic = getTopic(provider);
        this.selector = selector;
        if (this.topic != null) {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (!isConnected()) {
                        if (!connect()) {
                            return false;
                        }
                    }
                    if (socket == null) {
                        socket = context.socket(ZMQ.SUB);
                        log.info("Subscribing job '" + jobname + "' to " + this.topic + " topic.");
                        socket.subscribe(this.topic.getBytes());
                        socket.setLinger(0);
                        socket.connect(provider.getHubAddr());
                        poller.register(socket, ZMQ.Poller.POLLIN);
                        log.info("Successfully subscribed job '" + jobname + "' to " + this.topic + " topic with selector: " + selector);
                    } else {
                        log.info("Already subscribed to " + this.topic + " topic with selector: " + selector + " for job '" + jobname);
                    }
                    return true;
                } catch (Exception ex) {

                    // Either we were interrupted, or something else went
                    // wrong. If we were interrupted, then we will jump ship
                    // on the next iteration. If something else happened,
                    // then we just unsubscribe here, sleep, so that we may
                    // try again on the next iteration.

                    log.log(Level.SEVERE, "Eexception raised while subscribing job '" + jobname + "', retrying in " + RETRY_MINUTES + " minutes.", ex);
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

    private void process(FedmsgMessage data) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("CI_MESSAGE", data.getMessageBody());
        params.put("MESSAGE_HEADERS", data.getMessageHeaders());

        Iterator<String> it = data.getMsg().keySet().iterator();
        while (it.hasNext()) {
            String key = it.next();
            Object obj = data.getMsg().get(key);
            if (obj instanceof String) {
                params.put(key, (String)obj);
            }
            if (obj instanceof Integer) {
                params.put(key, ((Integer)obj).toString());
            }
        }
        trigger(jobname, provider.formatMessage(data), params);
    }

    @Override
    public void receive(String jobname, String selector, List<MsgCheck> checks, long timeoutInMs) {
        if (interrupt) {
            log.info("we have been interrupted at start of receive");
            return;
        }
        while (!subscribe(jobname, selector)) {
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
                        data.getMsg().put("topic", data.getTopic());
                        ZmqMessageSelector selectorObj =
                                ZmqSimpleMessageSelector.parse(selector);
                        log.fine("Evaluating selector: " + selectorObj.toString());
                        if (!selectorObj.evaluate(data.getMsg())) {
                            log.fine("false");
                            continue;
                        }
                        //check checks here
                        boolean allPassed = true;
                        for (MsgCheck check: checks) {
                            if (!provider.verify(data, check)) {
                                allPassed = false;
                                log.fine("msg check: " + check.toString() + " failed against: " + provider.formatMessage(data));
                                break;
                            }
                        }
                        if (allPassed) {
                            if (checks.size() > 0) {
                                log.fine("All msg checks have passed.");
                            }
                            process(data);
                        } else {
                            log.fine("Some msg checks did not pass.");
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
                log.log(Level.SEVERE, org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(e));
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

    @Override
    public boolean isConnected() {
        return poller != null;
    }

    @Override
    public boolean isConnectedAndSubscribed() {
        return isConnected();
    }

    @Override
    public void disconnect() {
    }

    @Override
    public SendResult sendMessage(Run<?, ?> build, TaskListener listener, MessageUtils.MESSAGE_TYPE type,
                                  String props, String content, boolean failOnError) {
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

        TreeMap<String, String> envVarParts = new TreeMap<String, String>();
        HashMap<String, Object> message = new HashMap<String, Object>();
        message.put("CI_NAME", build.getParent().getName());
        envVarParts.put("CI_NAME", build.getParent().getName());

        message.put("CI_TYPE", type.getMessage());
        envVarParts.put("CI_TYPE", type.getMessage());
        if (!build.isBuilding()) {
            String ciStatus = (build.getResult()
                    == Result.SUCCESS ? "passed" : "failed");
            message.put("CI_STATUS", ciStatus);
            envVarParts.put("CI_STATUS", ciStatus);

            envVarParts.put("BUILD_STATUS", build.getResult().toString());
        }

        FedmsgMessage blob = new FedmsgMessage();
        try {
            EnvVars baseEnvVars = build.getEnvironment(listener);
            EnvVars envVars = new EnvVars();
            envVars.putAll(baseEnvVars);
            envVars.putAll(envVarParts);

            if (props != null && !props.trim().equals("")) {
                OrderedProperties p = new OrderedProperties();
                p.load(new StringReader(props));
                @SuppressWarnings("unchecked")
                Enumeration<String> e = (Enumeration<String>) p.propertyNames();
                while (e.hasMoreElements()) {
                    String key = e.nextElement();
                    EnvVars envVars2 = new EnvVars();
                    envVars2.putAll(baseEnvVars);
                    envVars2.putAll(envVarParts);
                    // This allows us to use recently added key/vals
                    // to be substituted
                    String val = PluginUtils.getSubstitutedValue(p.getProperty(key),
                            envVars2);
                    message.put(key, val);
                    envVarParts.put(key, val);
                }
            }

            EnvVars envVars2 = new EnvVars();
            envVars2.putAll(baseEnvVars);
            envVars2.putAll(envVarParts);
            // This allows us to use recently added key/vals
            // to be substituted
            message.put(MESSAGECONTENTFIELD, PluginUtils.getSubstitutedValue(content,
                    envVars2));

            blob.setMsg(message);
            blob.setTopic(PluginUtils.getSubstitutedValue(getTopic(provider), build.getEnvironment(listener)));
            blob.setTimestamp((new java.util.Date()).getTime() / 1000);

            boolean successTopic = sock.sendMore(blob.getTopic());
            if (failOnError && !successTopic) {
                log.severe("Unhandled exception in perform: Failed to send message (topic)!");
                return new SendResult(false, blob.getMsgId(), blob.getMessageBody());
            }
            boolean successBody = sock.send(blob.toJson().toString());
            if (failOnError && !successBody) {
                log.severe("Unhandled exception in perform: Failed to send message (body)!");
                return new SendResult(false, blob.getMsgId(), blob.getMessageBody());
            }
            log.fine(blob.toJson().toString());
            listener.getLogger().println(blob.toJson().toString());

        } catch (Exception e) {
            if (failOnError) {
                log.severe("Unhandled exception in perform: ");
                log.severe(ExceptionUtils.getStackTrace(e));
                listener.fatalError("Unhandled exception in perform: ");
                listener.fatalError(ExceptionUtils.getStackTrace(e));
                return new SendResult(false, blob.getMsgId(), blob.getMessageBody());
            } else {
                log.warning("Unhandled exception in perform: ");
                log.warning(ExceptionUtils.getStackTrace(e));
                listener.error("Unhandled exception in perform: ");
                listener.error(ExceptionUtils.getStackTrace(e));
                return new SendResult(true, blob.getMsgId(), blob.getMessageBody());
            }
        } finally {
            sock.close();
            context.term();
        }

        return new SendResult(true, blob.getMsgId(), blob.getMessageBody());
    }

    @Override
    public String waitForMessage(Run<?, ?> build, TaskListener listener,
                                 String selector, String variable,
                                 List<MsgCheck> checks,
                                 Integer timeout) {
        log.info("Waiting for message with selector: " + selector);
        for (MsgCheck msgCheck: checks) {
            log.info(" with check: " + msgCheck.toString());
        }
        listener.getLogger().println("Waiting for message with selector: " + selector);
        for (MsgCheck msgCheck: checks) {
            listener.getLogger().println(" with check: " + msgCheck.toString());
        }
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
                        data.getMsg().put("topic", data.getTopic());
                        ZmqMessageSelector selectorObj =
                                ZmqSimpleMessageSelector.parse(selector);
                        log.fine("Evaluating selector: " + selectorObj.toString());
                        listener.getLogger().println("Evaluating selector: " + selectorObj.toString());
                        if (!selectorObj.evaluate(data.getMsg())) {
                            log.fine("false");
                            listener.getLogger().println("selector match failed");
                            continue;
                        }
                        //check checks here
                        boolean allPassed = true;
                        for (MsgCheck check: checks) {
                            if (!provider.verify(data, check)) {
                                allPassed = false;
                                log.info("msg check: " + check.toString() + " failed against: " + provider.formatMessage(data));
                                listener.getLogger().println("msg check: " + check.toString() + " failed against: " + provider.formatMessage(data));
                            }
                        }
                        if (allPassed) {
                            if (checks.size() > 0) {
                                log.info("All msg checks have passed.");
                                listener.getLogger().println("All msg checks have passed.");
                            }
                        } else {
                            log.info("Some msg checks did not pass.");
                            listener.getLogger().println("Some msg checks did not pass.");
                            continue;
                        }
                        String value = data.getMessageBody();
                        if (build != null) {
                            if (StringUtils.isNotEmpty(variable)) {
                                EnvVars vars = new EnvVars();
                                vars.put(variable, value);
                                build.addAction(new CIEnvironmentContributingAction(vars));
                            }
                        }
                        return value;
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

    @Override
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
                    Thread.currentThread().sleep(1000);
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

    @Override
    public boolean isBeingInterrupted() {
        return interrupt;
    }

}
