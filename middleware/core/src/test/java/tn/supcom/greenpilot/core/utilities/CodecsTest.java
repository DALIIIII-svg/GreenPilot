package tn.supcom.greenpilot.core.utilities;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodecsTest {

    @Test
    void pkceChallengeMatchesRfc7636Example() {
        String verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String challenge = Codecs.b64url(Codecs.sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", challenge);
    }

    @Test
    void base64UrlRoundTripHasNoPadding() {
        byte[] data = {(byte) 0xfb, (byte) 0xff, 0x01};
        String encoded = Codecs.b64url(data);
        assertFalse(encoded.contains("=") || encoded.contains("+") || encoded.contains("/"));
        assertEquals(java.util.Arrays.toString(data), java.util.Arrays.toString(Codecs.b64urlDecode(encoded)));
    }

    @Test
    void sha256HexOfKnownValue() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Codecs.sha256Hex("abc"));
    }

    @Test
    void randomTokensAreUrlSafeAndDifferent() {
        String a = Codecs.randomToken(32);
        String b = Codecs.randomToken(32);
        assertNotEquals(a, b);
        assertEquals(43, a.length());
        assertTrue(a.matches("[A-Za-z0-9_-]+"));
    }

    @Test
    void constantTimeEquals() {
        assertTrue(Codecs.constantTimeEquals("same", "same"));
        assertFalse(Codecs.constantTimeEquals("same", "other"));
        assertFalse(Codecs.constantTimeEquals(null, "x"));
        assertFalse(Codecs.constantTimeEquals("x", null));
    }
}
