package tn.supcom.greenpilot.iam.boundaries;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import tn.supcom.greenpilot.core.security.Role;

import java.util.Set;

/** Body of POST/PUT /identities (all fields optional on PUT). */
public record IdentityRequest(
        @Email String username,
        @Size(min = 8, max = 128) String password,
        @Size(max = 100) String displayName,
        Set<Role> roles,
        String scopes,
        Boolean active) {
}
