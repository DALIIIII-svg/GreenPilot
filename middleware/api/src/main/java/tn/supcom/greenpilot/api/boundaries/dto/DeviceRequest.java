package tn.supcom.greenpilot.api.boundaries.dto;

import jakarta.json.JsonObject;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/** PUT /devices/{id} - thingDescription is a W3C WoT Thing Description (JSON object). */
public record DeviceRequest(@NotBlank String siteId, @NotNull @Pattern(regexp = "ESP32_NODE|CAMERA|GATEWAY") String kind,
                            String name, List<String> zoneIds, JsonObject thingDescription) {
}
