package com.universalsmapi.core.zip;

/** One central-directory record. */
public final class ZipEntryInfo {
    public static final int METHOD_STORED = 0;
    public static final int METHOD_DEFLATED = 8;

    public final String name;
    public final int method;
    public final int flags;
    public final long crc32;
    public final long compressedSize;
    public final long size;
    public final long localHeaderOffset;

    public ZipEntryInfo(String name, int method, int flags, long crc32, long compressedSize, long size, long localHeaderOffset) {
        this.name = name;
        this.method = method;
        this.flags = flags;
        this.crc32 = crc32;
        this.compressedSize = compressedSize;
        this.size = size;
        this.localHeaderOffset = localHeaderOffset;
    }

    public boolean isDirectory() {
        return name.endsWith("/");
    }

    public boolean isStored() {
        return method == METHOD_STORED;
    }

    @Override
    public String toString() {
        return name + " (" + size + " B, method " + method + ")";
    }
}
