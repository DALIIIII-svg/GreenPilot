package tn.supcom.greenpilot.api.boundaries.mqtt;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** greenpilot/{gw}/up/cmd-ack - commandId may also come from the MQTT5 correlation data. */
public record CommandAckMessage(String commandId,
                                @NotNull @Pattern(regexp = "ACCEPTED|REJECTED|COMPLETED|FAILED") String status,
                                String reason) {
}
