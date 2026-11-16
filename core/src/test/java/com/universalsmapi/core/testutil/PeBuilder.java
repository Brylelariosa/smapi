package com.universalsmapi.core.testutil;

/** Builds minimal PE images (DOS header, PE signature, COFF header, optional header with data directories). */
public final class PeBuilder {
    private PeBuilder() {
    }

    public static byte[] pe(boolean pe32Plus, int machine, boolean managed, int totalSize) {
        int dirOffset = pe32Plus ? 112 : 96;
        int optSize = dirOffset + 16 * 8;
        byte[] opt = new byte[optSize];
        opt[0] = (byte) (pe32Plus ? 0x0B : 0x0B);
        opt[1] = (byte) (pe32Plus ? 0x02 : 0x01);
        if (managed) {
            int d = dirOffset + 14 * 8;
            opt[d] = 0x08;
            opt[d + 1] = 0x20; // rva 0x2008
            opt[d + 4] = 0x48; // size 0x48
        }
        byte[] dos = new byte[0x80];
        dos[0] = 'M';
        dos[1] = 'Z';
        dos[0x3C] = (byte) 0x80;
        Bytes b = new Bytes();
        b.bytes(dos).ascii("PE").u8(0).u8(0);
        b.u16(machine).u16(1).u32(0).u32(0).u32(0).u16(optSize).u16(0x2022);
        b.bytes(opt);
        b.zeros(40); // one section header
        int pad = Math.max(0, totalSize - b.size());
        b.zeros(pad);
        return b.toByteArray();
    }

    public static byte[] managedAnyCpu(int totalSize) {
        return pe(false, 0x14c, true, totalSize);
    }
}
