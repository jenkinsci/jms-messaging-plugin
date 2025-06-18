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
package com.redhat.utils;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.IdCredentials;

import hudson.security.ACL;
import hudson.security.ACLContext;

public class CredentialLookup {

    private static final Logger log = Logger.getLogger(CredentialLookup.class.getName());

    public static <C extends IdCredentials> C lookupById(String credentialId, Class<C> type) {
        try (ACLContext ctx = ACL.as2(ACL.SYSTEM2)) {
            log.log(Level.INFO, "Attempting to lookup credential ID: {0} with system privileges.", credentialId);

            List<Credentials> allGlobalCredentials = SystemCredentialsProvider.getInstance().getCredentials();

            for (Credentials credential : allGlobalCredentials) {
                if (credential instanceof IdCredentials) {
                    IdCredentials idCredential = (IdCredentials) credential;
                    if (idCredential.getId().equals(credentialId)) {
                        if (type.isInstance(credential)) {
                            log.info(String.format("Found credential with ID: %s of type %s.", credentialId,
                                    type.getName()));
                            return type.cast(credential);
                        } else {
                            log.warning(String.format(
                                    "Credential with ID %s found, but it is not of the expected type %s. Actual type: %s",
                                    credentialId, type.getName(), credential.getClass().getName()));
                        }
                    }
                }
            }
            log.warning(String.format("Credential with ID: %s not found or not of the expected type %s.", credentialId,
                    type.getName()));
            return null;
        } catch (

        Exception e) {
            log.log(Level.SEVERE, "Error during credential lookup for ID: " + credentialId, e);
            return null;
        }
    }
}
