package dev.portfolio.finance.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.zip.CRC32;
import dev.portfolio.finance.exception.account.InvalidProfilePhotoException;
import static dev.portfolio.finance.exception.account.InvalidProfilePhotoException.Reason.*;

/** Bounded container checks, not a substitute for actual raster decoding. */
final class ProfilePhotoContainerInspector {
    private static final byte[] PNG = {(byte)137, 80, 78, 71, 13, 10, 26, 10};
    private static final Set<String> RASTER_CHUNKS = Set.of("IHDR", "PLTE", "tRNS", "IDAT", "IEND");

    private ProfilePhotoContainerInspector() {}

    static boolean isPng(byte[] bytes) { return bytes.length >= 8 && Arrays.equals(PNG, Arrays.copyOf(bytes, 8)); }

    static byte[] inspect(byte[] bytes) {
        if (isPng(bytes)) return png(bytes);
        if (bytes.length >= 3 && u(bytes[0]) == 255 && u(bytes[1]) == 216 && u(bytes[2]) == 255) return jpeg(bytes);
        throw new InvalidProfilePhotoException(UNSUPPORTED);
    }

    private static byte[] png(byte[] b) {
        var out = new ByteArrayOutputStream();
        out.writeBytes(PNG);
        int pos = 8;
        boolean header = false;
        boolean data = false;
        boolean dataEnded = false;
        boolean palette = false;
        boolean transparency = false;
        while (pos <= b.length - 12) {
            long length = unsignedInt(b, pos);
            if (length > b.length - pos - 12L) throw invalid();
            int size = (int) length;
            for (int i = pos + 4; i < pos + 8; i++) {
                int letter = u(b[i]);
                if (!(letter >= 'A' && letter <= 'Z' || letter >= 'a' && letter <= 'z')) throw invalid();
            }
            if ((b[pos + 6] & 32) != 0) throw invalid();
            String type = new String(b, pos + 4, 4, StandardCharsets.US_ASCII);
            var crc = new CRC32();
            crc.update(b, pos + 4, size + 4);
            if (crc.getValue() != unsignedInt(b, pos + 8 + size)) throw invalid();
            if (Set.of("acTL", "fcTL", "fdAT").contains(type)) throw new InvalidProfilePhotoException(ANIMATION);
            if (!header && !type.equals("IHDR")) throw invalid();
            if (type.equals("IHDR")) {
                if (header || size != 13) throw invalid();
                header = true;
            }
            if (type.equals("PLTE")) {
                if (palette || data) throw invalid();
                palette = true;
            }
            if (type.equals("tRNS")) {
                if (transparency || data) throw invalid();
                transparency = true;
            }
            if (type.equals("IDAT")) {
                if (dataEnded) throw invalid();
                data = true;
            } else if (data) dataEnded = true;
            // Reject unknown critical chunks. Discard ancillary metadata before the decoder
            // can inflate compressed text/ICC data unrelated to the raster.
            if (!RASTER_CHUNKS.contains(type) && (b[pos + 4] & 32) == 0) throw invalid();
            if (RASTER_CHUNKS.contains(type)) out.write(b, pos, size + 12);
            pos += size + 12;
            if (type.equals("IEND")) {
                if (!data || size != 0) throw invalid();
                if (pos + 8 <= b.length && Arrays.equals(PNG, Arrays.copyOfRange(b, pos, pos + 8))) {
                    throw new InvalidProfilePhotoException(ANIMATION);
                }
                return out.toByteArray();
            }
        }
        throw invalid();
    }

    private static byte[] jpeg(byte[] b) {
        int pos = 2;
        boolean scan = false;
        boolean entropy = false;
        while (pos < b.length) {
            if (entropy && u(b[pos]) != 255) { pos++; continue; }
            if (u(b[pos++]) != 255) throw invalid();
            while (pos < b.length && u(b[pos]) == 255) pos++;
            if (pos == b.length) throw invalid();
            int marker = u(b[pos++]);
            if (entropy && (marker == 0 || marker >= 208 && marker <= 215)) continue;
            entropy = false;
            if (marker == 217) {
                if (!scan) throw invalid();
                if (pos + 1 < b.length && u(b[pos]) == 255 && u(b[pos + 1]) == 216) {
                    throw new InvalidProfilePhotoException(ANIMATION);
                }
                return Arrays.copyOf(b, pos);
            }
            if (marker == 216) throw new InvalidProfilePhotoException(ANIMATION);
            if (pos + 2 > b.length) throw invalid();
            int length = u(b[pos]) * 256 + u(b[pos + 1]);
            if (length < 2 || length > b.length - pos) throw invalid();
            if (marker == 226 && length >= 6 && b[pos + 2] == 'M' && b[pos + 3] == 'P'
                    && b[pos + 4] == 'F' && b[pos + 5] == 0) throw new InvalidProfilePhotoException(ANIMATION);
            pos += length;
            if (marker == 218) { scan = true; entropy = true; }
        }
        throw invalid();
    }

    private static int u(byte b) { return b & 255; }
    private static long unsignedInt(byte[] b, int p) {
        return ((long)u(b[p]) << 24) | ((long)u(b[p + 1]) << 16) | (u(b[p + 2]) << 8) | u(b[p + 3]);
    }
    private static InvalidProfilePhotoException invalid() { return new InvalidProfilePhotoException(INVALID); }
}
