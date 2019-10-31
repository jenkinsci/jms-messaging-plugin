package com.redhat.jenkins.plugins.ci.authentication.rabbitmq;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;

import javax.servlet.ServletException;
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
public class UsernameAuthenticationMethod extends RabbitMQAuthenticationMethod  {
    private static final long serialVersionUID = 452156745621333923L;
    private transient static final Logger log = Logger.getLogger(UsernameAuthenticationMethod.class.getName());

    private String username;
    private Secret password;

    @DataBoundConstructor
    public UsernameAuthenticationMethod(String username, Secret password) {
        this.setUsername(username);
        this.setPassword(password);
    }

    public String getUsername() {
        return username;
    }

    @DataBoundSetter
    public void setUsername(String username) {
        this.username = username;
    }

    public Secret getPassword() {
        return password;
    }

    @DataBoundSetter
    public void setPassword(Secret password) {
        this.password = password;
    }

    @Override
    public ConnectionFactory getConnectionFactory(String hostname, Integer portNumber, String virtualHost) {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(hostname);
        connectionFactory.setPort(portNumber);
        connectionFactory.setVirtualHost(virtualHost);
        connectionFactory.setUsername(getUsername());
        connectionFactory.setPassword(getPassword().getPlainText());
        return connectionFactory;
    }

    @Override
    public Descriptor<RabbitMQAuthenticationMethod> getDescriptor() {
        return Jenkins.getInstance().getDescriptorByType(UsernameAuthenticationMethodDescriptor.class);
    }

    @Extension
    public static class UsernameAuthenticationMethodDescriptor extends AuthenticationMethodDescriptor {

        @Override
        public String getDisplayName() {
            return "Username and Password Authentication";
        }

        @Override
        public UsernameAuthenticationMethod newInstance(StaplerRequest sr, JSONObject jo) {
            return new UsernameAuthenticationMethod(jo.getString("user"), Secret.fromString(jo.getString("password")));
        }

        public String getConfigPage() {
            return "username.jelly";
        }

        @RequirePOST
        public FormValidation doTestConnection(@QueryParameter("hostname") String hostname,
                                               @QueryParameter("portNumber") Integer portNumber,
                                               @QueryParameter("virtualHost") String virtualHost,
                                               @QueryParameter("username") String username,
                                               @QueryParameter("password") String password) throws ServletException {

            checkAdmin();

            Connection connection = null;
            Channel channel = null;
            try {
                UsernameAuthenticationMethod uam = new UsernameAuthenticationMethod(username, Secret.fromString(password));
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
