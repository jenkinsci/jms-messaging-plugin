package com.redhat.jenkins.plugins.ci.integration.po;

import org.jenkinsci.test.acceptance.po.Control;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.Jenkins;
import org.jenkinsci.test.acceptance.po.JenkinsConfig;
import org.jenkinsci.test.acceptance.po.PageObject;

/**
 * Created by shebert on 01/12/16.
 */
@Describable("Active MQ")
public class ActiveMqMessagingProvider extends MessagingProvider {

    public final Control name       = control("name");
    public final Control broker     = control("broker");
    public final Control topic      = control("topic");
    public final Control user       = control("user");
    public final Control password   = control("password");

    public ActiveMqMessagingProvider(PageObject parent, String path) {
        super(parent, path);
    }

    public ActiveMqMessagingProvider(GlobalCIConfiguration context) {
        super(context);
    }

    public ActiveMqMessagingProvider name(String nameVal) {
        name.set(nameVal);
        return this;
    }
    public ActiveMqMessagingProvider broker(String brokerVal) {
        broker.set(brokerVal);
        return this;
    }
    public ActiveMqMessagingProvider topic(String topicVal) {
        topic.set(topicVal);
        return this;
    }
    public ActiveMqMessagingProvider user(String userVal) {
        user.set(userVal);
        return this;
    }
    public ActiveMqMessagingProvider password(String passwordVal) {
        password.set(passwordVal);
        return this;
    }

    public void testConnection() {
        clickButton("Test Connection");
    }

    @Override
    public ActiveMqMessagingProvider addMessagingProvider() {
        String path = createPageArea("configs", new Runnable() {
            @Override public void run() {
                control("hetero-list-add[configs]").selectDropdownMenu(ActiveMqMessagingProvider.class);
            }
        });
        return new ActiveMqMessagingProvider(getPage(), path);
    }

}
