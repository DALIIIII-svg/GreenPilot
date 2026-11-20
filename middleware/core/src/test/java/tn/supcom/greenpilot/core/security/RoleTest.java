package tn.supcom.greenpilot.core.security;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoleTest {

    @Test
    void bitsRoundTrip() {
        Set<Role> both = EnumSet.of(Role.GROWER, Role.ADMIN);
        assertEquals(3L, Role.toBits(both));
        assertEquals(both, Role.fromBits(3L));
        assertEquals(EnumSet.of(Role.ADMIN), Role.fromBits(2L));
        assertEquals(EnumSet.noneOf(Role.class), Role.fromBits(0L));
    }
}
