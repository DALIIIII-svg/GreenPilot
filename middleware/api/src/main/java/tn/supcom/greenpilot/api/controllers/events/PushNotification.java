package tn.supcom.greenpilot.api.controllers.events;

/** Something the browsers of a site should see immediately (sent over WebSocket). */
public record PushNotification(String siteId, String type, Object data) {
}
