package tn.supcom.greenpilot.iam.controllers.managers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tn.supcom.greenpilot.core.boundaries.ApiError;
import tn.supcom.greenpilot.core.controllers.MongoAccess;
import tn.supcom.greenpilot.core.security.AuthenticatedUser;
import tn.supcom.greenpilot.core.security.Role;
import tn.supcom.greenpilot.core.security.Scopes;
import tn.supcom.greenpilot.core.utilities.UUIDv7;
import tn.supcom.greenpilot.iam.boundaries.IdentityRequest;
import tn.supcom.greenpilot.iam.controllers.repositories.IdentityRepository;
import tn.supcom.greenpilot.iam.entities.Identity;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Account administration (F07): create / update identities. */
@ApplicationScoped
public class IdentityManager {

    @Inject IdentityRepository identities;
    @Inject MongoAccess mongo;

    public List<Identity> list() { return identities.findAll().toList(); }

    public Identity get(String id) {
        return identities.findById(id).orElseThrow(() -> ApiError.notFound("Identity"));
    }

    public Identity create(IdentityRequest r) {
        if (r.username() == null || r.password() == null) throw new IllegalArgumentException("username and password are required");
        String username = r.username().trim().toLowerCase();
        if (identities.findByUsername(username).isPresent()) throw ApiError.conflict("Username already exists");
        Set<Role> roles = r.roles() == null || r.roles().isEmpty() ? Set.of(Role.GROWER) : r.roles();
        Identity i = new Identity();
        i.setId(UUIDv7.next());
        i.setUsername(username);
        i.setPasswordHash(PasswordHasher.hash(r.password()));
        i.setDisplayName(r.displayName());
        i.setRoles(Role.toBits(roles));
        i.setProvidedScopes(r.scopes() != null ? r.scopes()
                : roles.contains(Role.ADMIN) ? Scopes.ALL : Scopes.GROWER_DEFAULT);
        i.setActive(r.active() == null || r.active());
        i.setCreatedAt(Instant.now());
        return identities.save(i);
    }

    /** Partial update; version already checked against If-Match by the boundary, claimed atomically here. */
    public Identity update(AuthenticatedUser admin, Identity i, IdentityRequest r) {
        boolean self = i.getId().equals(admin.subject());
        if (self && ((r.active() != null && !r.active()) || (r.roles() != null && !r.roles().contains(Role.ADMIN)))) {
            throw ApiError.conflict("You cannot disable or demote your own administrator account");
        }
        mongo.claimVersion(Identity.COLLECTION, i.getId(), i.getVersion());
        if (r.displayName() != null) i.setDisplayName(r.displayName());
        if (r.roles() != null && !r.roles().isEmpty()) i.setRoles(Role.toBits(r.roles()));
        if (r.scopes() != null) i.setProvidedScopes(r.scopes());
        if (r.active() != null) i.setActive(r.active());
        if (r.password() != null) i.setPasswordHash(PasswordHasher.hash(r.password()));
        i.setVersion(i.getVersion() + 1);
        return identities.save(i);
    }
}
