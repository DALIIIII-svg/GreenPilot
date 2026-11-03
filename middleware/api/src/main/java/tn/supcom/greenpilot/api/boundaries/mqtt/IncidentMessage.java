package tn.supcom.greenpilot.api.boundaries.mqtt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tn.supcom.greenpilot.api.entities.Detection;

import java.time.Instant;
import java.util.List;

/** greenpilot/{gw}/up/incident - confirmed disease event; its JPEG arrives on .../up/image/{imageKey}. */
public record IncidentMessage(@NotBlank String eventId, @NotBlank String plantId, String zoneId, @NotNull Instant ts,
                              String label, Double score, String modelVersion, String imageKey,
                              Integer imageW, Integer imageH, List<Detection> detections) {
}
