package tn.supcom.greenpilot.core.security;

import java.security.Principal;
import java.util.Set;

/** The caller, as described by a verified access token. */
public record AuthenticatedUser(String subject, String username, String name,
                                Set<Role> roles, Set<String> scopes, String clientId) implements Principal {

    @Override
    public String getName() { return username != null ? username : subject; }

    public boolean hasRole(Role role) { return roles.contains(role); }

    public boolean hasScope(String scope) { return scopes.contains(scope); }

    public boolean isAdmin() { return roles.contains(Role.ADMIN) && scopes.contains(Scopes.ADMIN); }
}
