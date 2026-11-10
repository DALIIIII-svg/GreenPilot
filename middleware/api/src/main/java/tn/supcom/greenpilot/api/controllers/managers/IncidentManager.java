package tn.supcom.greenpilot.api.controllers.managers;

import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.nosql.Template;
import org.bson.Document;
import tn.supcom.greenpilot.api.boundaries.dto.IncidentDetails;
import tn.supcom.greenpilot.api.boundaries.dto.ReviewRequest;
import tn.supcom.greenpilot.api.boundaries.mqtt.IncidentMessage;
import tn.supcom.greenpilot.api.controllers.repositories.DiseaseEventRepository;
import tn.supcom.greenpilot.api.controllers.repositories.IncidentRepository;
import tn.supcom.greenpilot.api.entities.DiseaseEvent;
import tn.supcom.greenpilot.api.entities.Incident;
import tn.supcom.greenpilot.api.entities.IncidentStatus;
import tn.supcom.greenpilot.api.entities.Review;
import tn.supcom.greenpilot.api.entities.Site;
import tn.supcom.greenpilot.api.entities.Verdict;
import tn.supcom.greenpilot.core.boundaries.ApiError;
import tn.supcom.greenpilot.core.controllers.MongoAccess;
import tn.supcom.greenpilot.core.security.AuthenticatedUser;
import tn.supcom.greenpilot.core.utilities.UUIDv7;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static com.mongodb.client.model.Filters.eq;

/**
 * Disease alerts (F09): turns confirmed events into incidents, correlates event + JPEG,
 * acknowledges them to the Pi, and runs the grower workflow (ack -> review -> close).
 */
@ApplicationScoped
public class IncidentManager {

    private static final Logger LOG = Logger.getLogger(IncidentManager.class.getName());
    public static final List<String> ACTIVE = List.of(IncidentStatus.OPEN.name(), IncidentStatus.ACKNOWLEDGED.name());

    @Inject IncidentRepository incidents;
    @Inject DiseaseEventRepository diseaseEvents;
    @Inject Template template;
    @Inject MongoAccess mongo;
    @Inject MqttManager mqtt;
    @Inject EventManager events;
    @Inject AuditManager audit;
    @Inject AccessManager access;

    // ------------------------------------------------------------------ from the gateway

    public void onConfirmedEvent(Site site, String gw, IncidentMessage msg) {
        if (diseaseEvents.findById(msg.eventId()).isPresent()) {
            tryAck(gw, msg.eventId()); // duplicate: the Pi re-sent because it never got our ack
            return;
        }
        DiseaseEvent e = new DiseaseEvent();
        e.setId(msg.eventId());
        e.setSiteId(site.getId());
        e.setGatewayId(gw);
        e.setPlantId(msg.plantId());
        e.setZoneId(msg.zoneId());
        e.setTs(msg.ts());
        e.setLabel(msg.label() == null ? "bacterial_spot_suspected" : msg.label());
        e.setScore(msg.score());
        e.setModelVersion(msg.modelVersion());
        e.setImageKey(msg.imageKey());
        e.setImageW(msg.imageW());
        e.setImageH(msg.imageH());
        e.setDetections(msg.detections() == null ? new ArrayList<>() : new ArrayList<>(msg.detections()));
        e.setReceivedAt(Instant.now());
        diseaseEvents.save(e);

        // one open incident per plant: new detections update it instead of raising a new alert
        List<Incident> open = incidents.findBySiteIdAndPlantIdAndStatusIn(site.getId(), msg.plantId(), ACTIVE);
        Incident incident;
        if (open.isEmpty()) {
            incident = new Incident();
            incident.setId(UUIDv7.next());
            incident.setSiteId(site.getId());
            incident.setPlantId(msg.plantId());
            incident.setZoneId(msg.zoneId());
            incident.setLabel(e.getLabel());
            incident.setStatus(IncidentStatus.OPEN);
            incident.setOpenedAt(msg.ts());
            applyEvent(incident, msg);
            incidents.save(incident);
            LOG.info("New disease incident for plant " + msg.plantId() + " on " + site.getId());
            events.push(site.getId(), "incident.opened", incident);
        } else {
            incident = update(open.getFirst(), i -> applyEvent(i, msg));
            events.push(site.getId(), "incident.updated", incident);
        }
        tryAck(gw, msg.eventId());
    }

    private static void applyEvent(Incident i, IncidentMessage msg) {
        i.getEventIds().add(msg.eventId());
        i.setOccurrences(i.getOccurrences() + 1);
        i.setLastSeenAt(msg.ts());
        i.setLastScore(msg.score());
        i.setModelVersion(msg.modelVersion());
    }

    public void onImage(Site site, String gw, String imageKey, byte[] jpeg) {
        if (imageKey == null || imageKey.isBlank()) throw new IllegalArgumentException("Image topic must be .../up/image/{imageKey}");
        if (!imageExists(imageKey)) {
            Document meta = new Document("imageKey", imageKey).append("siteId", site.getId()).append("gatewayId", gw)
                    .append("contentType", "image/jpeg").append("receivedAt", java.util.Date.from(Instant.now()));
            mongo.images().uploadFromStream(imageKey + ".jpg", new ByteArrayInputStream(jpeg),
                    new GridFSUploadOptions().metadata(meta));
        }
        diseaseEvents.findByImageKey(imageKey).ifPresent(e -> tryAck(gw, e.getId()));
    }

