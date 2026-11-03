package tn.supcom.greenpilot.iam.entities;

import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import tn.supcom.greenpilot.core.entities.NosqlEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * An OAuth 2.0 client application (course skeleton: Tenant). id = client_id.
 * The PWA is a "public" client (no secret, it runs in a browser): PKCE replaces the secret.
 */
@Entity(Tenant.COLLECTION)
public class Tenant extends NosqlEntity {
    public static final String COLLECTION = "tenants";

    @Column
    private String name;
    @Column
    @JsonbTransient
    private String secretHash;               // null for public clients
    @Column
    private List<String> redirectUris = new ArrayList<>();
    @Column
    private long allowedRoles;               // bitmask of Role
    @Column
    private String requiredScopes;
    @Column
    private String supportedGrantTypes;      // "authorization_code refresh_token"

    @JsonbTransient
    public String getSecretHash() { return secretHash; }

    // ---- getters / setters (generated)
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public void setSecretHash(String secretHash) { this.secretHash = secretHash; }
    public List<String> getRedirectUris() { return redirectUris; }
    public void setRedirectUris(List<String> redirectUris) { this.redirectUris = redirectUris; }
    public long getAllowedRoles() { return allowedRoles; }
    public void setAllowedRoles(long allowedRoles) { this.allowedRoles = allowedRoles; }
    public String getRequiredScopes() { return requiredScopes; }
    public void setRequiredScopes(String requiredScopes) { this.requiredScopes = requiredScopes; }
    public String getSupportedGrantTypes() { return supportedGrantTypes; }
    public void setSupportedGrantTypes(String supportedGrantTypes) { this.supportedGrantTypes = supportedGrantTypes; }
}
