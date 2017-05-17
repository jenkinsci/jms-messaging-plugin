import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;

/**
 * Just an example about how to extract the keys from a PEM encoded files
 *
 */
public class Test {

    public static void main(String[] args) throws PEMException {
        //Test t = new Test(new File("/tmp/scott.pem"), "");
        Test t = new Test(new File("/tmp/privkey.pem"), "foobar");
    }

    private PrivateKeyInfo privateKeyInfo;
    private SubjectPublicKeyInfo publicKeyInfo;

    private JcaPEMKeyConverter pemKeyConverter = new JcaPEMKeyConverter();

    public Test(File file, String passphrase) {

        try {
            Security.addProvider(new BouncyCastleProvider());
            PEMParser pemParser = new PEMParser(new FileReader(file));
            Object encryptedKeys = pemParser.readObject();

            if (encryptedKeys instanceof PEMEncryptedKeyPair) {
                PEMDecryptorProvider pemDecryptor = new JcePEMDecryptorProviderBuilder().build(passphrase.toCharArray());
                PEMKeyPair rawKeyPair = ((PEMEncryptedKeyPair) encryptedKeys).decryptKeyPair(pemDecryptor);

                privateKeyInfo = rawKeyPair.getPrivateKeyInfo();
                publicKeyInfo = rawKeyPair.getPublicKeyInfo();
                System.out.println(privateKeyInfo);
            } else {
                System.out.println("Unencrypted key - no password needed");
                PrivateKey kp = pemKeyConverter.setProvider("BC").getPrivateKey((PrivateKeyInfo) encryptedKeys);
                System.out.println(kp.toString());
            }

            pemParser.close();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public PrivateKey getPrivateKey() throws PEMException {
        return pemKeyConverter.getPrivateKey(privateKeyInfo);
    }

    public PublicKey getPublicKey() throws PEMException {
        return pemKeyConverter.getPublicKey(publicKeyInfo);

    }

}
