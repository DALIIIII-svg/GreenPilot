package tn.supcom.greenpilot.core.boundaries;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Validation errors thrown by our code (IllegalArgumentException) -> 400. Bean Validation errors are handled by RESTEasy. */
@Provider
@ApplicationScoped
public class BadRequestMapper implements ExceptionMapper<IllegalArgumentException> {
    @Override
    public Response toResponse(IllegalArgumentException e) {
        return ApiError.of(Response.Status.BAD_REQUEST, e.getMessage()).getResponse();
    }
}
