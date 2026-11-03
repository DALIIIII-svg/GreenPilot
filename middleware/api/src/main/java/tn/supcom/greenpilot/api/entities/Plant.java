package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Embeddable;


/** A monitored plant (e.g. pepper-1). */
@Embeddable(Embeddable.EmbeddableType.GROUPING)
public class Plant {
    @Column
    private String id;
    @Column
    private String name;
    @Column
    private String species;

    // ---- getters / setters (generated)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
}
