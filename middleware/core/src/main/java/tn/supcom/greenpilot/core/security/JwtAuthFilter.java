package tn.supcom.greenpilot.core.security;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;

import java.security.Principal;
import java.util.Arrays;

/**
 * Resource-server side of OAuth 2.0: runs before every @Secured method.
 * 401 = no/invalid token, 403 = valid token but missing role or scope (RFC 6750 error codes).
 */
@Secured
@Provider
@Priority(Priorities.AUTHENTICATION)
@ApplicationScoped
public class JwtAuthFilter implements ContainerRequestFilter {

    @Context
    ResourceInfo resourceInfo;

    @Inject
    TokenVerifier verifier;

    @Override
    public void filter(ContainerRequestContext ctx) {
        String header = ctx.getHeaderString(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            abort(ctx, Response.Status.UNAUTHORIZED, "invalid_request", "Missing 'Authorization: Bearer <token>' header");
            return;
        }
        AuthenticatedUser user;
        try {
            user = verifier.verify(header.substring(7).trim());
        } catch (SecurityException e) {
            abort(ctx, Response.Status.UNAUTHORIZED, "invalid_token", e.getMessage());
            return;
        }

        Secured rule = rule();
        if (rule.roles().length > 0 && Arrays.stream(rule.roles()).noneMatch(user::hasRole)) {
            abort(ctx, Response.Status.FORBIDDEN, "insufficient_scope", "Requires role " + Arrays.toString(rule.roles()));
            return;
        }
        for (String scope : rule.scopes()) {
            if (!user.hasScope(scope)) {
                abort(ctx, Response.Status.FORBIDDEN, "insufficient_scope", "Token lacks scope '" + scope + "'");
                return;
            }
        }

        boolean secure = ctx.getSecurityContext().isSecure();
        AuthenticatedUser caller = user;
        ctx.setSecurityContext(new SecurityContext() {
            @Override public Principal getUserPrincipal() { return caller; }
            @Override public boolean isUserInRole(String role) { return caller.roles().stream().anyMatch(r -> r.name().equals(role)); }
            @Override public boolean isSecure() { return secure; }
            @Override public String getAuthenticationScheme() { return "Bearer"; }
        });
    }

    private Secured rule() {
        Secured onMethod = resourceInfo.getResourceMethod().getAnnotation(Secured.class);
        if (onMethod != null) return onMethod;
        return resourceInfo.getResourceClass().getAnnotation(Secured.class);
    }

    private static void abort(ContainerRequestContext ctx, Response.Status status, String error, String message) {
        ctx.abortWith(Response.status(status)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"" + error + "\"")
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(Json.createObjectBuilder().add("error", message).add("status", status.getStatusCode()).build().toString())
                .build());
    }
}
