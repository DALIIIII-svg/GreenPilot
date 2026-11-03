package tn.supcom.greenpilot.api.boundaries;

import com.mongodb.client.gridfs.model.GridFSFile;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.StreamingOutput;
import tn.supcom.greenpilot.api.controllers.managers.AccessManager;
import tn.supcom.greenpilot.core.boundaries.ApiError;
import tn.supcom.greenpilot.core.controllers.MongoAccess;
import tn.supcom.greenpilot.core.security.Principals;
import tn.supcom.greenpilot.core.security.Scopes;
import tn.supcom.greenpilot.core.security.Secured;

import static com.mongodb.client.model.Filters.eq;

/**
 * GET /api/images/{imageKey} - JPEG from GridFS. An <img src> cannot send the Authorization header:
 * the PWA loads it with fetch(url, {headers}) and URL.createObjectURL(blob).
 */
@Path("/images")
@RequestScoped
@Secured(scopes = Scopes.READ)
public class ImageResource {

    @Inject MongoAccess mongo;
    @Inject AccessManager access;
    @Context SecurityContext securityContext;

    @GET
    @Path("/{key}")
    public Response get(@PathParam("key") String key) {
        GridFSFile file = mongo.images().find(eq("metadata.imageKey", key)).first();
        if (file == null || file.getMetadata() == null) throw ApiError.notFound("Image '" + key + "'");
        access.requireSite(Principals.of(securityContext), file.getMetadata().getString("siteId"));
        StreamingOutput body = out -> mongo.images().downloadToStream(file.getObjectId(), out);
        return Response.ok(body, "image/jpeg")
                .header("Cache-Control", "private, max-age=86400, immutable") // an image never changes
                .build();
    }
}
