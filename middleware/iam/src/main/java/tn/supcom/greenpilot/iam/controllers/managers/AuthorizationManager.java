package tn.supcom.greenpilot.iam.controllers.managers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import tn.supcom.greenpilot.core.security.Scopes;
import tn.supcom.greenpilot.core.utilities.Codecs;
import tn.supcom.greenpilot.iam.controllers.repositories.AuthorizationCodeRepository;
import tn.supcom.greenpilot.iam.controllers.repositories.GrantRepository;
import tn.supcom.greenpilot.iam.controllers.repositories.IdentityRepository;
import tn.supcom.greenpilot.iam.controllers.repositories.RefreshTokenRepository;
import tn.supcom.greenpilot.iam.controllers.repositories.TenantRepository;
import tn.supcom.greenpilot.iam.entities.AuthorizationCode;
import tn.supcom.greenpilot.iam.entities.Grant;
import tn.supcom.greenpilot.iam.entities.Identity;
import tn.supcom.greenpilot.iam.entities.RefreshToken;
import tn.supcom.greenpilot.iam.entities.Tenant;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

/**
 * OAuth 2.0 Authorization Code flow with PKCE (RFC 6749 + RFC 7636), as recommended by OAuth 2.1 for browser apps.
 *  1. PWA -> GET /authorize?client_id&redirect_uri&code_challenge=BASE64URL(SHA256(verifier))...
 *  2. user logs in -> IAM redirects to redirect_uri?code=...&state=...
 *  3. PWA -> POST /token code + code_verifier -> access_token (JWT) + refresh_token
 * A stolen code is useless without the verifier, which never left the browser.
 */
@ApplicationScoped
public class AuthorizationManager {

    private static final long CODE_TTL_SECONDS = 120;

    @Inject TenantRepository tenants;
    @Inject IdentityRepository identities;
    @Inject GrantRepository grants;
    @Inject AuthorizationCodeRepository codes;
    @Inject RefreshTokenRepository refreshTokens;
    @Inject TokenManager tokens;

    /** Client + exact redirect_uri match. Errors here are NOT redirectable. */
    public Tenant checkClient(String clientId, String redirectUri) {
        Tenant tenant = clientId == null ? null : tenants.findById(clientId).orElse(null);
        if (tenant == null) throw new OAuthException("invalid_client", "Unknown client_id", false);
        if (redirectUri == null || !tenant.getRedirectUris().contains(redirectUri)) {
            throw new OAuthException("invalid_request", "redirect_uri is not registered for this client", false);
        }
        return tenant;
    }

    public void checkRequest(Tenant tenant, String responseType, String codeChallenge, String method) {
        if (!"code".equals(responseType)) {
            throw new OAuthException("unsupported_response_type", "Only response_type=code is supported", true);
        }
        if (!Scopes.parse(tenant.getSupportedGrantTypes()).contains("authorization_code")) {
            throw new OAuthException("unauthorized_client", "Client may not use the authorization code flow", true);
        }
        if (codeChallenge == null || codeChallenge.length() < 43 || !"S256".equals(method)) {
            throw new OAuthException("invalid_request", "PKCE is required: code_challenge with code_challenge_method=S256", true);
        }
    }

    /** @return the identity, or null if the credentials are wrong (same answer for unknown user and wrong password). */
    public Identity authenticate(String username, String password) {
        if (username == null || password == null) return null;
        Identity identity = identities.findByUsername(username.trim().toLowerCase()).orElse(null);
        if (identity == null) {
            PasswordHasher.hash(password); // spend the same time as a real check: no user enumeration by timing
            return null;
        }
        return identity.isActive() && PasswordHasher.verify(password, identity.getPasswordHash()) ? identity : null;
    }

    /** approved = requested ∩ scopes the identity may grant; the tenant's required scopes must survive. */
    public String approveScopes(Tenant tenant, Identity identity, String requested) {
        if ((identity.getRoles() & tenant.getAllowedRoles()) == 0) {
            throw new OAuthException("access_denied", "Your account is not allowed to use this application", true);
        }
        Set<String> approved = new TreeSet<>(Scopes.parse(
                requested == null || requested.isBlank() ? tenant.getRequiredScopes() : requested));
        approved.retainAll(Scopes.parse(identity.getProvidedScopes()));
        if (!approved.containsAll(Scopes.parse(tenant.getRequiredScopes()))) {
            throw new OAuthException("access_denied", "Your account lacks the scopes this application requires", true);
        }
        return Scopes.join(approved);
    }

