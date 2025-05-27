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
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest2;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;
import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.Session;
import java.util.logging.Level;
import java.util.logging.Logger;

public class UsernameAuthenticationMethod extends ActiveMQAuthenticationMethod {
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
    public ActiveMQConnectionFactory getConnectionFactory(String broker) {
        return new ActiveMQConnectionFactory(getUsername(), getPassword().getPlainText(), broker);
    }

    @Override
    public Descriptor<ActiveMQAuthenticationMethod> getDescriptor() {
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
            return new UsernameAuthenticationMethod(jo.getString("user"), Secret.fromString(jo.getString("password")));
        }

        public String getConfigPage() {
            return "username.jelly";
        }

        @POST
        public FormValidation doTestConnection(@QueryParameter("broker") String broker,
                                               @QueryParameter("username") String username,
                                               @QueryParameter("password") String password) {

            checkAdmin();

            broker = StringUtils.strip(StringUtils.stripToNull(broker), "/");
            Session session = null;
            Connection connection = null;
            if (broker != null && isValidURL(broker)) {
                try {
                    UsernameAuthenticationMethod uam = new UsernameAuthenticationMethod(username, Secret.fromString(password));
                    ActiveMQConnectionFactory connectionFactory = uam.getConnectionFactory(broker);
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
