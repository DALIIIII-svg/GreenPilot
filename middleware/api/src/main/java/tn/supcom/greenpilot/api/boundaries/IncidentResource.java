package tn.supcom.greenpilot.api.boundaries;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import tn.supcom.greenpilot.core.boundaries.ApiError;
import tn.supcom.greenpilot.core.boundaries.Conditional;
import tn.supcom.greenpilot.core.security.AuthenticatedUser;
import tn.supcom.greenpilot.core.security.Principals;
import tn.supcom.greenpilot.core.security.Role;
import tn.supcom.greenpilot.core.security.Scopes;
import tn.supcom.greenpilot.core.security.Secured;
import tn.supcom.greenpilot.api.boundaries.dto.CloseRequest;
import tn.supcom.greenpilot.api.boundaries.dto.ReviewRequest;
import tn.supcom.greenpilot.api.controllers.managers.IncidentManager;
import tn.supcom.greenpilot.api.entities.Incident;

import java.util.Arrays;
import java.util.List;

/**
 * Disease alerts (F09). OPEN -> ACKNOWLEDGED -> (reviews) -> CLOSED.
 * New alerts are pushed over the WebSocket; GET /incidents?status=OPEN remains for (re)loading.
 * State changes honour If-Match: a stale page gets 412 instead of overwriting newer evidence.
 */
@Path("/incidents")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Secured(scopes = Scopes.READ)
public class IncidentResource {

    @Inject IncidentManager incidents;
    @Context SecurityContext securityContext;
    @Context Request request;

    /** ?siteId=&status=OPEN,ACKNOWLEDGED&limit= */
    @GET
    public List<Incident> list(@QueryParam("siteId") String siteId, @QueryParam("status") String status,
                               @QueryParam("limit") @DefaultValue("50") @Min(1) @Max(500) int limit) {
        List<String> statuses = status == null || status.isBlank() ? List.of() : Arrays.asList(status.split(","));
        return incidents.list(Principals.of(securityContext), siteId, statuses, limit);
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        Incident i = incidents.get(Principals.of(securityContext), id);
        return Response.ok(incidents.details(i)).tag(i.tag()).build();
    }

    @POST
    @Path("/{id}/ack")
    @Secured(scopes = {Scopes.READ, Scopes.INCIDENTS})
    public Response acknowledge(@PathParam("id") String id) {
        AuthenticatedUser user = Principals.of(securityContext);
        Incident i = checked(user, id);
        return reply(incidents.acknowledge(user, i));
    }

    @POST
    @Path("/{id}/review")
    @Secured(scopes = {Scopes.READ, Scopes.INCIDENTS})
    public Response review(@PathParam("id") String id, @Valid ReviewRequest body) {
        AuthenticatedUser user = Principals.of(securityContext);
        return reply(incidents.review(user, checked(user, id), body));
    }

    @POST
    @Path("/{id}/close")
    @Secured(scopes = {Scopes.READ, Scopes.INCIDENTS})
    public Response close(@PathParam("id") String id, @Valid CloseRequest body) {
        AuthenticatedUser user = Principals.of(securityContext);
        return reply(incidents.close(user, checked(user, id), body == null ? null : body.note()));
    }

    private Incident checked(AuthenticatedUser user, String id) {
        Incident i = incidents.get(user, id);
        Conditional.checkIfMatch(request, i);
        return i;
    }

    private static Response reply(Incident i) {
        return Response.ok(i).tag(i.tag()).build();
    }
}
