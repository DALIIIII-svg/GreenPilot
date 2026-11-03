package tn.supcom.greenpilot.iam.controllers.managers;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.Startup;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import tn.supcom.greenpilot.core.controllers.MongoAccess;
import tn.supcom.greenpilot.core.security.Role;
import tn.supcom.greenpilot.core.security.Scopes;
import tn.supcom.greenpilot.iam.boundaries.IdentityRequest;
import tn.supcom.greenpilot.iam.controllers.repositories.IdentityRepository;
import tn.supcom.greenpilot.iam.controllers.repositories.TenantRepository;
import tn.supcom.greenpilot.iam.entities.AuthorizationCode;
import tn.supcom.greenpilot.iam.entities.Identity;
import tn.supcom.greenpilot.iam.entities.RefreshToken;
import tn.supcom.greenpilot.iam.entities.Tenant;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/** First start: indexes, the default administrator and the PWA client registration. */
@ApplicationScoped
public class Bootstrap {

    private static final Logger LOG = Logger.getLogger(Bootstrap.class.getName());

    @Inject MongoAccess mongo;
    @Inject IdentityRepository identities;
    @Inject TenantRepository tenants;
    @Inject IdentityManager identityManager;

    @Inject
    @ConfigProperty(name = "gp.iam.admin.username")
    String adminUsername;

    @Inject
    @ConfigProperty(name = "gp.iam.admin.password")
    String adminPassword;

    @Inject
    @ConfigProperty(name = "gp.iam.pwa.client-id", defaultValue = "greenpilot-pwa")
    String pwaClientId;

    @Inject
    @ConfigProperty(name = "gp.iam.pwa.redirect-uris")
    List<String> pwaRedirectUris;

    void onStart(@Observes Startup event) {
        try {
            mongo.collection(Identity.COLLECTION).createIndex(Indexes.ascending("username"), new IndexOptions().unique(true));
            // Expired codes / refresh tokens are always rejected; this just removes the leftovers.
            // (A MongoDB TTL index would need BSON dates, but JNoSQL stores Instant as ISO-8601 text.)
            String now = Instant.now().toString();
            mongo.collection(AuthorizationCode.COLLECTION).deleteMany(Filters.lt("expiresAt", now));
            mongo.collection(RefreshToken.COLLECTION).deleteMany(Filters.lt("expiresAt", now));

            if (identities.findAll().findAny().isEmpty()) {
                identityManager.create(new IdentityRequest(adminUsername, adminPassword, "Administrator",
                        Set.of(Role.ADMIN, Role.GROWER), Scopes.ALL, true));
                LOG.warning("Default administrator created: " + adminUsername + " - change its password!");
            }

            Tenant pwa = tenants.findById(pwaClientId).orElseGet(Tenant::new);
            pwa.setId(pwaClientId);
            pwa.setName("GreenPilot PWA");
            pwa.setSecretHash(null); // public client: PKCE instead of a secret
            pwa.setRedirectUris(new ArrayList<>(pwaRedirectUris));
            pwa.setAllowedRoles(Role.toBits(Set.of(Role.GROWER, Role.ADMIN)));
            pwa.setRequiredScopes(Scopes.READ);
            pwa.setSupportedGrantTypes("authorization_code refresh_token");
            tenants.save(pwa);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "IAM bootstrap failed - is MongoDB running?", e);
        }
    }
}
