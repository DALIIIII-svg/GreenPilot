package tn.supcom.greenpilot.iam.boundaries;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tn.supcom.greenpilot.iam.controllers.managers.KeyManager;

/** GET /iam/jwks - public keys used to verify access tokens (RFC 7517). Public, cacheable. */
@Path("/jwks")
@RequestScoped
public class JwksEndpoint {

    @Inject
    KeyManager keys;

    @GET
    public Response jwks() {
        return Response.ok(keys.jwks().toString(), MediaType.APPLICATION_JSON_TYPE)
                .header("Cache-Control", "public, max-age=300").build();
    }
}
