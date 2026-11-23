package tn.supcom.greenpilot.core.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScopesTest {

    @Test
    void parseSplitsOnAnyWhitespace() {
        assertEquals(Set.of("admin", "greenhouse:read"), Scopes.parse("  greenhouse:read \t admin "));
    }

    @Test
    void parseOfNothingIsEmpty() {
        assertTrue(Scopes.parse(null).isEmpty());
        assertTrue(Scopes.parse("   ").isEmpty());
    }

    @Test
    void joinIsSortedAndWithoutDuplicates() {
        assertEquals("admin greenhouse:read", Scopes.join(List.of("greenhouse:read", "admin", "admin")));
    }

    @Test
    void growerDefaultHasNoAdminScope() {
        assertTrue(!Scopes.parse(Scopes.GROWER_DEFAULT).contains(Scopes.ADMIN));
    }
}
