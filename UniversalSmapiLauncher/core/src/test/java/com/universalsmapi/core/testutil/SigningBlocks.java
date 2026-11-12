package com.universalsmapi.core.testutil;

import java.nio.charset.StandardCharsets;

/** Builds an "APK Sig Block 42" structure with dummy values. */
public final class SigningBlocks {
    private SigningBlocks() {
    }

    public static byte[] build(long... ids) {
        Bytes pairs = new Bytes();
        for (long id : ids) {
            byte[] value = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
            pairs.u64(4 + value.length).u32(id).bytes(value);
        }
        long blockSize = pairs.size() + 24; // second size field (8) + magic (16)
        Bytes out = new Bytes();
        out.u64(blockSize).bytes(pairs.toByteArray()).u64(blockSize).bytes("APK Sig Block 42".getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }
}
