package tn.supcom.greenpilot.api.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Embeddable;
import java.util.ArrayList;
import java.util.List;

/** One YOLO box: class, confidence, [x1,y1,x2,y2] in pixels of the original image. */
@Embeddable(Embeddable.EmbeddableType.GROUPING)
public class Detection {
    @Column
    private String cls;
    @Column
    private Double score;
    @Column
    private List<Double> bbox = new ArrayList<>();

    // ---- getters / setters (generated)
    public String getCls() { return cls; }
    public void setCls(String cls) { this.cls = cls; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public List<Double> getBbox() { return bbox; }
    public void setBbox(List<Double> bbox) { this.bbox = bbox; }
}
