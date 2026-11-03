package tn.supcom.greenpilot.api.boundaries.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** POST /irrigation. The per-site maximum is checked by IrrigationManager. */
public record IrrigationRequest(@NotBlank String siteId, @NotNull @Min(1) @Max(3600) Integer durationSec) {
}
