package com.redhat.jenkins.plugins.ci.integration.po;

import org.jenkinsci.test.acceptance.po.Control;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.PageObject;

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
@Describable("Active MQ")
public class ActiveMqMessagingProvider extends MessagingProvider {

    public final Control name       = control("name");
    public final Control broker     = control("broker");
    public final Control useQueues  = control("useQueues");
    public final Control topic      = control("topic");

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
    public ActiveMqMessagingProvider useQueues(boolean value) {
        useQueues.check(value);
        return this;
    }
    public ActiveMqMessagingProvider topic(String topicVal) {
        topic.set(topicVal);
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

    public ActiveMqMessagingProvider userNameAuthentication(String user, String password) {
        Control select = control("/[1]");
        select.select("1");
        control("/authenticationMethod/username").set(user);
        control("/authenticationMethod/password").set(password);
        return this;
    }

    public ActiveMqMessagingProvider sslCertAuthentication(String keystore,
                                                           String keystorePass,
                                                           String trustStore,
                                                           String trustStorePass) {
        Control select = control("/[0]");
        select.select("0");
        control("/authenticationMethod/keystore").set(keystore);
        control("/authenticationMethod/keypwd").set(keystorePass);
        control("/authenticationMethod/truststore").set(trustStore);
        control("/authenticationMethod/trustpwd").set(trustStorePass);
        return this;
    }
}
