package com.universalsmapi.core.io;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 * {@link SeekableSource} over a {@link FileChannel}. Works for plain files and, on Android, for
 * {@code FileInputStream(parcelFileDescriptor.getFileDescriptor()).getChannel()} obtained through the
 * Storage Access Framework. Positional reads never move the channel position, so no extra locking is needed.
 */
public final class ChannelSource implements SeekableSource {
    private final FileChannel channel;
    private final Closeable owner;

    /** @param owner extra resource to close with the channel (for example a ParcelFileDescriptor); may be null */
    public ChannelSource(FileChannel channel, Closeable owner) {
        this.channel = channel;
        this.owner = owner;
    }

    /** Open a regular file read-only. Throws the platform's own IOException so callers can log the real reason. */
    public static ChannelSource open(File file) throws IOException {
        return new ChannelSource(FileChannel.open(file.toPath(), StandardOpenOption.READ), null);
    }

    @Override
    public long size() throws IOException {
        return channel.size();
    }

    @Override
    public int read(long position, byte[] dst, int off, int len) throws IOException {
        return channel.read(ByteBuffer.wrap(dst, off, len), position);
    }

    @Override
    public void close() throws IOException {
        try {
            channel.close();
        } finally {
            if (owner != null) {
                owner.close();
            }
        }
    }
}
