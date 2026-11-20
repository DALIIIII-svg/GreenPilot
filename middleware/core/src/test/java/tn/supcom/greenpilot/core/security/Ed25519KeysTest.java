package tn.supcom.greenpilot.core.security;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Ed25519KeysTest {

    @Test
    void publicKeySurvivesTheJwkRoundTrip() {
        KeyPair pair = Ed25519Keys.generate();
        JsonObject jwk = Ed25519Keys.toJwk(pair.getPublic(), "k1");
        assertEquals("OKP", jwk.getString("kty"));
        assertEquals("Ed25519", jwk.getString("crv"));
        assertEquals("k1", jwk.getString("kid"));
        PublicKey back = Ed25519Keys.fromJwkX(jwk.getString("x"));
        assertArrayEquals(pair.getPublic().getEncoded(), back.getEncoded());
    }

    @Test
    void keyFromJwkVerifiesTokens() {
        KeyPair pair = Ed25519Keys.generate();
        PublicKey published = Ed25519Keys.fromJwkX(Ed25519Keys.toJwk(pair.getPublic(), "k1").getString("x"));
        String token = Jwt.sign(Json.createObjectBuilder().add("sub", "s").build(), "k1", pair.getPrivate());
        assertEquals("s", Jwt.verify(token, kid -> published).getString("sub"));
    }

    @Test
    void storedKeysDecodeBack() {
        KeyPair pair = Ed25519Keys.generate();
        assertArrayEquals(pair.getPrivate().getEncoded(),
                Ed25519Keys.decodePrivate(Ed25519Keys.encodePrivate(pair.getPrivate())).getEncoded());
        assertArrayEquals(pair.getPublic().getEncoded(),
                Ed25519Keys.decodePublic(Ed25519Keys.encodePublic(pair.getPublic())).getEncoded());
    }
}
