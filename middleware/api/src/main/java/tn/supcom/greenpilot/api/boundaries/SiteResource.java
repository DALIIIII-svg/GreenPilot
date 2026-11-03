package tn.supcom.greenpilot.api.boundaries;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import tn.supcom.greenpilot.api.boundaries.dto.DashboardState;
import tn.supcom.greenpilot.api.boundaries.dto.NearbySite;
import tn.supcom.greenpilot.api.boundaries.dto.SiteRequest;
import tn.supcom.greenpilot.api.controllers.managers.SiteManager;
import tn.supcom.greenpilot.api.entities.Site;
import tn.supcom.greenpilot.core.boundaries.Conditional;
import tn.supcom.greenpilot.core.security.Principals;
import tn.supcom.greenpilot.core.security.Role;
import tn.supcom.greenpilot.core.security.Scopes;
import tn.supcom.greenpilot.core.security.Secured;

import java.net.URI;
import java.util.List;

/** Greenhouses (F07), location search (F08), dashboard (F06). */
@Path("/sites")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Secured(scopes = Scopes.READ)
public class SiteResource {

    @Inject SiteManager sites;
    @Context SecurityContext securityContext;
    @Context Request request;

    @GET
    public List<Site> list() { return sites.list(Principals.of(securityContext)); }

    /** GET /sites/nearby?lat=36.89&lng=10.18&radiusKm=50 (phone geolocation, with the user's permission) */
    @GET
    @Path("/nearby")
    public List<NearbySite> nearby(@QueryParam("lat") @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
                                   @QueryParam("lng") @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
                                   @QueryParam("radiusKm") @DefaultValue("50") @Positive double radiusKm) {
        return sites.nearby(Principals.of(securityContext), lat, lng, radiusKm);
    }

    /** Supports If-None-Match -> 304. */
    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        return Conditional.ok(request, sites.getAccessible(Principals.of(securityContext), id));
    }

    @GET
    @Path("/{id}/state")
    public DashboardState state(@PathParam("id") String id) {
        return sites.state(Principals.of(securityContext), id);
    }

    @POST
    @Secured(roles = Role.ADMIN, scopes = Scopes.ADMIN)
    public Response create(@Valid SiteRequest body) {
        Site s = sites.create(Principals.of(securityContext), body);
        return Response.created(URI.create("sites/" + s.getId())).entity(s).tag(s.tag()).build();
    }

    /** Supports If-Match -> 412 if the site changed since the client read it. */
    @PUT
    @Path("/{id}")
    @Secured(roles = Role.ADMIN, scopes = Scopes.ADMIN)
    public Response update(@PathParam("id") String id, @Valid SiteRequest body) {
        Site current = sites.get(id);
        Conditional.checkIfMatch(request, current);
        Site updated = sites.update(Principals.of(securityContext), current, body);
        return Response.ok(updated).tag(updated.tag()).build();
    }
}
