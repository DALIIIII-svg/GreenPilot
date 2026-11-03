package tn.supcom.greenpilot.api.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tn.supcom.greenpilot.core.security.Ed25519Keys;
import tn.supcom.greenpilot.core.security.KeyResolver;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.PublicKey;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * The API is an OAuth 2.0 resource server: it downloads the IAM public keys (JWKS) and caches them.
 * An unknown "kid" (key rotation) triggers a refresh, at most once every 30 s.
 */
@ApplicationScoped
public class JwksKeyResolver implements KeyResolver {

    private static final Logger LOG = Logger.getLogger(JwksKeyResolver.class.getName());

    @Inject
    @ConfigProperty(name = "gp.iam.jwks-url")
    String jwksUrl;

    private final Map<String, PublicKey> keys = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private volatile long lastRefresh;

    @Override
    public PublicKey resolve(String kid) {
        PublicKey key = keys.get(kid);
        if (key == null && System.currentTimeMillis() - lastRefresh > 30_000) {
            refresh();
            key = keys.get(kid);
        }
        return key;
    }

    private synchronized void refresh() {
        lastRefresh = System.currentTimeMillis();
        try {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(jwksUrl)).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw new IllegalStateException("HTTP " + res.statusCode());
            try (JsonReader r = Json.createReader(new StringReader(res.body()))) {
                for (JsonObject jwk : r.readObject().getJsonArray("keys").getValuesAs(JsonObject.class)) {
                    if ("OKP".equals(jwk.getString("kty", "")) && "Ed25519".equals(jwk.getString("crv", ""))) {
                        keys.put(jwk.getString("kid"), Ed25519Keys.fromJwkX(jwk.getString("x")));
                    }
                }
            }
        } catch (Exception e) {
            LOG.warning("Could not load IAM keys from " + jwksUrl + ": " + e.getMessage());
        }
    }
}
