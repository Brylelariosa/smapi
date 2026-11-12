package com.universalsmapi.core.testutil;

import java.nio.charset.StandardCharsets;

/** Minimal ELF images with a ".shstrtab" and a "payload" section, for exercising the section reader. */
public final class ElfBuilder {
    private ElfBuilder() {
    }

    public static byte[] elf64(byte[] payload) {
        return build(payload, true);
    }

    public static byte[] elf32(byte[] payload) {
        return build(payload, false);
    }

    private static byte[] build(byte[] payload, boolean is64) {
        byte[] shstr = "\0.shstrtab\0payload\0".getBytes(StandardCharsets.US_ASCII);
        int headerSize = is64 ? 64 : 52;
        int shentsize = is64 ? 64 : 40;
        long payloadOff = headerSize;
        long strOff = payloadOff + payload.length;
        long shoff = strOff + shstr.length;

        Bytes b = new Bytes();
        b.u8(0x7F).u8('E').u8('L').u8('F').u8(is64 ? 2 : 1).u8(1).u8(1).zeros(9);
        b.u16(3).u16(is64 ? 0xB7 : 0x28).u32(1);
        if (is64) {
            b.u64(0).u64(0).u64(shoff).u32(0).u16(64).u16(0).u16(0).u16(64).u16(3).u16(1);
        } else {
            b.u32(0).u32(0).u32(shoff).u32(0).u16(52).u16(0).u16(0).u16(40).u16(3).u16(1);
        }
        b.bytes(payload).bytes(shstr);
        b.zeros(shentsize); // null section
        section(b, is64, 1, 3, strOff, shstr.length);
        section(b, is64, 11, 1, payloadOff, payload.length);
        return b.toByteArray();
    }

    private static void section(Bytes b, boolean is64, int nameIdx, int type, long off, long size) {
        if (is64) {
            b.u32(nameIdx).u32(type).u64(0).u64(0).u64(off).u64(size).u32(0).u32(0).u64(1).u64(0);
        } else {
            b.u32(nameIdx).u32(type).u32(0).u32(0).u32(off).u32(size).u32(0).u32(0).u32(1).u32(0);
        }
    }
}
