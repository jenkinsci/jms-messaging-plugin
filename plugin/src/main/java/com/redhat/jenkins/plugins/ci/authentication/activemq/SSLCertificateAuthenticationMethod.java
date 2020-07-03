package com.redhat.jenkins.plugins.ci.authentication.activemq;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.servlet.ServletException;

import org.apache.activemq.ActiveMQSslConnectionFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.redhat.jenkins.plugins.ci.Messages;
import com.redhat.utils.PluginUtils;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

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
        vars.put("JENKINS_HOME", Jenkins.getInstance().getRootDir().toString());
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
    @CheckForNull
    public ConnectionFactory getConnectionFactory(String broker) {
        if (broker.startsWith("amqp")) {
            StringBuilder sb = new StringBuilder(broker);
            sb.append("?transport.keyStoreLocation=");
            sb.append(getSubstitutedValue(getKeystore()));
            sb.append("&transport.keyStorePassword=");
            sb.append(Secret.toString(getKeypwd()));
            sb.append("&transport.trustStoreLocation=");
            sb.append(getSubstitutedValue(getTruststore()));
            sb.append("&transport.trustStorePassword=");
            sb.append(Secret.toString(getTrustpwd()));
            return new JmsConnectionFactory(sb.toString());
        } else {
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
        }
        return null;
    }

    @Override
    public Descriptor<ActiveMQAuthenticationMethod> getDescriptor() {
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
            return new SSLCertificateAuthenticationMethod(jo.getString("keystore"), Secret.fromString(jo.getString("keypwd")),
                                                          jo.getString("truststore"), Secret.fromString(jo.getString("trustpwd")));
        }

        public String getConfigPage() {
            return "sslcert.jelly";
        }

        @RequirePOST
        public FormValidation doTestConnection(@QueryParameter("broker") String broker,
                                               @QueryParameter("keystore") String keystore,
                                               @QueryParameter("keypwd") String keypwd,
                                               @QueryParameter("truststore") String truststore,
                                               @QueryParameter("trustpwd") String trustpwd) throws ServletException {

            checkAdmin();

            broker = StringUtils.strip(StringUtils.stripToNull(broker), "/");
            Connection connection = null;
            Session session = null;
            if (broker != null && isValidURL(broker)) {
                try {
                    SSLCertificateAuthenticationMethod sam = new SSLCertificateAuthenticationMethod(keystore, Secret.fromString(keypwd), truststore, Secret.fromString(trustpwd));
                    ConnectionFactory connectionFactory = sam.getConnectionFactory(broker);
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
