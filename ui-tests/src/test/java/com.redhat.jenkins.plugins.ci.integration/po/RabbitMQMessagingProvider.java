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
@Describable("RabbitMQ")
public class RabbitMQMessagingProvider extends MessagingProvider {

    public final Control name           = control("name");
    public final Control hostname       = control("hostname");
    public final Control portNumber     = control("portNumber");
    public final Control virtualHost    = control("virtualHost");
    public final Control topic          = control("topic");
    public final Control exchange       = control("exchange");
    public final Control queue          = control("queue");

    public RabbitMQMessagingProvider(PageObject parent, String path) {
        super(parent, path);
    }

    public RabbitMQMessagingProvider(GlobalCIConfiguration context) {
        super(context);
    }

    public RabbitMQMessagingProvider name(String nameVal) {
        name.set(nameVal);
        return this;
    }
    public RabbitMQMessagingProvider hostname(String hostnameVal) {
        hostname.set(hostnameVal);
        return this;
    }
    public RabbitMQMessagingProvider portNumber(String portNumberVal) {
        portNumber.set(portNumberVal);
        return this;
    }
    public RabbitMQMessagingProvider virtualHost(String virtualHostVal) {
        virtualHost.set(virtualHostVal);
        return this;
    }
    public RabbitMQMessagingProvider topic(String topicVal) {
        topic.set(topicVal);
        return this;
    }
    public RabbitMQMessagingProvider exchange(String exchangeVal) {
        exchange.set(exchangeVal);
        return this;
    }
    public RabbitMQMessagingProvider queue(String queueVal) {
        queue.set(queueVal);
        return this;
    }

    public void testConnection() {
        clickButton("Test Connection");
    }

    @Override
    public RabbitMQMessagingProvider addMessagingProvider() {
        String path = createPageArea("configs", new Runnable() {
            @Override public void run() {
                control("hetero-list-add[configs]").selectDropdownMenu(RabbitMQMessagingProvider.class);
            }
        });
        return new RabbitMQMessagingProvider(getPage(), path);
    }

    public RabbitMQMessagingProvider userNameAuthentication(String user, String password) {
        Control select = control("/");
        select.select("1");
        control("/authenticationMethod/username").set(user);
        control("/authenticationMethod/password").set(password);
        return this;
    }

    public RabbitMQMessagingProvider sslCertAuthentication(String keystore,
                                                           String keystorePass,
                                                           String trustStore,
                                                           String trustStorePass) {
        Control select = control("/");
        select.select("0");
        control("/authenticationMethod/keystore").set(keystore);
        control("/authenticationMethod/keypwd").set(keystorePass);
        control("/authenticationMethod/truststore").set(trustStore);
        control("/authenticationMethod/trustpwd").set(trustStorePass);
        return this;
    }
}
