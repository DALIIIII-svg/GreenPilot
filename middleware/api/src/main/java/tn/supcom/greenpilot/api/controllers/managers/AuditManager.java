package tn.supcom.greenpilot.api.controllers.managers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tn.supcom.greenpilot.api.controllers.repositories.AuditEntryRepository;
import tn.supcom.greenpilot.api.entities.AuditEntry;
import tn.supcom.greenpilot.core.security.AuthenticatedUser;
import tn.supcom.greenpilot.core.utilities.UUIDv7;

import java.time.Instant;
import java.util.logging.Logger;

/** Control auditing (irrigation, configuration, deployments, incident reviews, access changes). */
@ApplicationScoped
public class AuditManager {

    private static final Logger LOG = Logger.getLogger(AuditManager.class.getName());

    @Inject
    AuditEntryRepository audit;

    public void log(AuthenticatedUser who, String action, String siteId, String details) {
        AuditEntry e = new AuditEntry();
        e.setId(UUIDv7.next());
        e.setTs(Instant.now());
        e.setAction(action);
        e.setSubject(who == null ? "system" : who.subject());
        e.setUsername(who == null ? "system" : who.username());
        e.setSiteId(siteId);
        e.setDetails(details);
        try {
            audit.insert(e); // append-only
        } catch (RuntimeException ex) {
            LOG.warning("Audit log failed: " + ex.getMessage());
        }
    }
}
