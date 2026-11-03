package tn.supcom.greenpilot.iam.controllers.managers;

/**
 * An OAuth 2.0 error (RFC 6749 section 4.1.2.1 / 5.2), e.g. invalid_grant.
 * redirectable = false when the client or redirect_uri cannot be trusted: then we must show an
 * error page instead of redirecting (otherwise the IAM becomes an open redirector).
 */
public class OAuthException extends RuntimeException {
    private final String error;
    private final boolean redirectable;

    public OAuthException(String error, String description, boolean redirectable) {
        super(description);
        this.error = error;
        this.redirectable = redirectable;
    }

    public String error() { return error; }

    public boolean redirectable() { return redirectable; }
}
