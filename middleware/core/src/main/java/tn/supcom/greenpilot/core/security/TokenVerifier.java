package tn.supcom.greenpilot.core.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** Verifies an access token completely: signature, issuer, audience, expiry. Used by REST and WebSocket. */
@ApplicationScoped
public class TokenVerifier {

    private static final long CLOCK_SKEW_SECONDS = 30;

    @Inject
    KeyResolver keys;

    @Inject
    @ConfigProperty(name = "gp.jwt.issuer")
    String issuer;

    @Inject
    @ConfigProperty(name = "gp.jwt.audience", defaultValue = "greenpilot")
    String audience;

    public AuthenticatedUser verify(String token) {
        if (token == null || token.isBlank()) throw new SecurityException("Missing token");
        JsonObject c = Jwt.verify(token, keys);
        try {
            if (!issuer.equals(c.getString("iss", ""))) throw new SecurityException("Wrong token issuer");
            if (!audienceMatches(c.get("aud"))) throw new SecurityException("Token not issued for this audience");
            long now = Instant.now().getEpochSecond();
            if (!c.containsKey("exp") || c.getJsonNumber("exp").longValue() + CLOCK_SKEW_SECONDS < now) {
                throw new SecurityException("Token expired");
            }
            Set<Role> roles = EnumSet.noneOf(Role.class);
            if (c.get("groups") instanceof JsonArray groups) {
                for (JsonString g : groups.getValuesAs(JsonString.class)) {
                    for (Role r : Role.values()) if (r.name().equals(g.getString())) roles.add(r);
                }
            }
            return new AuthenticatedUser(c.getString("sub"), c.getString("upn", null), c.getString("name", null),
                    roles, Scopes.parse(c.getString("scope", "")), c.getString("client_id", null));
        } catch (SecurityException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new SecurityException("Malformed token claims");
        }
    }

    private boolean audienceMatches(JsonValue aud) {
        if (aud instanceof JsonString s) return audience.equals(s.getString());
        if (aud instanceof JsonArray a) {
            return a.getValuesAs(JsonString.class).stream().anyMatch(s -> audience.equals(s.getString()));
        }
        return false;
    }
}
