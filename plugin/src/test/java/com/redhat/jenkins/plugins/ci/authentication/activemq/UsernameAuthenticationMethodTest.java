/*
 * Copyright (c) 2019 Adobe Systems Incorporated
 * 
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 */
package com.redhat.jenkins.plugins.ci.authentication.activemq;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import hudson.util.Secret;

public class UsernameAuthenticationMethodTest {

    private static final String USER = "user";
    private static final String PASSWORD = "password";

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule log = new LoggerRule();

    private UsernameAuthenticationMethod method;

    @Before
    public void setup() {
        method = new UsernameAuthenticationMethod(USER, Secret.fromString(PASSWORD));
    }

    @Test
    public void testGetConnectionFactoryTcp() {
        ConnectionFactory factory = method.getConnectionFactory("tcp://example.com");
        assertTrue(factory instanceof ActiveMQConnectionFactory);
        ActiveMQConnectionFactory f = (ActiveMQConnectionFactory) factory;
        assertEquals(USER, f.getUserName());
        assertEquals(PASSWORD, f.getPassword());
    }

    @Test
    public void testGetConnectionFactoryAmqp() {
        String uri = "amqps://example.com";
        ConnectionFactory factory = method.getConnectionFactory(uri);
        assertTrue(factory instanceof JmsConnectionFactory);
        JmsConnectionFactory f = (JmsConnectionFactory) factory;
        assertEquals(uri, f.getRemoteURI());
        assertEquals(USER, f.getUsername());
        assertEquals(PASSWORD, f.getPassword());
    }
}
