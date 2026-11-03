package tn.supcom.greenpilot.api.boundaries;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.nosql.QueryMapper;
import jakarta.nosql.Template;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.SecurityContext;
import tn.supcom.greenpilot.api.controllers.managers.AccessManager;
import tn.supcom.greenpilot.api.entities.Forecast;
import tn.supcom.greenpilot.api.entities.Inspection;
import tn.supcom.greenpilot.api.entities.Measurement;
import tn.supcom.greenpilot.core.security.Principals;
import tn.supcom.greenpilot.core.security.Scopes;
import tn.supcom.greenpilot.core.security.Secured;
import tn.supcom.greenpilot.core.utilities.Times;

import java.time.Instant;
import java.util.List;

/**
 * Histories (F01, 4.1, F05/F06), newest first. Built with the Jakarta NoSQL fluent Template API:
 * the optional filters are added one by one with and(...).
 *   GET /measurements?siteId=&zoneId=&type=&from=&to=&limit=
 *   GET /forecasts?siteId=&zoneId=&from=&to=&limit=
 *   GET /inspections?siteId=&plantId=&status=&from=&to=&limit=
 */
@Path("/")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Secured(scopes = Scopes.READ)
public class MeasurementResource {

    @Inject Template template;
    @Inject AccessManager access;
    @Context SecurityContext securityContext;

    @GET
    @Path("measurements")
    public List<Measurement> measurements(@QueryParam("siteId") String siteId, @QueryParam("zoneId") String zoneId,
                                          @QueryParam("type") String type, @QueryParam("from") String from,
                                          @QueryParam("to") String to,
                                          @QueryParam("limit") @DefaultValue("500") @Min(1) @Max(5000) int limit) {
        access.requireSite(Principals.of(securityContext), siteId);
        QueryMapper.MapperWhere q = template.select(Measurement.class).where("siteId").eq(siteId);
        q = optional(q, "zoneId", zoneId);
        q = optional(q, "type", type);
        q = range(q, from, to);
        return q.orderBy("ts").desc().limit(limit).result();
    }

    @GET
    @Path("forecasts")
    public List<Forecast> forecasts(@QueryParam("siteId") String siteId, @QueryParam("zoneId") String zoneId,
                                    @QueryParam("from") String from, @QueryParam("to") String to,
                                    @QueryParam("limit") @DefaultValue("200") @Min(1) @Max(5000) int limit) {
        access.requireSite(Principals.of(securityContext), siteId);
        QueryMapper.MapperWhere q = template.select(Forecast.class).where("siteId").eq(siteId);
        q = optional(q, "zoneId", zoneId);
        q = range(q, from, to);
        return q.orderBy("ts").desc().limit(limit).result();
    }

    /** Visual inspection timeline; each entry's image is at /api/images/{imageKey}. */
    @GET
    @Path("inspections")
    public List<Inspection> inspections(@QueryParam("siteId") String siteId, @QueryParam("plantId") String plantId,
                                        @QueryParam("status") String status, @QueryParam("from") String from,
                                        @QueryParam("to") String to,
                                        @QueryParam("limit") @DefaultValue("100") @Min(1) @Max(1000) int limit) {
        access.requireSite(Principals.of(securityContext), siteId);
        QueryMapper.MapperWhere q = template.select(Inspection.class).where("siteId").eq(siteId);
        q = optional(q, "plantId", plantId);
        q = optional(q, "status", status);
        q = range(q, from, to);
        return q.orderBy("ts").desc().limit(limit).result();
    }

    private static QueryMapper.MapperWhere optional(QueryMapper.MapperWhere q, String field, String value) {
        return value == null || value.isBlank() ? q : q.and(field).eq(value);
    }

    private static QueryMapper.MapperWhere range(QueryMapper.MapperWhere q, String from, String to) {
        Instant f = Times.parseOrNull(from), t = Times.parseOrNull(to);
        if (f != null) q = q.and("ts").gte(f);
        if (t != null) q = q.and("ts").lte(t);
        return q;
    }
}
