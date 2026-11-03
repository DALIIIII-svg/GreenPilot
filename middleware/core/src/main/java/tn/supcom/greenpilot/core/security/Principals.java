package tn.supcom.greenpilot.core.security;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.core.SecurityContext;

/** Inside a @Secured resource: Principals.of(securityContext) returns the caller. */
public final class Principals {
    private Principals() {}

    public static AuthenticatedUser of(SecurityContext sc) {
        if (sc != null && sc.getUserPrincipal() instanceof AuthenticatedUser user) return user;
        throw new NotAuthorizedException("Bearer");
    }
}
