package tn.supcom.greenpilot.api.controllers.managers;

import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.nosql.Template;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tn.supcom.greenpilot.api.boundaries.mqtt.CommandAckMessage;
import tn.supcom.greenpilot.api.controllers.repositories.CommandRepository;
import tn.supcom.greenpilot.api.entities.Command;
import tn.supcom.greenpilot.api.entities.CommandStatus;
import tn.supcom.greenpilot.api.entities.CommandType;
import tn.supcom.greenpilot.api.entities.Site;
import tn.supcom.greenpilot.api.entities.StatusChange;
import tn.supcom.greenpilot.core.boundaries.ApiError;
import tn.supcom.greenpilot.core.security.AuthenticatedUser;
import tn.supcom.greenpilot.core.utilities.UUIDv7;

import java.io.StringReader;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Every command sent to a gateway: unique commandId (UUIDv7), expiry, audit, status history.
 * The gateway/firmware must reject expired or already-seen commandIds (F02).
 */
@ApplicationScoped
public class CommandManager {

    private static final Logger LOG = Logger.getLogger(CommandManager.class.getName());
    private static final long UNCONFIRMED_AFTER_SECONDS = 600;

    @Inject CommandRepository commands;
    @Inject Template template;
    @Inject MqttManager mqtt;
    @Inject AuditManager audit;
    @Inject EventManager events;
    @Inject SiteManager sites;

    @Inject
    @ConfigProperty(name = "gp.command.ttl", defaultValue = "60")
    long ttlSeconds;

    @Resource(lookup = "java:comp/DefaultManagedScheduledExecutorService")
    ManagedScheduledExecutorService scheduler;

    void onStart(@Observes Startup event) {
        scheduler.scheduleWithFixedDelay(this::expireStale, 30, 30, TimeUnit.SECONDS);
    }

    public Command send(AuthenticatedUser user, Site site, CommandType type, JsonObject fields) {
        if (site.getGatewayId() == null) throw ApiError.conflict("Site '" + site.getId() + "' has no gateway registered");
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(ttlSeconds);
        String commandId = UUIDv7.next();
        String payload = Json.createObjectBuilder(fields)
                .add("commandId", commandId).add("action", type.name())
                .add("issuedAt", now.toString()).add("expiresAt", expires.toString())
                .build().toString();

        Command c = new Command();
        c.setId(commandId);
        c.setType(type);
        c.setSiteId(site.getId());
        c.setGatewayId(site.getGatewayId());
        c.setStatus(CommandStatus.PENDING);
        c.setIssuedAt(now);
        c.setExpiresAt(expires);
        c.setRequestedBy(user.username());
        c.setDurationSec(fields.containsKey("durationSec") ? fields.getInt("durationSec") : null);
        c.setPayload(payload);
        c.getHistory().add(StatusChange.of(CommandStatus.PENDING, null));
        commands.save(c);

        try {
            mqtt.publishCommand(site.getGatewayId(), commandId, payload, ttlSeconds);
        } catch (Exception e) {
            c.setStatus(CommandStatus.SEND_FAILED);
            c.getHistory().add(StatusChange.of(CommandStatus.SEND_FAILED, e.getMessage()));
            commands.save(c);
            throw ApiError.of(Response.Status.SERVICE_UNAVAILABLE, "MQTT broker unreachable - command not sent");
        }
        audit.log(user, "COMMAND_" + type.name(), site.getId(), payload);
        events.push(site.getId(), "command", c);
        return c;
    }

    public void onAck(Site site, CommandAckMessage ack, String correlationId) {
        String id = ack.commandId() != null ? ack.commandId() : correlationId;
        if (id == null) throw new IllegalArgumentException("commandId missing (payload or MQTT correlation data)");
        Command c = commands.findById(id).filter(x -> x.getSiteId().equals(site.getId())).orElse(null);
        if (c == null) {
            LOG.warning("Ack for unknown command " + id);
            return;
        }
        CommandStatus status = CommandStatus.valueOf(ack.status());
        c.setStatus(status);
        c.setUpdatedAt(Instant.now());
        c.getHistory().add(StatusChange.of(status, ack.reason()));
        commands.save(c);
        // a model becomes "active" only when the Pi confirms deployment + smoke test
        if (c.getType() == CommandType.DEPLOY_MODEL && status == CommandStatus.COMPLETED) {
            try (JsonReader r = Json.createReader(new StringReader(c.getPayload()))) {
                JsonObject p = r.readObject();
                sites.activateModel(site.getId(), p.getString("kind"), p.getString("version"));
            }
        }
        events.push(site.getId(), "command", c);
    }

    public Command lastOfType(String siteId, CommandType type) {
        List<Command> last = template.select(Command.class).where("siteId").eq(siteId).and("type").eq(type.name())
                .orderBy("issuedAt").desc().limit(1).result();
        return last.isEmpty() ? null : last.getFirst();
    }

    public List<Command> history(String siteId, CommandType type, int limit) {
        return template.select(Command.class).where("siteId").eq(siteId).and("type").eq(type.name())
                .orderBy("issuedAt").desc().limit(limit).result();
    }

    /** Every 30 s: unacknowledged commands expire, so they do not block the next request. */
    void expireStale() {
        try {
            Instant now = Instant.now();
            List<Command> pending = template.select(Command.class).where("status").eq(CommandStatus.PENDING.name())
                    .and("expiresAt").lt(now).result();
            for (Command c : pending) mark(c, CommandStatus.EXPIRED);
            List<Command> accepted = template.select(Command.class).where("status").eq(CommandStatus.ACCEPTED.name())
                    .and("issuedAt").lt(now.minusSeconds(UNCONFIRMED_AFTER_SECONDS)).result();
            for (Command c : accepted) mark(c, CommandStatus.UNCONFIRMED);
        } catch (RuntimeException e) {
            LOG.fine("Command expiry skipped: " + e.getMessage());
        }
    }

    private void mark(Command c, CommandStatus status) {
        c.setStatus(status);
        c.setUpdatedAt(Instant.now());
        c.getHistory().add(StatusChange.of(status, null));
        commands.save(c);
        events.push(c.getSiteId(), "command", c);
    }
}