    /** Event and JPEG are two messages in any order: once both are stored, acknowledge to the Pi. */
    private void tryAck(String gw, String eventId) {
        DiseaseEvent e = diseaseEvents.findById(eventId).orElse(null);
        if (e == null) return;
        if (e.getImageKey() != null && !imageExists(e.getImageKey())) return; // wait for the JPEG
        if (!e.isAcked()) {
            e.setAcked(true);
            diseaseEvents.save(e);
        }
        mqtt.publishEventAck(gw, Json.createObjectBuilder()
                .add("eventId", eventId).add("status", "STORED").add("ts", Instant.now().toString()).build().toString());
    }

    public boolean imageExists(String imageKey) {
        return mongo.images().find(eq("metadata.imageKey", imageKey)).first() != null;
    }

    // ------------------------------------------------------------------ queries

    public List<Incident> list(AuthenticatedUser user, String siteId, List<String> statuses, int limit) {
        Set<String> allowed = access.accessibleSites(user);
        if (siteId != null) access.requireSite(user, siteId);
        List<Incident> result;
        if (siteId != null && statuses != null && !statuses.isEmpty()) {
            result = incidents.findBySiteIdAndStatusInOrderByLastSeenAtDesc(siteId, statuses);
        } else if (siteId != null) {
            result = template.select(Incident.class).where("siteId").eq(siteId).orderBy("lastSeenAt").desc().result();
        } else {
            result = template.select(Incident.class).orderBy("lastSeenAt").desc().result();
        }
        return result.stream()
                .filter(i -> allowed == null || allowed.contains(i.getSiteId()))
                .filter(i -> statuses == null || statuses.isEmpty() || statuses.contains(i.getStatus().name()))
                .limit(limit).toList();
    }

    public Incident get(AuthenticatedUser user, String id) {
        Incident i = incidents.findById(id).orElseThrow(() -> ApiError.notFound("Incident"));
        access.requireSite(user, i.getSiteId());
        return i;
    }

    public IncidentDetails details(Incident i) {
        List<DiseaseEvent> evidence = new ArrayList<>();
        for (String eventId : i.getEventIds()) diseaseEvents.findById(eventId).ifPresent(evidence::add);
        evidence.sort(Comparator.comparing(DiseaseEvent::getTs));
        return new IncidentDetails(i, evidence);
    }

    // ------------------------------------------------------------------ grower workflow

    public Incident acknowledge(AuthenticatedUser user, Incident incident) {
        if (incident.getStatus() != IncidentStatus.OPEN) throw ApiError.conflict("Only an OPEN incident can be acknowledged");
        Incident i = update(incident, x -> {
            x.setStatus(IncidentStatus.ACKNOWLEDGED);
            x.setAcknowledgedBy(user.username());
            x.setAcknowledgedAt(Instant.now());
        });
        audit.log(user, "INCIDENT_ACKNOWLEDGED", i.getSiteId(), i.getId());
        events.push(i.getSiteId(), "incident.updated", i);
        return i;
    }

    public Incident review(AuthenticatedUser user, Incident incident, ReviewRequest body) {
        if (incident.getStatus() == IncidentStatus.CLOSED) throw ApiError.conflict("Incident is closed");
        Incident i = update(incident, x -> {
            Review r = new Review();
            r.setVerdict(Verdict.valueOf(body.verdict()));
            r.setNote(body.note());
            r.setBy(user.username());
            r.setAt(Instant.now());
            x.getReviews().add(r);
        });
        audit.log(user, "INCIDENT_REVIEWED", i.getSiteId(), i.getId() + " " + body.verdict());
        events.push(i.getSiteId(), "incident.updated", i);
        return i;
    }

    public Incident close(AuthenticatedUser user, Incident incident, String note) {
        if (incident.getStatus() != IncidentStatus.ACKNOWLEDGED) throw ApiError.conflict("Acknowledge the incident before closing it");
        if (incident.getReviews().isEmpty()) throw ApiError.conflict("Review the incident before closing it");
        Incident i = update(incident, x -> {
            x.setStatus(IncidentStatus.CLOSED);
            x.setClosedBy(user.username());
            x.setClosedAt(Instant.now());
            x.setCloseNote(note);
        });
        audit.log(user, "INCIDENT_CLOSED", i.getSiteId(), i.getId());
        events.push(i.getSiteId(), "incident.updated", i);
        return i;
    }

    /** Optimistic-locking update: claim version v -> v+1 atomically, then save. */
    private Incident update(Incident incident, Consumer<Incident> change) {
        mongo.claimVersion(Incident.COLLECTION, incident.getId(), incident.getVersion());
        change.accept(incident);
        incident.setVersion(incident.getVersion() + 1);
        return incidents.save(incident);
    }
}
