package com.universalsmapi.core.detect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.universalsmapi.core.compat.CompatDatabase;
import com.universalsmapi.core.compat.CompatResolver;
import com.universalsmapi.core.detect.DetectionPipeline.ApkInput;
import com.universalsmapi.core.diag.DiagnosticLog;
import com.universalsmapi.core.dotnet.AssemblyLayout;
import com.universalsmapi.core.testutil.MiniZip;
import com.universalsmapi.core.testutil.SyntheticApk;
import com.universalsmapi.core.testutil.SyntheticApk.Layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class DetectionPipelineTest {
    static DeviceFacts device64() {
        return new DeviceFacts(30, "11", "Google", "Pixel 5", Arrays.asList("arm64-v8a", "armeabi-v7a", "armeabi"),
                Arrays.asList("arm64-v8a"), Arrays.asList("armeabi-v7a", "armeabi"));
    }

    static DeviceFacts device32() {
        return new DeviceFacts(30, "11", "Acme", "Old", Arrays.asList("armeabi-v7a", "armeabi"),
                new ArrayList<String>(), Arrays.asList("armeabi-v7a", "armeabi"));
    }

    static PackageFacts.Builder stardew() {
        PackageFacts.Builder b = new PackageFacts.Builder();
        b.packageName = "com.chucklefish.stardewvalley";
        b.label = "Stardew Valley";
        b.versionName = "1.6.15.1";
        b.versionCode = 321;
        b.installerPackage = "com.android.vending";
        b.signerSha256.add("ab12cd34");
        b.sourceDir = "/data/app/~~x/com.chucklefish.stardewvalley-y/base.apk";
        b.nativeLibraryDir = "/data/app/~~x/com.chucklefish.stardewvalley-y/lib/arm64";
        b.minSdk = 23;
        b.targetSdk = 34;
        return b;
    }

    private static List<ApkInput> base(byte[] apk) {
        return new ArrayList<>(Collections.singletonList(new ApkInput("base", SyntheticApk.source(apk, "path:/data/app/x/base.apk"))));
    }

    private static InstallationReport run(PackageFacts pkg, DeviceFacts dev, List<ApkInput> inputs, List<ProbeResult> probes) throws Exception {
        return new DetectionPipeline(CompatDatabase.loadBundled()).inspect(pkg, dev, inputs, probes, new DiagnosticLog());
    }

    private static AssemblyCheck.State check(InstallationReport r, String name) {
        for (AssemblyCheck c : r.assemblyChecks) {
            if (c.name.equals(name)) {
                return c.state;
            }
        }
        throw new AssertionError("no assembly check for " + name);
    }

    private static Finding finding(InstallationReport r, String code) {
        for (Finding f : r.findings) {
            if (f.code.equals(code)) {
                return f;
            }
        }
        return null;
    }

    @Test
    public void fullHappyPathOnAnArm64Device() throws Exception {
        byte[] apk = SyntheticApk.stardewLike(Layout.BLOB_WITH_MANIFEST, "1.6.15.1", true, true);
        InstallationReport r = run(stardew().build(), device64(), base(apk),
                Collections.singletonList(ProbeResult.ok("package-visibility", "visible")));

        assertEquals("1.6.15.1", r.gameVersion.toString());
        assertTrue(r.gameVersionSource.contains("PackageManager"));
        assertEquals(1, r.apks.size());
        assertTrue(r.apks.get(0).ok());
        assertEquals(ArchitectureAssessment.Verdict.ARM64, r.architecture.verdict);
        assertEquals("arm64-v8a", r.architecture.installedAbi);
        assertTrue(r.architecture.abisPackaged.contains("armeabi-v7a"));
        assertEquals(AssemblyLayout.Kind.STORE_BLOB, r.assemblies.kind);
        assertEquals("base", r.assembliesFrom);

        assertEquals(AssemblyCheck.State.PRESENT, check(r, "Stardew Valley"));
        assertEquals(AssemblyCheck.State.PRESENT, check(r, "StardewValley.GameData"));
        assertEquals(AssemblyCheck.State.PRESENT, check(r, "MonoGame.Framework"));
        assertEquals(AssemblyCheck.State.PRESENT, check(r, "xTile"));
        assertEquals(AssemblyCheck.State.ABSENT, check(r, "GalaxyCSharp"));
        assertEquals(AssemblyCheck.State.ABSENT, check(r, "SkiaSharp"));

        assertEquals(CompatResolver.Status.NO_ANDROID_BUILD_CATALOGUED, r.compat.status);
        assertNotNull(finding(r, "NO_ANDROID_BUILD"));
        assertNotNull(finding(r, "STORE_FORMAT"));
        assertNull(finding(r, "VERSION_MISMATCH"));
        for (Finding f : r.findings) {
            assertFalse("unexpected error finding " + f.code, f.severity == Finding.Severity.ERROR);
        }
        assertEquals(3, r.probes.size());
        assertEquals(ProbeResult.Outcome.OK, r.probes.get(1).outcome);
    }

    @Test
    public void deniedBaseApkIsRecordedWithTheExactOsMessageAndTheRestStillWorks() throws Exception {
        List<ApkInput> inputs = new ArrayList<>();
        inputs.add(new ApkInput("base", SyntheticApk.denied("path:/data/app/x/base.apk", "/data/app/x/base.apk: open failed: EACCES (Permission denied)")));
        InstallationReport r = run(stardew().build(), device64(), inputs, new ArrayList<ProbeResult>());

        assertFalse(r.apks.get(0).ok());
        assertTrue(r.apks.get(0).error.contains("EACCES"));
        assertEquals(ProbeResult.Outcome.FAILED, r.probes.get(0).outcome);
        assertEquals("apk-open:base", r.probes.get(0).id);
        assertTrue(r.probes.get(0).detail.contains("EACCES (Permission denied)"));
        Finding f = finding(r, "BASE_APK_UNREADABLE");
        assertNotNull(f);
        assertEquals(Finding.Severity.ERROR, f.severity);
        assertTrue(f.message.contains("pick the APK manually"));
        assertNull(r.assemblies);
        assertTrue(r.assemblyChecks.isEmpty());
        assertNotNull(finding(r, "ASSEMBLIES_UNKNOWN"));
        assertEquals(ArchitectureAssessment.Verdict.ARM64, r.architecture.verdict);
        assertEquals("1.6.15.1", r.gameVersion.toString());
    }

    @Test
    public void assembliesInASplitApkAreFound() throws Exception {
        List<ApkInput> inputs = base(SyntheticApk.stardewLike(Layout.NONE, "1.6.15.1", false, false));
        inputs.add(new ApkInput("split:config.shared", SyntheticApk.source(
                SyntheticApk.stardewLike(Layout.BLOB_WITH_MANIFEST, "1.6.15.1", false, false), "path:/data/app/x/split_config.shared.apk")));
        InstallationReport r = run(stardew().build(), device64(), inputs, new ArrayList<ProbeResult>());
        assertEquals("split:config.shared", r.assembliesFrom);
        assertEquals(AssemblyCheck.State.PRESENT, check(r, "Stardew Valley"));
    }

    @Test
    public void versionMismatchBetweenPackageManagerAndApkIsFlagged() throws Exception {
        byte[] apk = SyntheticApk.stardewLike(Layout.BLOB_WITH_MANIFEST, "1.6.15.0", false, false);
        InstallationReport r = run(stardew().build(), device64(), base(apk), new ArrayList<ProbeResult>());
        Finding f = finding(r, "VERSION_MISMATCH");
        assertNotNull(f);
        assertEquals(Finding.Severity.WARN, f.severity);
        assertEquals("1.6.15.1", r.gameVersion.toString());
    }

    @Test
    public void pickedApkWithoutPackageInfoUsesItsManifest() throws Exception {
        byte[] apk = SyntheticApk.stardewLike(Layout.BLOB_WITH_MANIFEST, "1.6.15.1", true, true);
        List<ApkInput> inputs = new ArrayList<>();
        inputs.add(new ApkInput("picked", SyntheticApk.source(apk, "saf:content://x/y")));
        InstallationReport r = run(null, device64(), inputs, new ArrayList<ProbeResult>());
        assertEquals("Picked APK", r.title());
        assertEquals("1.6.15.1", r.gameVersion.toString());
        assertTrue(r.gameVersionSource.contains("AndroidManifest"));
        assertEquals(ArchitectureAssessment.Verdict.ARM64_PACKAGED, r.architecture.verdict);
        assertEquals(CompatResolver.CheckState.UNKNOWN, r.compat.checks.get(2).state);
    }

    @Test
    public void bundleFilesAreRecognisedAsContainers() throws Exception {
        byte[] bundle = new MiniZip().add("base.apk", new byte[] {1}).add("split_config.arm64_v8a.apk", new byte[] {2}).add("info.json", "{}").build();
        List<ApkInput> inputs = new ArrayList<>();
        inputs.add(new ApkInput("picked", SyntheticApk.source(bundle, "saf:content://bundle")));
        InstallationReport r = run(null, device64(), inputs, new ArrayList<ProbeResult>());
        assertTrue(r.apks.get(0).facts.bundleContainer);
        assertEquals(2, r.apks.get(0).facts.nestedApks.size());
        assertNotNull(finding(r, "BUNDLE_CONTAINER"));
        assertNull(finding(r, "NO_MANIFEST"));
    }

    @Test
    public void thirtyTwoBitInstallOnA64BitDeviceIsCalledOut() throws Exception {
        PackageFacts.Builder b = stardew();
        b.nativeLibraryDir = "/data/app/~~x/pkg-y/lib/arm";
        byte[] apk = SyntheticApk.stardewLike(Layout.BLOB_WITH_MANIFEST, "1.6.15.1", true, false);
        InstallationReport r = run(b.build(), device64(), base(apk), new ArrayList<ProbeResult>());
        assertEquals(ArchitectureAssessment.Verdict.ARM32, r.architecture.verdict);
        assertNotNull(finding(r, "ARCH_NOT_ARM64"));
        assertNotNull(finding(r, "ARCH_NOTE"));
    }

    @Test
    public void thirtyTwoBitDeviceFailsTheDocumented64BitRequirement() throws Exception {
        byte[] apk = SyntheticApk.stardewLike(Layout.BLOB_WITH_MANIFEST, "1.6.15.1", true, false);
        InstallationReport r = run(stardew().build(), device32(), base(apk), new ArrayList<ProbeResult>());
        Finding f = finding(r, "REQUIREMENT_NOT_MET");
        assertNotNull(f);
        assertTrue(f.message.contains("64-bit"));
    }

    @Test
    public void nothingReadableAndNoVersionStillProducesAnHonestReport() throws Exception {
        PackageFacts.Builder b = stardew();
        b.versionName = null;
        b.nativeLibraryDir = null;
        InstallationReport r = run(b.build(), device64(), new ArrayList<ApkInput>(), new ArrayList<ProbeResult>());
        assertNull(r.gameVersion);
        assertEquals(CompatResolver.Status.GAME_VERSION_UNKNOWN, r.compat.status);
        assertEquals(ArchitectureAssessment.Verdict.UNKNOWN, r.architecture.verdict);
        assertNotNull(finding(r, "GAME_VERSION_UNKNOWN"));
        assertNotNull(finding(r, "ARCH_NOT_ARM64"));
    }

    @Test
    public void splitNamesAloneRevealPackagedAbis() throws Exception {
        PackageFacts.Builder b = stardew();
        b.nativeLibraryDir = null;
        b.splitNames.add("config.arm64_v8a");
        b.splitNames.add("config.xxhdpi");
        InstallationReport r = run(b.build(), device64(), new ArrayList<ApkInput>(), new ArrayList<ProbeResult>());
        assertEquals(Arrays.asList("arm64-v8a"), r.architecture.abisPackaged);
        assertEquals(ArchitectureAssessment.Verdict.ARM64_PACKAGED, r.architecture.verdict);
    }

    @Test
    public void garbageApkProducesAParseFailureNotACrash() throws Exception {
        InstallationReport r = run(stardew().build(), device64(), base(new byte[100]), new ArrayList<ProbeResult>());
        assertFalse(r.apks.get(0).ok());
        assertEquals("apk-parse:base", r.probes.get(1).id);
        assertEquals(ProbeResult.Outcome.FAILED, r.probes.get(1).outcome);
        assertNotNull(finding(r, "BASE_APK_UNREADABLE"));
    }
}
