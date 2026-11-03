package tn.supcom.greenpilot.iam.boundaries;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tn.supcom.greenpilot.iam.controllers.managers.AuthorizationManager;
import tn.supcom.greenpilot.iam.controllers.managers.OAuthException;
import tn.supcom.greenpilot.iam.entities.Identity;
import tn.supcom.greenpilot.iam.entities.Tenant;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** OAuth 2.0 authorization endpoint (RFC 6749 section 3.1): login form, then redirect with the code. */
@Path("/authorize")
@RequestScoped
public class AuthorizationEndpoint {

    @Inject
    AuthorizationManager auth;

    @GET
    public Response showLogin(@QueryParam("client_id") String clientId, @QueryParam("redirect_uri") String redirectUri,
                              @QueryParam("response_type") String responseType, @QueryParam("scope") String scope,
                              @QueryParam("state") String state, @QueryParam("code_challenge") String codeChallenge,
                              @QueryParam("code_challenge_method") String method) {
        OAuthParams p = new OAuthParams(clientId, redirectUri, responseType, scope, state, codeChallenge, method);
        Tenant tenant;
        try {
            tenant = auth.checkClient(clientId, redirectUri);
        } catch (OAuthException e) {
            return LoginPage.error(e);
        }
        try {
            auth.checkRequest(tenant, responseType, codeChallenge, method);
        } catch (OAuthException e) {
            return redirectWithError(p, e);
        }
        return LoginPage.form(tenant, p, null, 200);
    }

    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response login(@FormParam("client_id") String clientId, @FormParam("redirect_uri") String redirectUri,
                          @FormParam("response_type") String responseType, @FormParam("scope") String scope,
                          @FormParam("state") String state, @FormParam("code_challenge") String codeChallenge,
                          @FormParam("code_challenge_method") String method,
                          @FormParam("username") String username, @FormParam("password") String password) {
        OAuthParams p = new OAuthParams(clientId, redirectUri, responseType, scope, state, codeChallenge, method);
        Tenant tenant;
        try {
            tenant = auth.checkClient(clientId, redirectUri);
        } catch (OAuthException e) {
            return LoginPage.error(e);
        }
        try {
            auth.checkRequest(tenant, responseType, codeChallenge, method);
            Identity identity = auth.authenticate(username, password);
            if (identity == null) return LoginPage.form(tenant, p, "Invalid e-mail or password", 401);
            String scopes = auth.approveScopes(tenant, identity, scope);
            String code = auth.issueCode(tenant, identity, redirectUri, scopes, codeChallenge);
            // 303: the browser follows with a GET to the PWA callback
            return Response.seeOther(URI.create(withParams(redirectUri, "code", code, "state", state)))
                    .header("Cache-Control", "no-store").build();
        } catch (OAuthException e) {
            return redirectWithError(p, e);
        }
    }

    private static Response redirectWithError(OAuthParams p, OAuthException e) {
        String uri = withParams(p.redirectUri(), "error", e.error(), "error_description", e.getMessage(), "state", p.state());
        return Response.seeOther(URI.create(uri)).header("Cache-Control", "no-store").build();
    }

    private static String withParams(String base, String... kv) {
        StringBuilder b = new StringBuilder(base);
        char sep = base.contains("?") ? '&' : '?';
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i + 1] == null) continue;
            b.append(sep).append(kv[i]).append('=').append(URLEncoder.encode(kv[i + 1], StandardCharsets.UTF_8));
            sep = '&';
        }
        return b.toString();
    }
}
