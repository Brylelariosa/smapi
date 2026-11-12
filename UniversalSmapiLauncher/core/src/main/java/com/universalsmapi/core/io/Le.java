package com.universalsmapi.core.io;

/** Little-endian readers over byte arrays. All results are zero-extended (no accidental sign bugs). */
public final class Le {
    private Le() {
    }

    public static int u8(byte[] b, int o) {
        return b[o] & 0xFF;
    }

    public static int u16(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }

    /** Unsigned 32-bit as long. */
    public static long u32(byte[] b, int o) {
        return (b[o] & 0xFFL) | ((b[o + 1] & 0xFFL) << 8) | ((b[o + 2] & 0xFFL) << 16) | ((b[o + 3] & 0xFFL) << 24);
    }

    /** Signed 32-bit. */
    public static int i32(byte[] b, int o) {
        return (int) u32(b, o);
    }

    /** 64-bit value (callers treat values above Long.MAX_VALUE as invalid). */
    public static long u64(byte[] b, int o) {
        return u32(b, o) | (u32(b, o + 4) << 32);
    }
}
