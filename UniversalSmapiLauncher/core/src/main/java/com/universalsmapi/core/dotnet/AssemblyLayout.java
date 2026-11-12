package com.universalsmapi.core.dotnet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * How a .NET-for-Android / Xamarin APK packages its managed assemblies, as observed (never assumed).
 *
 * <p>Layouts documented by the dotnet/android project that this probe recognises:
 * <ul>
 *   <li>loose {@code assemblies/*.dll} (Xamarin.Android and early .NET), optionally LZ4 "XALZ" compressed;</li>
 *   <li>{@code assemblies/assemblies[.arch].blob} stores, with a sibling {@code assemblies.manifest} naming entries;</li>
 *   <li>stores wrapped in an ELF shared object under {@code lib/ABI/} ({@code libassembly-store.so} or
 *       {@code libassemblies.ABI.blob.so}); assembly names live inside the store.</li>
 * </ul>
 */
public final class AssemblyLayout {
    public enum Kind { NONE, LOOSE_ASSEMBLIES, STORE_BLOB, STORE_IN_ELF, MIXED, UNKNOWN }

    /** One assembly-store file and what could be read from its header. */
    public static final class StoreFile {
        public final String entryName;
        public final String format; // "raw-blob" or "elf-payload"
        public final long entrySize;
        public final boolean storedUncompressed;
        public boolean headerRead;
        public boolean magicOk;
        public long version;
        /** Five raw 32-bit header words (their meaning depends on the store version). */
        public final long[] words = new long[5];
        public String detail = "";

        /** Header words for display: the magic in hex, the rest in decimal, e.g. [0x41424158, 2, 7, 7, 0]. */
        public String wordsText() {
            StringBuilder sb = new StringBuilder("[0x").append(Long.toHexString(words[0]));
            for (int i = 1; i < words.length; i++) {
                sb.append(", ").append(words[i]);
            }
            return sb.append(']').toString();
        }

        public StoreFile(String entryName, String format, long entrySize, boolean storedUncompressed) {
            this.entryName = entryName;
            this.format = format;
            this.entrySize = entrySize;
            this.storedUncompressed = storedUncompressed;
        }
    }

    public Kind kind = Kind.NONE;
    public final List<String> looseAssemblyEntries = new ArrayList<>();
    public int looseSampled;
    public int looseXalzCompressed;
    public int looseRawPe;
    public final List<StoreFile> stores = new ArrayList<>();
    public boolean manifestPresent;
    /** Assembly names listed in assemblies.manifest (without ".dll"). Empty when no manifest exists. */
    public final List<String> manifestNames = new ArrayList<>();
    public final List<String> notes = new ArrayList<>();

    /**
     * Case-insensitive presence check by assembly simple name. Only answers {@code true}/{@code false} from names
     * that were actually observed (loose files or manifest); stores without a manifest cannot answer, see
     * {@link #canListNames()}.
     */
    public boolean hasAssembly(String simpleName) {
        String want = simpleName.toLowerCase(Locale.ROOT);
        for (String entry : looseAssemblyEntries) {
            String file = entry.substring(entry.lastIndexOf('/') + 1);
            if (stripDll(file).toLowerCase(Locale.ROOT).equals(want)) {
                return true;
            }
        }
        for (String n : manifestNames) {
            if (n.toLowerCase(Locale.ROOT).equals(want)) {
                return true;
            }
        }
        return false;
    }

    /** True when assembly names are observable (loose files or a manifest), so absence is meaningful. */
    public boolean canListNames() {
        return !looseAssemblyEntries.isEmpty() || !manifestNames.isEmpty();
    }

    static String stripDll(String file) {
        return file.toLowerCase(Locale.ROOT).endsWith(".dll") ? file.substring(0, file.length() - 4) : file;
    }
}
