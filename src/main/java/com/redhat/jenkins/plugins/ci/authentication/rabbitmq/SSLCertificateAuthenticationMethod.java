package com.redhat.jenkins.plugins.ci.authentication.rabbitmq;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultSaslConfig;

import javax.net.ssl.*;
import javax.servlet.ServletException;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.redhat.jenkins.plugins.ci.Messages;
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
public class SSLCertificateAuthenticationMethod extends RabbitMQAuthenticationMethod {
    private static final long serialVersionUID = -5934219869726669459L;
    private transient static final Logger log = Logger.getLogger(SSLCertificateAuthenticationMethod.class.getName());

    private String username;
    private String keystore;
    private Secret keypwd = Secret.fromString("");
    private String truststore;
    private Secret trustpwd = Secret.fromString("");

    @DataBoundConstructor
    public SSLCertificateAuthenticationMethod(String username, String keystore, Secret keypwd, String truststore, Secret trustpwd) {
        this.setUsername(username);
        this.setKeystore(keystore);
        this.setKeypwd(keypwd);
        this.setTruststore(truststore);
        this.setTrustpwd(trustpwd);
    }

    public String getUsername() { return username; }

    public void setUsername(String username) { this.username = username; }

    public String getKeystore() {
        return keystore;
    }

    public void setKeystore(String keystore) {
        this.keystore = keystore;
    }

    public Secret getKeypwd() {
        return keypwd;
    }

    public void setKeypwd(Secret password) {
        this.keypwd = password;
    }

    public String getTruststore() {
        return truststore;
    }

    public void setTruststore(String truststore) {
        this.truststore = truststore;
    }

    public Secret getTrustpwd() {
        return trustpwd;
    }

    public void setTrustpwd(Secret trustpwd) {
        this.trustpwd = trustpwd;
    }

    @Override
    public ConnectionFactory getConnectionFactory(String hostname, Integer portNumber, String virtualHost) {
        try {
            // Prepare SSL context
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(new FileInputStream(getKeystore()), getKeypwd().getPlainText().toCharArray());

            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
            keyManagerFactory.init(ks, getKeypwd().getPlainText().toCharArray());

            KeyStore tks = KeyStore.getInstance("JKS");
            tks.load(new FileInputStream(getTruststore()), getTrustpwd().getPlainText().toCharArray());

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
            trustManagerFactory.init(tks);

            SSLContext c = SSLContext.getInstance("TLSv1.2");
            c.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setUsername(getUsername());
            connectionFactory.setHost(hostname);
            connectionFactory.setPort(portNumber);
            connectionFactory.setVirtualHost(virtualHost);
            connectionFactory.useSslProtocol(c);
            connectionFactory.setSaslConfig(DefaultSaslConfig.EXTERNAL);
            connectionFactory.enableHostnameVerification();
            return connectionFactory;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unhandled exception creating connection factory.", e);;
        }
        return null;
    }

    @Override
    public Descriptor<RabbitMQAuthenticationMethod> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(SSLCertificateAuthenticationMethodDescriptor.class);
    }

    @Extension
    public static class SSLCertificateAuthenticationMethodDescriptor extends AuthenticationMethodDescriptor {

        @Override
        public String getDisplayName() {
            return "SSL Certificate Authentication";
        }

        @Override
        public SSLCertificateAuthenticationMethod newInstance(StaplerRequest sr, JSONObject jo) {
            return new SSLCertificateAuthenticationMethod(jo.getString("username"),jo.getString("keystore"), Secret.fromString(jo.getString("keypwd")),
                                                          jo.getString("truststore"), Secret.fromString(jo.getString("trustpwd")));
        }

        public String getConfigPage() {
            return "sslcert.jelly";
        }

        @RequirePOST
        public FormValidation doTestConnection(@QueryParameter("username") String username,
                                               @QueryParameter("hostname") String hostname,
                                               @QueryParameter("portNumber") Integer portNumber,
                                               @QueryParameter("virtualHost") String virtualHost,
                                               @QueryParameter("keystore") String keystore,
                                               @QueryParameter("keypwd") String keypwd,
                                               @QueryParameter("truststore") String truststore,
                                               @QueryParameter("trustpwd") String trustpwd) throws ServletException {

            checkAdmin();

            Connection connection = null;
            Channel channel = null;
            try {
                SSLCertificateAuthenticationMethod sam = new SSLCertificateAuthenticationMethod(username,
                        keystore, Secret.fromString(keypwd), truststore, Secret.fromString(trustpwd));
                ConnectionFactory connectionFactory = sam.getConnectionFactory(hostname, portNumber, virtualHost);
                connection = connectionFactory.newConnection();
                channel = connection.createChannel();
                channel.close();
                connection.close();
                return FormValidation.ok(Messages.SuccessBrokerConnect(hostname + ":" + portNumber));
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unhandled exception in SSLCertificateAuthenticationMethod.doTestConnection: ", e);
                return FormValidation.error(Messages.Error() + ": " + e);
            } finally {
                try {
                    if (channel != null) {
                        channel.close();
                    }
                    if (connection != null) {
                        connection.close();
                    }
                } catch (Exception e) {
                    //
                }
            }
        }
    }
}
