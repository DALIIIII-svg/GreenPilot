package tn.supcom.greenpilot.api.boundaries.dto;

import java.time.Instant;

/** Latest value of one sensor, with its freshness (F06: all observations expose freshness). */
public record SensorReading(String zoneId, String type, Double value, String unit, String quality,
                            Instant ts, long ageSec, boolean stale) {
}
