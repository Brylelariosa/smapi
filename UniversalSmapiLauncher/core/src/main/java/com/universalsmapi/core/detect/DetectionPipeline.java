package com.universalsmapi.core.detect;

import com.universalsmapi.core.apk.ApkFacts;
import com.universalsmapi.core.apk.ApkInspector;
import com.universalsmapi.core.apk.ApkSource;
import com.universalsmapi.core.compat.CompatDatabase;
import com.universalsmapi.core.compat.CompatResolver;
import com.universalsmapi.core.diag.DiagnosticLog;
import com.universalsmapi.core.dotnet.AssemblyLayout;
import com.universalsmapi.core.io.SeekableSource;
import com.universalsmapi.core.version.GameVersion;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Turns "what the OS said about a package" plus "what its APKs contain" into an {@link InstallationReport}.
 *
 * <p>The pipeline is platform-neutral: all Android-specific access happens in the caller, which hands over
 * {@link PackageFacts}, {@link ApkSource}s and the results of its own access probes. Every failure to read an APK is
 * recorded as a probe result with the exact exception text instead of being assumed away.
 */
public final class DetectionPipeline {
    private static final String TAG = "pipeline";

    /** One APK to inspect, tagged with its role: "base", "split:&lt;name&gt;" or "picked". */
    public static final class ApkInput {
        public final String role;
        public final ApkSource source;

        public ApkInput(String role, ApkSource source) {
            this.role = role;
            this.source = source;
        }
    }

    private final CompatDatabase db;
    private final CompatResolver resolver;

    public DetectionPipeline(CompatDatabase db) {
        this.db = db;
        this.resolver = new CompatResolver(db);
    }

    public InstallationReport inspect(PackageFacts pkg, DeviceFacts device, List<ApkInput> inputs,
                                      List<ProbeResult> platformProbes, DiagnosticLog log) {
        InstallationReport rep = new InstallationReport(pkg, System.currentTimeMillis());
        rep.probes.addAll(platformProbes);
        log.info(TAG, "inspecting " + (pkg == null ? "picked APK" : pkg.packageName) + " with " + inputs.size() + " APK(s)");

        for (ApkInput in : inputs) {
            rep.apks.add(inspectOne(in, rep.probes, log));
        }
        resolveVersion(rep, pkg);
        rep.architecture = ArchitectureAssessment.assess(pkg, device, rep.apks);
        chooseAssemblies(rep);
        boolean device64 = device != null && device.has64Bit();
        rep.compat = resolver.resolve(rep.gameVersion, device64, pkg == null ? null : pkg.installerPackage);
        buildFindings(rep, pkg);
        log.info(TAG, "done: version=" + rep.gameVersion + " arch=" + rep.architecture.verdict + " compat="
                + rep.compat.status + " findings=" + rep.findings.size());
        return rep;
    }

    private ApkReport inspectOne(ApkInput in, List<ProbeResult> probes, DiagnosticLog log) {
        ApkReport r = new ApkReport(in.role, in.source.describe());
        SeekableSource src;
        try {
            src = in.source.open();
        } catch (IOException | RuntimeException e) {
            r.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            probes.add(ProbeResult.failed("apk-open:" + in.role, r.origin + " -> " + r.error));
            log.error(TAG, "cannot open " + in.role + " (" + r.origin + ")", e);
            return r;
        }
        try {
            r.size = src.size();
            probes.add(ProbeResult.ok("apk-open:" + in.role, r.origin + " (" + r.size + " bytes)"));
            r.facts = ApkInspector.inspect(src, in.role, log);
            probes.add(ProbeResult.ok("apk-parse:" + in.role, r.facts.entryCount + " ZIP entries read"));
        } catch (IOException | RuntimeException e) {
            r.error = e.getClass().getSimpleName() + ": " + e.getMessage();
            probes.add(ProbeResult.failed("apk-parse:" + in.role, r.error));
            log.error(TAG, "cannot parse " + in.role + " (" + r.origin + ")", e);
        } finally {
            try {
                src.close();
            } catch (IOException ignored) {
                // closing a read-only handle; nothing to recover
            }
        }
        return r;
    }

