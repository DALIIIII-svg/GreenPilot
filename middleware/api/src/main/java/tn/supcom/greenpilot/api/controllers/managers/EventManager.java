package tn.supcom.greenpilot.api.controllers.managers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import tn.supcom.greenpilot.api.controllers.events.InboundMessage;
import tn.supcom.greenpilot.api.controllers.events.MessageEvent;
import tn.supcom.greenpilot.api.controllers.events.PushNotification;

import java.util.logging.Logger;

/**
 * Event hub (pattern of the course skeleton): producers fire CDI ASYNC events, consumers observe them
 * with @ObservesAsync. The MQTT client thread is never blocked by database work, and the WebSocket push
 * is decoupled from the business logic.
 *   MqttManager --InboundMessage(@MQTT)--> IngestManager --PushNotification--> WebSocketManager
 */
@ApplicationScoped
public class EventManager {

    private static final Logger LOG = Logger.getLogger(EventManager.class.getName());

    @Inject
    @MessageEvent(MessageEvent.Type.MQTT)
    Event<InboundMessage> mqttEvent;

    @Inject
    Event<PushNotification> pushEvent;

    public void publishFromMqtt(InboundMessage message) {
        mqttEvent.fireAsync(message).exceptionally(e -> {
            LOG.warning("MQTT message processing failed: " + e.getMessage());
            return null;
        });
    }

    public void push(String siteId, String type, Object data) {
        pushEvent.fireAsync(new PushNotification(siteId, type, data)).exceptionally(e -> {
            LOG.fine("Push failed: " + e.getMessage());
            return null;
        });
    }
}
