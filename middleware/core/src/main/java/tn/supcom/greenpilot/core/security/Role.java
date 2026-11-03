package tn.supcom.greenpilot.core.security;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/** Roles stored as a bitmask in Identity.roles (same idea as the course skeleton's "Long roles"). */
public enum Role {
    GROWER(1L),
    ADMIN(2L);

    private final long bit;

    Role(long bit) { this.bit = bit; }

    public long bit() { return bit; }

    public static Set<Role> fromBits(long bits) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        for (Role r : values()) {
            if ((bits & r.bit) != 0) roles.add(r);
        }
        return roles;
    }

    public static long toBits(Collection<Role> roles) {
        long bits = 0;
        for (Role r : roles) bits |= r.bit;
        return bits;
    }
}
