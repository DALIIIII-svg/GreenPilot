package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Embeddable;
import java.util.ArrayList;
import java.util.List;

/** A zone of the greenhouse with its plants (zone A / zone B). */
@Embeddable(Embeddable.EmbeddableType.GROUPING)
public class Zone {
    @Column
    private String id;
    @Column
    private String name;
    @Column
    private List<Plant> plants = new ArrayList<>();

    // ---- getters / setters (generated)
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<Plant> getPlants() { return plants; }
    public void setPlants(List<Plant> plants) { this.plants = plants; }
}
