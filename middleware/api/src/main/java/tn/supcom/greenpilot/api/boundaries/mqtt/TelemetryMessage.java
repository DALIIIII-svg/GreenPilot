package tn.supcom.greenpilot.api.boundaries.mqtt;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

/** greenpilot/{gw}/up/telemetry - validated with Bean Validation before being stored. */
public record TelemetryMessage(@NotBlank String msgId, String deviceId, @NotNull Instant ts,
                               @NotEmpty List<@Valid @NotNull Reading> readings) {

    public record Reading(@NotBlank String type, String zoneId, @NotNull Double value, String unit, String quality) {
    }
}
