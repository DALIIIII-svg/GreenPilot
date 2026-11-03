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
import tn.supcom.greenpilot.api.boundaries.dto.MeView;
import tn.supcom.greenpilot.api.boundaries.dto.SiteAccessRequest;
import tn.supcom.greenpilot.api.controllers.managers.AccessManager;
import tn.supcom.greenpilot.api.controllers.managers.AuditManager;
import tn.supcom.greenpilot.api.controllers.managers.SiteManager;
import tn.supcom.greenpilot.api.controllers.repositories.UserProfileRepository;
import tn.supcom.greenpilot.api.entities.UserProfile;
import tn.supcom.greenpilot.core.controllers.MongoAccess;

import java.util.ArrayList;
import java.util.List;

/**
 * Site access of users. Accounts themselves live in the IAM (/iam/identities);
 * here the administrator decides WHICH greenhouses each account (IAM subject) may see.
 */
@Path("/users")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Secured(roles = Role.ADMIN, scopes = Scopes.ADMIN)
public class UserResource {

    @Inject UserProfileRepository profiles;
    @Inject AccessManager access;
    @Inject SiteManager sites;
    @Inject AuditManager audit;
    @Inject MongoAccess mongo;
    @Context SecurityContext securityContext;
    @Context Request request;

    @GET
    @Path("/me")
    @Secured
    public MeView me() {
        AuthenticatedUser user = Principals.of(securityContext);
        return new MeView(access.profile(user), user.roles(), user.scopes());
    }

    @GET
    public List<UserProfile> list() { return profiles.findAll().toList(); }

    /** subject = the "sub" of the user's tokens = the identity id in the IAM. */
    @PUT
    @Path("/{subject}/sites")
    public Response setSites(@PathParam("subject") String subject, @Valid SiteAccessRequest body) {
        body.siteIds().forEach(sites::get); // 404 for an unknown site
        UserProfile p = profiles.findById(subject).orElse(null);
        if (p == null) {
            p = new UserProfile();
            p.setId(subject);
        } else {
            Conditional.checkIfMatch(request, p);
            mongo.claimVersion(UserProfile.COLLECTION, subject, p.getVersion());
            p.setVersion(p.getVersion() + 1);
        }
        p.setSiteIds(new ArrayList<>(body.siteIds()));
        UserProfile saved = profiles.save(p);
        audit.log(Principals.of(securityContext), "SITE_ACCESS_CHANGED", null, subject + " -> " + body.siteIds());
        return Response.ok(saved).tag(saved.tag()).build();
    }
}
