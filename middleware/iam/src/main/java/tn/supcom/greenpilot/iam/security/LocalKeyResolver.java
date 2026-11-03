package tn.supcom.greenpilot.iam.security;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import tn.supcom.greenpilot.core.security.KeyResolver;
import tn.supcom.greenpilot.iam.controllers.managers.KeyManager;

import java.security.PublicKey;

/** Inside the IAM, tokens (for its admin endpoints) are verified with its own keys. */
@ApplicationScoped
public class LocalKeyResolver implements KeyResolver {

    @Inject
    KeyManager keys;

    @Override
    public PublicKey resolve(String kid) {
        return keys.publicKey(kid);
    }
}