    public String issueCode(Tenant tenant, Identity identity, String redirectUri, String scopes, String codeChallenge) {
        // record the consent (first-party PWA: approved automatically, no consent screen)
        String grantId = tenant.getId() + ":" + identity.getId();
        Grant grant = grants.findById(grantId).orElseGet(Grant::new);
        grant.setId(grantId);
        grant.setTenantId(tenant.getId());
        grant.setIdentityId(identity.getId());
        grant.setApprovedScopes(scopes);
        grant.setIssuanceDateTime(Instant.now());
        grants.save(grant);

        String code = Codecs.randomToken(32);
        AuthorizationCode c = new AuthorizationCode();
        c.setId(code);
        c.setTenantId(tenant.getId());
        c.setIdentityId(identity.getId());
        c.setRedirectUri(redirectUri);
        c.setScopes(scopes);
        c.setCodeChallenge(codeChallenge);
        c.setExpiresAt(Instant.now().plusSeconds(CODE_TTL_SECONDS));
        codes.save(c);
        return code;
    }

    /** grant_type=authorization_code */
    public JsonObject exchangeCode(String code, String clientId, String clientSecret, String redirectUri, String verifier) {
        if (code == null || code.isBlank()) throw new OAuthException("invalid_request", "code is required", false);
        AuthorizationCode c = codes.findById(code)
                .orElseThrow(() -> new OAuthException("invalid_grant", "Unknown, expired or already used code", false));
        codes.deleteById(code); // single use, even if the checks below fail
        if (c.getExpiresAt().isBefore(Instant.now())) throw new OAuthException("invalid_grant", "Code expired", false);
        if (!c.getTenantId().equals(clientId) || !c.getRedirectUri().equals(redirectUri)) {
            throw new OAuthException("invalid_grant", "client_id or redirect_uri does not match the authorization request", false);
        }
        Tenant tenant = tenants.findById(clientId).orElseThrow(() -> new OAuthException("invalid_client", "Unknown client", false));
        authenticateClient(tenant, clientSecret);
        String expected = verifier == null ? null : Codecs.b64url(Codecs.sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        if (!Codecs.constantTimeEquals(expected, c.getCodeChallenge())) {
            throw new OAuthException("invalid_grant", "PKCE verification failed", false);
        }
        Identity identity = identities.findById(c.getIdentityId()).filter(Identity::isActive)
                .orElseThrow(() -> new OAuthException("invalid_grant", "Account disabled", false));
        return tokens.issue(identity, tenant, c.getScopes());
    }

    /** grant_type=refresh_token, with rotation: the old refresh token is deleted. */
    public JsonObject refresh(String refreshToken, String clientId, String clientSecret) {
        if (refreshToken == null || refreshToken.isBlank()) throw new OAuthException("invalid_request", "refresh_token is required", false);
        RefreshToken stored = refreshTokens.findById(Codecs.sha256Hex(refreshToken))
                .orElseThrow(() -> new OAuthException("invalid_grant", "Unknown or already used refresh token", false));
        refreshTokens.deleteById(stored.getId());
        if (stored.getExpiresAt().isBefore(Instant.now())) throw new OAuthException("invalid_grant", "Refresh token expired", false);
        if (!stored.getTenantId().equals(clientId)) throw new OAuthException("invalid_grant", "Token issued to another client", false);
        Tenant tenant = tenants.findById(clientId).orElseThrow(() -> new OAuthException("invalid_client", "Unknown client", false));
        authenticateClient(tenant, clientSecret);
        Identity identity = identities.findById(stored.getIdentityId()).filter(Identity::isActive)
                .orElseThrow(() -> new OAuthException("invalid_grant", "Account disabled", false));
        return tokens.issue(identity, tenant, stored.getScopes());
    }

    /** Confidential clients (with a secret) must authenticate; public clients rely on PKCE. */
    private void authenticateClient(Tenant tenant, String secret) {
        if (tenant.getSecretHash() != null && !PasswordHasher.verify(secret, tenant.getSecretHash())) {
            throw new OAuthException("invalid_client", "Client authentication failed", false);
        }
    }
}
