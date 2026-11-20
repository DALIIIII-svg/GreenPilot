package tn.supcom.greenpilot.core.utilities;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UUIDv7Test {

    @Test
    void hasVersion7AndIetfVariant() {
        UUID id = UUIDv7.generate();
        assertEquals(7, id.version());
        assertEquals(2, id.variant());
    }

    @Test
    void carriesTheCurrentTimeInMilliseconds() {
        long before = System.currentTimeMillis();
        long embedded = UUIDv7.generate().getMostSignificantBits() >>> 16;
        long after = System.currentTimeMillis();
        assertTrue(embedded >= before && embedded <= after);
    }

    @Test
    void idsCreatedLaterSortAfter() throws InterruptedException {
        String first = UUIDv7.next();
        Thread.sleep(2);
        String second = UUIDv7.next();
        assertTrue(first.compareTo(second) < 0);
    }
}
