package tn.supcom.greenpilot.core.boundaries;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.NoSuchElementException;

/** Optional.orElseThrow() on a missing entity -> 404. */
@Provider
@ApplicationScoped
public class NotFoundMapper implements ExceptionMapper<NoSuchElementException> {
    @Override
    public Response toResponse(NoSuchElementException e) {
        return ApiError.of(Response.Status.NOT_FOUND, "Not found").getResponse();
    }
}
