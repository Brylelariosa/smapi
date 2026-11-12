package com.universalsmapi.core.dotnet;

import com.universalsmapi.core.diag.DiagnosticLog;
import com.universalsmapi.core.io.Le;
import com.universalsmapi.core.io.SeekableSource;
import com.universalsmapi.core.zip.ZipArchive;
import com.universalsmapi.core.zip.ZipEntryInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Observes how an APK packages managed assemblies. It reads headers only: it does not decompress assemblies, and it
 * never claims a store format it has not actually seen (unknown versions are reported with their raw header words so a
 * human can extend the parser from real samples).
 */
public final class AssemblyLayoutProbe {
    private static final String TAG = "assembly-probe";
    /** 'XABA' little-endian, the magic of .NET-for-Android assembly stores. */
    public static final long STORE_MAGIC = 0x41424158L;
    private static final long XALZ_MAGIC = 0x5A4C4158L; // 'XALZ'
    private static final int LOOSE_SAMPLE_LIMIT = 24;
    private static final int MANIFEST_LIMIT = 4 * 1024 * 1024;

    private AssemblyLayoutProbe() {
    }

    public static AssemblyLayout probe(ZipArchive zip, DiagnosticLog log) {
        AssemblyLayout out = new AssemblyLayout();
        List<ZipEntryInfo> loose = new ArrayList<>();
        List<ZipEntryInfo> blobs = new ArrayList<>();
        List<ZipEntryInfo> elfStores = new ArrayList<>();
        ZipEntryInfo manifest = null;

        for (ZipEntryInfo e : zip.entries()) {
            String n = e.name;
            if (n.startsWith("assemblies/")) {
                String rest = n.substring("assemblies/".length());
                if (rest.endsWith(".dll")) {
                    loose.add(e);
                } else if (rest.equals("assemblies.manifest")) {
                    manifest = e;
                } else if (rest.startsWith("assemblies") && rest.endsWith(".blob")) {
                    blobs.add(e);
                }
            } else if (n.startsWith("lib/")) {
                String file = n.substring(n.lastIndexOf('/') + 1);
                if (file.equals("libassembly-store.so")
                        || (file.startsWith("libassemblies.") && file.endsWith(".blob.so"))) {
                    elfStores.add(e);
                }
            }
        }

        for (ZipEntryInfo e : loose) {
            out.looseAssemblyEntries.add(e.name);
        }
        sampleLoose(zip, loose, out, log);

        for (ZipEntryInfo e : blobs) {
            AssemblyLayout.StoreFile sf = new AssemblyLayout.StoreFile(e.name, "raw-blob", e.size, e.isStored());
            readRawHeader(zip, e, sf, log);
            out.stores.add(sf);
        }
        for (ZipEntryInfo e : elfStores) {
            AssemblyLayout.StoreFile sf = new AssemblyLayout.StoreFile(e.name, "elf-payload", e.size, e.isStored());
            readElfHeader(zip, e, sf, log);
            out.stores.add(sf);
        }

        if (manifest != null) {
            out.manifestPresent = true;
            try {
                byte[] raw = zip.readAll(manifest, MANIFEST_LIMIT);
                out.manifestNames.addAll(parseManifestNames(new String(raw, StandardCharsets.UTF_8)));
                log.info(TAG, "assemblies.manifest lists " + out.manifestNames.size() + " assemblies");
            } catch (IOException ex) {
                log.warn(TAG, "assemblies.manifest present but unreadable", ex);
                out.notes.add("assemblies.manifest could not be read: " + ex.getMessage());
            }
        }

        boolean hasLoose = !loose.isEmpty();
        boolean hasBlob = !blobs.isEmpty();
        boolean hasElf = !elfStores.isEmpty();
        int kinds = (hasLoose ? 1 : 0) + (hasBlob ? 1 : 0) + (hasElf ? 1 : 0);
        if (kinds == 0) {
            out.kind = AssemblyLayout.Kind.NONE;
            out.notes.add("No assemblies/ folder or assembly store found in this APK. In an installed game this usually"
                    + " means the managed code lives in another split APK.");
        } else if (kinds > 1) {
            out.kind = AssemblyLayout.Kind.MIXED;
        } else if (hasLoose) {
            out.kind = AssemblyLayout.Kind.LOOSE_ASSEMBLIES;
        } else if (hasBlob) {
            out.kind = AssemblyLayout.Kind.STORE_BLOB;
        } else {
            out.kind = AssemblyLayout.Kind.STORE_IN_ELF;
        }
        if ((hasBlob || hasElf) && !out.canListNames()) {
            out.notes.add("Assembly names are not readable from this layout without decoding the store itself"
                    + " (no assemblies.manifest). Presence checks for individual assemblies are therefore 'unknown',"
                    + " not 'absent'.");
        }
        log.info(TAG, "layout=" + out.kind + " loose=" + loose.size() + " blobs=" + blobs.size()
                + " elfStores=" + elfStores.size() + " manifest=" + out.manifestPresent);
        return out;
    }

