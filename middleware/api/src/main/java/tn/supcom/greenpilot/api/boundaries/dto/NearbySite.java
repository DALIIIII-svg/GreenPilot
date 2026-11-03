package tn.supcom.greenpilot.api.boundaries.dto;



/** GET /sites/nearby - result of the location-based search (F08). */
public record NearbySite(String id, String name, String address, long distanceM, long activeIncidents) {
}
