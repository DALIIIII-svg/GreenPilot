package tn.supcom.greenpilot.iam.entities;

import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import tn.supcom.greenpilot.core.entities.NosqlEntity;

import java.time.Instant;

/**
 * Ed25519 key pair used to sign access tokens. id = "kid" published in the JWKS.
 * Development simplification: the private key is stored in MongoDB; in production use a keystore or a vault.
 */
@Entity(SigningKey.COLLECTION)
public class SigningKey extends NosqlEntity {
    public static final String COLLECTION = "signing_keys";

    @Column
    @JsonbTransient
    private String privateKey;   // PKCS#8, base64
    @Column
    private String publicKey;    // X.509, base64
    @Column
    private boolean active;
    @Column
    private Instant createdAt;

    @JsonbTransient
    public String getPrivateKey() { return privateKey; }

    // ---- getters / setters (generated)
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
