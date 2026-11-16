package com.universalsmapi.core.dotnet;

import com.universalsmapi.core.io.Le;

/** Just enough of the PE/COFF format to tell whether a byte array is a managed (.NET) assembly image. */
public final class PeSummary {
    public final boolean valid;
    public final String problem;
    public final int machine;
    public final boolean pe32Plus;
    /** True when the CLR runtime header data directory (index 14) is present. */
    public final boolean managed;
    public final int sections;

    private PeSummary(boolean valid, String problem, int machine, boolean pe32Plus, boolean managed, int sections) {
        this.valid = valid;
        this.problem = problem;
        this.machine = machine;
        this.pe32Plus = pe32Plus;
        this.managed = managed;
        this.sections = sections;
    }

    private static PeSummary invalid(String why) {
        return new PeSummary(false, why, 0, false, false, 0);
    }

    public static PeSummary parse(byte[] image) {
        if (image.length < 0x40 || image[0] != 'M' || image[1] != 'Z') {
            return invalid("does not start with the DOS 'MZ' header");
        }
        long lfanew = Le.u32(image, 0x3C);
        if (lfanew < 0x40 || lfanew + 24 > image.length) {
            return invalid("e_lfanew " + lfanew + " is outside the image");
        }
        int pe = (int) lfanew;
        if (image[pe] != 'P' || image[pe + 1] != 'E' || image[pe + 2] != 0 || image[pe + 3] != 0) {
            return invalid("PE signature missing at offset " + pe);
        }
        int machine = Le.u16(image, pe + 4);
        int sections = Le.u16(image, pe + 6);
        int optSize = Le.u16(image, pe + 20);
        int opt = pe + 24;
        if (optSize < 2 || opt + optSize > image.length) {
            return invalid("optional header does not fit the image");
        }
        int magic = Le.u16(image, opt);
        boolean plus;
        int dirOffset;
        if (magic == 0x10B) {
            plus = false;
            dirOffset = 96;
        } else if (magic == 0x20B) {
            plus = true;
            dirOffset = 112;
        } else {
            return invalid("unknown optional header magic 0x" + Integer.toHexString(magic));
        }
        boolean managed = false;
        if (optSize >= dirOffset + 15 * 8) {
            long rva = Le.u32(image, opt + dirOffset + 14 * 8);
            long size = Le.u32(image, opt + dirOffset + 14 * 8 + 4);
            managed = rva != 0 && size != 0;
        }
        return new PeSummary(true, null, machine, plus, managed, sections);
    }

    public String machineName() {
        switch (machine) {
            case 0x14c:
                return "i386/AnyCPU";
            case 0x8664:
                return "x64";
            case 0xAA64:
                return "arm64";
            case 0x1c4:
                return "arm";
            default:
                return "0x" + Integer.toHexString(machine);
        }
    }
}