    private void resolveVersion(InstallationReport rep, PackageFacts pkg) {
        String fromManifestText = null;
        for (ApkReport r : rep.apks) {
            if (r.facts != null && r.facts.manifest != null && r.facts.manifest.splitName == null) {
                fromManifestText = r.facts.manifest.versionName;
                break;
            }
        }
        GameVersion fromPkg = GameVersion.parse(pkg == null ? null : pkg.versionName);
        GameVersion fromManifest = GameVersion.parse(fromManifestText);
        if (fromPkg != null) {
            rep.gameVersion = fromPkg;
            rep.gameVersionSource = "PackageManager versionName";
        } else if (fromManifest != null) {
            rep.gameVersion = fromManifest;
            rep.gameVersionSource = "AndroidManifest.xml versionName";
        }
        if (fromPkg != null && fromManifest != null && fromPkg.version.compareTo(fromManifest.version) != 0) {
            rep.findings.add(new Finding(Finding.Severity.WARN, "VERSION_MISMATCH",
                    "PackageManager says " + fromPkg + " but the APK manifest says " + fromManifest
                            + ". The APK that was read may not be the one that is installed."));
        }
    }

    private void chooseAssemblies(InstallationReport rep) {
        ApkReport chosen = null;
        for (ApkReport r : rep.apks) {
            if (r.facts != null && r.facts.assemblies != null && r.facts.assemblies.kind != AssemblyLayout.Kind.NONE) {
                chosen = r;
                break;
            }
        }
        if (chosen == null) {
            for (ApkReport r : rep.apks) {
                if (r.facts != null && r.facts.assemblies != null) {
                    chosen = r;
                    break;
                }
            }
        }
        if (chosen == null) {
            return;
        }
        AssemblyLayout layout = chosen.facts.assemblies;
        rep.assemblies = layout;
        rep.assembliesFrom = chosen.role;
        for (String name : db.upstreamReferencedAssemblies) {
            boolean present = isGameAssemblyName(name) ? anyGameAssembly(layout) : layout.hasAssembly(name);
            AssemblyCheck.State state = present ? AssemblyCheck.State.PRESENT
                    : (layout.canListNames() ? AssemblyCheck.State.ABSENT : AssemblyCheck.State.UNKNOWN);
            rep.assemblyChecks.add(new AssemblyCheck(name, state));
        }
    }

    private boolean isGameAssemblyName(String name) {
        for (String alias : db.upstreamGameAssemblyAliases) {
            if (alias.equalsIgnoreCase(name) && alias.toLowerCase(Locale.ROOT).startsWith("stardew")) {
                return true;
            }
        }
        return false;
    }

    private boolean anyGameAssembly(AssemblyLayout layout) {
        for (String alias : db.upstreamGameAssemblyAliases) {
            if (alias.toLowerCase(Locale.ROOT).startsWith("stardew") && layout.hasAssembly(alias)) {
                return true;
            }
        }
        return false;
    }

