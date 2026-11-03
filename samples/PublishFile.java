import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttMessage;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.UUID;

/**
 * Publishes a file's raw bytes (e.g. a JPEG) over MQTT v5 + TLS, like the Raspberry Pi will.
 * mosquitto_pub on Windows cannot read binary files, hence this helper.
 * Usage: java -cp <paho-mqttv5.jar> PublishFile.java <ssl://host:8883> <topic> <file> <user> <password> <truststore.p12> <storepass>
 */
public class PublishFile {
    public static void main(String[] a) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (var in = new FileInputStream(a[5])) { ks.load(in, a[6].toCharArray()); }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        SSLContext tls = SSLContext.getInstance("TLSv1.3");
        tls.init(null, tmf.getTrustManagers(), null);

        MqttConnectionOptions o = new MqttConnectionOptions();
        o.setUserName(a[3]);
        o.setPassword(a[4].getBytes(StandardCharsets.UTF_8));
        o.setSocketFactory(tls.getSocketFactory());
        o.setCleanStart(true);

        MqttClient client = new MqttClient(a[0], a[3] + "-tool-" + UUID.randomUUID().toString().substring(0, 6), new MemoryPersistence());
        client.connect(o);
        MqttMessage m = new MqttMessage(Files.readAllBytes(Path.of(a[2])));
        m.setQos(1);
        client.publish(a[1], m);
        client.disconnect();
        client.close();
        System.out.println("  published " + m.getPayload().length + " bytes to " + a[1]);
    }
}
