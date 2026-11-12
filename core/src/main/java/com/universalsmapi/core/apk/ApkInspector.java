package com.universalsmapi.core.apk;

import com.universalsmapi.core.axml.ManifestFacts;
import com.universalsmapi.core.diag.DiagnosticLog;
import com.universalsmapi.core.dotnet.AssemblyLayoutProbe;
import com.universalsmapi.core.io.SeekableSource;
import com.universalsmapi.core.zip.ZipArchive;
import com.universalsmapi.core.zip.ZipEntryInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Reads an APK's ZIP structure and summarises it. Nothing is extracted or written; only the central directory and a few
 * small headers are read. Failures propagate as {@link IOException} so the caller can log the OS's exact answer.
 */
public final class ApkInspector {
    private static final String TAG = "apk-inspector";

    /** Native library file name to a human-readable runtime hint. */
    private static final String[][] RUNTIME_HINTS = {
            {"libmonodroid.so", "Mono for Android host (libmonodroid.so)"},
            {"libmonosgen-2.0.so", "Mono runtime (libmonosgen-2.0.so)"},
            {"libmono-android.release.so", "Mono runtime (libmono-android.release.so)"},
            {"libmono-android.debug.so", "Mono runtime (libmono-android.debug.so)"},
            {"libcoreclr.so", "CoreCLR runtime (libcoreclr.so)"},
            {"libSystem.Native.so", ".NET native shim (libSystem.Native.so)"},
            {"libxamarin-app.so", "xamarin-app glue (libxamarin-app.so)"},
            {"libassembly-store.so", "assembly store packaged as a library (libassembly-store.so)"},
            {"libarc.bin.so", "runtime config blob (libarc.bin.so)"},
    };

    private ApkInspector() {
    }

    public static ApkFacts inspect(SeekableSource src, String label, DiagnosticLog log) throws IOException {
        ApkFacts f = new ApkFacts();
        f.fileSize = src.size();
        ZipArchive zip = ZipArchive.parse(src);
        f.entryCount = zip.entries().size();
        f.zip64 = zip.isZip64();
        f.centralDirectoryOffset = zip.centralDirectoryOffset();
        f.zipWarnings.addAll(zip.warnings());

        boolean sfFile = false;
        boolean sigFile = false;
        TreeSet<String> libFileNames = new TreeSet<>();

        for (ZipEntryInfo e : zip.entries()) {
            f.totalUncompressed += e.size;
            f.totalCompressed += e.compressedSize;
            String n = e.name;
            int slash = n.indexOf('/');
            increment(f.topLevelCounts, slash < 0 ? n : n.substring(0, slash + 1));
            if (e.isDirectory()) {
                continue;
            }
            if (n.endsWith(".apk") && f.nestedApks.size() < 50) {
                f.nestedApks.add(n);
            }
            if (n.equals("AndroidManifest.xml")) {
                f.hasManifest = true;
            } else if (n.equals("resources.arsc")) {
                f.hasResourcesArsc = true;
            } else if (n.matches("classes\\d*\\.dex")) {
                f.dexFiles++;
            } else if (n.startsWith("META-INF/")) {
                String u = n.toUpperCase(Locale.ROOT);
                sfFile |= u.endsWith(".SF");
                sigFile |= u.endsWith(".RSA") || u.endsWith(".DSA") || u.endsWith(".EC");
            } else if (n.startsWith("lib/")) {
                String rest = n.substring(4);
                int s = rest.indexOf('/');
                if (s > 0) {
                    String abi = rest.substring(0, s);
                    String file = rest.substring(s + 1);
                    ApkFacts.AbiLibs libs = f.nativeLibs.get(abi);
                    if (libs == null) {
                        libs = new ApkFacts.AbiLibs(abi);
                        f.nativeLibs.put(abi, libs);
                    }
                    libs.files.add(file);
                    libs.bytes += e.size;
                    if (!e.isStored()) {
                        libs.compressedCount++;
                    }
                    libFileNames.add(file);
                }
            } else if (n.startsWith("assets/")) {
                f.assetFiles++;
                f.assetBytes += e.size;
                String rest = n.substring(7);
                int s = rest.indexOf('/');
                increment(f.assetTopLevel, s < 0 ? "(root)" : rest.substring(0, s + 1));
            }
        }
        f.v1Signature = sfFile && sigFile;
        f.bundleContainer = !f.hasManifest && !f.nestedApks.isEmpty();

        try {
            f.signingBlock = SigningBlockInfo.find(src, zip.centralDirectoryOffset());
        } catch (IOException ex) {
            log.warn(TAG, label + ": could not examine signing block", ex);
        }

        List<String> markers = new ArrayList<>();
        for (String[] hint : RUNTIME_HINTS) {
            if (libFileNames.contains(hint[0])) {
                markers.add(hint[1]);
            }
        }
        for (String name : libFileNames) {
            if (name.startsWith("libassemblies.") && name.endsWith(".blob.so")) {
                markers.add("assembly store packaged as a library (" + name + ")");
            }
        }
        f.runtimeMarkers.addAll(markers);

        f.assemblies = AssemblyLayoutProbe.probe(zip, log);

        ZipEntryInfo manifestEntry = zip.find("AndroidManifest.xml");
        if (manifestEntry != null) {
            try {
                f.manifest = ManifestFacts.parse(zip.readAll(manifestEntry, 8 * 1024 * 1024));
            } catch (IOException ex) {
                f.manifestError = ex.getMessage();
                log.warn(TAG, label + ": AndroidManifest.xml could not be parsed", ex);
            }
        }

        log.info(TAG, label + ": " + f.entryCount + " entries, " + f.fileSize + " bytes, abis=" + f.abis()
                + ", signingBlock=" + (f.signingBlock == null ? "none" : f.signingBlock.schemes())
                + ", v1=" + f.v1Signature + ", manifest=" + f.hasManifest + ", bundle=" + f.bundleContainer
                + ", assemblies=" + f.assemblies.kind);
        return f;
    }

    private static void increment(Map<String, Integer> map, String key) {
        Integer cur = map.get(key);
        map.put(key, cur == null ? 1 : cur + 1);
    }
}
