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

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.redhat.jenkins.plugins.ci.Messages;
import com.redhat.utils.CredentialLookup;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

public class UsernameAuthenticationMethod extends RabbitMQAuthenticationMethod {
    private static final long serialVersionUID = 452156745621333923L;
    private transient static final Logger log = Logger.getLogger(UsernameAuthenticationMethod.class.getName());

    private String credentialId;

    @DataBoundConstructor
    public UsernameAuthenticationMethod(String credentialId) {
        this.setCredentialId(credentialId);
    }

    public String getCredentialId() {
        return credentialId;
    }

    @DataBoundSetter
    public void setCredentialId(String credentialId) {
        this.credentialId = credentialId;
    }

    @Override
    public ConnectionFactory getConnectionFactory(String hostname, Integer portNumber, String virtualHost) {
        if (credentialId == null || credentialId.isEmpty()) {
            log.warning("Credential ID is empty.");
            return null;
        }

        StandardUsernamePasswordCredentials credentials = CredentialLookup.lookupById(credentialId,
                StandardUsernamePasswordCredentials.class);

        if (credentials == null) {
            log.warning(
                    String.format("Credential '%s' not found or is not a username/password credential", credentialId));
            return null;
        }
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(hostname);
        connectionFactory.setPort(portNumber);
        connectionFactory.setVirtualHost(virtualHost);
        connectionFactory.setUsername(credentials.getUsername());
        connectionFactory.setPassword(credentials.getPassword().getPlainText());
        return connectionFactory;
    }

    @Override
    public Descriptor<RabbitMQAuthenticationMethod> getDescriptor() {
        return Jenkins.get().getDescriptorByType(UsernameAuthenticationMethodDescriptor.class);
    }

    @Extension
    @Symbol("simple")
    public static class UsernameAuthenticationMethodDescriptor extends AuthenticationMethodDescriptor {

        @Override
        public @Nonnull String getDisplayName() {
            return "Username and Password Authentication";
        }

        @Override
        public UsernameAuthenticationMethod newInstance(StaplerRequest2 sr, JSONObject jo) {
            return new UsernameAuthenticationMethod(jo.getString("credentialId"));
        }

        public String getConfigPage() {
            return "username.jelly";
        }

        @POST
        public ListBoxModel doFillCredentialIdItems(@AncestorInPath Item project, @QueryParameter String credentialId) {
            checkAdmin();

            return doFillCredentials(project, credentialId, StandardUsernamePasswordCredentials.class,
                    "Username/Password");
        }

        @POST
        public FormValidation doTestConnection(@QueryParameter("hostname") String hostname,
                @QueryParameter("portNumber") Integer portNumber, @QueryParameter("virtualHost") String virtualHost,
                @QueryParameter("credentialId") String credentialId) {

            checkAdmin();

            Connection connection = null;
            Channel channel = null;
            try {
                UsernameAuthenticationMethod uam = new UsernameAuthenticationMethod(credentialId);
                ConnectionFactory connectionFactory = uam.getConnectionFactory(hostname, portNumber, virtualHost);
                connection = connectionFactory.newConnection();
                channel = connection.createChannel();
                channel.close();
                connection.close();
                return FormValidation.ok(Messages.SuccessBrokerConnect(hostname + ":" + portNumber));
            } catch (Exception e) {
                log.log(Level.SEVERE, "Unhandled exception in UsernameAuthenticationMethod.doTestConnection: ", e);
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
