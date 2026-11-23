package tn.supcom.greenpilot.api.boundaries.mqtt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TopicsTest {

    @Test
    void uplinkTopicGivesGatewayAndKind() {
        Topics.Incoming in = Topics.Incoming.parse("greenpilot/pi-01/up/telemetry");
        assertEquals("pi-01", in.gatewayId());
        assertEquals("telemetry", in.kind());
        assertNull(in.key());
    }

    @Test
    void imageTopicCarriesTheImageKey() {
        Topics.Incoming in = Topics.Incoming.parse("greenpilot/pi-01/up/image/plant-7-20261120");
        assertEquals("image", in.kind());
        assertEquals("plant-7-20261120", in.key());
    }

    @Test
    void foreignOrDownlinkTopicsAreIgnored() {
        assertNull(Topics.Incoming.parse("greenpilot/pi-01/down/cmd"));
        assertNull(Topics.Incoming.parse("other/pi-01/up/telemetry"));
        assertNull(Topics.Incoming.parse("greenpilot/pi-01"));
    }

    @Test
    void downlinkTopicsFollowTheContract() {
        assertEquals("greenpilot/pi-01/down/cmd", Topics.commands("pi-01"));
        assertEquals("greenpilot/pi-01/down/event-ack", Topics.eventAck("pi-01"));
        assertEquals("greenpilot/pi-01/up/cmd-ack", Topics.commandAck("pi-01"));
    }
}
