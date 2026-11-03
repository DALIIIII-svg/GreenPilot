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
import jakarta.json.Json;
import tn.supcom.greenpilot.api.boundaries.dto.DeployRequest;
import tn.supcom.greenpilot.api.boundaries.dto.ModelReleaseRequest;
import tn.supcom.greenpilot.api.controllers.managers.AuditManager;
import tn.supcom.greenpilot.api.controllers.managers.CommandManager;
import tn.supcom.greenpilot.api.controllers.managers.SiteManager;
import tn.supcom.greenpilot.api.controllers.repositories.ModelReleaseRepository;
import tn.supcom.greenpilot.api.entities.Command;
import tn.supcom.greenpilot.api.entities.Deployment;
import tn.supcom.greenpilot.api.entities.ModelRelease;
import tn.supcom.greenpilot.api.entities.Site;
import tn.supcom.greenpilot.core.controllers.MongoAccess;
import tn.supcom.greenpilot.core.utilities.UUIDv7;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Versioned model releases (MLOps 4.4). Register after training, then deploy to a site's gateway.
 * The site's activeModels changes only when the Pi acks COMPLETED; rollback = deploy the previous version.
 */
@Path("/models")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Secured(scopes = Scopes.READ)
public class ModelResource {

    @Inject ModelReleaseRepository releases;
    @Inject SiteManager sites;
    @Inject CommandManager commands;
    @Inject AuditManager audit;
    @Inject MongoAccess mongo;
    @Context SecurityContext securityContext;
    @Context Request request;

    @GET
    public List<ModelRelease> list(@QueryParam("kind") String kind) {
        return releases.findAll()
                .filter(r -> kind == null || kind.equals(r.getKind()))
                .sorted(Comparator.comparing(ModelRelease::getCreatedAt).reversed())
                .toList();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        return Conditional.ok(request, releases.findById(id).orElseThrow(() -> ApiError.notFound("Model release")));
    }

    @POST
    @Secured(roles = Role.ADMIN, scopes = Scopes.ADMIN)
    public Response register(@Valid ModelReleaseRequest body) {
        if (releases.findByKindAndModelVersion(body.kind(), body.version()).isPresent()) {
            throw ApiError.conflict(body.kind() + " " + body.version() + " already registered");
        }
        AuthenticatedUser user = Principals.of(securityContext);
        ModelRelease r = new ModelRelease();
        r.setId(UUIDv7.next());
        r.setKind(body.kind());
        r.setModelVersion(body.version());
        r.setArtifactUri(body.artifactUri());
        r.setSha256(body.sha256().toLowerCase());
        r.setMetrics(body.metrics() == null ? new HashMap<>() : new HashMap<>(body.metrics()));
        r.setDatasetRef(body.datasetRef());
        r.setNotes(body.notes());
        r.setCreatedBy(user.username());
        r.setCreatedAt(Instant.now());
        releases.save(r);
        audit.log(user, "MODEL_REGISTERED", null, r.getKind() + " " + r.getModelVersion());
        return Response.created(URI.create("models/" + r.getId())).entity(r).tag(r.tag()).build();
    }

    @POST
    @Path("/{id}/deploy")
    @Secured(roles = Role.ADMIN, scopes = Scopes.ADMIN)
    public Response deploy(@PathParam("id") String id, @Valid DeployRequest body) {
        AuthenticatedUser user = Principals.of(securityContext);
        ModelRelease r = releases.findById(id).orElseThrow(() -> ApiError.notFound("Model release"));
        Site site = sites.get(body.siteId());
        Command c = commands.send(user, site, "DEPLOY_MODEL", Json.createObjectBuilder()
                .add("kind", r.getKind()).add("version", r.getModelVersion())
                .add("artifactUri", r.getArtifactUri()).add("sha256", r.getSha256()).build());
        mongo.claimVersion(ModelRelease.COLLECTION, r.getId(), r.getVersion());
        Deployment d = new Deployment();
        d.setSiteId(site.getId());
        d.setCommandId(c.getId());
        d.setBy(user.username());
        d.setAt(Instant.now());
        r.getDeployments().add(d);
        r.setVersion(r.getVersion() + 1);
        releases.save(r);
        return Response.accepted(c).build();
    }
}
