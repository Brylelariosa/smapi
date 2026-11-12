package com.universalsmapi.core.testutil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * Hand-rolled ZIP writer (stored entries only) used where the JDK's ZipOutputStream cannot express what a test needs:
 * an APK Signing Block between the last entry and the central directory, and ZIP64 end records.
 */
public final class MiniZip {
    private static final class E {
        final String name;
        final byte[] data;

        E(String name, byte[] data) {
            this.name = name;
            this.data = data;
        }
    }

    private final List<E> entries = new ArrayList<>();
    private byte[] signingBlock;
    private boolean zip64;

    public MiniZip add(String name, byte[] data) {
        entries.add(new E(name, data));
        return this;
    }

    public MiniZip add(String name, String text) {
        return add(name, text.getBytes(StandardCharsets.UTF_8));
    }

    public MiniZip signingBlock(byte[] block) {
        this.signingBlock = block;
        return this;
    }

    /** Use ZIP64 end records, and a ZIP64 extra field for the first entry's sizes. */
    public MiniZip zip64() {
        this.zip64 = true;
        return this;
    }

    public byte[] build() {
        Bytes out = new Bytes();
        long[] offsets = new long[entries.size()];
        long[] crcs = new long[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            E e = entries.get(i);
            CRC32 crc = new CRC32();
            crc.update(e.data);
            crcs[i] = crc.getValue();
            offsets[i] = out.size();
            byte[] name = e.name.getBytes(StandardCharsets.UTF_8);
            out.u32(0x04034b50L).u16(20).u16(0x0800).u16(0).u16(0).u16(0x21).u32(crcs[i])
                    .u32(e.data.length).u32(e.data.length).u16(name.length).u16(0).bytes(name).bytes(e.data);
        }
        if (signingBlock != null) {
            out.bytes(signingBlock);
        }
        long cdOffset = out.size();
        for (int i = 0; i < entries.size(); i++) {
            E e = entries.get(i);
            byte[] name = e.name.getBytes(StandardCharsets.UTF_8);
            boolean z64 = zip64 && i == 0;
            out.u32(0x02014b50L).u16(45).u16(45).u16(0x0800).u16(0).u16(0).u16(0x21).u32(crcs[i])
                    .u32(z64 ? 0xFFFFFFFFL : e.data.length).u32(z64 ? 0xFFFFFFFFL : e.data.length)
                    .u16(name.length).u16(z64 ? 20 : 0).u16(0).u16(0).u16(0).u32(0).u32(offsets[i]).bytes(name);
            if (z64) {
                out.u16(1).u16(16).u64(e.data.length).u64(e.data.length);
            }
        }
        long cdSize = out.size() - cdOffset;
        if (zip64) {
            long z64Pos = out.size();
            out.u32(0x06064b50L).u64(44).u16(45).u16(45).u32(0).u32(0).u64(entries.size()).u64(entries.size())
                    .u64(cdSize).u64(cdOffset);
            out.u32(0x07064b50L).u32(0).u64(z64Pos).u32(1);
            out.u32(0x06054b50L).u16(0).u16(0).u16(0xFFFF).u16(0xFFFF).u32(0xFFFFFFFFL).u32(0xFFFFFFFFL).u16(0);
        } else {
            out.u32(0x06054b50L).u16(0).u16(0).u16(entries.size()).u16(entries.size()).u32(cdSize).u32(cdOffset).u16(0);
        }
        return out.toByteArray();
    }
}
