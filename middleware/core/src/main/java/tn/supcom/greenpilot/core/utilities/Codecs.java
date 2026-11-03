package tn.supcom.greenpilot.core.utilities;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Small encoding helpers used by the OAuth/JWT code. */
public final class Codecs {
    private Codecs() {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DEC = Base64.getUrlDecoder();

    public static String b64url(byte[] bytes) { return B64URL.encodeToString(bytes); }

    public static byte[] b64urlDecode(String s) { return B64URL_DEC.decode(s); }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256Hex(String s) {
        return HexFormat.of().formatHex(sha256(s.getBytes(StandardCharsets.UTF_8)));
    }

    /** Cryptographically random, URL-safe token (authorization codes, refresh tokens). */
    public static String randomToken(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return b64url(b);
    }

    /** Comparison whose duration does not depend on where the strings differ (no timing attack). */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
