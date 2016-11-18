package com.redhat.jenkins.plugins.ci;

import hudson.model.AbstractProject;
import hudson.security.ACL;

import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

import jenkins.model.Jenkins;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.apache.activemq.ActiveMQConnectionFactory;

import com.redhat.utils.MessageUtils;

public class CITriggerThread implements Runnable {
    private static final Logger log = Logger.getLogger(CITriggerThread.class.getName());

    private static final Integer WAIT_HOURS = 1;
    private static final Integer RETRY_MINUTES = 1;
    private static final Integer WAIT_SECONDS = 2;

    private String jobname;
    private String selector;
    private Connection connection;
    private TopicSubscriber subscriber;

    public CITriggerThread(String jobname, String selector) {
        this.jobname = jobname;
        this.selector = selector;
    }

    public void run() {
        SecurityContext old = ACL.impersonate(ACL.SYSTEM);
        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (connection != null || subscribe()) {
                    try {
                        Message m = subscriber.receive(WAIT_HOURS * 60 * 60 * 1000); // In milliseconds!
                        if (m != null) {
                            process(m);
                        } else {
                            log.info("No message received for the past " + WAIT_HOURS + " hours, re-subscribing job '" + jobname + "'.");
                            unsubscribe();
                        }
                    } catch (JMSException e) {
                        if (!Thread.currentThread().isInterrupted()) {
                            // Something other than an interrupt causes this.
                            // Unsubscribe, but stay in our loop and try to reconnect..
                            log.log(Level.WARNING, "JMS exception raised while receiving, going to re-subscribe job '" + jobname + "'.", e);
                            unsubscribe(); // Try again next time.
                        }
                    }
                } else {
                    // Should not get here unless subscribe failed. This could be
                    // because global configuration may not yet be available or
                    // because we were interrupted. If not the latter, let's sleep
                    // for a bit before retrying.
                    if (!Thread.currentThread().isInterrupted()) {
                        try {
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
            }
            log.info("Shutting down trigger thread for job '" + jobname + "'.");
            unsubscribe();
        } finally {
            SecurityContextHolder.setContext(old);
        }
    }

    private Boolean subscribe() {
        cleanup();

        String ip = null;
        try {
            ip = Inet4Address.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            log.severe("Unable to get localhost IP address.");
        }

        GlobalCIConfiguration config = GlobalCIConfiguration.get();
        if (config != null) {
            String user = config.getUser();
            String password = config.getPassword().getPlainText();
            String broker = config.getBroker();
            String topic = config.getTopic();

            if (ip != null & user != null && password != null && topic != null && broker != null) {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        log.info("Subscribing job '" + jobname + "' to CI topic.");
                        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(user, password, broker);
                        connection = connectionFactory.createConnection();
                        connection.setClientID(ip + "_" + jobname);
                        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                        Topic destination = session.createTopic(topic);

                        subscriber = session.createDurableSubscriber(destination, jobname, selector, false);
                        connection.start();
                        log.info("Successfully subscribed job '" + jobname + "' to CI topic with selector: " + selector);
                        return true;
                    } catch (JMSException ex) {

                        // Either we were interrupted, or something else went
                        // wrong. If we were interrupted, then we will jump ship
                        // on the next iteration. If something else happened,
                        // then we just unsubscribe here, sleep, so that we may
                        // try again on the next iteration.

                        if (!Thread.currentThread().isInterrupted()) {

                            log.log(Level.SEVERE, "JMS exception raised while subscribing job '" + jobname + "', retrying in " + RETRY_MINUTES + " minutes.", ex);
                            unsubscribe();

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
        }
        return false;
    }

    private void process (Message message) {
        try {
            Map<String, String> params = new HashMap<String, String>();
            params.put("CI_MESSAGE", MessageUtils.getMessageBody(message));

            @SuppressWarnings("unchecked")
            Enumeration<String> e = message.getPropertyNames();
            while (e.hasMoreElements()) {
                String s = e.nextElement();
                if (message.getStringProperty(s) != null) {
                    params.put(s, message.getObjectProperty(s).toString());
                }
            }

            CIBuildTrigger trigger = findTrigger(jobname);
            if (trigger != null) {
                log.info("Scheduling job '" + jobname + "' based on message:\n" + MessageUtils.formatMessage(message));
                trigger.scheduleBuild(params);
            } else {
                log.log(Level.WARNING, "Unable to find CIBuildTrigger for '" + jobname + "'.");
            }
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unhandled exception processing message:\n" + MessageUtils.formatMessage(message), e);
        }
    }

    private void unsubscribe() {
        log.info("Unsubcribing job '" + jobname + "' from the CI topic.");
        cleanup();
    }

    private void cleanup() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ce) {
            } finally {
                connection = null;
            }
        }
        if (subscriber != null) {
            try {
                subscriber.close();
            } catch (Exception se) {
            } finally {
                subscriber = null;
            }
        }
    }

    public static CIBuildTrigger findTrigger(String fullname) {
        Jenkins jenkins = Jenkins.getInstance();
        AbstractProject<?, ?> p = jenkins.getItemByFullName(fullname, AbstractProject.class);
        if (p != null) {
            return p.getTrigger(CIBuildTrigger.class);
        }
        return null;
    }
}
