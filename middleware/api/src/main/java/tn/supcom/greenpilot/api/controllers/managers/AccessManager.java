package tn.supcom.greenpilot.api.controllers.managers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tn.supcom.greenpilot.api.controllers.repositories.UserProfileRepository;
import tn.supcom.greenpilot.api.entities.UserProfile;
import tn.supcom.greenpilot.core.boundaries.ApiError;
import tn.supcom.greenpilot.core.security.AuthenticatedUser;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** Site-level authorization: administrators see everything, growers only the sites in their profile. */
@ApplicationScoped
public class AccessManager {

    @Inject
    UserProfileRepository profiles;

    public UserProfile profile(AuthenticatedUser user) {
        return profiles.findById(user.subject()).orElseGet(() -> {
            UserProfile p = new UserProfile();
            p.setId(user.subject());
            p.setUsername(user.username());
            p.setDisplayName(user.name());
            p.setLastSeenAt(Instant.now());
            return profiles.save(p);
        });
    }

    public boolean canAccess(AuthenticatedUser user, String siteId) {
        return user.isAdmin() || profile(user).getSiteIds().contains(siteId);
    }

    public void requireSite(AuthenticatedUser user, String siteId) {
        if (siteId == null || siteId.isBlank()) throw new IllegalArgumentException("'siteId' is required");
        if (!canAccess(user, siteId)) throw ApiError.forbidden("No access to site '" + siteId + "'");
    }

    /** @return the accessible site ids, or null meaning "all sites" (administrator) */
    public Set<String> accessibleSites(AuthenticatedUser user) {
        return user.isAdmin() ? null : new HashSet<>(profile(user).getSiteIds());
    }
}
