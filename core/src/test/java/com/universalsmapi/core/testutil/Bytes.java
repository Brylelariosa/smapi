package com.universalsmapi.core.testutil;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Little-endian byte builder for fixtures. */
public final class Bytes {
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public Bytes u8(int v) {
        out.write(v & 0xFF);
        return this;
    }

    public Bytes u16(int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
        return this;
    }

    public Bytes u32(long v) {
        for (int i = 0; i < 4; i++) {
            out.write((int) ((v >>> (8 * i)) & 0xFF));
        }
        return this;
    }

    public Bytes u64(long v) {
        for (int i = 0; i < 8; i++) {
            out.write((int) ((v >>> (8 * i)) & 0xFF));
        }
        return this;
    }

    public Bytes bytes(byte[] b) {
        out.write(b, 0, b.length);
        return this;
    }

    public Bytes ascii(String s) {
        return bytes(s.getBytes(StandardCharsets.US_ASCII));
    }

    public Bytes zeros(int n) {
        for (int i = 0; i < n; i++) {
            out.write(0);
        }
        return this;
    }

    public Bytes padTo4() {
        while (out.size() % 4 != 0) {
            out.write(0);
        }
        return this;
    }

    public int size() {
        return out.size();
    }

    public byte[] toByteArray() {
        return out.toByteArray();
    }
}
