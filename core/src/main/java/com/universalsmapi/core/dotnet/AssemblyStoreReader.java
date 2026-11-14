package com.universalsmapi.core.dotnet;

import com.universalsmapi.core.io.Le;
import com.universalsmapi.core.io.SeekableSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Decoder for .NET-for-Android assembly store <b>version 1</b>, following the dotnet/android documentation
 * (Documentation/project-docs/AssemblyStores.md): a 20-byte header (magic 'XABA', version, local entry count, global
 * entry count, store id) immediately followed by {@code local_entry_count} descriptors of 24 bytes (six u32: image
 * offset/size, debug offset/size, config offset/size), all offsets measured from the start of the store file. Only the
 * descriptor table is decoded; the name-hash tables that follow it are not needed because names come from
 * assemblies.manifest.
 *
 * <p>Every descriptor is validated against the file size and its payload signature ('MZ' or 'XALZ') is checked, so a
 * wrong assumption shows up as a reported problem instead of wrong data. Other store versions are reported, not guessed.
 */
public final class AssemblyStoreReader {
    public static final long MAGIC = 0x41424158L;
    private static final long XALZ_MAGIC = 0x5A4C4158L;
    private static final int HEADER_SIZE = 20;
    private static final int DESCRIPTOR_SIZE = 24;
    private static final long MAX_ENTRIES = 100_000;

    private AssemblyStoreReader() {
    }

    /** Result of decoding one store file. */
    public static final class StoreDecode {
        public final String storeFile;
        public long version;
        public long localCount;
        public long globalCount;
        public long storeId;
        public final List<AssemblyLayout.Entry> entries = new ArrayList<>();
        /** Null when every descriptor decoded and validated. */
        public String problem;

        StoreDecode(String storeFile) {
            this.storeFile = storeFile;
        }

        public String summary() {
            if (problem != null) {
                return problem;
            }
            return entries.size() + " assemblies decoded (store " + storeId + ", " + localCount + " local / " + globalCount + " global)";
        }
    }

    public static StoreDecode decode(SeekableSource store, String storeFile) throws IOException {
        StoreDecode d = new StoreDecode(storeFile);
        long size = store.size();
        if (size < HEADER_SIZE) {
            d.problem = "file is shorter than the " + HEADER_SIZE + "-byte header";
            return d;
        }
        byte[] h = store.readBytes(0, HEADER_SIZE);
        long magic = Le.u32(h, 0);
        d.version = Le.u32(h, 4);
        d.localCount = Le.u32(h, 8);
        d.globalCount = Le.u32(h, 12);
        d.storeId = Le.u32(h, 16);
        if (magic != MAGIC) {
            d.problem = "bad magic 0x" + Long.toHexString(magic);
            return d;
        }
        if (d.version != 1) {
            d.problem = "store version " + d.version + " is not decoded (this reader implements version 1 only)";
            return d;
        }
        long tableEnd = HEADER_SIZE + (long) DESCRIPTOR_SIZE * d.localCount;
        if (d.localCount > MAX_ENTRIES || tableEnd > size) {
            d.problem = "descriptor table of " + d.localCount + " entries does not fit in " + size + " bytes";
            return d;
        }
        byte[] table = store.readBytes(HEADER_SIZE, (int) (tableEnd - HEADER_SIZE));
        int invalid = 0;
        String firstBad = null;
        for (int i = 0; i < d.localCount; i++) {
            int o = i * DESCRIPTOR_SIZE;
            long off = Le.u32(table, o);
            long len = Le.u32(table, o + 4);
            long dOff = Le.u32(table, o + 8);
            long dLen = Le.u32(table, o + 12);
            long cOff = Le.u32(table, o + 16);
            long cLen = Le.u32(table, o + 20);
            String why = null;
            if (len == 0) {
                why = "zero image size";
            } else if (off < tableEnd) {
                why = "image offset " + off + " lies inside the header or tables";
            } else if (off + len > size) {
                why = "image range " + off + "+" + len + " runs past the end of the file";
            } else if (dOff != 0 && dOff + dLen > size) {
                why = "debug data range runs past the end of the file";
            } else if (cOff != 0 && cOff + cLen > size) {
                why = "config data range runs past the end of the file";
            }
            if (why != null) {
                invalid++;
                if (firstBad == null) {
                    firstBad = "descriptor " + i + ": " + why;
                }
                continue;
            }
            byte[] head = store.readBytes(off, (int) Math.min(12, len));
            String payload = "unknown";
            long uncompressed = len;
            if (head.length >= 4 && Le.u32(head, 0) == XALZ_MAGIC) {
                payload = "xalz";
                if (head.length >= 12) {
                    uncompressed = Le.u32(head, 8);
                }
            } else if (head.length >= 2 && head[0] == 'M' && head[1] == 'Z') {
                payload = "pe";
            }
            d.entries.add(new AssemblyLayout.Entry(d.storeFile, d.storeId, i, off, len, dLen, cLen, payload, uncompressed));
        }
        if (invalid > 0) {
            d.problem = invalid + " of " + d.localCount + " descriptors failed validation; first: " + firstBad;
        }
        return d;
    }
}
