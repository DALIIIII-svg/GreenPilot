package tn.supcom.greenpilot.iam.controllers.managers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tn.supcom.greenpilot.core.security.Jwt;
import tn.supcom.greenpilot.core.security.Role;
import tn.supcom.greenpilot.core.utilities.Codecs;
import tn.supcom.greenpilot.core.utilities.UUIDv7;
import tn.supcom.greenpilot.iam.controllers.repositories.RefreshTokenRepository;
import tn.supcom.greenpilot.iam.entities.Identity;
import tn.supcom.greenpilot.iam.entities.RefreshToken;
import tn.supcom.greenpilot.iam.entities.Tenant;

import java.time.Instant;

/**
 * Issues tokens. The access token is a short-lived JWT (claim names follow MicroProfile JWT: upn, groups);
 * the refresh token is an opaque random string stored hashed.
 */
@ApplicationScoped
public class TokenManager {

    @Inject
    KeyManager keys;

    @Inject
    RefreshTokenRepository refreshTokens;

    @Inject
    @ConfigProperty(name = "gp.jwt.issuer")
    String issuer;

    @Inject
    @ConfigProperty(name = "gp.jwt.audience", defaultValue = "greenpilot")
    String audience;

    @Inject
    @ConfigProperty(name = "gp.iam.access-token-ttl", defaultValue = "900")
    long accessTtlSeconds;

    @Inject
    @ConfigProperty(name = "gp.iam.refresh-token-ttl", defaultValue = "604800")
    long refreshTtlSeconds;

    /** Token response of RFC 6749 section 5.1. */
    public JsonObject issue(Identity identity, Tenant tenant, String scopes) {
        long now = Instant.now().getEpochSecond();
        JsonArrayBuilder groups = Json.createArrayBuilder();
        for (Role r : Role.fromBits(identity.getRoles())) groups.add(r.name());

        JsonObject claims = Json.createObjectBuilder()
                .add("iss", issuer)
                .add("sub", identity.getId())
                .add("aud", audience)
                .add("upn", identity.getUsername())
                .add("name", identity.getDisplayName() != null ? identity.getDisplayName() : identity.getUsername())
                .add("groups", groups)
                .add("scope", scopes)
                .add("client_id", tenant.getId())
                .add("iat", now)
                .add("exp", now + accessTtlSeconds)
                .add("jti", UUIDv7.next())
                .build();
        String accessToken = Jwt.sign(claims, keys.currentKid(), keys.currentPrivateKey());

        String refreshToken = Codecs.randomToken(32);
        RefreshToken stored = new RefreshToken();
        stored.setId(Codecs.sha256Hex(refreshToken));
        stored.setTenantId(tenant.getId());
        stored.setIdentityId(identity.getId());
        stored.setScopes(scopes);
        stored.setExpiresAt(Instant.now().plusSeconds(refreshTtlSeconds));
        refreshTokens.save(stored);

        return Json.createObjectBuilder()
                .add("access_token", accessToken)
                .add("token_type", "Bearer")
                .add("expires_in", accessTtlSeconds)
                .add("refresh_token", refreshToken)
                .add("scope", scopes)
                .build();
    }
}
