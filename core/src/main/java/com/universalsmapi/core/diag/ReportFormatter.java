package com.universalsmapi.core.diag;

import com.universalsmapi.core.apk.ApkFacts;
import com.universalsmapi.core.axml.ManifestFacts;
import com.universalsmapi.core.compat.CompatResolver;
import com.universalsmapi.core.detect.ApkReport;
import com.universalsmapi.core.detect.AssemblyCheck;
import com.universalsmapi.core.detect.DeviceFacts;
import com.universalsmapi.core.detect.Finding;
import com.universalsmapi.core.detect.InstallationReport;
import com.universalsmapi.core.detect.PackageFacts;
import com.universalsmapi.core.detect.ProbeResult;
import com.universalsmapi.core.dotnet.AssemblyLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Renders reports and the event log as one plain-text document suitable for sharing in a bug report. */
public final class ReportFormatter {
    private ReportFormatter() {
    }

    public static String format(String appVersion, DeviceFacts device, List<InstallationReport> reports, DiagnosticLog log) {
        StringBuilder sb = new StringBuilder();
        sb.append("Universal SMAPI Launcher - detection report\n");
        sb.append("App ").append(appVersion).append("   generated ").append(iso(System.currentTimeMillis())).append('\n');
        sb.append("DETECTION ONLY. Nothing in this report has been tested with SMAPI.\n\n");

        sb.append("== Device ==\n");
        if (device != null) {
            sb.append("Android ").append(device.androidRelease).append(" (API ").append(device.sdkInt).append(")  ")
                    .append(device.manufacturer).append(' ').append(device.model).append('\n');
            sb.append("ABIs: ").append(device.supportedAbis).append("  64-bit: ").append(device.has64Bit() ? "yes" : "no").append('\n');
        } else {
            sb.append("(not provided)\n");
        }
        sb.append('\n');

        if (reports.isEmpty()) {
            sb.append("== Installations ==\nNone detected.\n\n");
        }
        for (InstallationReport r : reports) {
            appendInstallation(sb, r);
        }

        sb.append("== Event log ==\n").append(log.render(true));
        return sb.toString();
    }

