package com.universalsmapi.core.testutil;

/**
 * Builds .NET-for-Android assembly stores in the version-1 layout documented in dotnet/android
 * (Documentation/project-docs/AssemblyStores.md): 20-byte header, 24-byte descriptors, optional hash tables, image data.
 */
public final class StoreBuilder {
    private StoreBuilder() {
    }

    /** A plain PE image of the given size. */
    public static byte[] pe(int size) {
        byte[] b = new byte[size];
        b[0] = 'M';
        b[1] = 'Z';
        return b;
    }

    /** An LZ4-compressed assembly: XALZ header (magic, descriptor index, uncompressed length) plus filler. */
    public static byte[] xalz(long uncompressedLength, int storedSize) {
        Bytes b = new Bytes().ascii("XALZ").u32(7).u32(uncompressedLength);
        b.zeros(storedSize - 12);
        return b.toByteArray();
    }

    public static byte[] v1(long storeId, long globalCount, boolean indexTables, byte[]... images) {
        return build(1, storeId, globalCount, indexTables, images);
    }

    public static byte[] build(long version, long storeId, long globalCount, boolean indexTables, byte[]... images) {
        int n = images.length;
        int tableEnd = 20 + 24 * n;
        int hashBytes = indexTables ? (int) (globalCount * 20 * 2) : 0;
        long off = tableEnd + hashBytes;
        Bytes out = new Bytes();
        out.u32(0x41424158L).u32(version).u32(n).u32(globalCount).u32(storeId);
        for (byte[] img : images) {
            out.u32(off).u32(img.length).u32(0).u32(0).u32(0).u32(0);
            off += img.length;
        }
        out.zeros(hashBytes);
        for (byte[] img : images) {
            out.bytes(img);
        }
        return out.toByteArray();
    }
}
