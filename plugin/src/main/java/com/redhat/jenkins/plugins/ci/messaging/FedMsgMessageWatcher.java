package com.redhat.jenkins.plugins.ci.messaging;

import static com.redhat.jenkins.plugins.ci.messaging.FedMsgMessagingWorker.DEFAULT_TOPIC;

import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.zeromq.ZMQ;
import org.zeromq.ZMsg;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.jenkins.plugins.ci.messaging.checks.MsgCheck;
import com.redhat.jenkins.plugins.ci.messaging.data.FedmsgMessage;
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
public class FedMsgMessageWatcher extends JMSMessageWatcher {

    private static final Logger log = Logger.getLogger(FedMsgMessageWatcher.class.getName());

    private ZMQ.Context lcontext;
    private ZMQ.Poller lpoller;
    private ZMQ.Socket lsocket;

    private String topic;

    private FedMsgMessagingProvider fedMsgMessagingProvider;
    private boolean interrupted;

    public FedMsgMessageWatcher(String jobname) {
        super(jobname);
    }

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

        topic = PluginUtils.getSubstitutedValue(getTopic(overrides, fedMsgMessagingProvider.getTopic(), DEFAULT_TOPIC), environment);

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

                        if (!provider.verify(data.getBodyJson(), checks, jobname)) {
                            continue;
                        }
                        return data.getBodyJson();
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
