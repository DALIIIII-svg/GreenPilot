package tn.supcom.greenpilot.core.utilities;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32C;

/** ETag computation from the course skeleton: CRC-32C (RFC 3720) of id + version, in base 36. */
public final class ETagSupport {
    private ETagSupport() {}

    public static <ID extends Serializable> String computeETag(ID id, long version) {
        var crc = new CRC32C();
        if (id != null) {
            crc.update(id.toString().getBytes(StandardCharsets.UTF_8));
        }
        for (int shift = 56; shift >= 0; shift -= 8) {
            crc.update((byte) (version >>> shift));
        }
        return Long.toString(crc.getValue(), 36);
    }
}
