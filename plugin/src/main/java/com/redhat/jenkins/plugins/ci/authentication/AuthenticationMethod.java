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
package com.redhat.jenkins.plugins.ci.authentication;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.cloudbees.plugins.credentials.common.StandardCredentials;
import com.redhat.utils.CredentialLookup;

import hudson.model.Item;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

public abstract class AuthenticationMethod implements Serializable {
    private static final long serialVersionUID = -6077120270692721571L;

    private transient static final Logger log = Logger.getLogger(AuthenticationMethod.class.getName());

    /**
     * Taken from gerrit-trigger-plugin
     */
    public static void checkAdmin() {
        final Jenkins jenkins = Jenkins.get();
        // If Jenkins is not alive then we are not started, so no unauthorised user might do anything
        jenkins.checkPermission(Jenkins.ADMINISTER);
    }

    protected StandardCertificateCredentials getStandardCertificateCredentials(String credentialId) {
        if (credentialId == null || credentialId.isEmpty()) {
            log.warning(String.format("Credential ID '%s' is empty. Cannot create SSLContext.", credentialId));
            return null;
        }

        StandardCertificateCredentials ccreds = CredentialLookup.lookupById(credentialId,
                StandardCertificateCredentials.class);
        if (ccreds == null) {
            log.warning(String.format("Credential '%s' not found or is not a certificate credential", credentialId));
        }
        return ccreds;
    }

    protected static ListBoxModel doFillCredentials(Item project, String credentialId, Class<?> clazz, String prompt) {
        return doFillCredentials(project, credentialId, Collections.singletonList(clazz), prompt);
    }

    protected static ListBoxModel doFillCredentials(Item project, String credentialId, List<Class<?>> classes,
            String prompt) {
        ListBoxModel items = new ListBoxModel();
        items.add("-- Select " + prompt + " Credential --", "");

        ACL.impersonate2(ACL.SYSTEM2, () -> {
            List<StandardCredentials> availableCredentials = CredentialsProvider
                    .lookupCredentialsInItem(StandardCredentials.class, project, ACL.SYSTEM2);

            for (StandardCredentials c : availableCredentials) {
                if (isInstanceOf(classes, c)) {
                    items.add(c.getDescription() != null && !c.getDescription().isEmpty()
                            ? c.getDescription() + " (" + c.getId() + ")"
                            : c.getId(), c.getId());
                }
            }
        });

        return items;
    }

    private static boolean isInstanceOf(List<Class<?>> classes, Object object) {
        for (Class<?> clazz : classes) {
            if (clazz.isInstance(object)) {
                return true;
            }
        }
        return false;
    }
}
