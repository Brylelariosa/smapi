package com.universalsmapi.core.detect;

import com.universalsmapi.core.apk.ApkFacts;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Which CPU architecture the installed game runs as, and which ones its APKs contain. */
public final class ArchitectureAssessment {
    public enum Verdict {
        /** The installed package's native library directory is the 64-bit ARM one. */
        ARM64,
        /** arm64-v8a code is packaged but the running ABI could not be confirmed. */
        ARM64_PACKAGED,
        ARM32,
        X86,
        UNKNOWN
    }

    public final String installedAbi;
    public final List<String> abisPackaged;
    public final Verdict verdict;
    public final List<String> notes;

    private ArchitectureAssessment(String installedAbi, List<String> abisPackaged, Verdict verdict, List<String> notes) {
        this.installedAbi = installedAbi;
        this.abisPackaged = abisPackaged;
        this.verdict = verdict;
        this.notes = notes;
    }

    public static ArchitectureAssessment assess(PackageFacts pkg, DeviceFacts device, List<ApkReport> apks) {
        List<String> notes = new ArrayList<>();
        TreeSet<String> packaged = new TreeSet<>();
        for (ApkReport r : apks) {
            if (r.facts != null) {
                packaged.addAll(r.facts.abis());
            }
        }
        String installed = null;
        if (pkg != null) {
            installed = abiFromLibDir(pkg.nativeLibraryDir);
            for (String split : pkg.splitNames) {
                String abi = abiFromSplitName(split);
                if (abi != null) {
                    packaged.add(abi);
                }
            }
        }

        Verdict v;
        if ("arm64-v8a".equals(installed)) {
            v = Verdict.ARM64;
        } else if ("armeabi-v7a".equals(installed)) {
            v = Verdict.ARM32;
            if (packaged.contains("arm64-v8a") && device != null && device.has64Bit()) {
                notes.add("The 32-bit ABI is installed although arm64-v8a is packaged and this device supports 64-bit.");
            }
        } else if (installed != null) {
            v = Verdict.X86;
        } else if (packaged.contains("arm64-v8a")) {
            v = Verdict.ARM64_PACKAGED;
            notes.add("Running ABI not reported by the system; 64-bit ARM code is packaged.");
        } else if (packaged.contains("armeabi-v7a")) {
            v = Verdict.ARM32;
        } else if (!packaged.isEmpty()) {
            v = Verdict.X86;
        } else {
            v = Verdict.UNKNOWN;
            notes.add("No native-library information available (APK not readable and no usable native library path).");
        }
        return new ArchitectureAssessment(installed, new ArrayList<>(packaged), v, notes);
    }

    static String abiFromLibDir(String nativeLibraryDir) {
        if (nativeLibraryDir == null) {
            return null;
        }
        String p = nativeLibraryDir.replace('\\', '/');
        while (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        String leaf = p.substring(p.lastIndexOf('/') + 1);
        switch (leaf) {
            case "arm64":
            case "arm64-v8a":
                return "arm64-v8a";
            case "arm":
            case "armeabi-v7a":
                return "armeabi-v7a";
            case "x86_64":
                return "x86_64";
            case "x86":
                return "x86";
            default:
                return null;
        }
    }

    static String abiFromSplitName(String splitName) {
        if (splitName == null) {
            return null;
        }
        String n = splitName.startsWith("config.") ? splitName.substring(7) : splitName;
        switch (n) {
            case "arm64_v8a":
                return "arm64-v8a";
            case "armeabi_v7a":
                return "armeabi-v7a";
            case "x86_64":
                return "x86_64";
            case "x86":
                return "x86";
            default:
                return null;
        }
    }
}