    private void buildFindings(InstallationReport rep, PackageFacts pkg) {
        for (ApkReport r : rep.apks) {
            boolean primary = r.role.equals("base") || r.role.equals("picked");
            if (!r.ok()) {
                rep.findings.add(new Finding(primary ? Finding.Severity.ERROR : Finding.Severity.WARN,
                        primary ? "BASE_APK_UNREADABLE" : "SPLIT_APK_UNREADABLE",
                        r.role + " APK could not be read: " + r.error + (primary
                                ? ". Reading another app's APK is an Android implementation detail, not a guaranteed API;"
                                + " pick the APK manually as a fallback." : "")));
                continue;
            }
            ApkFacts f = r.facts;
            if (f.bundleContainer) {
                rep.findings.add(new Finding(Finding.Severity.WARN, "BUNDLE_CONTAINER",
                        "This file is a bundle (" + f.nestedApks.size() + " nested .apk entries). Pick the base APK itself."));
            } else if (!f.hasManifest) {
                rep.findings.add(new Finding(Finding.Severity.WARN, "NO_MANIFEST", r.role + " has no AndroidManifest.xml."));
            }
            if (f.manifestError != null) {
                rep.findings.add(new Finding(Finding.Severity.WARN, "MANIFEST_UNPARSED",
                        r.role + " manifest could not be parsed: " + f.manifestError));
            }
        }

        if (rep.assemblies == null) {
            rep.findings.add(new Finding(Finding.Severity.WARN, "ASSEMBLIES_UNKNOWN",
                    "No APK could be read, so managed assemblies were not identified."));
        } else {
            AssemblyLayout l = rep.assemblies;
            if (l.kind == AssemblyLayout.Kind.NONE) {
                rep.findings.add(new Finding(Finding.Severity.WARN, "ASSEMBLIES_NOT_FOUND",
                        "No managed assemblies found in the APKs that were read."));
            } else if (!l.canListNames()) {
                rep.findings.add(new Finding(Finding.Severity.INFO, "ASSEMBLY_NAMES_NOT_LISTABLE",
                        "Assembly names are not readable from this layout; individual assemblies are reported as unknown."));
            }
            for (AssemblyLayout.StoreFile s : l.stores) {
                if (!s.headerRead) {
                    rep.findings.add(new Finding(Finding.Severity.WARN, "STORE_HEADER_UNREAD",
                            s.entryName + ": header not read (" + s.detail + ")"));
                } else if (!s.magicOk) {
                    rep.findings.add(new Finding(Finding.Severity.WARN, "STORE_MAGIC_MISMATCH",
                            s.entryName + ": does not start with the XABA assembly-store magic."));
                } else {
                    rep.findings.add(new Finding(Finding.Severity.INFO, "STORE_FORMAT",
                            s.entryName + ": assembly store version " + s.version + ", header words "
                                    + s.wordsText()));
                }
            }
            if (l.looseXalzCompressed > 0) {
                rep.findings.add(new Finding(Finding.Severity.INFO, "ASSEMBLIES_LZ4_COMPRESSED",
                        l.looseXalzCompressed + " of " + l.looseSampled + " sampled loose assemblies use XALZ (LZ4) compression."));
            }
        }

        ArchitectureAssessment arch = rep.architecture;
        if (arch.verdict == ArchitectureAssessment.Verdict.ARM32 || arch.verdict == ArchitectureAssessment.Verdict.X86
                || arch.verdict == ArchitectureAssessment.Verdict.UNKNOWN) {
            rep.findings.add(new Finding(Finding.Severity.WARN, "ARCH_NOT_ARM64",
                    "The game does not appear to run as arm64-v8a (" + arch.verdict + ")."));
        }
        for (String note : arch.notes) {
            rep.findings.add(new Finding(Finding.Severity.INFO, "ARCH_NOTE", note));
        }

        if (pkg != null) {
            if (pkg.installerPackage == null) {
                rep.findings.add(new Finding(Finding.Severity.INFO, "INSTALLER_UNKNOWN",
                        "The system did not report an installing store (sideloaded, or hidden from this app)."));
            }
            if (pkg.multipleSigners) {
                rep.findings.add(new Finding(Finding.Severity.INFO, "MULTIPLE_SIGNERS", "The package has more than one signer."));
            }
        }

        if (rep.compat.status == CompatResolver.Status.GAME_VERSION_UNKNOWN) {
            rep.findings.add(new Finding(Finding.Severity.WARN, "GAME_VERSION_UNKNOWN", "Game version could not be determined."));
        } else if (rep.compat.status == CompatResolver.Status.NO_ANDROID_BUILD_CATALOGUED) {
            rep.findings.add(new Finding(Finding.Severity.INFO, "NO_ANDROID_BUILD",
                    "No Android SMAPI build is catalogued for " + rep.gameVersion + "; nothing is claimed as compatible."));
        }
        for (CompatResolver.Check c : rep.compat.checks) {
            if (c.state == CompatResolver.CheckState.NOT_MET) {
                rep.findings.add(new Finding(Finding.Severity.WARN, "REQUIREMENT_NOT_MET", c.label + " (" + c.detail + ")"));
            }
        }
    }
}
