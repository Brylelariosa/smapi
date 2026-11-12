package com.universalsmapi.core.dotnet;

import com.universalsmapi.core.io.Le;
import com.universalsmapi.core.io.SeekableSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Just enough ELF parsing (32/64-bit little-endian) to find a named section's file range. */
public final class ElfSections {
    private ElfSections() {
    }

    /** File range of a section. */
    public static final class Section {
        public final String name;
        public final long offset;
        public final long size;

        Section(String name, long offset, long size) {
            this.name = name;
            this.offset = offset;
            this.size = size;
        }
    }

    /**
     * @return the section, or null when the file is not a little-endian ELF or has no such section
     */
    public static Section find(SeekableSource src, String wanted) throws IOException {
        long fileSize = src.size();
        if (fileSize < 64) {
            return null;
        }
        byte[] eh = src.readBytes(0, 64);
        if (eh[0] != 0x7F || eh[1] != 'E' || eh[2] != 'L' || eh[3] != 'F') {
            return null;
        }
        int cls = eh[4]; // 1 = 32-bit, 2 = 64-bit
        if (eh[5] != 1 || (cls != 1 && cls != 2)) {
            return null; // big-endian or unknown class
        }
        boolean is64 = cls == 2;
        long shoff = is64 ? Le.u64(eh, 0x28) : Le.u32(eh, 0x20);
        int shentsize = Le.u16(eh, is64 ? 0x3A : 0x2E);
        int shnum = Le.u16(eh, is64 ? 0x3C : 0x30);
        int shstrndx = Le.u16(eh, is64 ? 0x3E : 0x32);
        int minEntry = is64 ? 64 : 40;
        if (shoff <= 0 || shnum == 0 || shnum > 4096 || shentsize < minEntry || shstrndx >= shnum
                || shoff + (long) shnum * shentsize > fileSize) {
            return null;
        }
        byte[] table = src.readBytes(shoff, shnum * shentsize);

        int strBase = shstrndx * shentsize;
        long strOff = is64 ? Le.u64(table, strBase + 24) : Le.u32(table, strBase + 16);
        long strSize = is64 ? Le.u64(table, strBase + 32) : Le.u32(table, strBase + 20);
        if (strOff < 0 || strSize <= 0 || strSize > 1_000_000 || strOff + strSize > fileSize) {
            return null;
        }
        byte[] names = src.readBytes(strOff, (int) strSize);

        for (int i = 0; i < shnum; i++) {
            int base = i * shentsize;
            int nameIdx = (int) Le.u32(table, base);
            if (nameIdx < 0 || nameIdx >= names.length) {
                continue;
            }
            int end = nameIdx;
            while (end < names.length && names[end] != 0) {
                end++;
            }
            String name = new String(names, nameIdx, end - nameIdx, StandardCharsets.US_ASCII);
            if (name.equals(wanted)) {
                long off = is64 ? Le.u64(table, base + 24) : Le.u32(table, base + 16);
                long size = is64 ? Le.u64(table, base + 32) : Le.u32(table, base + 20);
                if (off < 0 || size < 0 || off + size > fileSize) {
                    return null;
                }
                return new Section(name, off, size);
            }
        }
        return null;
    }
}
