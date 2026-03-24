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

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Date;
import java.util.Enumeration;

import javax.annotation.CheckForNull;

import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;
import com.redhat.jenkins.plugins.ci.Messages;

import hudson.util.FormValidation;

/**
 * Validates X.509 material before attempting a network connection (e.g. Test Connection in UI).
 */
public final class CertificateExpiryChecker {

    private CertificateExpiryChecker() {
    }

    /**
     * @return a validation error if the credential resolves to a keystore containing an expired X.509 certificate;
     *         {@code null} if there is nothing to check or no expiry problem
     */
    @CheckForNull
    public static FormValidation checkStandardCertificateCredential(String credentialId, String storeDescription) {
        if (credentialId == null || credentialId.isEmpty()) {
            return null;
        }
        StandardCertificateCredentials cc = CredentialLookup.lookupById(credentialId,
                StandardCertificateCredentials.class);
        if (cc == null) {
            return null;
        }
        try {
            String msg = findExpiredInKeyStore(cc.getKeyStore(), storeDescription);
            if (msg != null) {
                return FormValidation.error(msg);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    /**
     * @return an error message if any X.509 entry in the store is past its not-after time; otherwise {@code null}
     */
    @CheckForNull
    public static String findExpiredInKeyStore(KeyStore keyStore, String storeDescription) {
        if (keyStore == null) {
            return null;
        }
        Date now = new Date();
        try {
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (keyStore.isCertificateEntry(alias)) {
                    String msg = messageIfExpired(keyStore.getCertificate(alias), storeDescription, alias, now);
                    if (msg != null) {
                        return msg;
                    }
                }
                if (keyStore.isKeyEntry(alias)) {
                    Certificate[] chain = keyStore.getCertificateChain(alias);
                    if (chain != null) {
                        for (int i = 0; i < chain.length; i++) {
                            String msg = messageIfExpired(chain[i], storeDescription, alias + "[" + i + "]", now);
                            if (msg != null) {
                                return msg;
                            }
                        }
                    }
                }
            }
        } catch (KeyStoreException e) {
            return null;
        }
        return null;
    }

    @CheckForNull
    private static String messageIfExpired(Certificate cert, String storeDescription, String alias, Date now) {
        if (!(cert instanceof X509Certificate)) {
            return null;
        }
        X509Certificate x = (X509Certificate) cert;
        if (now.after(x.getNotAfter())) {
            return Messages.certificateExpired(storeDescription, alias, x.getSubjectX500Principal().getName(),
                    formatNotAfter(x.getNotAfter()));
        }
        return null;
    }

    private static String formatNotAfter(Date d) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.LONG).withZone(ZoneId.systemDefault())
                .format(d.toInstant());
    }
}
