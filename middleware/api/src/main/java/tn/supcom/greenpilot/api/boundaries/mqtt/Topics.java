package tn.supcom.greenpilot.api.boundaries.mqtt;

/**
 * MQTT v5 topic contract between the Raspberry Pi gateways and the cloud.
 *
 *  Pi -> cloud (backend: shared subscription $share/<group>/greenpilot/+/up/#)
 *    greenpilot/{gw}/up/telemetry        JSON TelemetryMessage (every 60 s)
 *    greenpilot/{gw}/up/forecast         JSON ForecastMessage
 *    greenpilot/{gw}/up/inspection       JSON InspectionMessage
 *    greenpilot/{gw}/up/incident         JSON IncidentMessage (3 consecutive positives)
 *    greenpilot/{gw}/up/image/{imageKey} raw JPEG
 *    greenpilot/{gw}/up/cmd-ack          JSON CommandAckMessage (also the MQTT5 "response topic" of commands)
 *
 *  cloud -> Pi
 *    greenpilot/{gw}/down/cmd            JSON command (MQTT5 message expiry + correlation data = commandId)
 *    greenpilot/{gw}/down/event-ack      JSON {"eventId","status":"STORED"}
 *
 * Mosquitto ACL: a gateway (MQTT username = gatewayId) may only write greenpilot/<itself>/up/# and read
 * greenpilot/<itself>/down/#, so the gatewayId in the topic is trustworthy.
 */
public final class Topics {
    private Topics() {}

    public static final String ROOT = "greenpilot";
    public static final String SUBSCRIPTION = ROOT + "/+/up/#";

    public static String commands(String gw) { return ROOT + "/" + gw + "/down/cmd"; }

    public static String eventAck(String gw) { return ROOT + "/" + gw + "/down/event-ack"; }

    public static String commandAck(String gw) { return ROOT + "/" + gw + "/up/cmd-ack"; }

    public record Incoming(String gatewayId, String kind, String key) {
        public static Incoming parse(String topic) {
            String[] p = topic.split("/");
            if (p.length < 4 || !ROOT.equals(p[0]) || !"up".equals(p[2])) return null;
            return new Incoming(p[1], p[3], p.length > 4 ? p[4] : null);
        }
    }
}
