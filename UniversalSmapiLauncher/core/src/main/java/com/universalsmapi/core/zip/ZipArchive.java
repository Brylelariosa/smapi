package com.universalsmapi.core.zip;

import com.universalsmapi.core.io.Le;
import com.universalsmapi.core.io.SeekableSource;
import com.universalsmapi.core.io.SliceSource;
import com.universalsmapi.core.io.SourceInputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Minimal read-only ZIP reader built on {@link SeekableSource}.
 *
 * <p>It reads only the central directory up front (an APK of several hundred MB costs a few hundred KB of I/O) and
 * touches entry data on demand. It does not extract anything to disk. ZIP64 is supported; encryption and
 * compression methods other than stored/deflate are reported, not decoded.
 *
 * <p>The source is borrowed: the caller keeps ownership and must close it.
 */
public final class ZipArchive {
    private static final long SIG_EOCD = 0x06054b50L;
    private static final long SIG_ZIP64_EOCD = 0x06064b50L;
    private static final long SIG_ZIP64_LOCATOR = 0x07064b50L;
    private static final long SIG_CENTRAL = 0x02014b50L;
    private static final long SIG_LOCAL = 0x04034b50L;
    private static final long MAX_CENTRAL_DIRECTORY_BYTES = 128L * 1024 * 1024;

    private final SeekableSource source;
    private final List<ZipEntryInfo> entries;
    private final Map<String, ZipEntryInfo> byName;
    private final long centralDirectoryOffset;
    private final long centralDirectorySize;
    private final boolean zip64;
    private final List<String> warnings;

    private ZipArchive(SeekableSource source, List<ZipEntryInfo> entries, long cdOffset, long cdSize, boolean zip64,
                       List<String> warnings) {
        this.source = source;
        this.entries = Collections.unmodifiableList(entries);
        this.byName = new HashMap<>();
        for (ZipEntryInfo e : entries) {
            this.byName.put(e.name, e);
        }
        this.centralDirectoryOffset = cdOffset;
        this.centralDirectorySize = cdSize;
        this.zip64 = zip64;
        this.warnings = Collections.unmodifiableList(warnings);
    }

    /** Parse the central directory. Throws {@link ZipFormatException} with a specific reason when malformed. */
    public static ZipArchive parse(SeekableSource source) throws IOException {
        long size = source.size();
        if (size < 22) {
            throw new ZipFormatException("File too small to be a ZIP (" + size + " bytes)");
        }
        int tailLen = (int) Math.min(size, 22L + 65535L);
        byte[] tail = source.readBytes(size - tailLen, tailLen);

        int eocd = -1;
        for (int i = tailLen - 22; i >= 0; i--) {
            if (Le.u32(tail, i) == SIG_EOCD) {
                int commentLen = Le.u16(tail, i + 20);
                if (i + 22 + commentLen <= tailLen) {
                    eocd = i;
                    break;
                }
            }
        }
        if (eocd < 0) {
            throw new ZipFormatException("End-of-central-directory record not found (not a ZIP/APK, or truncated)");
        }
        long eocdAbs = size - tailLen + eocd;
        long total = Le.u16(tail, eocd + 10);
        long cdSize = Le.u32(tail, eocd + 12);
        long cdOffset = Le.u32(tail, eocd + 16);
        boolean zip64 = false;

        if (total == 0xFFFFL || cdSize == 0xFFFFFFFFL || cdOffset == 0xFFFFFFFFL) {
            long locatorPos = eocdAbs - 20;
            if (locatorPos >= 0) {
                byte[] loc = source.readBytes(locatorPos, 20);
                if (Le.u32(loc, 0) == SIG_ZIP64_LOCATOR) {
                    long recPos = Le.u64(loc, 8);
                    if (recPos < 0 || recPos + 56 > size) {
                        throw new ZipFormatException("ZIP64 end record offset out of range: " + recPos);
                    }
                    byte[] rec = source.readBytes(recPos, 56);
                    if (Le.u32(rec, 0) != SIG_ZIP64_EOCD) {
                        throw new ZipFormatException("ZIP64 end record signature mismatch at " + recPos);
                    }
                    total = Le.u64(rec, 32);
                    cdSize = Le.u64(rec, 40);
                    cdOffset = Le.u64(rec, 48);
                    zip64 = true;
                }
            }
        }

        if (cdOffset < 0 || cdSize < 0 || cdOffset + cdSize > size) {
            throw new ZipFormatException("Central directory out of range (offset " + cdOffset + ", size " + cdSize
                    + ", file " + size + ")");
        }
        if (cdSize > MAX_CENTRAL_DIRECTORY_BYTES) {
            throw new ZipFormatException("Central directory implausibly large: " + cdSize + " bytes");
        }

        byte[] cd = source.readBytes(cdOffset, (int) cdSize);
        List<ZipEntryInfo> entries = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int p = 0;
        for (long n = 0; n < total; n++) {
            if (p + 46 > cd.length) {
                warnings.add("Central directory ended after " + n + " of " + total + " declared entries");
                break;
            }
            if (Le.u32(cd, p) != SIG_CENTRAL) {
                throw new ZipFormatException("Bad central directory signature at entry " + n);
            }
            int flags = Le.u16(cd, p + 8);
            int method = Le.u16(cd, p + 10);
            long crc = Le.u32(cd, p + 16);
            long comp = Le.u32(cd, p + 20);
            long uncomp = Le.u32(cd, p + 24);
            int nameLen = Le.u16(cd, p + 28);
            int extraLen = Le.u16(cd, p + 30);
            int commentLen = Le.u16(cd, p + 32);
            long localOff = Le.u32(cd, p + 42);
            int recordEnd = p + 46 + nameLen + extraLen + commentLen;
            if (recordEnd > cd.length) {
                throw new ZipFormatException("Central directory entry " + n + " runs past the directory end");
            }
            String name = new String(cd, p + 46, nameLen, StandardCharsets.UTF_8);

            int e = p + 46 + nameLen;
            int eEnd = e + extraLen;
            while (e + 4 <= eEnd) {
                int id = Le.u16(cd, e);
                int len = Le.u16(cd, e + 2);
                int d = e + 4;
                int dEnd = Math.min(d + len, eEnd);
                if (id == 1) {
                    if (uncomp == 0xFFFFFFFFL && d + 8 <= dEnd) {
                        uncomp = Le.u64(cd, d);
                        d += 8;
                    }
                    if (comp == 0xFFFFFFFFL && d + 8 <= dEnd) {
                        comp = Le.u64(cd, d);
                        d += 8;
                    }
                    if (localOff == 0xFFFFFFFFL && d + 8 <= dEnd) {
                        localOff = Le.u64(cd, d);
                    }
                }
                e += 4 + len;
            }
            entries.add(new ZipEntryInfo(name, method, flags, crc, comp, uncomp, localOff));
            p = recordEnd;
        }
        return new ZipArchive(source, entries, cdOffset, cdSize, zip64, warnings);
    }

