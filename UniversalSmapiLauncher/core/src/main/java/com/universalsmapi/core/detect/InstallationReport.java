package com.universalsmapi.core.detect;

import com.universalsmapi.core.compat.CompatResolver;
import com.universalsmapi.core.dotnet.AssemblyLayout;
import com.universalsmapi.core.version.GameVersion;

import java.util.ArrayList;
import java.util.List;

/** Everything learned about one Stardew Valley installation (or one user-picked APK). */
public final class InstallationReport {
    /** Null when analysing a user-picked APK that is not installed. */
    public final PackageFacts pkg;
    public final long createdAtMillis;
    public final List<ProbeResult> probes = new ArrayList<>();
    public final List<ApkReport> apks = new ArrayList<>();
    public GameVersion gameVersion;
    public String gameVersionSource;
    public ArchitectureAssessment architecture;
    /** Layout from the APK that actually contains managed assemblies (or the base APK if none does). */
    public AssemblyLayout assemblies;
    public String assembliesFrom;
    public final List<AssemblyCheck> assemblyChecks = new ArrayList<>();
    public CompatResolver.Resolution compat;
    public final List<Finding> findings = new ArrayList<>();

    public InstallationReport(PackageFacts pkg, long createdAtMillis) {
        this.pkg = pkg;
        this.createdAtMillis = createdAtMillis;
    }

    public boolean hasFinding(String code) {
        for (Finding f : findings) {
            if (f.code.equals(code)) {
                return true;
            }
        }
        return false;
    }

    public String title() {
        if (pkg != null && pkg.label != null) {
            return pkg.label;
        }
        if (pkg != null) {
            return pkg.packageName;
        }
        return "Picked APK";
    }
}
