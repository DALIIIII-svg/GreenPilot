package tn.supcom.greenpilot.core.security;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** OAuth 2.0 scopes understood by the GreenPilot API (space-separated in tokens). */
public final class Scopes {
    private Scopes() {}

    public static final String READ = "greenhouse:read";          // dashboards, histories, images
    public static final String CONTROL = "greenhouse:control";    // irrigation requests
    public static final String INCIDENTS = "incidents:manage";    // acknowledge / review / close alerts
    public static final String ADMIN = "admin";                    // sites, devices, models, users

    public static final String ALL = String.join(" ", READ, CONTROL, INCIDENTS, ADMIN);
    public static final String GROWER_DEFAULT = String.join(" ", READ, CONTROL, INCIDENTS);

    public static Set<String> parse(String spaceSeparated) {
        if (spaceSeparated == null || spaceSeparated.isBlank()) return new TreeSet<>();
        return Arrays.stream(spaceSeparated.trim().split("\\s+")).collect(Collectors.toCollection(TreeSet::new));
    }

    public static String join(Collection<String> scopes) {
        return String.join(" ", new TreeSet<>(scopes));
    }
}
