package tn.supcom.greenpilot.core.boundaries;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.MongoWriteException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.logging.Level;
import java.util.logging.Logger;

/** Duplicate key -> 409, MongoDB unreachable -> 503 (instead of an HTML error page). */
@Provider
@ApplicationScoped
public class MongoErrorMapper implements ExceptionMapper<MongoException> {
    private static final Logger LOG = Logger.getLogger(MongoErrorMapper.class.getName());

    @Override
    public Response toResponse(MongoException e) {
        if (e instanceof MongoWriteException w && w.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
            return ApiError.conflict("Already exists (duplicate key)").getResponse();
        }
        LOG.log(Level.WARNING, "MongoDB error", e);
        return ApiError.of(Response.Status.SERVICE_UNAVAILABLE, "Database unavailable").getResponse();
    }
}
