package com.universalsmapi.core.apk;

import com.universalsmapi.core.axml.ManifestFacts;
import com.universalsmapi.core.dotnet.AssemblyLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything learned about one APK by reading its ZIP structure. Populated by {@link ApkInspector}; treat as read-only.
 */
public final class ApkFacts {
    /** Native libraries packaged for one ABI. */
    public static final class AbiLibs {
        public final String abi;
        public final List<String> files = new ArrayList<>();
        public long bytes;
        public int compressedCount;

        public AbiLibs(String abi) {
            this.abi = abi;
        }
    }

    public long fileSize;
    public int entryCount;
    public boolean zip64;
    public long centralDirectoryOffset;
    public long totalUncompressed;
    public long totalCompressed;
    public final List<String> zipWarnings = new ArrayList<>();

    /** Null when the file has no APK Signing Block (v1-only or unsigned). */
    public SigningBlockInfo signingBlock;
    public boolean v1Signature;

    public boolean hasManifest;
    public boolean hasResourcesArsc;
    public int dexFiles;

    /** True for .apks/.xapk/.apkm style containers: nested .apk entries and no manifest of its own. */
    public boolean bundleContainer;
    public final List<String> nestedApks = new ArrayList<>();

    /** ABI name (e.g. arm64-v8a) to its native libraries. */
    public final Map<String, AbiLibs> nativeLibs = new TreeMap<>();
    /** Human-readable hints about the .NET/Mono runtime packaged in this APK. */
    public final List<String> runtimeMarkers = new ArrayList<>();

    public final Map<String, Integer> topLevelCounts = new TreeMap<>();
    public int assetFiles;
    public long assetBytes;
    public final Map<String, Integer> assetTopLevel = new TreeMap<>();

    /** Set when the assembly layout probe ran. */
    public AssemblyLayout assemblies;

    /** Parsed AndroidManifest.xml, or null (see {@link #manifestError}). */
    public ManifestFacts manifest;
    public String manifestError;

    public List<String> abis() {
        return new ArrayList<>(nativeLibs.keySet());
    }
}