    private static void sampleLoose(ZipArchive zip, List<ZipEntryInfo> loose, AssemblyLayout out, DiagnosticLog log) {
        int limit = Math.min(LOOSE_SAMPLE_LIMIT, loose.size());
        for (int i = 0; i < limit; i++) {
            ZipEntryInfo e = loose.get(i);
            try {
                byte[] head = zip.readPrefix(e, 4);
                out.looseSampled++;
                if (head.length >= 4 && Le.u32(head, 0) == XALZ_MAGIC) {
                    out.looseXalzCompressed++;
                } else if (head.length >= 2 && head[0] == 'M' && head[1] == 'Z') {
                    out.looseRawPe++;
                }
            } catch (IOException ex) {
                log.warn(TAG, "could not sample " + e.name, ex);
            }
        }
    }

    private static void readRawHeader(ZipArchive zip, ZipEntryInfo e, AssemblyLayout.StoreFile sf, DiagnosticLog log) {
        try {
            byte[] h = zip.readPrefix(e, 20);
            fillHeader(h, sf);
            log.info(TAG, "store " + e.name + ": magicOk=" + sf.magicOk + " version=" + sf.version);
        } catch (IOException ex) {
            sf.detail = "header unreadable: " + ex.getMessage();
            log.warn(TAG, "store header unreadable: " + e.name, ex);
        }
    }

    private static void readElfHeader(ZipArchive zip, ZipEntryInfo e, AssemblyLayout.StoreFile sf, DiagnosticLog log) {
        if (!e.isStored()) {
            sf.detail = "ELF store is compressed inside the APK; random access needed to find its payload section";
            log.warn(TAG, sf.entryName + ": " + sf.detail);
            return;
        }
        try (SeekableSource elf = zip.slice(e)) {
            ElfSections.Section payload = ElfSections.find(elf, "payload");
            if (payload == null) {
                sf.detail = "no 'payload' section found (not an ELF, or a layout this probe does not know)";
                log.warn(TAG, sf.entryName + ": " + sf.detail);
                return;
            }
            if (payload.size < 20) {
                sf.detail = "payload section too small (" + payload.size + " bytes)";
                return;
            }
            fillHeader(elf.readBytes(payload.offset, 20), sf);
            sf.detail = "payload section at +" + payload.offset + ", " + payload.size + " bytes";
            log.info(TAG, "store " + e.name + ": magicOk=" + sf.magicOk + " version=" + sf.version);
        } catch (IOException ex) {
            sf.detail = "ELF unreadable: " + ex.getMessage();
            log.warn(TAG, "ELF store unreadable: " + e.name, ex);
        }
    }

    private static void fillHeader(byte[] h, AssemblyLayout.StoreFile sf) {
        if (h.length < 20) {
            sf.detail = "header shorter than 20 bytes";
            return;
        }
        for (int i = 0; i < 5; i++) {
            sf.words[i] = Le.u32(h, i * 4);
        }
        sf.headerRead = true;
        sf.magicOk = sf.words[0] == STORE_MAGIC;
        sf.version = sf.words[1];
    }

    /** Parse the human-readable assemblies.manifest table: {@code Hash32 Hash64 BlobId BlobIdx Name...}. */
    static List<String> parseManifestNames(String text) {
        List<String> names = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty() || !t.startsWith("0x")) {
                continue; // header row or blank
            }
            String[] tok = t.split("\\s+");
            if (tok.length < 5) {
                continue;
            }
            StringBuilder name = new StringBuilder();
            for (int i = 4; i < tok.length; i++) {
                if (i > 4) {
                    name.append(' ');
                }
                name.append(tok[i]);
            }
            names.add(AssemblyLayout.stripDll(name.toString()));
        }
        return names;
    }
}
