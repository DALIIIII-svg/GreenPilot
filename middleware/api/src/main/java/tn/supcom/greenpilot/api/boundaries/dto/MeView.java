package tn.supcom.greenpilot.api.boundaries.dto;

import tn.supcom.greenpilot.api.entities.UserProfile;
import tn.supcom.greenpilot.core.security.Role;

import java.util.Set;

/** GET /users/me */
public record MeView(UserProfile profile, Set<Role> roles, Set<String> scopes) {
}
