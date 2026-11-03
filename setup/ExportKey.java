import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Key;
import java.security.KeyStore;
import java.util.Base64;

/** Usage: java ExportKey.java <keystore.p12> <password> <alias> <out.pem>  - writes the private key as PKCS#8 PEM. */
public class ExportKey {
    public static void main(String[] a) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (var in = new FileInputStream(a[0])) { ks.load(in, a[1].toCharArray()); }
        Key key = ks.getKey(a[2], a[1].toCharArray());
        String b64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key.getEncoded());
        Files.writeString(Path.of(a[3]), "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n");
    }
}
