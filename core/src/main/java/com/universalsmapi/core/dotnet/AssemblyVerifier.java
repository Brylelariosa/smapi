package com.universalsmapi.core.dotnet;

import com.universalsmapi.core.apk.ApkSource;
import com.universalsmapi.core.diag.DiagnosticLog;
import com.universalsmapi.core.io.Le;
import com.universalsmapi.core.io.SeekableSource;
import com.universalsmapi.core.zip.ZipArchive;
import com.universalsmapi.core.zip.ZipEntryInfo;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only dry run of "extract every assembly the runtime maps": reads each image from its store, decompresses XALZ
 * (LZ4) payloads in memory, and checks that the result has the length its header declares and is a managed PE image.
 * Nothing is written to disk. It answers whether a host could obtain the game's assemblies from the installed APK.
 */
public final class AssemblyVerifier {
    private static final long MAX_ASSEMBLY_BYTES = 256L * 1024 * 1024;

    private AssemblyVerifier() {
    }

    /** Outcome for one assembly. */
    public static final class Item {
        public final String name;
        public final String storeFile;
        public final int index;
        public boolean ok;
        public String reason = "";
        public long storedSize;
        public long expandedSize;
        public String machine = "?";
        public boolean managed;
        /** First 8 bytes of the SHA-256 of the decompressed image, hex. */
        public String sha256 = "";

        Item(String name, String storeFile, int index) {
            this.name = name;
            this.storeFile = storeFile;
            this.index = index;
        }
    }

    public static final class Result {
        public final List<Item> items = new ArrayList<>();
        public int ok;
        public int failed;
        public long stored;
        public long expanded;
        public long millis;
    }

    public static Result verify(ApkSource apk, AssemblyLayout layout, String abi, DiagnosticLog log) throws IOException {
        long t0 = System.nanoTime();
        Result r = new Result();
        List<AssemblyLayout.Entry> entries = layout.entriesFor(abi);
        try (SeekableSource src = apk.open()) {
            ZipArchive zip = ZipArchive.parse(src);
            Map<String, SeekableSource> blobs = new LinkedHashMap<>();
            for (AssemblyLayout.Entry e : entries) {
                String label = e.name != null ? e.name : (e.storeFile + "#" + e.localIndex);
                Item it = new Item(label, e.storeFile, e.localIndex);
                r.items.add(it);
                it.storedSize = e.dataSize;
                try {
                    SeekableSource blob = blobs.get(e.storeFile);
                    if (blob == null) {
                        ZipEntryInfo ze = zip.find(e.storeFile);
                        if (ze == null) {
                            throw new IOException("store entry missing from the APK: " + e.storeFile);
                        }
                        blob = zip.slice(ze);
                        blobs.put(e.storeFile, blob);
                    }
                    verifyOne(blob, e, it);
                } catch (IOException | RuntimeException ex) {
                    it.ok = false;
                    it.reason = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                }
                if (it.ok) {
                    r.ok++;
                } else {
                    r.failed++;
                    log.warn("assembly-verify", label + " failed: " + it.reason);
                }
                r.stored += it.storedSize;
                r.expanded += it.expandedSize;
            }
        }
        r.millis = (System.nanoTime() - t0) / 1_000_000;
        log.info("assembly-verify", r.ok + " of " + r.items.size() + " assemblies verified in " + r.millis + " ms");
        return r;
    }

    private static void verifyOne(SeekableSource blob, AssemblyLayout.Entry e, Item it) throws IOException {
        if (e.dataSize > MAX_ASSEMBLY_BYTES) {
            throw new IOException("stored size " + e.dataSize + " is implausible");
        }
        byte[] raw = blob.readBytes(e.dataOffset, (int) e.dataSize);
        byte[] image;
        if (e.isCompressed()) {
            if (raw.length < 12) {
                throw new IOException("XALZ payload is shorter than its 12-byte header");
            }
            long declared = Le.u32(raw, 8);
            if (declared <= 0 || declared > MAX_ASSEMBLY_BYTES) {
                throw new IOException("implausible uncompressed length " + declared);
            }
            image = new byte[(int) declared];
            int written = Lz4.decompress(raw, 12, raw.length - 12, image, 0, image.length);
            if (written != image.length) {
                throw new IOException("decompressed " + written + " bytes but the header declares " + declared);
            }
        } else {
            image = raw;
        }
        it.expandedSize = image.length;
        PeSummary pe = PeSummary.parse(image);
        if (!pe.valid) {
            throw new IOException("not a PE image: " + pe.problem);
        }
        it.machine = pe.machineName();
        it.managed = pe.managed;
        it.sha256 = sha256Prefix(image);
        if (!pe.managed) {
            throw new IOException("PE image has no CLR header (not a managed assembly)");
        }
        it.ok = true;
    }

    private static String sha256Prefix(byte[] data) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(Character.forDigit((d[i] >> 4) & 0xF, 16)).append(Character.forDigit(d[i] & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
