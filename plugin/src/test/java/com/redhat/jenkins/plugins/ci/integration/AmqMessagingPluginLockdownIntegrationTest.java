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
package com.redhat.jenkins.plugins.ci.integration;

import com.redhat.jenkins.plugins.ci.GlobalCIConfiguration;
import com.redhat.jenkins.plugins.ci.authentication.activemq.UsernameAuthenticationMethod;
import com.redhat.jenkins.plugins.ci.integration.fixtures.ActiveMQContainer;
import com.redhat.jenkins.plugins.ci.messaging.ActiveMqMessagingProvider;
import hudson.security.Permission;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicAuthCache;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jenkinsci.test.acceptance.docker.DockerClassRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;

import static org.hamcrest.MatcherAssert.assertThat;

public class AmqMessagingPluginLockdownIntegrationTest {

    @ClassRule
    public static DockerClassRule<ActiveMQContainer> docker = new DockerClassRule<>(ActiveMQContainer.class);

    @Rule
    public final JenkinsRule j = new JenkinsRule();

    private void configureSecurity(final String admin, final String user) {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        MockAuthorizationStrategy mas = new MockAuthorizationStrategy();
        j.jenkins.setAuthorizationStrategy(mas);
        mas.grant(Jenkins.ADMINISTER).everywhere().to(admin);
        mas.grant(Permission.READ).everywhere().to(user);
    }

    @Before
    public void setUp() throws Exception {
        ActiveMQContainer amq = docker.create();

        GlobalCIConfiguration gcc = GlobalCIConfiguration.get();
        gcc.setConfigs(Collections.singletonList(new ActiveMqMessagingProvider(
                "name",
                createFailoverUrl(amq.getBroker()),
                true,
                "CI",
                null,
                new UsernameAuthenticationMethod("admin", Secret.fromString("redhat"))
        )));

        String adminUser = "admin";
        String user = "user";
        configureSecurity(adminUser, user);

        // TODO test connection. WebClient? Rest?
    }

    private String createFailoverUrl(String broker) {
        return "failover:(" + broker + "," + broker + ")?startupMaxReconnectAttempts=1&maxReconnectAttempts=1";
    }

    /**
     * This test first configures a JMS message provider as an admin user
     * and ensures that a test connection is successful.
     * Then we login in a regular user and attempt to probe the testConnection actions
     * to make sure we are blocked.
     */
    @Test
    public void testBlockNonAdminAccess() throws Exception {
        j.jenkins.setCrumbIssuer(null);

        String format = "/descriptorByName/com.redhat.jenkins.plugins.ci.authentication.activemq.%s/testConnection?broker=tcp%%3A%%2F%%2F192.168.182.142%%3A80&username=JMS&password=JMS";
        String userAuthUrl = j.getURL().toExternalForm() + String.format(format, "UsernameAuthenticationMethod");
        String sslAuthUrl = j.getURL().toExternalForm() + String.format(format, "SSLCertificateAuthenticationMethod");

        String warning = "content should have user is missing the Overall/Administer permission...but is ";
        String errorString = "user is missing the Overall/Administer permission";

        HashMap<String, String> rMap = performPOST(userAuthUrl, "user", "user");
        assertThat("code is " + rMap.get("statuscode") + " should be 403; url was " + userAuthUrl, rMap.get("statuscode").equals("403"));
        assertThat(warning + rMap.get("responsebody"),
                rMap.get("responsebody").indexOf(errorString) > 0);

        rMap = performPOST(sslAuthUrl, "user", "user");
        assertThat("code is " + rMap.get("statuscode") + " should be 403; url was " + sslAuthUrl, rMap.get("statuscode").equals("403"));
        assertThat(warning + rMap.get("responsebody"),
                rMap.get("responsebody").indexOf(errorString) > 0);

        rMap = performPOST(userAuthUrl, "admin", "admin");
        assertThat("code is " + rMap.get("statuscode") + " should be 200; url was " + userAuthUrl, rMap.get("statuscode").equals("200"));
    }

    private HashMap<String, String> performPOST(String url, String username, String password) throws IOException {

        URI uri = URI.create(url);
        HttpHost host = new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme());
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(uri.getHost(), uri.getPort()), new UsernamePasswordCredentials(username, password));
        // Create AuthCache instance
        AuthCache authCache = new BasicAuthCache();
        // Generate BASIC scheme object and add it to the local auth cache
        BasicScheme basicAuth = new BasicScheme();
        authCache.put(host, basicAuth);
        CloseableHttpClient httpClient = HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
        HttpPost post = new HttpPost(uri);
        // Add AuthCache to the execution context
        HttpClientContext localContext = HttpClientContext.create();
        localContext.setAuthCache(authCache);

        HttpResponse response = httpClient.execute(host, post, localContext);

        HashMap<String, String> returnMap = new HashMap<>();
        returnMap.put("statuscode", Integer.toString(response.getStatusLine().getStatusCode()));
        returnMap.put("responsebody", EntityUtils.toString(response.getEntity()));
        return returnMap;
    }
}
