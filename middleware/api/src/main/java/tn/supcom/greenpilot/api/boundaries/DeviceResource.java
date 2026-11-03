package tn.supcom.greenpilot.api.boundaries;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
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
import tn.supcom.greenpilot.api.boundaries.dto.DeviceRequest;
import tn.supcom.greenpilot.api.controllers.managers.AccessManager;
import tn.supcom.greenpilot.api.controllers.managers.AuditManager;
import tn.supcom.greenpilot.api.controllers.managers.SiteManager;
import tn.supcom.greenpilot.api.controllers.repositories.DeviceRepository;
import tn.supcom.greenpilot.api.entities.Device;
import tn.supcom.greenpilot.core.boundaries.ApiError;
import tn.supcom.greenpilot.core.boundaries.Conditional;
import tn.supcom.greenpilot.core.controllers.MongoAccess;
import tn.supcom.greenpilot.core.security.AuthenticatedUser;
import tn.supcom.greenpilot.core.security.Principals;
import tn.supcom.greenpilot.core.security.Role;
import tn.supcom.greenpilot.core.security.Scopes;
import tn.supcom.greenpilot.core.security.Secured;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Devices and their W3C WoT Thing Descriptions (objective 6). */
@Path("/devices")
@RequestScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Secured(scopes = Scopes.READ)
public class DeviceResource {

    @Inject DeviceRepository devices;
    @Inject SiteManager sites;
    @Inject AccessManager access;
    @Inject AuditManager audit;
    @Inject MongoAccess mongo;
    @Context SecurityContext securityContext;
    @Context Request request;

    @GET
    public List<Device> list(@QueryParam("siteId") String siteId) {
        access.requireSite(Principals.of(securityContext), siteId);
        return devices.findBySiteId(siteId);
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") String id) {
        return Conditional.ok(request, load(id));
    }

    /** The Thing Description, with its registered media type. */
    @GET
    @Path("/{id}/td")
    @Produces("application/td+json")
    public Response thingDescription(@PathParam("id") String id) {
        Device d = load(id);
        if (d.getThingDescription() == null) throw ApiError.notFound("Thing Description of '" + id + "'");
        return Response.ok(d.getThingDescription(), "application/td+json").build();
    }

    /** Create or replace. If-Match is honoured when the device exists. */
    @PUT
    @Path("/{id}")
    @Secured(roles = Role.ADMIN, scopes = Scopes.ADMIN)
    public Response upsert(@PathParam("id") String id, @Valid DeviceRequest body) {
        AuthenticatedUser user = Principals.of(securityContext);
        sites.get(body.siteId()); // 404 if the site does not exist
        if (body.thingDescription() != null
                && (!body.thingDescription().containsKey("@context") || !body.thingDescription().containsKey("title"))) {
            throw new IllegalArgumentException("A Thing Description needs at least '@context' and 'title'");
        }
        Device device = devices.findById(id).orElse(null);
        if (device != null) {
            Conditional.checkIfMatch(request, device);
            mongo.claimVersion(Device.COLLECTION, id, device.getVersion());
            device.setVersion(device.getVersion() + 1);
        } else {
            device = new Device();
            device.setId(id);
        }
        device.setSiteId(body.siteId());
        device.setKind(body.kind());
        device.setName(body.name());
        device.setZoneIds(body.zoneIds() == null ? new ArrayList<>() : new ArrayList<>(body.zoneIds()));
        device.setThingDescription(body.thingDescription() == null ? null : body.thingDescription().toString());
        device.setUpdatedAt(Instant.now());
        Device saved = devices.save(device);
        audit.log(user, "DEVICE_SAVED", body.siteId(), id);
        return Response.ok(saved).tag(saved.tag()).build();
    }

    private Device load(String id) {
        Device d = devices.findById(id).orElseThrow(() -> ApiError.notFound("Device '" + id + "'"));
        access.requireSite(Principals.of(securityContext), d.getSiteId());
        return d;
    }
}
