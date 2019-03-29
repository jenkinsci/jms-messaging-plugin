package com.redhat.jenkins.plugins.ci.integration.po;

import org.jenkinsci.test.acceptance.po.Control;
import org.jenkinsci.test.acceptance.po.Describable;
import org.jenkinsci.test.acceptance.po.PageObject;

/*
 * The MIT License
 *
 * Copyright (c) Red Hat, Inc.
 * Copyright (c) Valentin Titov
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
@Describable("Kafka")
public class KafkaMessagingProvider extends MessagingProvider {

    public final Control name     = control("name");
    public final Control hubAddr  = control("hubAddr");
    public final Control pubAddr  = control("pubAddr");
    public final Control topic    = control("topic");

    public KafkaMessagingProvider(PageObject parent, String path) {
        super(parent, path);
    }

    public KafkaMessagingProvider(GlobalCIConfiguration context) {
        super(context);
    }

    public KafkaMessagingProvider name(String nameVal) {
        name.set(nameVal);
        return this;
    }
    public KafkaMessagingProvider hubAddr(String hubAddrVal) {
        hubAddr.set(hubAddrVal);
        return this;
    }
    public KafkaMessagingProvider pubAddr(String pubAddrVal) {
        pubAddr.set(pubAddrVal);
        return this;
    }
    public KafkaMessagingProvider topic(String topicVal) {
        topic.set(topicVal);
        return this;
    }
    public void testConnection() {
        clickButton("Test Connection");
    }

    @Override
    public KafkaMessagingProvider addMessagingProvider() {
        String path = createPageArea("configs", new Runnable() {
            @Override public void run() {
                control("hetero-list-add[configs]").selectDropdownMenu(KafkaMessagingProvider.class);
            }
        });
        return new KafkaMessagingProvider(getPage(), path);
    }

}
