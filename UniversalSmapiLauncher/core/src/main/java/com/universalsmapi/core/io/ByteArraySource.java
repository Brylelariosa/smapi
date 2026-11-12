package com.universalsmapi.core.io;

/** In-memory source. Used by tests and for small nested payloads. */
public final class ByteArraySource implements SeekableSource {
    private final byte[] data;

    public ByteArraySource(byte[] data) {
        this.data = data;
    }

    @Override
    public long size() {
        return data.length;
    }

    @Override
    public int read(long position, byte[] dst, int off, int len) {
        if (position >= data.length) {
            return -1;
        }
        int n = (int) Math.min(len, data.length - position);
        System.arraycopy(data, (int) position, dst, off, n);
        return n;
    }

    @Override
    public void close() {
        // nothing to release
    }
}
