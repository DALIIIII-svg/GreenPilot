package tn.supcom.greenpilot.core.utilities;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * UUID version 7 (RFC 9562): 48-bit Unix timestamp in milliseconds + random bits.
 * Ids are time-ordered, so sorting by id = sorting by creation time (good for MongoDB indexes).
 * Fix compared to the course skeleton: the skeleton masked the timestamp to 32 bits
 * (timestamp & 0xFFFFFFFF), so ids stopped being ordered every ~49 days.
 */
public final class UUIDv7 {
    private UUIDv7() {}

    private static final SecureRandom RANDOM = new SecureRandom();

    public static UUID generate() {
        long millis = System.currentTimeMillis();
        long msb = (millis << 16)                    // 48-bit timestamp in the top bits
                | 0x7000L                            // version 7
                | (RANDOM.nextInt(0x1000) & 0x0FFFL); // 12 random bits
        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L; // IETF variant
        return new UUID(msb, lsb);
    }

    public static String next() {
        return generate().toString();
    }
}
