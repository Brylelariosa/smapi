package com.universalsmapi.core.testutil;

import java.util.Arrays;

/** Tiny LZ4 block encoder for tests: literals, plus offset-1 matches for long runs of one repeated byte. */
public final class Lz4Encoder {
    private Lz4Encoder() {
    }

    public static byte[] encode(byte[] data) {
        Bytes out = new Bytes();
        int n = data.length;
        int litStart = 0;
        int i = 0;
        while (i < n) {
            int run = 1;
            while (i + run < n && data[i + run] == data[i]) {
                run++;
            }
            if (run >= 10) {
                int litLen = (i + 1) - litStart; // include the first byte of the run as a literal
                sequence(out, data, litStart, litLen, 1, run - 1);
                i += run;
                litStart = i;
            } else {
                i += run;
            }
        }
        finalLiterals(out, data, litStart, n - litStart);
        return out.toByteArray();
    }

    public static byte[] literalsOnly(byte[] data) {
        Bytes out = new Bytes();
        finalLiterals(out, data, 0, data.length);
        return out.toByteArray();
    }

    private static void sequence(Bytes out, byte[] data, int litStart, int litLen, int offset, int matchLen) {
        int ml = matchLen - 4;
        out.u8((Math.min(litLen, 15) << 4) | Math.min(ml, 15));
        if (litLen >= 15) {
            ext(out, litLen - 15);
        }
        out.bytes(Arrays.copyOfRange(data, litStart, litStart + litLen));
        out.u16(offset);
        if (ml >= 15) {
            ext(out, ml - 15);
        }
    }

    private static void finalLiterals(Bytes out, byte[] data, int litStart, int litLen) {
        out.u8(Math.min(litLen, 15) << 4);
        if (litLen >= 15) {
            ext(out, litLen - 15);
        }
        out.bytes(Arrays.copyOfRange(data, litStart, litStart + litLen));
    }

    private static void ext(Bytes out, int v) {
        while (v >= 255) {
            out.u8(255);
            v -= 255;
        }
        out.u8(v);
    }
}
