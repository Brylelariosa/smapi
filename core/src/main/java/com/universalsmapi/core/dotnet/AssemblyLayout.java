package com.universalsmapi.core.dotnet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        /** Header fields as documented for store version 1 (meaningful only when {@link #magicOk}). */
        public long localEntryCount;
        public long globalEntryCount;
        public long storeId;
        /** One-line outcome of decoding the descriptor table, e.g. "58 assemblies decoded" or why it could not. */
        public String decodeSummary = "";

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

    /** One assembly inside a store, as described by the store's own descriptor table. */
    public static final class Entry {
        public final String storeFile;
        public final long storeId;
        public final int localIndex;
        public final long dataOffset;
        public final long dataSize;
        public final long debugSize;
        public final long configSize;
        /** "pe" (plain assembly), "xalz" (LZ4-compressed with an XALZ header) or "unknown". */
        public final String payload;
        /** For "xalz": the size after decompression as declared in its header; otherwise equal to dataSize. */
        public final long uncompressedSize;
        /** From assemblies.manifest (matched on store id and index), or null when no manifest row matched. */
        public String name;

        public Entry(String storeFile, long storeId, int localIndex, long dataOffset, long dataSize, long debugSize,
                     long configSize, String payload, long uncompressedSize) {
            this.storeFile = storeFile;
            this.storeId = storeId;
            this.localIndex = localIndex;
            this.dataOffset = dataOffset;
            this.dataSize = dataSize;
            this.debugSize = debugSize;
            this.configSize = configSize;
            this.payload = payload;
            this.uncompressedSize = uncompressedSize;
        }

        public boolean isCompressed() {
            return "xalz".equals(payload);
        }
    }

    /** One data row of assemblies.manifest. */
    public static final class ManifestRow {
        public final long blobId;
        public final long blobIndex;
        public final String name;

        public ManifestRow(long blobId, long blobIndex, String name) {
            this.blobId = blobId;
            this.blobIndex = blobIndex;
            this.name = name;
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
    public final List<ManifestRow> manifestRows = new ArrayList<>();
    /** Every assembly decoded from the stores (all stores together). Empty when no store could be decoded. */
    public final List<Entry> entries = new ArrayList<>();
    /** Reasons a store could not be (fully) decoded. Never silently dropped. */
    public final List<String> decodeProblems = new ArrayList<>();
    public final List<String> notes = new ArrayList<>();

    /**
     * Decoded assemblies that apply to one ABI: everything in the shared store (id 0) plus the entries of that ABI's
     * architecture-specific store. The runtime maps exactly one architecture store, so listing every ABI's store would
     * count the same assemblies several times. When the ABI is unknown, arm64-v8a is preferred, then the first store.
     */
    public List<Entry> entriesFor(String abi) {
        List<Entry> shared = new ArrayList<>();
        Map<String, List<Entry>> byArchFile = new LinkedHashMap<>();
        for (Entry e : entries) {
            if (e.storeId == 0) {
                shared.add(e);
            } else {
                List<Entry> group = byArchFile.get(e.storeFile);
                if (group == null) {
                    group = new ArrayList<>();
                    byArchFile.put(e.storeFile, group);
                }
                group.add(e);
            }
        }
        List<Entry> chosen = null;
        if (abi != null) {
            chosen = byArchFile.get("assemblies/assemblies." + abi.replace('-', '_') + ".blob");
        }
        if (chosen == null) {
            chosen = byArchFile.get("assemblies/assemblies.arm64_v8a.blob");
        }
        if (chosen == null && !byArchFile.isEmpty()) {
            chosen = byArchFile.values().iterator().next();
        }
        List<Entry> out = new ArrayList<>(shared);
        if (chosen != null) {
            out.addAll(chosen);
        }
        return out;
    }

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
