package tn.supcom.greenpilot.api.boundaries.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

/** POST /models - register a trained model release. */
public record ModelReleaseRequest(@NotNull @Pattern(regexp = "FORECAST|VISION") String kind, @NotBlank String version,
                                  @NotBlank String artifactUri, @NotNull @Pattern(regexp = "[0-9a-fA-F]{64}") String sha256,
                                  Map<String, Double> metrics, String datasetRef, String notes) {
}
