package com.universalsmapi.core.io;

import java.io.IOException;
import java.io.InputStream;

/** Sequential {@link InputStream} view of a byte range of a {@link SeekableSource}. */
public final class SourceInputStream extends InputStream {
    private final SeekableSource source;
    private long position;
    private final long end;

    public SourceInputStream(SeekableSource source, long start, long length) {
        this.source = source;
        this.position = start;
        this.end = start + length;
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        return n < 0 ? -1 : (one[0] & 0xFF);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }
        if (position >= end) {
            return -1;
        }
        int want = (int) Math.min(len, end - position);
        int n = source.read(position, b, off, want);
        if (n > 0) {
            position += n;
        }
        return n;
    }
}
