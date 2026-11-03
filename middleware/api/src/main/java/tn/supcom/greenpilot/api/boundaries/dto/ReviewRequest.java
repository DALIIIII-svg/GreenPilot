package tn.supcom.greenpilot.api.boundaries.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** POST /incidents/{id}/review */
public record ReviewRequest(@NotNull @Pattern(regexp = "CONFIRMED|REJECTED") String verdict, @Size(max = 1000) String note) {
}
