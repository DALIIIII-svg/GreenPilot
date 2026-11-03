package tn.supcom.greenpilot.api.boundaries.dto;

import tn.supcom.greenpilot.api.entities.DiseaseEvent;
import tn.supcom.greenpilot.api.entities.Incident;

import java.util.List;

/** GET /incidents/{id} - the incident and its evidence events. */
public record IncidentDetails(Incident incident, List<DiseaseEvent> events) {
}
