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

import com.redhat.jenkins.plugins.ci.Messages;
import com.redhat.utils.PluginUtils;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;
import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SSLCertificateAuthenticationMethod extends ActiveMQAuthenticationMethod {
    private static final long serialVersionUID = -5934219869726669459L;
    private transient static final Logger log = Logger.getLogger(SSLCertificateAuthenticationMethod.class.getName());

    private String keystore;
    private Secret keypwd = Secret.fromString("");
    private String truststore;
    private Secret trustpwd = Secret.fromString("");

    @DataBoundConstructor
    public SSLCertificateAuthenticationMethod(String keystore, Secret keypwd, String truststore, Secret trustpwd) {
        this.setKeystore(keystore);
        this.setKeypwd(keypwd);
        this.setTruststore(truststore);
        this.setTrustpwd(trustpwd);
    }

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

    private String getSubstitutedValue(String value) {
        EnvVars vars = new EnvVars();
        vars.put("JENKINS_HOME", Jenkins.get().getRootDir().toString());
        return PluginUtils.getSubstitutedValue(value, vars);
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
    public ActiveMQSslConnectionFactory getConnectionFactory(String broker) {
        try {
            ActiveMQSslConnectionFactory connectionFactory = new ActiveMQSslConnectionFactory(broker);
            connectionFactory.setKeyStore(getSubstitutedValue(getKeystore()));
            connectionFactory.setKeyStorePassword(Secret.toString(getKeypwd()));
            connectionFactory.setTrustStore(getSubstitutedValue(getTruststore()));
            connectionFactory.setTrustStorePassword(Secret.toString(getTrustpwd()));
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
            return new SSLCertificateAuthenticationMethod(jo.getString("keystore"), Secret.fromString(jo.getString("keypwd")),
                    jo.getString("truststore"), Secret.fromString(jo.getString("trustpwd")));
        }

        public String getConfigPage() {
            return "sslcert.jelly";
        }

        @POST
        public FormValidation doTestConnection(@QueryParameter("broker") String broker,
                                               @QueryParameter("keystore") String keystore,
                                               @QueryParameter("keypwd") String keypwd,
                                               @QueryParameter("truststore") String truststore,
                                               @QueryParameter("trustpwd") String trustpwd) {

            checkAdmin();

            broker = StringUtils.strip(StringUtils.stripToNull(broker), "/");
            Connection connection = null;
            Session session = null;
            if (broker != null && isValidURL(broker)) {
                try {
                    SSLCertificateAuthenticationMethod sam = new SSLCertificateAuthenticationMethod(keystore, Secret.fromString(keypwd), truststore, Secret.fromString(trustpwd));
                    ActiveMQSslConnectionFactory connectionFactory = sam.getConnectionFactory(broker);
                    connection = connectionFactory.createConnection();
                    connection.start();
                    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    session.close();
                    connection.close();
                    return FormValidation.ok(Messages.SuccessBrokerConnect(broker));
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Unhandled exception in SSLCertificateAuthenticationMethod.doTestConnection: ", e);
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
