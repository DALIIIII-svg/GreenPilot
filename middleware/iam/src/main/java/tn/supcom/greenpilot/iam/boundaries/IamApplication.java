package tn.supcom.greenpilot.iam.boundaries;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/** Endpoints: /iam/authorize, /iam/token, /iam/jwks, /iam/identities */
@ApplicationPath("/")
public class IamApplication extends Application {
}
