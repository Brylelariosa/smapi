package com.universalsmapi.core.io;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;

/**
 * Random-access, read-only byte source. Implementations must be safe to call from one thread at a time.
 * It abstracts "a file on disk" and "a content:// descriptor" so the parsers never care where bytes come from.
 */
public interface SeekableSource extends Closeable {
    /** Total size in bytes. */
    long size() throws IOException;

    /** Read up to {@code len} bytes at an absolute position. Returns bytes read, or -1 at end of source. */
    int read(long position, byte[] dst, int off, int len) throws IOException;

    /** Read exactly {@code len} bytes or throw {@link EOFException}. */
    default void readFully(long position, byte[] dst, int off, int len) throws IOException {
        int done = 0;
        while (done < len) {
            int n = read(position + done, dst, off + done, len - done);
            if (n < 0) {
                throw new EOFException("Unexpected end of data at offset " + (position + done)
                        + " (wanted " + len + " bytes at " + position + ")");
            }
            done += n;
        }
    }

    /** Convenience: read exactly {@code len} bytes into a new array. */
    default byte[] readBytes(long position, int len) throws IOException {
        byte[] out = new byte[len];
        readFully(position, out, 0, len);
        return out;
    }
}
