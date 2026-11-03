package tn.supcom.greenpilot.iam.controllers.managers;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import tn.supcom.greenpilot.core.security.Ed25519Keys;
import tn.supcom.greenpilot.core.utilities.UUIDv7;
import tn.supcom.greenpilot.iam.controllers.repositories.SigningKeyRepository;
import tn.supcom.greenpilot.iam.entities.SigningKey;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Holds the token-signing keys. Generates the first Ed25519 key pair on first use and keeps it in MongoDB. */
@ApplicationScoped
public class KeyManager {

    @Inject
    SigningKeyRepository repository;

    private final Map<String, PublicKey> publicKeys = new ConcurrentHashMap<>();
    private volatile String currentKid;
    private volatile PrivateKey currentPrivateKey;

    private synchronized void load() {
        if (currentKid != null) return;
        List<SigningKey> active = repository.findByActive(true);
        if (active.isEmpty()) {
            KeyPair pair = Ed25519Keys.generate();
            SigningKey key = new SigningKey();
            key.setId(UUIDv7.next());
            key.setPrivateKey(Ed25519Keys.encodePrivate(pair.getPrivate()));
            key.setPublicKey(Ed25519Keys.encodePublic(pair.getPublic()));
            key.setActive(true);
            key.setCreatedAt(Instant.now());
            repository.save(key);
            active = List.of(key);
        }
        for (SigningKey k : active) publicKeys.put(k.getId(), Ed25519Keys.decodePublic(k.getPublicKey()));
        SigningKey newest = active.stream().max(Comparator.comparing(SigningKey::getCreatedAt)).orElseThrow();
        currentPrivateKey = Ed25519Keys.decodePrivate(newest.getPrivateKey());
        currentKid = newest.getId();
    }

    public String currentKid() {
        load();
        return currentKid;
    }

    public PrivateKey currentPrivateKey() {
        load();
        return currentPrivateKey;
    }

    public PublicKey publicKey(String kid) {
        load();
        return publicKeys.get(kid);
    }

    /** JSON Web Key Set (RFC 7517) published at /iam/jwks: lets the API verify tokens without any secret. */
    public JsonObject jwks() {
        load();
        JsonArrayBuilder keys = Json.createArrayBuilder();
        publicKeys.forEach((kid, key) -> keys.add(Ed25519Keys.toJwk(key, kid)));
        return Json.createObjectBuilder().add("keys", keys).build();
    }
}
