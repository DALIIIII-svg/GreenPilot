package tn.supcom.greenpilot.api.boundaries;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.nosql.Template;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import tn.supcom.greenpilot.api.entities.AuditEntry;
import tn.supcom.greenpilot.core.security.Role;
import tn.supcom.greenpilot.core.security.Scopes;
import tn.supcom.greenpilot.core.security.Secured;

import java.util.List;

/** GET /audit?siteId=&limit= - administrators only. UUIDv7 ids sort by time. */
@Path("/audit")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Secured(roles = Role.ADMIN, scopes = Scopes.ADMIN)
public class AuditResource {

    @Inject
    Template template;

    @GET
    public List<AuditEntry> list(@QueryParam("siteId") String siteId,
                                 @QueryParam("limit") @DefaultValue("100") @Min(1) @Max(1000) int limit) {
        if (siteId == null || siteId.isBlank()) {
            return template.select(AuditEntry.class).orderBy("ts").desc().limit(limit).result();
        }
        return template.select(AuditEntry.class).where("siteId").eq(siteId).orderBy("ts").desc().limit(limit).result();
    }
}
