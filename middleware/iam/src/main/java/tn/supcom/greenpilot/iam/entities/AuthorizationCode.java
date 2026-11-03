package tn.supcom.greenpilot.iam.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import tn.supcom.greenpilot.core.entities.NosqlEntity;

import java.time.Instant;

/** Short-lived (2 min), single-use code of the Authorization Code flow, bound to the PKCE challenge. id = the code. */
@Entity(AuthorizationCode.COLLECTION)
public class AuthorizationCode extends NosqlEntity {
    public static final String COLLECTION = "authorization_codes";

    @Column
    private String tenantId;
    @Column
    private String identityId;
    @Column
    private String redirectUri;
    @Column
    private String scopes;
    @Column
    private String codeChallenge;
    @Column
    private Instant expiresAt;

    // ---- getters / setters (generated)
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getIdentityId() { return identityId; }
    public void setIdentityId(String identityId) { this.identityId = identityId; }
    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
    public String getScopes() { return scopes; }
    public void setScopes(String scopes) { this.scopes = scopes; }
    public String getCodeChallenge() { return codeChallenge; }
    public void setCodeChallenge(String codeChallenge) { this.codeChallenge = codeChallenge; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
