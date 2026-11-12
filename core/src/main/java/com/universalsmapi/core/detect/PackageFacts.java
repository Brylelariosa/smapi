package com.universalsmapi.core.detect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** What the OS package manager reports about one installed package. Pure data; built by the Android layer. */
public final class PackageFacts {
    public final String packageName;
    public final String label;
    public final String versionName;
    public final long versionCode;
    public final String installerPackage;
    public final String initiatingPackage;
    /** SHA-256 digests (lowercase hex) of the current signing certificates. */
    public final List<String> signerSha256;
    public final boolean multipleSigners;
    public final String sourceDir;
    public final List<String> splitNames;
    public final List<String> splitSourceDirs;
    public final String nativeLibraryDir;
    public final long firstInstallTime;
    public final long lastUpdateTime;
    public final int minSdk;
    public final int targetSdk;
    public final boolean enabled;

    private PackageFacts(Builder b) {
        this.packageName = b.packageName;
        this.label = b.label;
        this.versionName = b.versionName;
        this.versionCode = b.versionCode;
        this.installerPackage = b.installerPackage;
        this.initiatingPackage = b.initiatingPackage;
        this.signerSha256 = Collections.unmodifiableList(new ArrayList<>(b.signerSha256));
        this.multipleSigners = b.multipleSigners;
        this.sourceDir = b.sourceDir;
        this.splitNames = Collections.unmodifiableList(new ArrayList<>(b.splitNames));
        this.splitSourceDirs = Collections.unmodifiableList(new ArrayList<>(b.splitSourceDirs));
        this.nativeLibraryDir = b.nativeLibraryDir;
        this.firstInstallTime = b.firstInstallTime;
        this.lastUpdateTime = b.lastUpdateTime;
        this.minSdk = b.minSdk;
        this.targetSdk = b.targetSdk;
        this.enabled = b.enabled;
    }

    /** Mutable builder; every field is optional. */
    public static final class Builder {
        public String packageName;
        public String label;
        public String versionName;
        public long versionCode = -1;
        public String installerPackage;
        public String initiatingPackage;
        public List<String> signerSha256 = new ArrayList<>();
        public boolean multipleSigners;
        public String sourceDir;
        public List<String> splitNames = new ArrayList<>();
        public List<String> splitSourceDirs = new ArrayList<>();
        public String nativeLibraryDir;
        public long firstInstallTime;
        public long lastUpdateTime;
        public int minSdk;
        public int targetSdk;
        public boolean enabled = true;

        public PackageFacts build() {
            return new PackageFacts(this);
        }
    }
}
