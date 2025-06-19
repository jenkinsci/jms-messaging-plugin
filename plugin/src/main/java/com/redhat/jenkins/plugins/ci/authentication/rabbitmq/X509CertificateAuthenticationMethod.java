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
package com.redhat.jenkins.plugins.ci.authentication.rabbitmq;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.docker.commons.credentials.DockerServerCredentials;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultSaslConfig;
import com.redhat.jenkins.plugins.ci.Messages;
import com.redhat.utils.CredentialLookup;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

public class X509CertificateAuthenticationMethod extends RabbitMQAuthenticationMethod {
    private static final long serialVersionUID = -5934219869726669459L;
    private transient static final Logger log = Logger.getLogger(X509CertificateAuthenticationMethod.class.getName());

    private static final String CERTIFICATE_TYPE = "X.509";
    private static final String KEYSTORE_TYPE = "PKCS12";

    private String credentialId;

    @DataBoundConstructor
    public X509CertificateAuthenticationMethod(String credentialId) {
        this.setCredentialId(credentialId);
    }

    public String getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    @Override
    public ConnectionFactory getConnectionFactory(String hostname, Integer portNumber, String virtualHost) {
        DockerServerCredentials creds = getDockerServerCredentials(credentialId);
        if (creds == null || creds.getClientKeySecret() == null || creds.getClientCertificate() == null
                || creds.getServerCaCertificate() == null) {
            return null;
        }

        try {
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(createKeyStore(Secret.toString(creds.getClientKeySecret()), creds.getClientCertificate()),
                    new char[0]);

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(createTrustStore(creds.getServerCaCertificate()));

            SSLContext c = SSLContext.getInstance("TLSv1.2");
            c.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

            ConnectionFactory connectionFactory = new ConnectionFactory();
            connectionFactory.setHost(hostname);
            connectionFactory.setPort(portNumber);
            connectionFactory.setVirtualHost(virtualHost);
            connectionFactory.useSslProtocol(c);
            connectionFactory.setSaslConfig(DefaultSaslConfig.EXTERNAL);
            connectionFactory.enableHostnameVerification();
            return connectionFactory;

        } catch (KeyManagementException | UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
            log.log(Level.SEVERE, String.format("Error creating SSLContext from credential '%s'", credentialId), e);
        }

        return null;
    }

    private KeyStore createTrustStore(String pem) {
        if (pem != null) {
            try {
                InputStream is = new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8));
                CertificateFactory certFactory = CertificateFactory.getInstance(CERTIFICATE_TYPE);
                X509Certificate cert = (X509Certificate) certFactory.generateCertificate(is);
                KeyStore ks = KeyStore.getInstance(KEYSTORE_TYPE);
                ks.load(null, null);
                ks.setCertificateEntry("x509-cert", cert);
                return ks;
            } catch (CertificateException | IOException | KeyStoreException | NoSuchAlgorithmException e) {
                log.log(Level.SEVERE, "Error creating truststore", e);
            }
        }
        return null;
    }

    private KeyStore createKeyStore(String pemKey, String pemCertificateChain) {
        if (pemKey != null && pemCertificateChain != null) {
            try {
                String keyContent = pemKey.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "").replace("-----BEGIN RSA PRIVATE KEY-----", "")
                        .replace("-----END RSA PRIVATE KEY-----", "").replaceAll("\\s", "");

                byte[] decodedKey = Base64.getDecoder().decode(keyContent);

                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decodedKey);
                KeyFactory keyFactory = KeyFactory.getInstance("RSA");
                PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

                CertificateFactory certFactory = CertificateFactory.getInstance(CERTIFICATE_TYPE);
                InputStream is = new ByteArrayInputStream(pemCertificateChain.getBytes(StandardCharsets.UTF_8));
                Collection<? extends Certificate> certsCollection = certFactory.generateCertificates(is);
                List<X509Certificate> certificateChain = new ArrayList<>();
                for (Certificate cert : certsCollection) {
                    if (cert instanceof X509Certificate) {
                        certificateChain.add((X509Certificate) cert);
                    }
                }
                if (certificateChain.isEmpty()) {
                    log.severe("No X.509 certificates found in the provided PEM certificate chain string.");
                    return null;
                }
                log.info(String.format("Successfully parsed %d certificates from PEM chain.", certificateChain.size()));
                log.info(String.format("Leaf Certificate Subject: %s",
                        certificateChain.get(0).getSubjectX500Principal().getName()));

                KeyStore keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
                keyStore.load(null, null);

                String alias = "x509-cert";
                keyStore.setKeyEntry(alias, privateKey, new char[0], certificateChain.toArray(new X509Certificate[0]));
                log.info(String.format("Private key and certificate chain added to KeyStore with alias: '%s'", alias));

                return keyStore;

            } catch (NoSuchAlgorithmException | InvalidKeySpecException | CertificateException | KeyStoreException
                    | IOException | IllegalArgumentException e) {
                log.log(Level.SEVERE, "Error creating keystore", e);
            }
        }
        return null;
    }

    private DockerServerCredentials getDockerServerCredentials(String credentialId) {
        if (credentialId == null || credentialId.isEmpty()) {
            log.warning(String.format("Credential ID '%s' is empty. Cannot create SSLContext.", credentialId));
            return null;
        }

        DockerServerCredentials creds = CredentialLookup.lookupById(credentialId, DockerServerCredentials.class);
        if (creds == null) {
            log.warning(String.format("Credential '%s' not found or is not a docker server credential", credentialId));
            return null;
        }

        return creds;
    }

    @Override
    public Descriptor<RabbitMQAuthenticationMethod> getDescriptor() {
        return Jenkins.get().getDescriptorByType(X509CertificateAuthenticationMethodDescriptor.class);
    }

    @Extension
    @Symbol("x509Certificate")
    public static class X509CertificateAuthenticationMethodDescriptor extends AuthenticationMethodDescriptor {

        @Override
        public @Nonnull String getDisplayName() {
            return "X.509 Certificate Authentication";
        }

        @Override
        public X509CertificateAuthenticationMethod newInstance(StaplerRequest2 sr, JSONObject jo) {
            return new X509CertificateAuthenticationMethod(jo.getString("credentialId"));
        }

        public String getConfigPage() {
            return "x509cert.jelly";
        }

        @POST
        public ListBoxModel doFillCredentialIdItems(@AncestorInPath Item project, @QueryParameter String credentialId) {
            checkAdmin();

            return doFillCredentials(project, credentialId, DockerServerCredentials.class, "X.509");
        }

        @POST
        public FormValidation doTestConnection(@QueryParameter("credentialId") String credentialId,
                @QueryParameter("hostname") String hostname, @QueryParameter("portNumber") Integer portNumber,
                @QueryParameter("virtualHost") String virtualHost) {

            checkAdmin();

            Connection connection = null;
            Channel channel = null;
            try {
                X509CertificateAuthenticationMethod sam = new X509CertificateAuthenticationMethod(credentialId);
                ConnectionFactory connectionFactory = sam.getConnectionFactory(hostname, portNumber, virtualHost);
                connection = connectionFactory.newConnection();
                channel = connection.createChannel();
                channel.close();
                connection.close();
                return FormValidation.ok(Messages.SuccessBrokerConnect(hostname + ":" + portNumber));
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unhandled exception in X509CertificateAuthenticationMethod.doTestConnection: ",
                        e);
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
