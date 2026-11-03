package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Embeddable;
import java.time.Instant;

/** A human review of an incident. Appended, never overwrites the model output (F07/F09). */
@Embeddable(Embeddable.EmbeddableType.GROUPING)
public class Review {
    @Column
    private String verdict;   // CONFIRMED | REJECTED
    @Column
    private String note;
    @Column
    private String by;
    @Column
    private Instant at;

    // ---- getters / setters (generated)
    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getBy() { return by; }
    public void setBy(String by) { this.by = by; }
    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }
}
