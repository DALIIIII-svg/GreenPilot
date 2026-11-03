package tn.supcom.greenpilot.api.boundaries.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** PUT /users/{subject}/sites - which greenhouses a user may access. */
public record SiteAccessRequest(@NotNull List<@NotBlank String> siteIds) {
}
