package tn.supcom.greenpilot.api.controllers.events;

/** A raw MQTT message, handed from the Paho thread to the CDI async event system. */
public record InboundMessage(String topic, byte[] payload, String correlationId) {
}