    public List<ZipEntryInfo> entries() {
        return entries;
    }

    /** Exact-name lookup, or null. */
    public ZipEntryInfo find(String name) {
        return byName.get(name);
    }

    public long centralDirectoryOffset() {
        return centralDirectoryOffset;
    }

    public long centralDirectorySize() {
        return centralDirectorySize;
    }

    public boolean isZip64() {
        return zip64;
    }

    /** Non-fatal oddities found while parsing. */
    public List<String> warnings() {
        return warnings;
    }

    /** Absolute offset of the entry's data, validated against the file size. */
    public long dataOffset(ZipEntryInfo entry) throws IOException {
        byte[] h = source.readBytes(entry.localHeaderOffset, 30);
        if (Le.u32(h, 0) != SIG_LOCAL) {
            throw new ZipFormatException("Bad local header signature for '" + entry.name + "'");
        }
        long off = entry.localHeaderOffset + 30 + Le.u16(h, 26) + Le.u16(h, 28);
        if (off + entry.compressedSize > source.size()) {
            throw new ZipFormatException("Data of '" + entry.name + "' extends past the end of the file");
        }
        return off;
    }

    /** Sequential stream of the decompressed bytes. Caller closes it. */
    public InputStream open(ZipEntryInfo entry) throws IOException {
        if ((entry.flags & 1) != 0) {
            throw new ZipFormatException("Entry '" + entry.name + "' is encrypted");
        }
        long off = dataOffset(entry);
        InputStream raw = new SourceInputStream(source, off, entry.compressedSize);
        if (entry.method == ZipEntryInfo.METHOD_STORED) {
            return raw;
        }
        if (entry.method == ZipEntryInfo.METHOD_DEFLATED) {
            final Inflater inflater = new Inflater(true);
            return new InflaterInputStream(raw, inflater, 8192) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        inflater.end();
                    }
                }
            };
        }
        throw new ZipFormatException("Unsupported compression method " + entry.method + " for '" + entry.name + "'");
    }

    /** First {@code max} decompressed bytes (fewer if the entry is shorter). */
    public byte[] readPrefix(ZipEntryInfo entry, int max) throws IOException {
        try (InputStream in = open(entry)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(max, 8192));
            byte[] buf = new byte[4096];
            while (out.size() < max) {
                int n = in.read(buf, 0, Math.min(buf.length, max - out.size()));
                if (n < 0) {
                    break;
                }
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    /** Whole decompressed entry; refuses anything larger than {@code maxBytes}. */
    public byte[] readAll(ZipEntryInfo entry, int maxBytes) throws IOException {
        if (entry.size > maxBytes) {
            throw new ZipFormatException("'" + entry.name + "' is " + entry.size + " bytes, over the " + maxBytes + " byte limit");
        }
        byte[] data = readPrefix(entry, maxBytes + 1);
        if (data.length > maxBytes) {
            throw new ZipFormatException("'" + entry.name + "' expanded past the " + maxBytes + " byte limit");
        }
        return data;
    }

    /** Random-access view of a STORED entry (no copy). Compressed entries cannot be sliced. */
    public SeekableSource slice(ZipEntryInfo entry) throws IOException {
        if (!entry.isStored()) {
            throw new ZipFormatException("'" + entry.name + "' is compressed; it cannot be read at random offsets");
        }
        return new SliceSource(source, dataOffset(entry), entry.size);
    }
}
