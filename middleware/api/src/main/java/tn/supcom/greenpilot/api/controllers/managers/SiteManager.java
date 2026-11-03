package tn.supcom.greenpilot.api.controllers.managers;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.nosql.Template;
import jakarta.ws.rs.WebApplicationException;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tn.supcom.greenpilot.api.boundaries.dto.DashboardState;
import tn.supcom.greenpilot.api.boundaries.dto.NearbySite;
import tn.supcom.greenpilot.api.boundaries.dto.SensorReading;
import tn.supcom.greenpilot.api.boundaries.dto.SiteRequest;
import tn.supcom.greenpilot.api.controllers.repositories.SiteRepository;
import tn.supcom.greenpilot.api.entities.Forecast;
import tn.supcom.greenpilot.api.entities.GeoPoint;
import tn.supcom.greenpilot.api.entities.Incident;
import tn.supcom.greenpilot.api.entities.Measurement;
import tn.supcom.greenpilot.api.entities.Plant;
import tn.supcom.greenpilot.api.entities.Site;
import tn.supcom.greenpilot.api.entities.Zone;
import tn.supcom.greenpilot.core.boundaries.ApiError;
import tn.supcom.greenpilot.core.controllers.MongoAccess;
import tn.supcom.greenpilot.core.security.AuthenticatedUser;
import tn.supcom.greenpilot.core.utilities.UUIDv7;

import java.io.StringReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/** Greenhouses: CRUD with optimistic locking, location search (F08), dashboard (F06). */
@ApplicationScoped
public class SiteManager {

    private static final Logger LOG = Logger.getLogger(SiteManager.class.getName());

    @Inject SiteRepository sites;
    @Inject Template template;
    @Inject MongoAccess mongo;
    @Inject AccessManager access;
    @Inject AuditManager audit;
    @Inject CommandManager commands;

    @Inject
    @ConfigProperty(name = "gp.stale-after", defaultValue = "180")
    long staleAfterSeconds;

    private final Jsonb jsonb = JsonbBuilder.create();

    public Site get(String id) {
        return sites.findById(id).orElseThrow(() -> ApiError.notFound("Site '" + id + "'"));
    }

    public Site getAccessible(AuthenticatedUser user, String id) {
        access.requireSite(user, id);
        return get(id);
    }

    public List<Site> list(AuthenticatedUser user) {
        Set<String> ids = access.accessibleSites(user);
        if (ids == null) return sites.findAll().toList();
        return ids.stream().map(sites::findById).flatMap(Optional::stream).toList();
    }

    public Site create(AuthenticatedUser user, SiteRequest r) {
        if (r.name() == null || r.lat() == null || r.lng() == null) throw new IllegalArgumentException("name, lat and lng are required");
        String id = r.id() != null ? r.id() : "site-" + UUIDv7.next().substring(0, 8);
        if (sites.findById(id).isPresent()) throw ApiError.conflict("Site '" + id + "' already exists");
        Site s = new Site();
        s.setId(id);
        apply(s, r);
        s.setCreatedAt(Instant.now());
        sites.save(s);
        audit.log(user, "SITE_CREATED", id, null);
        return s;
    }

    /** Version already checked against If-Match by the boundary; claimed atomically here. */
    public Site update(AuthenticatedUser user, Site s, SiteRequest r) {
        mongo.claimVersion(Site.COLLECTION, s.getId(), s.getVersion());
        apply(s, r);
        s.setVersion(s.getVersion() + 1);
        s.setUpdatedAt(Instant.now());
        Site saved = sites.save(s);
        audit.log(user, "SITE_UPDATED", s.getId(), null);
        if ((r.irrigation() != null || r.thresholds() != null) && saved.getGatewayId() != null) {
            try { // push the new local-rule parameters to the gateway
                JsonObject cfg = Json.createObjectBuilder()
                        .add("irrigation", toJson(saved.getIrrigation()))
                        .add("thresholds", toJson(saved.getThresholds()))
                        .build();
                commands.send(user, saved, "UPDATE_CONFIG", cfg);
            } catch (WebApplicationException e) {
                LOG.warning("Configuration saved but not pushed to " + saved.getGatewayId() + " (broker unreachable)");
            }
        }
        return saved;
    }

    public void activateModel(String siteId, String kind, String version) {
        Site s = get(siteId);
        mongo.claimVersion(Site.COLLECTION, s.getId(), s.getVersion());
        String previous = s.getActiveModels().get(kind);
        if (previous != null && !previous.equals(version)) s.getPreviousModels().put(kind, previous); // for rollback
        s.getActiveModels().put(kind, version);
        s.setVersion(s.getVersion() + 1);
        sites.save(s);
    }

    private void apply(Site s, SiteRequest r) {
        if (r.name() != null) s.setName(r.name());
        if (r.lat() != null || r.lng() != null) {
            if (r.lat() == null || r.lng() == null) throw new IllegalArgumentException("lat and lng go together");
            s.setLocation(GeoPoint.of(r.lat(), r.lng()));
        }
        if (r.address() != null) s.setAddress(r.address());
        if (r.gatewayId() != null) {
            sites.findByGatewayId(r.gatewayId()).filter(other -> !other.getId().equals(s.getId())).ifPresent(other -> {
                throw ApiError.conflict("Gateway '" + r.gatewayId() + "' already belongs to site " + other.getId());
            });
            s.setGatewayId(r.gatewayId());
        }
        if (r.zones() != null) s.setZones(validated(r.zones()));
        if (r.irrigation() != null) s.setIrrigation(r.irrigation());
        if (r.thresholds() != null) s.setThresholds(r.thresholds());
    }

