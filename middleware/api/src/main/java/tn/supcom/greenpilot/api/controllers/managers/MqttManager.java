package tn.supcom.greenpilot.api.controllers.managers;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import tn.supcom.greenpilot.api.boundaries.mqtt.Topics;
import tn.supcom.greenpilot.api.controllers.events.InboundMessage;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * MQTT v5 client of the backend (course skeleton: MqttManager), with these corrections/improvements:
 *  - TLS 1.3 + username/password, truststore optional (falls back to the JVM trust store);
 *  - callback registered BEFORE connect (no lost messages), resubscribe on every (re)connect;
 *  - persistent session (cleanStart=false + session expiry) so QoS 1 messages wait while we restart;
 *    the client id is stable per host (name + hostname) - a random id would lose the session;
 *  - shared subscription ($share/group/...) so several backend instances split the load;
 *  - a broker that is down does NOT break the deployment: retry every 10 s;
 *  - commands use MQTT 5 features: message expiry, response topic, correlation data.
 */
@ApplicationScoped
public class MqttManager implements MqttCallback {

    private static final Logger LOG = Logger.getLogger(MqttManager.class.getName());

    @Inject @ConfigProperty(name = "gp.mqtt.url") String brokerUrl;            // ssl://host:8883
    @Inject @ConfigProperty(name = "gp.mqtt.client-id") String clientIdPrefix;
    @Inject @ConfigProperty(name = "gp.mqtt.username") String username;
    @Inject @ConfigProperty(name = "gp.mqtt.password") String password;
    @Inject @ConfigProperty(name = "gp.mqtt.truststore.path") Optional<String> trustStorePath;
    @Inject @ConfigProperty(name = "gp.mqtt.truststore.password", defaultValue = "changeit") String trustStorePassword;
    @Inject @ConfigProperty(name = "gp.mqtt.shared-group") Optional<String> sharedGroup;
    @Inject @ConfigProperty(name = "gp.mqtt.session-expiry", defaultValue = "3600") long sessionExpirySeconds;

    @Inject
    EventManager events;

    @Resource(lookup = "java:comp/DefaultManagedScheduledExecutorService")
    ManagedScheduledExecutorService scheduler;

    private MqttAsyncClient client;
    private MqttConnectionOptions options;

    void onStart(@Observes Startup event) {
        try {
            options = buildOptions();
            client = new MqttAsyncClient(brokerUrl, stableClientId(), new MemoryPersistence());
            client.setCallback(this); // before connect
            connect();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Invalid MQTT configuration for " + brokerUrl, e);
        }
    }

    private MqttConnectionOptions buildOptions() throws Exception {
        MqttConnectionOptions o = new MqttConnectionOptions();
        o.setCleanStart(false);
        o.setSessionExpiryInterval(sessionExpirySeconds);
        o.setAutomaticReconnect(true);
        o.setKeepAliveInterval(30);
        o.setConnectionTimeout(10);
        o.setUserName(username);
        o.setPassword(password.getBytes(StandardCharsets.UTF_8));
        if (brokerUrl.startsWith("ssl://")) {
            SSLContext tls = SSLContext.getInstance("TLSv1.3");
            if (trustStorePath.isPresent() && !trustStorePath.get().isBlank()) {
                KeyStore ks = KeyStore.getInstance("PKCS12");
                try (var in = new FileInputStream(trustStorePath.get())) {
                    ks.load(in, trustStorePassword.toCharArray());
                }
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init(ks);
                tls.init(null, tmf.getTrustManagers(), null);
            } else {
                tls.init(null, null, null); // JVM default trust store (public CA certificates)
            }
            o.setSocketFactory(tls.getSocketFactory());
        } else {
            LOG.warning("MQTT without TLS (" + brokerUrl + ") - development only");
        }
        return o;
    }

    private String stableClientId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = UUID.randomUUID().toString().substring(0, 8);
        }
        return (clientIdPrefix + "-" + host).replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void connect() {
        try {
            client.connect(options).waitForCompletion(15_000);
            // subscription is done in connectComplete()
        } catch (MqttException e) {
            LOG.warning("MQTT broker not reachable at " + brokerUrl + " (" + e.getMessage() + "), retrying in 10 s");
            scheduler.schedule(this::connect, 10, TimeUnit.SECONDS);
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        scheduler.execute(() -> {
            String filter = sharedGroup.filter(g -> !g.isBlank())
                    .map(g -> "$share/" + g + "/" + Topics.SUBSCRIPTION).orElse(Topics.SUBSCRIPTION);
            try {
                client.subscribe(filter, 1).waitForCompletion(10_000);
                LOG.info("MQTT " + (reconnect ? "reconnected" : "connected") + " to " + serverURI + ", subscribed to " + filter);
            } catch (MqttException e) {
                LOG.log(Level.WARNING, "MQTT subscribe failed (check the broker ACL for user '" + username + "')", e);
            }
        });
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            MqttProperties p = message.getProperties();
            String correlation = p != null && p.getCorrelationData() != null
                    ? new String(p.getCorrelationData(), StandardCharsets.UTF_8) : null;
            events.publishFromMqtt(new InboundMessage(topic, message.getPayload(), correlation));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Could not dispatch MQTT message from " + topic, e); // never throw back to Paho
        }
    }

    /** Command with MQTT 5 request/response metadata. The broker drops it if not delivered before expiry. */
    public void publishCommand(String gatewayId, String commandId, String json, long ttlSeconds) throws MqttException {
        MqttProperties props = new MqttProperties();
        props.setMessageExpiryInterval(ttlSeconds);
        props.setResponseTopic(Topics.commandAck(gatewayId));
        props.setCorrelationData(commandId.getBytes(StandardCharsets.UTF_8));
        props.setContentType("application/json");
        publish(Topics.commands(gatewayId), json, props).waitForCompletion(5_000);
    }

    /** Fire-and-forget acknowledgement of a disease event. */
    public void publishEventAck(String gatewayId, String json) {
        try {
            MqttProperties props = new MqttProperties();
            props.setContentType("application/json");
            publish(Topics.eventAck(gatewayId), json, props);
        } catch (MqttException | IllegalStateException e) {
            LOG.warning("Event ack not sent to " + gatewayId + ": " + e.getMessage());
        }
    }

    private IMqttToken publish(String topic, String json, MqttProperties props) throws MqttException {
        if (!isConnected()) throw new IllegalStateException("MQTT broker not connected");
        MqttMessage m = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
        m.setQos(1);
        m.setRetained(false); // commands must never be replayed to a new subscriber
        m.setProperties(props);
        return client.publish(topic, m);
    }

    public boolean isConnected() { return client != null && client.isConnected(); }

    @Override
    public void disconnected(MqttDisconnectResponse response) {
        LOG.warning("MQTT disconnected: " + (response == null ? "?" : response.getReasonString()) + " - reconnecting");
    }

    @Override
    public void mqttErrorOccurred(MqttException exception) {
        LOG.warning("MQTT error: " + exception.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttToken token) {
    }

    @Override
    public void authPacketArrived(int reasonCode, MqttProperties properties) {
    }

    @PreDestroy
    void cleanUp() {
        try {
            if (client != null) {
                if (client.isConnected()) client.disconnect().waitForCompletion(5_000);
                client.close();
            }
        } catch (MqttException ignored) {
        }
    }
}
