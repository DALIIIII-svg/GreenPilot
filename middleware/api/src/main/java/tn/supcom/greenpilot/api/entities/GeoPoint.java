package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Embeddable;
import java.util.ArrayList;
import java.util.List;

/** GeoJSON point {"type":"Point","coordinates":[lng,lat]} - the format MongoDB 2dsphere indexes require. */
@Embeddable(Embeddable.EmbeddableType.GROUPING)
public class GeoPoint {
    @Column
    private String type = "Point";
    @Column
    private List<Double> coordinates = new ArrayList<>();

    public static GeoPoint of(double lat, double lng) {
        GeoPoint p = new GeoPoint();
        p.coordinates = new ArrayList<>(List.of(lng, lat)); // GeoJSON order: longitude first
        return p;
    }

    // ---- getters / setters (generated)
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public List<Double> getCoordinates() { return coordinates; }
    public void setCoordinates(List<Double> coordinates) { this.coordinates = coordinates; }
}
