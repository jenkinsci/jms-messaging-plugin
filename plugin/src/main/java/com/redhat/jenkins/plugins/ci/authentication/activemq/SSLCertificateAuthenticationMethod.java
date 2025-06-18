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
package com.redhat.jenkins.plugins.ci.authentication.activemq;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;

import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.redhat.jenkins.plugins.ci.Messages;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

public class SSLCertificateAuthenticationMethod extends ActiveMQAuthenticationMethod {
    private static final long serialVersionUID = -5934219869726669459L;
    private transient static final Logger log = Logger.getLogger(SSLCertificateAuthenticationMethod.class.getName());

    private String keyStoreCredentialId;
    private String trustStoreCredentialId;

    @DataBoundConstructor
    public SSLCertificateAuthenticationMethod(String keyStoreCredentialId, String trustStoreCredentialId) {
        this.setKeyStoreCredentialId(keyStoreCredentialId);
        this.setTrustStoreCredentialId(trustStoreCredentialId);
    }

    public String getKeyStoreCredentialId() {
        return keyStoreCredentialId;
    }

    public void setKeyStoreCredentialId(String keyStoreCredentialId) {
        this.keyStoreCredentialId = keyStoreCredentialId;
    }

    public String getTrustStoreCredentialId() {
        return trustStoreCredentialId;
    }

    public void setTrustStoreCredentialId(String trustStoreCredentialId) {
        this.trustStoreCredentialId = trustStoreCredentialId;
    }

    @Override
    public ActiveMQSslConnectionFactory getConnectionFactory(String broker) {
        StandardCertificateCredentials ksCreds = getStandardCertificateCredentials(keyStoreCredentialId);
        if (ksCreds == null) {
            return null;
        }

        StandardCertificateCredentials tsCreds = getStandardCertificateCredentials(trustStoreCredentialId);
        if (tsCreds == null) {
            return null;
        }

        try {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ksCreds.getKeyStore(), ksCreds.getPassword().getPlainText().toCharArray());

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(tsCreds.getKeyStore());

            ActiveMQSslConnectionFactory connectionFactory = new ActiveMQSslConnectionFactory(broker);
            connectionFactory.setKeyAndTrustManagers(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            return connectionFactory;
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unhandled exception creating connection factory.", e);
        }
        return null;
    }

    @Override
    public Descriptor<ActiveMQAuthenticationMethod> getDescriptor() {
        return Jenkins.get().getDescriptorByType(SSLCertificateAuthenticationMethodDescriptor.class);
    }

    @Extension
    @Symbol("sslCertificate")
    public static class SSLCertificateAuthenticationMethodDescriptor extends AuthenticationMethodDescriptor {

        @Override
        public @Nonnull String getDisplayName() {
            return "SSL Certificate Authentication";
        }

        @Override
        public SSLCertificateAuthenticationMethod newInstance(StaplerRequest2 sr, JSONObject jo) {
            return new SSLCertificateAuthenticationMethod(jo.getString("keyStoreCredentialId"),
                    jo.getString("trustStoreCredentialId"));
        }

        public String getConfigPage() {
            return "sslcert.jelly";
        }

        @POST
        public ListBoxModel doFillKeyStoreCredentialIdItems(@AncestorInPath Item project,
                @QueryParameter String keyStoreCredentialId) {

            checkAdmin();

            return doFillCredentials(project, keyStoreCredentialId, StandardCertificateCredentials.class,
                    "Certificate");
        }

        @POST
        public ListBoxModel doFillTrustStoreCredentialIdItems(@AncestorInPath Item project,
                @QueryParameter String trustStoreCredentialId) {

            checkAdmin();

            return doFillCredentials(project, trustStoreCredentialId, StandardCertificateCredentials.class,
                    "Certificate");
        }

        @POST
        public FormValidation doTestConnection(@QueryParameter("broker") String broker,
                @QueryParameter("keyStoreCredentialId") String keyStoreCredentialId,
                @QueryParameter("trustStoreCredentialId") String trustStoreCredentialId) {

            checkAdmin();

            broker = StringUtils.strip(StringUtils.stripToNull(broker), "/");
            Connection connection = null;
            Session session = null;
            if (broker != null && isValidURL(broker)) {
                try {
                    SSLCertificateAuthenticationMethod sam = new SSLCertificateAuthenticationMethod(
                            keyStoreCredentialId, trustStoreCredentialId);
                    ActiveMQSslConnectionFactory connectionFactory = sam.getConnectionFactory(broker);
                    connection = connectionFactory.createConnection();
                    connection.start();
                    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    session.close();
                    connection.close();
                    return FormValidation.ok(Messages.SuccessBrokerConnect(broker));
                } catch (Exception e) {
                    log.log(Level.SEVERE,
                            "Unhandled exception in SSLCertificateAuthenticationMethod.doTestConnection: ", e);
                    return FormValidation.error(Messages.Error() + ": " + e);
                } finally {
                    try {
                        if (session != null) {
                            session.close();
                        }
                        if (connection != null) {
                            connection.close();
                        }
                    } catch (JMSException e) {
                        //
                    }
                }
            } else {
                return FormValidation.error(Messages.InvalidURI());
            }
        }
    }
}
