package tn.supcom.greenpilot.iam.boundaries;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/** Endpoints at the root of iam.<domain>: /authorize, /token, /jwks, /identities */
@ApplicationPath("/")
public class IamApplication extends Application {
}
