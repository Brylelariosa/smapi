package com.universalsmapi.core.dotnet;

import java.io.IOException;

/**
 * LZ4 <b>block</b> decoder (the format used by .NET-for-Android's XALZ-compressed assemblies). A block is a series of
 * sequences: a token byte (high nibble = literal length, low nibble = match length minus 4; 15 means "add following
 * bytes until one is below 255"), the literals, then (except for the last sequence) a 2-byte little-endian match offset.
 * Matches may overlap their own output. Everything is bounds-checked; malformed input raises {@link IOException}.
 */
public final class Lz4 {
    private Lz4() {
    }

    /**
     * @return number of bytes written to {@code dst} (the caller compares it with the length it expects)
     */
    public static int decompress(byte[] src, int srcPos, int srcLen, byte[] dst, int dstPos, int dstLen) throws IOException {
        int sp = srcPos;
        final int sEnd = srcPos + srcLen;
        int dp = dstPos;
        final int dEnd = dstPos + dstLen;
        while (sp < sEnd) {
            int token = src[sp++] & 0xFF;
            int literals = token >>> 4;
            if (literals == 15) {
                int b;
                do {
                    if (sp >= sEnd) {
                        throw new IOException("LZ4: literal length runs past the input");
                    }
                    b = src[sp++] & 0xFF;
                    literals += b;
                } while (b == 255);
            }
            if (literals > sEnd - sp) {
                throw new IOException("LZ4: literal run of " + literals + " exceeds the input");
            }
            if (literals > dEnd - dp) {
                throw new IOException("LZ4: literal run of " + literals + " exceeds the output buffer");
            }
            System.arraycopy(src, sp, dst, dp, literals);
            sp += literals;
            dp += literals;
            if (sp >= sEnd) {
                break; // the final sequence carries literals only
            }
            if (sEnd - sp < 2) {
                throw new IOException("LZ4: truncated match offset");
            }
            int offset = (src[sp] & 0xFF) | ((src[sp + 1] & 0xFF) << 8);
            sp += 2;
            if (offset == 0 || offset > dp - dstPos) {
                throw new IOException("LZ4: invalid match offset " + offset + " at output position " + (dp - dstPos));
            }
            int match = token & 0x0F;
            if (match == 15) {
                int b;
                do {
                    if (sp >= sEnd) {
                        throw new IOException("LZ4: match length runs past the input");
                    }
                    b = src[sp++] & 0xFF;
                    match += b;
                } while (b == 255);
            }
            match += 4;
            if (match > dEnd - dp) {
                throw new IOException("LZ4: match of " + match + " exceeds the output buffer");
            }
            int from = dp - offset;
            for (int i = 0; i < match; i++) {
                dst[dp++] = dst[from++]; // byte by byte: source and destination may overlap
            }
        }
        return dp - dstPos;
    }
}
