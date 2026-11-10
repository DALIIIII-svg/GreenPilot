package tn.supcom.greenpilot.api.controllers.managers;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import tn.supcom.greenpilot.api.boundaries.mqtt.CommandAckMessage;
import tn.supcom.greenpilot.api.boundaries.mqtt.ForecastMessage;
import tn.supcom.greenpilot.api.boundaries.mqtt.IncidentMessage;
import tn.supcom.greenpilot.api.boundaries.mqtt.InspectionMessage;
import tn.supcom.greenpilot.api.boundaries.mqtt.TelemetryMessage;
import tn.supcom.greenpilot.api.boundaries.mqtt.Topics;
import tn.supcom.greenpilot.api.controllers.events.InboundMessage;
import tn.supcom.greenpilot.api.controllers.events.MessageEvent;
import tn.supcom.greenpilot.api.controllers.repositories.ForecastRepository;
import tn.supcom.greenpilot.api.controllers.repositories.InspectionRepository;
import tn.supcom.greenpilot.api.controllers.repositories.MeasurementRepository;
import tn.supcom.greenpilot.api.controllers.repositories.SiteRepository;
import tn.supcom.greenpilot.api.entities.Forecast;
import tn.supcom.greenpilot.api.entities.Inspection;
import tn.supcom.greenpilot.api.entities.InspectionStatus;
import tn.supcom.greenpilot.api.entities.Measurement;
import tn.supcom.greenpilot.api.entities.Site;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Consumes MQTT messages (async CDI events), validates them against the message contract
 * (JSON-B + Bean Validation) and stores them. Ids are deterministic (msgId:...), and save() is an upsert,
 * so a gateway re-sending its buffer after an outage creates no duplicates (F03).
 */
@ApplicationScoped
public class IngestManager {

    private static final Logger LOG = Logger.getLogger(IngestManager.class.getName());

    @Inject SiteRepository sites;
    @Inject MeasurementRepository measurements;
    @Inject ForecastRepository forecasts;
    @Inject InspectionRepository inspections;
    @Inject IncidentManager incidents;
    @Inject CommandManager commands;
    @Inject EventManager events;
    @Inject Validator validator;

    private final Jsonb jsonb = JsonbBuilder.create();

    void onMqttMessage(@ObservesAsync @MessageEvent(MessageEvent.Type.MQTT) InboundMessage message) {
        try {
            handle(message);
        } catch (RuntimeException e) {
            LOG.warning("Rejected MQTT message on " + message.topic() + ": " + e.getMessage());
        }
    }

    private void handle(InboundMessage m) {
        Topics.Incoming in = Topics.Incoming.parse(m.topic());
        if (in == null) return;
        Site site = sites.findByGatewayId(in.gatewayId()).orElse(null);
        if (site == null) {
            LOG.warning("Message from unregistered gateway '" + in.gatewayId() + "' ignored - set gatewayId on a site first");
            return;
        }
        switch (in.kind()) {
            case "telemetry" -> telemetry(site, in.gatewayId(), read(m, TelemetryMessage.class));
            case "forecast" -> forecast(site, in.gatewayId(), read(m, ForecastMessage.class));
            case "inspection" -> inspection(site, in.gatewayId(), read(m, InspectionMessage.class));
            case "incident" -> incidents.onConfirmedEvent(site, in.gatewayId(), read(m, IncidentMessage.class));
            case "image" -> incidents.onImage(site, in.gatewayId(), in.key(), m.payload());
            case "cmd-ack" -> commands.onAck(site, read(m, CommandAckMessage.class), m.correlationId());
            default -> LOG.fine("Unknown message kind '" + in.kind() + "'");
        }
    }

    private void telemetry(Site site, String gw, TelemetryMessage t) {
        Instant now = Instant.now();
        for (TelemetryMessage.Reading r : t.readings()) {
            Measurement e = new Measurement();
            e.setId(t.msgId() + ":" + (r.zoneId() == null ? "-" : r.zoneId()) + ":" + r.type());
            e.setMsgId(t.msgId());
            e.setSiteId(site.getId());
            e.setGatewayId(gw);
            e.setDeviceId(t.deviceId());
            e.setZoneId(r.zoneId());
            e.setType(r.type());
            e.setValue(r.value());
            e.setUnit(r.unit());
            e.setQuality(r.quality() == null ? "OK" : r.quality());
            e.setTs(t.ts());
            e.setReceivedAt(now);
            measurements.save(e);
        }
        events.push(site.getId(), "telemetry", t);
    }

    private void forecast(Site site, String gw, ForecastMessage f) {
        Forecast e = new Forecast();
        e.setId(f.msgId() + ":" + f.zoneId());
        e.setMsgId(f.msgId());
        e.setSiteId(site.getId());
        e.setGatewayId(gw);
        e.setZoneId(f.zoneId());
        e.setTs(f.ts());
        e.setHorizonMin(f.horizonMin());
        e.setPredicted(f.predicted());
        e.setBaseline(f.baseline());
        e.setModelVersion(f.modelVersion());
        e.setReceivedAt(Instant.now());
        forecasts.save(e);
        events.push(site.getId(), "forecast", e);
    }

    private void inspection(Site site, String gw, InspectionMessage i) {
        Inspection e = new Inspection();
        e.setId(i.msgId() + ":" + i.plantId());
        e.setMsgId(i.msgId());
        e.setSiteId(site.getId());
        e.setGatewayId(gw);
        e.setPlantId(i.plantId());
        e.setZoneId(i.zoneId());
        e.setTs(i.ts());
        e.setStatus(InspectionStatus.valueOf(i.status()));
        e.setDetections(i.detections() == null ? new ArrayList<>() : new ArrayList<>(i.detections()));
        e.setImageW(i.imageW());
        e.setImageH(i.imageH());
        e.setModelVersion(i.modelVersion());
        e.setImageKey(i.imageKey());
        e.setQuality(i.quality());
        e.setReceivedAt(Instant.now());
        inspections.save(e);
        events.push(site.getId(), "inspection", e);
    }

    private <T> T read(InboundMessage m, Class<T> type) {
        T value;
        try {
            value = jsonb.fromJson(new String(m.payload(), StandardCharsets.UTF_8), type);
        } catch (JsonbException e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage());
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            throw new IllegalArgumentException(violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage()).collect(Collectors.joining(", ")));
        }
        return value;
    }

    @PreDestroy
    void close() {
        try {
            jsonb.close();
        } catch (Exception ignored) {
        }
    }
}
