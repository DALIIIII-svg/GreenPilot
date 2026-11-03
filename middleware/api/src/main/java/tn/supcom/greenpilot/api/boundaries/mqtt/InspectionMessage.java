package tn.supcom.greenpilot.api.boundaries.mqtt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import tn.supcom.greenpilot.api.entities.Detection;

import java.time.Instant;
import java.util.List;

/** greenpilot/{gw}/up/inspection - routine observation of one plant. */
public record InspectionMessage(@NotBlank String msgId, @NotBlank String plantId, String zoneId, @NotNull Instant ts,
                                @NotNull @Pattern(regexp = "HEALTHY|DISEASE_SUSPECTED|INCONCLUSIVE") String status,
                                List<Detection> detections, Integer imageW, Integer imageH,
                                String modelVersion, String imageKey, String quality) {
}
