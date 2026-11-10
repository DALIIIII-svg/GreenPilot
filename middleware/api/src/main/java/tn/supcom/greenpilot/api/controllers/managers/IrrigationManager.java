package tn.supcom.greenpilot.api.controllers.managers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.nosql.Template;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tn.supcom.greenpilot.api.entities.Command;
import tn.supcom.greenpilot.api.entities.CommandStatus;
import tn.supcom.greenpilot.api.entities.CommandType;
import tn.supcom.greenpilot.api.entities.IrrigationLimits;
import tn.supcom.greenpilot.api.entities.Measurement;
import tn.supcom.greenpilot.api.entities.Site;
import tn.supcom.greenpilot.core.boundaries.ApiError;
import tn.supcom.greenpilot.core.security.AuthenticatedUser;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Bounded manual irrigation (F02). The cloud checks the limits BEFORE sending;
 * the ESP32 firmware must check them again (it is the real safety barrier).
 */
@ApplicationScoped
public class IrrigationManager {

    @Inject SiteManager sites;
    @Inject CommandManager commands;
    @Inject Template template;

    @Inject
    @ConfigProperty(name = "gp.stale-after", defaultValue = "180")
    long staleAfterSeconds;

    public Command request(AuthenticatedUser user, String siteId, int durationSec) {
        Site site = sites.getAccessible(user, siteId);
        IrrigationLimits limits = site.getIrrigation() == null ? new IrrigationLimits() : site.getIrrigation();
        int max = limits.getMaxDurationSec() == null ? 30 : limits.getMaxDurationSec();
        int cooldown = limits.getCooldownSec() == null ? 300 : limits.getCooldownSec();

        // 1. bounded duration
        if (durationSec < 1 || durationSec > max) throw new IllegalArgumentException("durationSec must be between 1 and " + max);

        // 2. reservoir known, fresh and OK (value 1 = water available, 0 = low)
        List<Measurement> last = template.select(Measurement.class).where("siteId").eq(siteId)
                .and("type").eq("reservoir").orderBy("ts").desc().limit(1).result();
        if (last.isEmpty()) throw ApiError.conflict("Reservoir status unknown - no reading received yet");
        Measurement reservoir = last.getFirst();
        long age = Duration.between(reservoir.getTs(), Instant.now()).toSeconds();
        if (age > staleAfterSeconds) throw ApiError.conflict("Reservoir reading is stale (" + age + " s old)");
        if (reservoir.getValue() == null || reservoir.getValue() == 0) throw ApiError.conflict("Reservoir level too low - irrigation refused");

        // 3. one command at a time + cooldown between waterings
        Command previous = commands.lastOfType(siteId, CommandType.IRRIGATE);
        if (previous != null) {
            CommandStatus status = previous.getStatus();
            if (status == CommandStatus.PENDING || status == CommandStatus.ACCEPTED) throw ApiError.conflict("Another irrigation command is still in progress");
            if (status == CommandStatus.COMPLETED) {
                long since = Duration.between(previous.getIssuedAt(), Instant.now()).toSeconds();
                if (since < cooldown) throw ApiError.conflict("Cooldown active: wait " + (cooldown - since) + " s");
            }
        }
        return commands.send(user, site, CommandType.IRRIGATE,
                Json.createObjectBuilder().add("durationSec", durationSec).add("source", "MANUAL").build());
    }
}
