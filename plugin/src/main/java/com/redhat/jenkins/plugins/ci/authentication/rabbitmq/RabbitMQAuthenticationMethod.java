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

import java.util.Iterator;

import com.rabbitmq.client.ConnectionFactory;
import com.redhat.jenkins.plugins.ci.authentication.AuthenticationMethod;
import com.redhat.jenkins.plugins.ci.authentication.rabbitmq.X509CertificateAuthenticationMethod.X509CertificateAuthenticationMethodDescriptor;

import hudson.ExtensionList;
import hudson.Plugin;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

public abstract class RabbitMQAuthenticationMethod extends AuthenticationMethod
        implements Describable<RabbitMQAuthenticationMethod> {

    private static final long serialVersionUID = -6077120270692721571L;

    public abstract static class AuthenticationMethodDescriptor extends Descriptor<RabbitMQAuthenticationMethod> {
        public static ExtensionList<AuthenticationMethodDescriptor> all() {
            ExtensionList<AuthenticationMethodDescriptor> elist = Jenkins.get()
                    .getExtensionList(AuthenticationMethodDescriptor.class);
            Plugin commons = Jenkins.get().getPlugin("docker-commons");
            if (commons == null || !commons.getWrapper().isActive()) {
                Iterator<AuthenticationMethodDescriptor> iter = elist.iterator();
                while (iter.hasNext()) {
                    AuthenticationMethodDescriptor d = iter.next();
                    if (d instanceof X509CertificateAuthenticationMethodDescriptor) {
                        iter.remove();
                    }
                }
            }
            return elist;
        }
    }

    public abstract ConnectionFactory getConnectionFactory(String hostname, Integer portNumber, String VirtualHost);
}
