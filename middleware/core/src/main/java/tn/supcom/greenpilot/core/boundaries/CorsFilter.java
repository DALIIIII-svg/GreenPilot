package tn.supcom.greenpilot.core.boundaries;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * Answers CORS pre-flight requests (OPTIONS) so they never reach a resource method.
 * The CORS response headers themselves are added by WildFly on each virtual host (setup/wildfly/*.cli),
 * only for origins of the configured domain; adding them here as well would send them twice.
 */
@Provider
@PreMatching
@ApplicationScoped
public class CorsFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext req) {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            req.abortWith(Response.ok().build());
        }
    }
}
