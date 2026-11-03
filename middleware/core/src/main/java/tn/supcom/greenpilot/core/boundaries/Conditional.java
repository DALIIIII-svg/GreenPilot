package tn.supcom.greenpilot.core.boundaries;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import tn.supcom.greenpilot.core.entities.RootEntity;

/**
 * HTTP conditional requests built on the entity ETag (RFC 9110):
 *  - GET  + If-None-Match: <etag>  -> 304 Not Modified (client cache still valid, no body sent)
 *  - PUT/POST + If-Match: <etag>   -> 412 Precondition Failed if someone modified it meanwhile
 */
public final class Conditional {
    private Conditional() {}

    public static Response ok(Request request, RootEntity<?> entity) {
        EntityTag tag = new EntityTag(entity.tag());
        Response.ResponseBuilder notModified = request.evaluatePreconditions(tag);
        if (notModified != null) return notModified.tag(tag).build();
        return Response.ok(entity).tag(tag).build();
    }

    public static void checkIfMatch(Request request, RootEntity<?> current) {
        Response.ResponseBuilder failed = request.evaluatePreconditions(new EntityTag(current.tag()));
        if (failed != null) throw new WebApplicationException(failed.build());
    }
}
