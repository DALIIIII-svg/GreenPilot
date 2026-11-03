package tn.supcom.greenpilot.core.boundaries;

import jakarta.json.Json;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** throw ApiError.conflict("...") anywhere -> the client receives {"error":"...","status":409}. */
public final class ApiError {
    private ApiError() {}

    public static WebApplicationException of(Response.Status status, String message) {
        return new WebApplicationException(message, Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Json.createObjectBuilder().add("error", message).add("status", status.getStatusCode()).build().toString())
                .build());
    }

    public static WebApplicationException notFound(String what) { return of(Response.Status.NOT_FOUND, what + " not found"); }

    public static WebApplicationException conflict(String message) { return of(Response.Status.CONFLICT, message); }

    public static WebApplicationException forbidden(String message) { return of(Response.Status.FORBIDDEN, message); }

    public static WebApplicationException preconditionFailed(String message) { return of(Response.Status.PRECONDITION_FAILED, message); }
}