    private static List<Zone> validated(List<Zone> zones) {
        Set<String> zoneIds = new HashSet<>(), plantIds = new HashSet<>();
        for (Zone z : zones) {
            if (z.getId() == null || !zoneIds.add(z.getId())) throw new IllegalArgumentException("Zone ids must be present and unique");
            for (Plant p : z.getPlants()) {
                if (p.getId() == null || !plantIds.add(p.getId())) throw new IllegalArgumentException("Plant ids must be present and unique");
            }
        }
        return new ArrayList<>(zones);
    }

    private JsonObject toJson(Object o) {
        try (JsonReader r = Json.createReader(new StringReader(jsonb.toJson(o)))) {
            return r.readObject();
        }
    }

    // ------------------------------------------------------------------ location-based service (F08)

    /** $geoNear is MongoDB-specific (Jakarta NoSQL has no geo queries), so it uses the driver. */
    public List<NearbySite> nearby(AuthenticatedUser user, double lat, double lng, double radiusKm) {
        Set<String> allowed = access.accessibleSites(user);
        Document query = allowed == null ? new Document() : new Document("_id", new Document("$in", new ArrayList<>(allowed)));
        List<Document> pipeline = List.of(
                new Document("$geoNear", new Document("near", new Document("type", "Point").append("coordinates", List.of(lng, lat)))
                        .append("distanceField", "distanceM")
                        .append("maxDistance", radiusKm * 1000)
                        .append("spherical", true)
                        .append("query", query)),
                new Document("$lookup", new Document("from", Incident.COLLECTION)
                        .append("let", new Document("sid", "$_id"))
                        .append("pipeline", List.of(
                                new Document("$match", new Document("$expr", new Document("$and", List.of(
                                        new Document("$eq", List.of("$siteId", "$$sid")),
                                        new Document("$in", List.of("$status", IncidentManager.ACTIVE)))))),
                                new Document("$count", "n")))
                        .append("as", "incidents")));
        List<NearbySite> result = new ArrayList<>();
        for (Document d : mongo.collection(Site.COLLECTION).aggregate(pipeline)) {
            List<Document> counts = d.getList("incidents", Document.class, List.of());
            long active = counts.isEmpty() ? 0 : ((Number) counts.getFirst().get("n")).longValue();
            result.add(new NearbySite(d.getString("_id"), d.getString("name"), d.getString("address"),
                    Math.round(((Number) d.get("distanceM")).doubleValue()), active));
        }
        return result;
    }

    // ------------------------------------------------------------------ dashboard (F06)

    public DashboardState state(AuthenticatedUser user, String siteId) {
        Site site = getAccessible(user, siteId);
        Instant now = Instant.now();

        // latest value per (zone, type) over the last 24 h - aggregation through the driver.
        // JNoSQL stores Instant fields as ISO-8601 strings, which sort chronologically as text.
        List<Document> pipeline = List.of(
                new Document("$match", new Document("siteId", siteId).append("ts", new Document("$gte", now.minus(Duration.ofHours(24)).toString()))),
                new Document("$sort", new Document("ts", -1)),
                new Document("$group", new Document("_id", new Document("zoneId", "$zoneId").append("type", "$type"))
                        .append("value", new Document("$first", "$value"))
                        .append("unit", new Document("$first", "$unit"))
                        .append("quality", new Document("$first", "$quality"))
                        .append("ts", new Document("$first", "$ts"))));
        List<SensorReading> readings = new ArrayList<>();
        for (Document g : mongo.collection(Measurement.COLLECTION).aggregate(pipeline)) {
            Document key = g.get("_id", Document.class);
            Instant ts = Instant.parse(g.getString("ts"));
            long age = Duration.between(ts, now).toSeconds();
            Double value = g.get("value") instanceof Number n ? n.doubleValue() : null;
            readings.add(new SensorReading(key.getString("zoneId"), key.getString("type"), value,
                    g.getString("unit"), g.getString("quality"), ts, age, age > staleAfterSeconds));
        }

        List<Forecast> forecasts = new ArrayList<>();
        for (Zone z : site.getZones()) {
            List<Forecast> latest = template.select(Forecast.class).where("siteId").eq(siteId).and("zoneId").eq(z.getId())
                    .orderBy("ts").desc().limit(1).result();
            forecasts.addAll(latest);
        }
        List<Incident> open = template.select(Incident.class).where("siteId").eq(siteId)
                .and("status").in(IncidentManager.ACTIVE).result();
        return new DashboardState(site, readings, forecasts, commands.lastOfType(siteId, "IRRIGATE"), open.size(), now);
    }

    @PreDestroy
    void close() {
        try {
            jsonb.close();
        } catch (Exception ignored) {
        }
    }
}
