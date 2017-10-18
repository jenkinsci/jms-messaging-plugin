package com.redhat.jenkins.plugins.ci.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.messaging.data.FedmsgMessage;
import com.redhat.utils.PluginUtils;
import hudson.EnvVars;
import org.zeromq.ZMQ;
import org.zeromq.ZMsg;
import org.zeromq.jms.selector.ZmqMessageSelector;
import org.zeromq.jms.selector.ZmqSimpleMessageSelector;

import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.redhat.jenkins.plugins.ci.messaging.FedMsgMessagingWorker.DEFAULT_PREFIX;

public class FedMsgMessageWatcher extends JMSMessageWatcher {

    private static final Logger log = Logger.getLogger(FedMsgMessagingWorker.class.getName());

    private ZMQ.Context lcontext;
    private ZMQ.Poller lpoller;
    private ZMQ.Socket lsocket;

    private String topic;

    private FedMsgMessagingProvider fedMsgMessagingProvider;
    private boolean interrupted;

    @Override
    public String watch() {

        fedMsgMessagingProvider = (FedMsgMessagingProvider)provider;

        log.info("Waiting for message with selector: " + selector);
        for (MsgCheck msgCheck: checks) {
            log.info(" with check: " + msgCheck.toString());
        }
        taskListener.getLogger().println("Waiting for message with selector: " + selector);
        for (MsgCheck msgCheck: checks) {
            taskListener.getLogger().println(" with check: " + msgCheck.toString());
        }
        log.info(" with timeout: " + timeout);
        taskListener.getLogger().println(" with timeout: " + timeout);

        lcontext = ZMQ.context(1);
        lpoller = lcontext.poller(1);
        lsocket = lcontext.socket(ZMQ.SUB);

        topic = getTopic(overrides, fedMsgMessagingProvider.getTopic(), DEFAULT_PREFIX);
        try {
            EnvVars envVars = new EnvVars();
            environmentExpander.expand(envVars);

            topic = PluginUtils.getSubstitutedValue(topic, envVars);
        } catch (IOException e) {
            log.warning(e.getMessage());
        } catch (InterruptedException e) {
            log.warning(e.getMessage());
        }

        lsocket.subscribe(topic.getBytes());
        lsocket.setLinger(0);
        lsocket.connect(fedMsgMessagingProvider.getHubAddr());
        lpoller.register(lsocket, ZMQ.Poller.POLLIN);

        ObjectMapper mapper = new ObjectMapper();
        long startTime = new Date().getTime();

        int timeoutInMs = timeout * 60 * 1000;
        interrupted = false;
        try {
            while ((new Date().getTime() - startTime) < timeoutInMs) {
                if (lpoller.poll(1000) > 0) {
                    if (lpoller.pollin(0)) {
                        ZMsg z = ZMsg.recvMsg(lpoller.getSocket(0));

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
                            if (!fedMsgMessagingProvider.verify(data, check)) {
                                allPassed = false;
                                log.fine("msg check: " + check.toString() + " failed against: "
                                        + fedMsgMessagingProvider.formatMessage(data));
                            }
                        }
                        if (allPassed) {
                            if (checks.size() > 0) {
                                log.fine("All msg checks have passed.");
                            }
                        } else {
                            log.fine("Some msg checks did not pass.");
                            continue;
                        }
                        String value = data.getMessageBody();
                        return value;
                    }
                }
            }
            if (interrupted) {
                return null;
            }
            log.severe("Timed out waiting for message!");
            taskListener.getLogger().println("Timed out waiting for message!");
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unhandled exception waiting for message.", e);
        } finally {
            try {
                if (lpoller != null) {
                    ZMQ.Socket s = lpoller.getSocket(0);
                    lpoller.unregister(s);
                    s.disconnect(fedMsgMessagingProvider.getHubAddr());
                    lsocket.unsubscribe(this.topic.getBytes());
                    lsocket.close();
                }
                if (lcontext != null) {
                    lcontext.term();
                }
            } catch (Exception e) {
                log.fine(e.getMessage());
            }
            lpoller = null;
            lsocket = null;
        }
        return null;
    }

    @Override
    public void interrupt() {
        log.severe("start interrupt");
        taskListener.getLogger().println("start interrupt");
        interrupted = true;
        try {
            if (lpoller != null) {
                ZMQ.Socket s = lpoller.getSocket(0);
                lpoller.unregister(s);
                s.disconnect(fedMsgMessagingProvider.getHubAddr());
                lsocket.unsubscribe(this.topic.getBytes());
                lsocket.close();
            }
            if (lcontext != null) {
                lcontext.term();
            }
            log.severe("term done");
            taskListener.getLogger().println("term done");
        } catch (Exception e) {
            log.fine(e.getMessage());
        }
        lpoller = null;
        lsocket = null;
        interrupted = false;
    }
}
