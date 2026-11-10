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
import tn.supcom.greenpilot.api.boundaries.dto.IrrigationRequest;
import tn.supcom.greenpilot.api.controllers.managers.AccessManager;
import tn.supcom.greenpilot.api.controllers.managers.CommandManager;
import tn.supcom.greenpilot.api.controllers.managers.IrrigationManager;
import tn.supcom.greenpilot.api.entities.Command;
import tn.supcom.greenpilot.api.entities.CommandType;

import java.net.URI;
import java.util.List;

/** Bounded irrigation requests (F02). 202 Accepted: sent to the gateway, not executed yet. */
@Path("/irrigation")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Secured(scopes = Scopes.READ)
public class IrrigationResource {

    @Inject IrrigationManager irrigation;
    @Inject CommandManager commands;
    @Inject AccessManager access;
    @Context SecurityContext securityContext;

    @POST
    @Secured(scopes = {Scopes.READ, Scopes.CONTROL})
    public Response request(@Valid IrrigationRequest body) {
        Command c = irrigation.request(Principals.of(securityContext), body.siteId(), body.durationSec());
        return Response.accepted(c).location(URI.create("irrigation?siteId=" + c.getSiteId())).build();
    }

    @GET
    public List<Command> history(@QueryParam("siteId") String siteId,
                                 @QueryParam("limit") @DefaultValue("50") @Min(1) @Max(500) int limit) {
        access.requireSite(Principals.of(securityContext), siteId);
        return commands.history(siteId, CommandType.IRRIGATE, limit);
    }
}
