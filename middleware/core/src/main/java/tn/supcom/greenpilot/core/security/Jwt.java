package tn.supcom.greenpilot.core.security;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import tn.supcom.greenpilot.core.utilities.Codecs;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

/** Minimal JWT (RFC 7519) signed with EdDSA/Ed25519 (RFC 8037). */
public final class Jwt {
    private Jwt() {}

    public static final String ALG = "EdDSA";

    public static String sign(JsonObject claims, String kid, PrivateKey key) {
        String header = Json.createObjectBuilder().add("alg", ALG).add("typ", "JWT").add("kid", kid).build().toString();
        String unsigned = Codecs.b64url(header.getBytes(StandardCharsets.UTF_8)) + "."
                + Codecs.b64url(claims.toString().getBytes(StandardCharsets.UTF_8));
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(key);
            s.update(unsigned.getBytes(StandardCharsets.US_ASCII));
            return unsigned + "." + Codecs.b64url(s.sign());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Checks the signature and returns the claims. Expiry/issuer/audience are checked by TokenVerifier. */
    public static JsonObject verify(String token, KeyResolver keys) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) throw new SecurityException("Malformed token");
        try {
            JsonObject header = parse(Codecs.b64urlDecode(parts[0]));
            // only accept our algorithm: never "none", never HS256 (classic "alg confusion" attack)
            if (!ALG.equals(header.getString("alg", ""))) throw new SecurityException("Unsupported token algorithm");
            PublicKey key = keys.resolve(header.getString("kid", ""));
            if (key == null) throw new SecurityException("Unknown signing key");
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(key);
            s.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!s.verify(Codecs.b64urlDecode(parts[2]))) throw new SecurityException("Invalid token signature");
            return parse(Codecs.b64urlDecode(parts[1]));
        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("Malformed token");
        }
    }

    private static JsonObject parse(byte[] json) {
        try (JsonReader r = Json.createReader(new StringReader(new String(json, StandardCharsets.UTF_8)))) {
            return r.readObject();
        }
    }
}
