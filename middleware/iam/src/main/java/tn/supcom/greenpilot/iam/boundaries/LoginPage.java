package tn.supcom.greenpilot.iam.boundaries;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import tn.supcom.greenpilot.iam.controllers.managers.OAuthException;
import tn.supcom.greenpilot.iam.entities.Tenant;

/** Server-rendered login form. Every value is HTML-escaped (no XSS through query parameters). */
final class LoginPage {
    private LoginPage() {}

    static Response form(Tenant tenant, OAuthParams p, String error, int status) {
        String html = """
                <!doctype html><html lang="en"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1"><title>GreenPilot - Sign in</title>
                <style>body{font-family:system-ui,sans-serif;background:#f3f5f2;display:grid;place-items:center;min-height:100vh;margin:0}
                form{background:#fff;padding:28px;border-radius:12px;box-shadow:0 2px 12px #0001;width:min(340px,90vw)}
                h1{font-size:20px;margin:0 0 4px}p{color:#555;margin:0 0 18px;font-size:14px}
                label{display:block;font-size:13px;margin:12px 0 4px}input{width:100%%;box-sizing:border-box;padding:10px;border:1px solid #ccc;border-radius:8px}
                button{margin-top:18px;width:100%%;padding:11px;border:0;border-radius:8px;background:#2f6f3e;color:#fff;font-size:15px}
                .err{color:#b00020;font-size:14px;margin-top:10px}</style></head><body>
                <form method="post" action="authorize">
                <h1>GreenPilot</h1><p>Sign in to continue to <b>%s</b></p>
                %s
                <label for="u">E-mail</label><input id="u" name="username" type="email" autocomplete="username" required autofocus>
                <label for="pw">Password</label><input id="pw" name="password" type="password" autocomplete="current-password" required>
                %s
                <button type="submit">Sign in</button></form></body></html>
                """.formatted(esc(tenant.getName()), hiddenFields(p), error == null ? "" : "<div class=\"err\">" + esc(error) + "</div>");
        return secured(Response.status(status).entity(html));
    }

    static Response error(OAuthException e) {
        String html = "<!doctype html><html><head><meta charset=\"utf-8\"><title>Error</title></head><body>"
                + "<h1>Authorization error</h1><p><b>" + esc(e.error()) + "</b>: " + esc(e.getMessage()) + "</p></body></html>";
        return secured(Response.status(Response.Status.BAD_REQUEST).entity(html));
    }

    private static String hiddenFields(OAuthParams p) {
        return hidden("client_id", p.clientId()) + hidden("redirect_uri", p.redirectUri())
                + hidden("response_type", p.responseType()) + hidden("scope", p.scope())
                + hidden("state", p.state()) + hidden("code_challenge", p.codeChallenge())
                + hidden("code_challenge_method", p.codeChallengeMethod());
    }

    private static String hidden(String name, String value) {
        return value == null ? "" : "<input type=\"hidden\" name=\"" + name + "\" value=\"" + esc(value) + "\">";
    }

    /** No caching, no framing (clickjacking), no scripts. */
    private static Response secured(Response.ResponseBuilder b) {
        return b.type(MediaType.TEXT_HTML_TYPE.withCharset("UTF-8"))
                .header("Cache-Control", "no-store")
                .header("X-Frame-Options", "DENY")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; frame-ancestors 'none'")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '&' -> b.append("&amp;");
                case '"' -> b.append("&quot;");
                case '\'' -> b.append("&#39;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }
}
