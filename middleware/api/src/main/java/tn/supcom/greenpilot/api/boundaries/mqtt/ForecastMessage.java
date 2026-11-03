package tn.supcom.greenpilot.api.boundaries.mqtt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** greenpilot/{gw}/up/forecast */
public record ForecastMessage(@NotBlank String msgId, @NotBlank String zoneId, @NotNull Instant ts,
                              Integer horizonMin, @NotNull Double predicted, Double baseline, String modelVersion) {
}
