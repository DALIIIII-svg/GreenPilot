package tn.supcom.greenpilot.core.boundaries;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Development helper: lets a PWA served from another origin (e.g. http://localhost:5500) call the API.
 * ETag must be "exposed", otherwise JavaScript cannot read it for If-Match.
 * In production Nginx serves PWA + API from one origin: set gp.cors.origin to that origin.
 */
@Provider
@PreMatching
@ApplicationScoped
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Inject
    @ConfigProperty(name = "gp.cors.origin", defaultValue = "*")
    String allowedOrigin;

    @Override
    public void filter(ContainerRequestContext req) {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            req.abortWith(Response.ok().build()); // browser pre-flight
        }
    }

    @Override
    public void filter(ContainerRequestContext req, ContainerResponseContext res) {
        res.getHeaders().putSingle("Access-Control-Allow-Origin", allowedOrigin);
        res.getHeaders().putSingle("Access-Control-Allow-Headers", "Authorization, Content-Type, If-Match, If-None-Match");
        res.getHeaders().putSingle("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        res.getHeaders().putSingle("Access-Control-Expose-Headers", "ETag, Location");
    }
}
