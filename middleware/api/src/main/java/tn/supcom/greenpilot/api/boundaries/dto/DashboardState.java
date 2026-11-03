package tn.supcom.greenpilot.api.boundaries.dto;

import tn.supcom.greenpilot.api.entities.Command;
import tn.supcom.greenpilot.api.entities.Forecast;
import tn.supcom.greenpilot.api.entities.Site;

import java.time.Instant;
import java.util.List;

/** GET /sites/{id}/state - everything the grower dashboard needs in one call. */
public record DashboardState(Site site, List<SensorReading> readings, List<Forecast> forecasts,
                             Command lastIrrigation, long openIncidents, Instant serverTime) {
}
