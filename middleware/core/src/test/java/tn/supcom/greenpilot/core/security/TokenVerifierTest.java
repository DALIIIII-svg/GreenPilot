package tn.supcom.greenpilot.core.security;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tn.supcom.greenpilot.core.utilities.Codecs;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenVerifierTest {

    private static final String ISSUER = "https://iam.greenpilot.local";
    private static final String KID = "key-1";

    private KeyPair keys;
    private TokenVerifier verifier;

    @BeforeEach
    void setUp() {
        keys = Ed25519Keys.generate();
        verifier = new TokenVerifier();
        verifier.keys = kid -> KID.equals(kid) ? keys.getPublic() : null;
        verifier.issuer = ISSUER;
        verifier.audience = "greenpilot";
    }

    private JsonObjectBuilder claims() {
        return Json.createObjectBuilder()
                .add("iss", ISSUER)
                .add("aud", "greenpilot")
                .add("sub", "user-1")
                .add("upn", "grower@greenpilot.local")
                .add("exp", Instant.now().plusSeconds(600).getEpochSecond())
                .add("groups", Json.createArrayBuilder().add("GROWER"))
                .add("scope", "greenhouse:read greenhouse:control");
    }

    private String sign(JsonObject claims) {
        return Jwt.sign(claims, KID, keys.getPrivate());
    }

    @Test
    void validTokenGivesTheUser() {
        AuthenticatedUser user = verifier.verify(sign(claims().build()));
        assertEquals("user-1", user.subject());
        assertEquals("grower@greenpilot.local", user.getName());
        assertEquals(Set.of(Role.GROWER), user.roles());
        assertTrue(user.hasScope(Scopes.CONTROL));
        assertFalse(user.isAdmin());
    }

    @Test
    void adminNeedsRoleAndScope() {
        JsonObject roleOnly = claims().add("groups", Json.createArrayBuilder().add("ADMIN")).build();
        assertFalse(verifier.verify(sign(roleOnly)).isAdmin());
        JsonObject both = claims().add("groups", Json.createArrayBuilder().add("ADMIN")).add("scope", "admin").build();
        assertTrue(verifier.verify(sign(both)).isAdmin());
    }

    @Test
    void expiredTokenIsRejected() {
        JsonObject expired = claims().add("exp", Instant.now().minusSeconds(120).getEpochSecond()).build();
        assertThrows(SecurityException.class, () -> verifier.verify(sign(expired)));
    }

    @Test
    void wrongIssuerIsRejected() {
        assertThrows(SecurityException.class, () -> verifier.verify(sign(claims().add("iss", "https://evil.example").build())));
    }

    @Test
    void wrongAudienceIsRejected() {
        assertThrows(SecurityException.class, () -> verifier.verify(sign(claims().add("aud", "other-api").build())));
    }

    @Test
    void audienceListIsAccepted() {
        JsonObject list = claims().add("aud", Json.createArrayBuilder().add("other").add("greenpilot")).build();
        assertEquals("user-1", verifier.verify(sign(list)).subject());
    }

    @Test
    void modifiedPayloadIsRejected() {
        String[] parts = sign(claims().build()).split("\\.");
        String forged = claims().add("scope", "admin").add("groups", Json.createArrayBuilder().add("ADMIN")).build().toString();
        String tampered = parts[0] + "." + Codecs.b64url(forged.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
        assertThrows(SecurityException.class, () -> verifier.verify(tampered));
    }

    @Test
    void tokenSignedByAnotherKeyIsRejected() {
        String other = Jwt.sign(claims().build(), KID, Ed25519Keys.generate().getPrivate());
        assertThrows(SecurityException.class, () -> verifier.verify(other));
    }

    @Test
    void unsignedTokenIsRejected() {
        String header = Codecs.b64url("{\"alg\":\"none\",\"kid\":\"key-1\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Codecs.b64url(claims().build().toString().getBytes(StandardCharsets.UTF_8));
        assertThrows(SecurityException.class, () -> verifier.verify(header + "." + payload + "."));
    }

    @Test
    void unknownKeyIdIsRejected() {
        String token = Jwt.sign(claims().build(), "key-2", keys.getPrivate());
        assertThrows(SecurityException.class, () -> verifier.verify(token));
    }

    @Test
    void garbageIsRejected() {
        assertThrows(SecurityException.class, () -> verifier.verify(null));
        assertThrows(SecurityException.class, () -> verifier.verify("not-a-token"));
        assertThrows(SecurityException.class, () -> verifier.verify("a.b.c"));
    }
}
