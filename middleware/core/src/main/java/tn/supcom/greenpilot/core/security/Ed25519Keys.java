package tn.supcom.greenpilot.core.security;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import tn.supcom.greenpilot.core.utilities.Codecs;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Ed25519 keys (EdDSA signatures, built into the JDK). Asymmetric signing means the API only needs the
 * PUBLIC key to verify tokens: no shared secret between IAM and API. Public keys are published as JWK (RFC 8037).
 */
public final class Ed25519Keys {
    private Ed25519Keys() {}

    /** DER prefix of an X.509 SubjectPublicKeyInfo for Ed25519; the raw 32-byte key follows it. */
    private static final byte[] X509_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");

    public static KeyPair generate() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String encodePrivate(PrivateKey key) { return Base64.getEncoder().encodeToString(key.getEncoded()); }

    public static String encodePublic(PublicKey key) { return Base64.getEncoder().encodeToString(key.getEncoded()); }

    public static PrivateKey decodePrivate(String b64) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(b64)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static PublicKey decodePublic(String b64) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(b64)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** JWK "x" member = base64url of the raw 32-byte public key. */
    public static PublicKey fromJwkX(String x) {
        byte[] raw = Codecs.b64urlDecode(x);
        byte[] spki = Arrays.copyOf(X509_PREFIX, X509_PREFIX.length + raw.length);
        System.arraycopy(raw, 0, spki, X509_PREFIX.length, raw.length);
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(spki));
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("Invalid Ed25519 JWK", e);
        }
    }

    public static JsonObject toJwk(PublicKey key, String kid) {
        byte[] enc = key.getEncoded();
        String x = Codecs.b64url(Arrays.copyOfRange(enc, enc.length - 32, enc.length));
        return Json.createObjectBuilder()
                .add("kty", "OKP").add("crv", "Ed25519").add("use", "sig").add("alg", Jwt.ALG)
                .add("kid", kid).add("x", x)
                .build();
    }
}
