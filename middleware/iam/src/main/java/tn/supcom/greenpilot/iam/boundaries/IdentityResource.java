package tn.supcom.greenpilot.iam.boundaries;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import tn.supcom.greenpilot.core.boundaries.Conditional;
import tn.supcom.greenpilot.core.security.Principals;
import tn.supcom.greenpilot.core.security.Role;
import tn.supcom.greenpilot.core.security.Scopes;
import tn.supcom.greenpilot.core.security.Secured;
import tn.supcom.greenpilot.iam.controllers.managers.IdentityManager;
import tn.supcom.greenpilot.iam.entities.Identity;

import java.net.URI;
import java.util.List;

/** Account administration (F07), administrators only. Updates support If-Match (412 on conflict). */
@Path("/identities")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Secured(roles = Role.ADMIN, scopes = Scopes.ADMIN)
public class IdentityResource {

    @Inject
    IdentityManager identities;

    @Context
    SecurityContext securityContext;

    @Context
    Request request;

    @GET
    public List<Identity> list() { return identities.list(); }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id) { return Conditional.ok(request, identities.get(id)); }

    @POST
    public Response create(@Valid IdentityRequest body) {
        Identity i = identities.create(body);
        return Response.created(URI.create("identities/" + i.getId())).entity(i).tag(i.tag()).build();
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") String id, @Valid IdentityRequest body) {
        Identity current = identities.get(id);
        Conditional.checkIfMatch(request, current);
        Identity updated = identities.update(Principals.of(securityContext), current, body);
        return Response.ok(updated).tag(updated.tag()).build();
    }
}
