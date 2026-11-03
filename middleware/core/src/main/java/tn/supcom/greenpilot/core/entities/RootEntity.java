package tn.supcom.greenpilot.core.entities;

import tn.supcom.greenpilot.core.utilities.ETagSupport;

import java.io.Serializable;

/**
 * Contract of every persistent object (same as the course skeleton):
 * an identifier and a version. The version gives optimistic concurrency and the HTTP ETag.
 */
public interface RootEntity<ID extends Serializable> extends Serializable {
    ID getId();

    void setId(ID id);

    long getVersion();

    void setVersion(long version);

    /** ETag = CRC-32C(id, version): changes every time the entity is modified. */
    default String tag() {
        return ETagSupport.computeETag(getId(), getVersion());
    }
}
