package tn.supcom.greenpilot.core.security;

import java.security.PublicKey;

/**
 * Finds the public key that verifies a token, from the "kid" in the token header.
 * IAM: reads its own keys. API: downloads them from the IAM JWKS endpoint.
 */
public interface KeyResolver {
    /** @return the key, or null if unknown */
    PublicKey resolve(String kid);
}
