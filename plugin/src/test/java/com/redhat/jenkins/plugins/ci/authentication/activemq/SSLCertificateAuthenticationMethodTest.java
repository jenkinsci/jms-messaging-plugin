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

import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import hudson.util.Secret;

public class SSLCertificateAuthenticationMethodTest {

    private static final String KEYSTORE = "keystore";
    private static final String KEYPWD = "kwypwd";
    private static final String TRUSTSTORE = "truststore";
    private static final String TRUSTPASSWORD = "trustpwd";

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule log = new LoggerRule();

    private SSLCertificateAuthenticationMethod method;

    @Before
    public void setup() {
        method = new SSLCertificateAuthenticationMethod(KEYSTORE, Secret.fromString(KEYPWD), TRUSTSTORE,
                Secret.fromString(TRUSTPASSWORD));
    }

    @Test
    public void testGetConnectionFactoryTcp() {
        ConnectionFactory factory = method.getConnectionFactory("tcps://example.com");
        assertTrue(factory instanceof ActiveMQSslConnectionFactory);
        ActiveMQSslConnectionFactory f = (ActiveMQSslConnectionFactory) factory;
        assertEquals(KEYSTORE, f.getKeyStore());
        assertEquals(KEYPWD, f.getKeyStorePassword());
        assertEquals(TRUSTSTORE, f.getTrustStore());
        assertEquals(TRUSTPASSWORD, f.getTrustStorePassword());
    }

    @Test
    public void testGetConnectionFactoryAmqp() {
        String uri = "amqps://example.com";
        ConnectionFactory factory = method.getConnectionFactory(uri);
        assertTrue(factory instanceof JmsConnectionFactory);
        JmsConnectionFactory f = (JmsConnectionFactory) factory;
        assertEquals(String.format(
                "%s?transport.keyStoreLocation=%s&transport.keyStorePassword=%s&transport.trustStoreLocation=%s&transport.trustStorePassword=%s",
                uri, KEYSTORE, KEYPWD, TRUSTSTORE, TRUSTPASSWORD), f.getRemoteURI());
    }
}
