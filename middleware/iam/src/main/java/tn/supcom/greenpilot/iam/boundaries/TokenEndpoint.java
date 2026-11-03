package tn.supcom.greenpilot.iam.boundaries;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tn.supcom.greenpilot.iam.controllers.managers.AuthorizationManager;
import tn.supcom.greenpilot.iam.controllers.managers.OAuthException;

/** OAuth 2.0 token endpoint (RFC 6749 section 3.2). Form-encoded in, JSON out, never cached. */
@Path("/token")
@RequestScoped
public class TokenEndpoint {

    @Inject
    AuthorizationManager auth;

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response token(@FormParam("grant_type") String grantType, @FormParam("code") String code,
                          @FormParam("redirect_uri") String redirectUri, @FormParam("client_id") String clientId,
                          @FormParam("client_secret") String clientSecret, @FormParam("code_verifier") String codeVerifier,
                          @FormParam("refresh_token") String refreshToken) {
        try {
            JsonObject body = switch (grantType == null ? "" : grantType) {
                case "authorization_code" -> auth.exchangeCode(code, clientId, clientSecret, redirectUri, codeVerifier);
                case "refresh_token" -> auth.refresh(refreshToken, clientId, clientSecret);
                default -> throw new OAuthException("unsupported_grant_type", "Use authorization_code or refresh_token", false);
            };
            return json(Response.ok(), body);
        } catch (OAuthException e) {
            Response.Status status = "invalid_client".equals(e.error()) ? Response.Status.UNAUTHORIZED : Response.Status.BAD_REQUEST;
            return json(Response.status(status), Json.createObjectBuilder()
                    .add("error", e.error()).add("error_description", e.getMessage()).build());
        }
    }

    private static Response json(Response.ResponseBuilder b, JsonObject body) {
        return b.type(MediaType.APPLICATION_JSON_TYPE).entity(body.toString())
                .header("Cache-Control", "no-store").header("Pragma", "no-cache").build();
    }
}
