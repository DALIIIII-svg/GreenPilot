package tn.supcom.greenpilot.core.utilities;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/** ISO-8601 parsing that produces an HTTP 400 (IllegalArgumentException) instead of a 500. */
public final class Times {
    private Times() {}

    public static Instant parseOrNull(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return Instant.parse(iso);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Bad timestamp '" + iso + "' (expected ISO-8601, e.g. 2026-11-02T12:00:00Z)");
        }
    }
}
