package tn.supcom.greenpilot.iam.controllers.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    @Test
    void rightPasswordIsAccepted() {
        assertTrue(PasswordHasher.verify("grower123", PasswordHasher.hash("grower123")));
    }

    @Test
    void wrongPasswordIsRejected() {
        assertFalse(PasswordHasher.verify("grower124", PasswordHasher.hash("grower123")));
    }

    @Test
    void samePasswordGivesDifferentHashes() {
        assertNotEquals(PasswordHasher.hash("admin123"), PasswordHasher.hash("admin123"));
    }

    @Test
    void hashDoesNotContainThePassword() {
        assertFalse(PasswordHasher.hash("admin123").contains("admin123"));
    }

    @Test
    void malformedStoredValueIsRejected() {
        assertFalse(PasswordHasher.verify("x", null));
        assertFalse(PasswordHasher.verify(null, PasswordHasher.hash("x")));
        assertFalse(PasswordHasher.verify("x", "plain-text"));
        assertFalse(PasswordHasher.verify("x", "md5$1$a$b"));
    }
}
