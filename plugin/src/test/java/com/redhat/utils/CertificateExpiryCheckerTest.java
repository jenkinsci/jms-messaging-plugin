package com.redhat.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.security.auth.x500.X500Principal;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.mockito.MockedStatic;

import com.cloudbees.plugins.credentials.common.StandardCertificateCredentials;

import hudson.util.FormValidation;

/**
 * Expiry detection is tested with mocked {@link X509Certificate} instances in an in-memory {@link KeyStore}. The JVM
 * default keystore type is often {@code PKCS12}, which rejects Mockito-generated certificate/key types; tests therefore
 * use {@code JKS}. No PEM or keystore files are required. {@link JenkinsRule} loads the plugin so localized
 * {@code Messages} resolve. {@link CredentialLookup} is stubbed with {@link MockedStatic} for the credential-id path.
 */
public class CertificateExpiryCheckerTest {

    private static final String TEST_KEYSTORE_TYPE = "JKS";

    @Rule
    public JenkinsRule j = new JenkinsRule();

    private static KeyStore newEmptyTestKeyStore() throws Exception {
        KeyStore ks = KeyStore.getInstance(TEST_KEYSTORE_TYPE);
        ks.load(null, null);
        return ks;
    }

    @Test
    public void findExpiredInKeyStore_nullStore_returnsNull() {
        assertThat(CertificateExpiryChecker.findExpiredInKeyStore(null, "test store"), nullValue());
    }

    @Test
    public void findExpiredInKeyStore_emptyStore_returnsNull() throws Exception {
        assertThat(CertificateExpiryChecker.findExpiredInKeyStore(newEmptyTestKeyStore(), "test store"), nullValue());
    }

    @Test
    public void findExpiredInKeyStore_validCertificateEntry_returnsNull() throws Exception {
        KeyStore ks = newEmptyTestKeyStore();
        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getNotAfter()).thenReturn(new Date(System.currentTimeMillis() + 365L * 86400_000L));
        when(cert.getSubjectX500Principal()).thenReturn(new X500Principal("CN=still-valid"));
        ks.setCertificateEntry("trusted", cert);

        assertThat(CertificateExpiryChecker.findExpiredInKeyStore(ks, "trust store credential"), nullValue());
    }

    @Test
    public void findExpiredInKeyStore_expiredCertificateEntry_returnsMessage() throws Exception {
        KeyStore ks = newEmptyTestKeyStore();
        X509Certificate cert = mock(X509Certificate.class);
        Date expired = new Date(System.currentTimeMillis() - 7L * 86400_000L);
        when(cert.getNotAfter()).thenReturn(expired);
        when(cert.getSubjectX500Principal()).thenReturn(new X500Principal("CN=expired-trust"));
        ks.setCertificateEntry("ca", cert);

        String msg = CertificateExpiryChecker.findExpiredInKeyStore(ks, "trust store credential");
        assertThat(msg, containsString("trust store credential"));
        assertThat(msg, containsString("CN=expired-trust"));
        assertThat(msg, containsString("ca"));
        assertThat(msg, containsString("Cannot test connection"));
    }

    @Test
    public void findExpiredInKeyStore_expiredCertInPrivateKeyChain_returnsMessage() throws Exception {
        KeyStore ks = newEmptyTestKeyStore();
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(512);
        KeyPair kp = kpg.generateKeyPair();

        X509Certificate leaf = mock(X509Certificate.class);
        when(leaf.getNotAfter()).thenReturn(new Date(System.currentTimeMillis() + 86400_000L));
        when(leaf.getSubjectX500Principal()).thenReturn(new X500Principal("CN=leaf"));

        X509Certificate expiredIssuer = mock(X509Certificate.class);
        Date expired = new Date(System.currentTimeMillis() - 86400_000L);
        when(expiredIssuer.getNotAfter()).thenReturn(expired);
        when(expiredIssuer.getSubjectX500Principal()).thenReturn(new X500Principal("CN=expired-issuer"));

        ks.setKeyEntry("identity", kp.getPrivate(), "secret".toCharArray(), new Certificate[] { leaf, expiredIssuer });

        String msg = CertificateExpiryChecker.findExpiredInKeyStore(ks, "key store credential");
        assertThat(msg, containsString("key store credential"));
        assertThat(msg, containsString("CN=expired-issuer"));
        assertThat(msg, containsString("identity[1]"));
    }

    @Test
    public void checkStandardCertificateCredential_blankId_returnsNull() {
        assertThat(CertificateExpiryChecker.checkStandardCertificateCredential("", "key store credential"),
                nullValue());
        assertThat(CertificateExpiryChecker.checkStandardCertificateCredential(null, "key store credential"),
                nullValue());
    }

    @Test
    public void checkStandardCertificateCredential_expiredCredential_returnsFormValidationError() throws Exception {
        KeyStore ks = newEmptyTestKeyStore();
        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getNotAfter()).thenReturn(new Date(0L));
        when(cert.getSubjectX500Principal()).thenReturn(new X500Principal("CN=from-credential"));
        ks.setCertificateEntry("only", cert);

        StandardCertificateCredentials cc = mock(StandardCertificateCredentials.class);
        when(cc.getKeyStore()).thenReturn(ks);

        try (MockedStatic<CredentialLookup> credentialLookup = mockStatic(CredentialLookup.class)) {
            credentialLookup
                    .when(() -> CredentialLookup.lookupById("expired-cert-id", StandardCertificateCredentials.class))
                    .thenReturn(cc);

            FormValidation fv = CertificateExpiryChecker.checkStandardCertificateCredential("expired-cert-id",
                    "key store credential");
            assertThat(fv.kind, is(FormValidation.Kind.ERROR));
            assertThat(fv.renderHtml(), containsString("CN=from-credential"));
            assertThat(fv.renderHtml(), containsString("key store credential"));
        }
    }

    @Test
    public void checkStandardCertificateCredential_validCredential_returnsNull() throws Exception {
        KeyStore ks = newEmptyTestKeyStore();
        X509Certificate cert = mock(X509Certificate.class);
        when(cert.getNotAfter()).thenReturn(new Date(System.currentTimeMillis() + 86400_000L));
        when(cert.getSubjectX500Principal()).thenReturn(new X500Principal("CN=ok"));
        ks.setCertificateEntry("only", cert);

        StandardCertificateCredentials cc = mock(StandardCertificateCredentials.class);
        when(cc.getKeyStore()).thenReturn(ks);

        try (MockedStatic<CredentialLookup> credentialLookup = mockStatic(CredentialLookup.class)) {
            credentialLookup
                    .when(() -> CredentialLookup.lookupById("valid-cert-id", StandardCertificateCredentials.class))
                    .thenReturn(cc);

            assertThat(CertificateExpiryChecker.checkStandardCertificateCredential("valid-cert-id",
                    "key store credential"), nullValue());
        }
    }
}
