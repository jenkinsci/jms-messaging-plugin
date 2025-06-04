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
package com.redhat.jenkins.plugins.ci;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;

import com.redhat.jenkins.plugins.ci.authentication.activemq.UsernameAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.authentication.rabbitmq.SSLCertificateAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.RabbitMQMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.topics.DefaultTopicProvider;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;

public class JcascTest {

    @Rule
    public JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    @Test
    @ConfiguredWithCode("JcascTest/casc.yaml")
    public void load() {
        GlobalCIConfiguration gc = GlobalCIConfiguration.get();
        ActiveMqMessagingProvider amq = (ActiveMqMessagingProvider) gc.getProvider("Active MQ");
        assertEquals("foo.com:4242", amq.getBroker());
        assertEquals("active.mq.com", amq.getTopic());
        assertThat(amq.getTopicProvider(), Matchers.instanceOf(DefaultTopicProvider.class));
        assertEquals(false, amq.getUseQueues());
        UsernameAuthenticationMethod amqam = (UsernameAuthenticationMethod) amq.getAuthenticationMethod();
        assertEquals("foo", amqam.getUsername());
        assertEquals("bar", amqam.getPassword().getPlainText());

        RabbitMQMessagingProvider rmq = (RabbitMQMessagingProvider) gc.getProvider("Rabbit MQ");
        assertEquals("ex", rmq.getExchange());
        assertEquals("rabbitmq.example.com", rmq.getHostname());
        assertEquals(4545, rmq.getPortNumber().intValue());
        assertEquals("foo.bar", rmq.getQueue());
        assertEquals("baz", rmq.getTopic());
        assertEquals("rabbitvh.example.com", rmq.getVirtualHost());
        SSLCertificateAuthenticationMethod rmqam = (SSLCertificateAuthenticationMethod) rmq.getAuthenticationMethod();
        assertEquals("/tmp/key", rmqam.getKeystore());
        assertEquals("/tmp/trust", rmqam.getTruststore());
        assertEquals("keypwd", rmqam.getKeypwd().getPlainText());
        assertEquals("trustpwd", rmqam.getTrustpwd().getPlainText());
    }
}
