package com.universalsmapi.core.apk;

import com.universalsmapi.core.io.Le;
import com.universalsmapi.core.io.SeekableSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Presence and shape of the "APK Signing Block" (signature scheme v2/v3/v3.1) that sits just before the ZIP central
 * directory. Only structure is reported; no signature is verified here (Android's PackageManager already did that
 * at install time, and exposes the signer certificate digests separately).
 */
public final class SigningBlockInfo {
    public static final long ID_V2 = 0x7109871aL;
    public static final long ID_V3 = 0xf05368c0L;
    public static final long ID_V31 = 0x1b93ad61L;
    public static final long ID_VERITY_PADDING = 0x42726577L;

    private static final byte[] MAGIC = "APK Sig Block 42".getBytes(StandardCharsets.US_ASCII);
    private static final long MAX_BLOCK_BYTES = 16L * 1024 * 1024;

    public final long offset;
    public final long totalSize;
    public final List<Long> pairIds;

    private SigningBlockInfo(long offset, long totalSize, List<Long> pairIds) {
        this.offset = offset;
        this.totalSize = totalSize;
        this.pairIds = pairIds;
    }

    public boolean hasV2() {
        return pairIds.contains(ID_V2);
    }

    public boolean hasV3() {
        return pairIds.contains(ID_V3);
    }

    public boolean hasV31() {
        return pairIds.contains(ID_V31);
    }

    /** Names of the schemes present, e.g. [v2, v3]. */
    public List<String> schemes() {
        List<String> out = new ArrayList<>();
        if (hasV2()) {
            out.add("v2");
        }
        if (hasV3()) {
            out.add("v3");
        }
        if (hasV31()) {
            out.add("v3.1");
        }
        return out;
    }

    /** Locate the block, or return null if the file has none (or it looks malformed). */
    public static SigningBlockInfo find(SeekableSource src, long centralDirectoryOffset) throws IOException {
        if (centralDirectoryOffset < 32) {
            return null;
        }
        byte[] footer = src.readBytes(centralDirectoryOffset - 24, 24);
        for (int i = 0; i < MAGIC.length; i++) {
            if (footer[8 + i] != MAGIC[i]) {
                return null;
            }
        }
        long blockSize = Le.u64(footer, 0);
        long total = blockSize + 8;
        long start = centralDirectoryOffset - total;
        if (blockSize < 24 || blockSize > MAX_BLOCK_BYTES || start < 0) {
            return null;
        }
        byte[] head = src.readBytes(start, 8);
        if (Le.u64(head, 0) != blockSize) {
            return null;
        }
        int pairsLen = (int) (blockSize - 24);
        byte[] pairs = src.readBytes(start + 8, pairsLen);
        List<Long> ids = new ArrayList<>();
        int p = 0;
        while (p + 12 <= pairs.length) {
            long len = Le.u64(pairs, p);
            if (len < 4 || len > pairs.length - p - 8) {
                break;
            }
            ids.add(Le.u32(pairs, p + 8));
            p += (int) (8 + len);
        }
        return new SigningBlockInfo(start, total, ids);
    }
}
