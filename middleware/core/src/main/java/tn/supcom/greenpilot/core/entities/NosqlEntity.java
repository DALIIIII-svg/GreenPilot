package tn.supcom.greenpilot.core.entities;

import jakarta.nosql.Column;
import jakarta.nosql.Id;
import jakarta.nosql.MappedSuperclass;

import java.util.Objects;

/**
 * Base class of every MongoDB document (Jakarta NoSQL mapping). The id is stored as MongoDB "_id".
 * Two fixes compared to the course skeleton:
 *  - setVersion accepts any non-negative value (the skeleton only accepted current+1, which breaks
 *    when JNoSQL loads a document from the database);
 *  - hashCode/equals work while the id is still null.
 */
@MappedSuperclass
public abstract class NosqlEntity implements RootEntity<String> {

    @Id
    private String id;

    @Column
    private long version;

    @Override
    public String getId() { return id; }

    @Override
    public void setId(String id) { this.id = id; }

    @Override
    public long getVersion() { return version; }

    @Override
    public void setVersion(long version) {
        if (version < 0L) throw new IllegalArgumentException("version must be >= 0");
        this.version = version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return id != null && id.equals(((NosqlEntity) o).id);
    }

    @Override
    public int hashCode() { return Objects.hashCode(id); }
}
