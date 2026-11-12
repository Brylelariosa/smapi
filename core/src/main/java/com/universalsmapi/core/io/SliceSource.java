package com.universalsmapi.core.io;

import java.io.IOException;

/** A window onto a parent source (used to read a stored ZIP entry without copying it). Does not close the parent. */
public final class SliceSource implements SeekableSource {
    private final SeekableSource parent;
    private final long start;
    private final long length;

    public SliceSource(SeekableSource parent, long start, long length) {
        this.parent = parent;
        this.start = start;
        this.length = length;
    }

    @Override
    public long size() {
        return length;
    }

    @Override
    public int read(long position, byte[] dst, int off, int len) throws IOException {
        if (position >= length) {
            return -1;
        }
        int n = (int) Math.min(len, length - position);
        return parent.read(start + position, dst, off, n);
    }

    @Override
    public void close() {
        // the parent owns the underlying handle
    }
}
