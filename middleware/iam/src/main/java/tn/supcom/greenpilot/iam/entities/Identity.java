package tn.supcom.greenpilot.iam.entities;

import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import tn.supcom.greenpilot.core.entities.NosqlEntity;
import tn.supcom.greenpilot.core.security.Role;

import java.security.Principal;
import java.time.Instant;
import java.util.Set;

/** A user account (course skeleton: Identity). roles = bitmask, providedScopes = scopes this user may grant. */
@Entity(Identity.COLLECTION)
public class Identity extends NosqlEntity implements Principal {
    public static final String COLLECTION = "identities";

    @Column
    private String username;          // e-mail, unique
    @Column
    @JsonbTransient
    private String passwordHash;      // PBKDF2, never serialized
    @Column
    private long roles;
    @Column
    private String providedScopes;
    @Column
    private String displayName;
    @Column
    private boolean active = true;
    @Column
    private Instant createdAt;

    @Override
    public String getName() { return username; }

    /** Readable form of the roles bitmask in API responses. */
    public Set<Role> getRoleNames() { return Role.fromBits(roles); }

    @JsonbTransient
    public String getPasswordHash() { return passwordHash; }

    // ---- getters / setters (generated)
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public long getRoles() { return roles; }
    public void setRoles(long roles) { this.roles = roles; }
    public String getProvidedScopes() { return providedScopes; }
    public void setProvidedScopes(String providedScopes) { this.providedScopes = providedScopes; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
