import com.android.apksig.ApkSigner;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

/** Signs an APK with v1 and v2 schemes. Usage: SignApk keystore alias in.apk out.apk (password in REDE_KEY_PASS). */
public class SignApk {
    public static void main(String[] args) throws Exception {
        char[] password = System.getenv("REDE_KEY_PASS").toCharArray();
        KeyStore store = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(args[0])) {
            store.load(in, password);
        }
        PrivateKey key = (PrivateKey) store.getKey(args[1], password);
        X509Certificate cert = (X509Certificate) store.getCertificate(args[1]);
        ApkSigner.SignerConfig signer = new ApkSigner.SignerConfig.Builder(
                "REDE", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(signer))
                .setInputApk(new File(args[2]))
                .setOutputApk(new File(args[3]))
                .setMinSdkVersion(24)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(false)
                .build()
                .sign();
    }
}
