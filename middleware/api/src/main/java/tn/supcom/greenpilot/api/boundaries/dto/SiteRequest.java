package tn.supcom.greenpilot.api.boundaries.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import tn.supcom.greenpilot.api.entities.IrrigationLimits;
import tn.supcom.greenpilot.api.entities.Thresholds;
import tn.supcom.greenpilot.api.entities.Zone;

import java.util.List;

/** POST /sites (name, lat, lng required) and PUT /sites/{id} (every field optional). */
public record SiteRequest(
        @Size(max = 64) @Pattern(regexp = "[a-z0-9-]+") String id,
        @Size(max = 100) String name,
        @DecimalMin("-90") @DecimalMax("90") Double lat,
        @DecimalMin("-180") @DecimalMax("180") Double lng,
        @Size(max = 200) String address,
        @Pattern(regexp = "[A-Za-z0-9_-]+") String gatewayId,
        List<@Valid Zone> zones,
        IrrigationLimits irrigation,
        Thresholds thresholds) {
}
