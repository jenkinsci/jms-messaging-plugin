package com.redhat.jenkins.plugins.ci.authentication.activemq;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Session;
import javax.servlet.ServletException;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.lang3.StringUtils;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.redhat.jenkins.plugins.ci.Messages;

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
public class UsernameAuthenticationMethod extends ActiveMQAuthenticationMethod  {
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
    public ConnectionFactory getConnectionFactory(String broker) {
        if (broker.startsWith("amqp")) {
            return new JmsConnectionFactory(this.username, this.password.getPlainText(), broker);
        } else {
            return new ActiveMQConnectionFactory(getUsername(), getPassword().getPlainText(), broker);
        }
    }

    @Override
    public Descriptor<ActiveMQAuthenticationMethod> getDescriptor() {
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
        public FormValidation doTestConnection(@QueryParameter("broker") String broker,
                                               @QueryParameter("username") String username,
                                               @QueryParameter("password") String password) throws ServletException {

            checkAdmin();

            broker = StringUtils.strip(StringUtils.stripToNull(broker), "/");
            Session session = null;
            Connection connection = null;
            if (broker != null && isValidURL(broker)) {
                try {
                    UsernameAuthenticationMethod uam = new UsernameAuthenticationMethod(username, Secret.fromString(password));
                    ConnectionFactory connectionFactory = uam.getConnectionFactory(broker);
                    connection = connectionFactory.createConnection();
                    connection.start();
                    session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                    session.close();
                    connection.close();
                    return FormValidation.ok(Messages.SuccessBrokerConnect(broker));
                } catch (Exception e) {
                    log.log(Level.SEVERE, "Unhandled exception in UsernameAuthenticationMethod.doTestConnection: ", e);
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
