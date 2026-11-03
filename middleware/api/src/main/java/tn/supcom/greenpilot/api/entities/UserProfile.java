package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Entity;
import tn.supcom.greenpilot.core.entities.NosqlEntity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** What a user may see in the API: id = IAM subject (sub). The IAM says WHO you are, this says WHICH greenhouses. */
@Entity(UserProfile.COLLECTION)
public class UserProfile extends NosqlEntity {
    public static final String COLLECTION = "user_profiles";

    @Column
    private String username;
    @Column
    private String displayName;
    @Column
    private List<String> siteIds = new ArrayList<>();
    @Column
    private Instant lastSeenAt;

    // ---- getters / setters (generated)
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public List<String> getSiteIds() { return siteIds; }
    public void setSiteIds(List<String> siteIds) { this.siteIds = siteIds; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
