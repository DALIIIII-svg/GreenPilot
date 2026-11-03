package tn.supcom.greenpilot.api.boundaries.dto;

import jakarta.validation.constraints.NotBlank;

/** POST /models/{id}/deploy */
public record DeployRequest(@NotBlank String siteId) {
}
