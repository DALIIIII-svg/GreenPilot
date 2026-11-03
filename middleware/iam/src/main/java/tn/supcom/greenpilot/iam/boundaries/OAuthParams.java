package tn.supcom.greenpilot.iam.boundaries;

/** The parameters of an authorization request, carried through the login form. */
public record OAuthParams(String clientId, String redirectUri, String responseType, String scope,
                          String state, String codeChallenge, String codeChallengeMethod) {
}
