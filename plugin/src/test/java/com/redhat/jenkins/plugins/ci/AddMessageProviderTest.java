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

import com.redhat.jenkins.plugins.ci.authentication.activemq.SSLCertificateAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingProvider;
import com.redhat.jenkins.plugins.ci.messaging.topics.UMBTopicProvider;
import hudson.util.Secret;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import static org.junit.Assert.assertEquals;

/**
 * @author ogondza.
 */
public class AddMessageProviderTest {
    @Rule public final JenkinsRule j = new JenkinsRule();

    @Test
    public void addMessageProvider(){
        ActiveMqMessagingProvider provider = new ActiveMqMessagingProvider(
                "UMB",
                "ssh://umb.api.redhat.com:61616",
                false,
                "VirtualTopic.eng.cd.rh-sso.&gt;",
                new UMBTopicProvider(),
                new SSLCertificateAuthenticationMethod(
                        "/ver/run/secrets/casc-secret/rhssocdjenkins.jks",
                        Secret.fromString("secret1"),
                        "/var/run/secrets/casc-secret/rh-it-trust.jks",
                        Secret.fromString("secret2")
                        )
        );
        GlobalCIConfiguration gc  = GlobalCIConfiguration.get();
        gc.addMessageProvider(provider);
        assertEquals(gc.getProvider("UMB"), provider);
        GlobalCIConfiguration gc2 = new GlobalCIConfiguration();
        assertEquals(gc.getConfigs(), gc2.getConfigs());

    }
}