    private static void appendInstallation(StringBuilder sb, InstallationReport r) {
        PackageFacts p = r.pkg;
        sb.append("== Installation: ").append(r.title());
        if (p != null) {
            sb.append(" (").append(p.packageName).append(')');
        }
        sb.append(" ==\n");
        if (p != null) {
            sb.append("Version: ").append(p.versionName).append("  code ").append(p.versionCode).append('\n');
            sb.append("Parsed game version: ").append(r.gameVersion == null ? "(not understood)" : r.gameVersion.toString())
                    .append(r.gameVersionSource == null ? "" : "  [" + r.gameVersionSource + "]").append('\n');
            sb.append("Installer: ").append(p.installerPackage).append("  initiating: ").append(p.initiatingPackage).append('\n');
            sb.append("Signers (SHA-256)").append(p.multipleSigners ? " [multiple]" : "").append(": ").append(p.signerSha256).append('\n');
            sb.append("Installed ").append(iso(p.firstInstallTime)).append(", updated ").append(iso(p.lastUpdateTime))
                    .append("; minSdk ").append(p.minSdk).append(" targetSdk ").append(p.targetSdk).append('\n');
            sb.append("sourceDir: ").append(p.sourceDir).append('\n');
            sb.append("splits: ").append(p.splitNames).append('\n');
            sb.append("nativeLibraryDir: ").append(p.nativeLibraryDir).append('\n');
        } else {
            sb.append("Parsed game version: ").append(r.gameVersion == null ? "(not understood)" : r.gameVersion.toString()).append('\n');
        }

        sb.append("\nProbes:\n");
        for (ProbeResult pr : r.probes) {
            sb.append("  [").append(pr.outcome).append("] ").append(pr.id).append(" - ").append(pr.detail).append('\n');
        }

        sb.append("\nAPKs:\n");
        for (ApkReport a : r.apks) {
            sb.append("  ").append(a.role).append(": ").append(a.origin).append('\n');
            if (!a.ok()) {
                sb.append("    NOT READ: ").append(a.error).append('\n');
                continue;
            }
            ApkFacts f = a.facts;
            sb.append("    ").append(bytes(a.size)).append(", ").append(f.entryCount).append(" entries")
                    .append(f.zip64 ? ", ZIP64" : "").append(", ABIs ").append(f.abis())
                    .append(", dex ").append(f.dexFiles).append('\n');
            sb.append("    signing: v1=").append(f.v1Signature).append(" block=")
                    .append(f.signingBlock == null ? "none" : f.signingBlock.schemes()).append('\n');
            if (!f.runtimeMarkers.isEmpty()) {
                sb.append("    runtime: ").append(f.runtimeMarkers).append('\n');
            }
            sb.append("    assets: ").append(f.assetFiles).append(" files, ").append(bytes(f.assetBytes))
                    .append(", top-level ").append(f.assetTopLevel).append('\n');
            if (f.manifest != null) {
                ManifestFacts m = f.manifest;
                sb.append("    manifest: package=").append(m.packageName).append(" versionName=").append(m.versionName)
                        .append(" versionCode=").append(m.versionCode).append(" minSdk=").append(m.minSdk)
                        .append(" targetSdk=").append(m.targetSdk).append(" split=").append(m.splitName)
                        .append(" extractNativeLibs=").append(m.extractNativeLibs)
                        .append(" launcher=").append(m.launcherActivity).append('\n');
                sb.append("    permissions: ").append(m.permissions).append('\n');
            }
            for (String w : f.zipWarnings) {
                sb.append("    zip warning: ").append(w).append('\n');
            }
        }

        sb.append("\nArchitecture: ").append(r.architecture.verdict).append("  installed ABI: ")
                .append(r.architecture.installedAbi).append("  packaged: ").append(r.architecture.abisPackaged).append('\n');

        sb.append("\nAssemblies");
        if (r.assemblies == null) {
            sb.append(": not identified\n");
        } else {
            AssemblyLayout l = r.assemblies;
            sb.append(" (from ").append(r.assembliesFrom).append("): layout ").append(l.kind).append('\n');
            if (!l.looseAssemblyEntries.isEmpty()) {
                sb.append("  loose: ").append(l.looseAssemblyEntries.size()).append(" files; sampled ").append(l.looseSampled)
                        .append(", XALZ ").append(l.looseXalzCompressed).append(", raw PE ").append(l.looseRawPe).append('\n');
            }
            for (AssemblyLayout.StoreFile s : l.stores) {
                sb.append("  store ").append(s.entryName).append(" [").append(s.format).append(", ").append(bytes(s.entrySize))
                        .append(s.storedUncompressed ? "" : ", compressed in APK").append("] header ")
                        .append(s.headerRead ? ("magicOk=" + s.magicOk + " version=" + s.version + " words="
                                + s.wordsText()) : "unread").append(' ').append(s.detail).append('\n');
            }
            sb.append("  manifest: ").append(l.manifestPresent ? (l.manifestNames.size() + " names") : "absent").append('\n');
            for (String n : l.notes) {
                sb.append("  note: ").append(n).append('\n');
            }
            sb.append("  Upstream SMAPI compiles against these assemblies:\n");
            for (AssemblyCheck c : r.assemblyChecks) {
                sb.append("    ").append(c.state).append("  ").append(c.name).append('\n');
            }
        }

        sb.append("\nCompatibility: ").append(r.compat.headline()).append('\n');
        for (CompatResolver.Check c : r.compat.checks) {
            sb.append("  ").append(c.state).append("  ").append(c.label).append(" - ").append(c.detail).append('\n');
        }
        for (String c : r.compat.caveats) {
            sb.append("  caveat: ").append(c).append('\n');
        }

        sb.append("\nFindings:\n");
        if (r.findings.isEmpty()) {
            sb.append("  (none)\n");
        }
        for (Finding f : r.findings) {
            sb.append("  [").append(f.severity).append("] ").append(f.code).append(": ").append(f.message).append('\n');
        }
        sb.append('\n');
    }

    static String bytes(long n) {
        if (n < 0) {
            return "? B";
        }
        if (n < 1024) {
            return n + " B";
        }
        double v = n;
        String[] units = {"KB", "MB", "GB"};
        int i = -1;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        return String.format(Locale.ROOT, "%.1f %s", v, units[i]);
    }

    static String iso(long millis) {
        if (millis <= 0) {
            return "?";
        }
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date(millis));
    }
}
