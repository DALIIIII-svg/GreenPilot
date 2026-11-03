package tn.supcom.greenpilot.core.security;

import jakarta.ws.rs.NameBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Requires a valid access token issued by the IAM.
 *   @Secured                                         -> any valid token
 *   @Secured(scopes = Scopes.CONTROL)                -> token must contain that scope
 *   @Secured(roles = Role.ADMIN, scopes = Scopes.ADMIN)
 * roles: the user needs ONE of them; scopes: the token needs ALL of them.
 * A method annotation replaces the class annotation. Checked by JwtAuthFilter.
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Secured {
    Role[] roles() default {};

    String[] scopes() default {};
}
