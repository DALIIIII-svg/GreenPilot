package tn.supcom.greenpilot.api.boundaries.dto;

import jakarta.validation.constraints.Size;

/** POST /incidents/{id}/close */
public record CloseRequest(@Size(max = 1000) String note) {
}
