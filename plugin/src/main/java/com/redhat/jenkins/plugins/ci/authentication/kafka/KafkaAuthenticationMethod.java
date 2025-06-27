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
package com.redhat.jenkins.plugins.ci.authentication.kafka;

import java.util.logging.Logger;

import com.redhat.jenkins.plugins.ci.authentication.AuthenticationMethod;

import hudson.ExtensionList;
import hudson.model.Describable;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

public abstract class KafkaAuthenticationMethod extends AuthenticationMethod
        implements Describable<KafkaAuthenticationMethod> {

    private static final long serialVersionUID = -6077120270692721589L;
    private transient static final Logger log = Logger.getLogger(AuthenticationMethod.class.getName());

    public abstract static class AuthenticationMethodDescriptor extends Descriptor<KafkaAuthenticationMethod> {
        public static ExtensionList<AuthenticationMethodDescriptor> all() {
            return Jenkins.get().getExtensionList(AuthenticationMethodDescriptor.class);
        }
    }
}
