package tn.supcom.greenpilot.core.utilities;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ETagSupportTest {

    @Test
    void sameIdAndVersionGiveTheSameETag() {
        assertEquals(ETagSupport.computeETag("greenhouse-01", 3), ETagSupport.computeETag("greenhouse-01", 3));
    }

    @Test
    void newVersionChangesTheETag() {
        assertNotEquals(ETagSupport.computeETag("greenhouse-01", 3), ETagSupport.computeETag("greenhouse-01", 4));
    }

    @Test
    void otherIdChangesTheETag() {
        assertNotEquals(ETagSupport.computeETag("greenhouse-01", 1), ETagSupport.computeETag("greenhouse-02", 1));
    }

    @Test
    void nullIdIsAccepted() {
        assertEquals(ETagSupport.computeETag(null, 0), ETagSupport.computeETag(null, 0));
    }
}
