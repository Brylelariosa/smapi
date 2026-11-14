package com.universalsmapi.core.diag;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.universalsmapi.core.compat.CompatDatabase;
import com.universalsmapi.core.detect.DetectionPipeline;
import com.universalsmapi.core.detect.DetectionPipeline.ApkInput;
import com.universalsmapi.core.detect.InstallationReport;
import com.universalsmapi.core.detect.PackageFacts;
import com.universalsmapi.core.detect.ProbeResult;
import com.universalsmapi.core.testutil.SyntheticApk;
import com.universalsmapi.core.testutil.SyntheticApk.Layout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ReportFormatterTest {
    private static PackageFacts pkg() {
        PackageFacts.Builder b = new PackageFacts.Builder();
        b.packageName = "com.chucklefish.stardewvalley";
        b.label = "Stardew Valley";
        b.versionName = "1.6.15.1";
        b.versionCode = 321;
        b.installerPackage = "com.android.vending";
        b.signerSha256.add("ab12cd34");
        b.nativeLibraryDir = "/data/app/x/lib/arm64";
        return b.build();
    }

    private static com.universalsmapi.core.detect.DeviceFacts device() {
        return new com.universalsmapi.core.detect.DeviceFacts(30, "11", "Google", "Pixel 5",
                Arrays.asList("arm64-v8a"), Arrays.asList("arm64-v8a"), new ArrayList<String>());
    }

    @Test
    public void reportIsPlainTextAndNeverClaimsCompatibility() throws Exception {
        DiagnosticLog log = new DiagnosticLog();
        List<ApkInput> inputs = new ArrayList<>();
        inputs.add(new ApkInput("base", SyntheticApk.source(SyntheticApk.stardewLike(Layout.BLOB_WITH_MANIFEST, "1.6.15.1", true, true), "path:/x/base.apk")));
        InstallationReport r = new DetectionPipeline(CompatDatabase.loadBundled()).inspect(pkg(), device(), inputs,
                Collections.singletonList(ProbeResult.ok("package-visibility", "visible")), log);
        String text = ReportFormatter.format("0.1.0-test", device(), Collections.singletonList(r), log);

        assertTrue(text.contains("DETECTION ONLY"));
        assertTrue(text.contains("== Installation: Stardew Valley (com.chucklefish.stardewvalley) =="));
        assertTrue(text.contains("layout STORE_BLOB"));
        assertTrue(text.contains("Upstream SMAPI compiles against these assemblies"));
        assertTrue(text.contains("ABSENT  GalaxyCSharp"));
        assertTrue(text.contains("PRESENT  Stardew Valley"));
        assertTrue(text.contains("No Android SMAPI build catalogued"));
        assertTrue(text.contains("== Event log =="));
        String lower = text.toLowerCase();
        assertFalse(lower.contains("is compatible"));
        assertFalse(lower.contains("fully compatible"));
        assertFalse(lower.contains("works with"));
    }

    @Test
    public void failureEvidenceAppearsVerbatim() throws Exception {
        DiagnosticLog log = new DiagnosticLog();
        List<ApkInput> inputs = new ArrayList<>();
        inputs.add(new ApkInput("base", SyntheticApk.denied("path:/x/base.apk", "open failed: EACCES (Permission denied)")));
        InstallationReport r = new DetectionPipeline(CompatDatabase.loadBundled()).inspect(pkg(), device(), inputs,
                new ArrayList<ProbeResult>(), log);
        String text = ReportFormatter.format("0.1.0-test", device(), Collections.singletonList(r), log);
        assertTrue(text.contains("EACCES (Permission denied)"));
        assertTrue(text.contains("BASE_APK_UNREADABLE"));
        assertTrue(text.contains("NOT READ:"));
        assertTrue(text.contains("Assemblies: not identified"));
    }

    @Test
    public void emptyResultSaysNoneDetected() {
        String text = ReportFormatter.format("0.1.0-test", device(), new ArrayList<InstallationReport>(), new DiagnosticLog());
        assertTrue(text.contains("None detected."));
    }

    @Test
    public void reportListsDecodedAssembliesAndTheFourPartVersion() throws Exception {
        DiagnosticLog log = new DiagnosticLog();
        PackageFacts.Builder b = new PackageFacts.Builder();
        b.packageName = "com.chucklefish.stardewvalley";
        b.label = "Stardew Valley";
        b.versionName = "1.6.15.0";
        b.versionCode = 239;
        b.installerPackage = "com.google.android.packageinstaller";
        b.nativeLibraryDir = "/data/app/x/lib/arm64";
        List<ApkInput> inputs = new ArrayList<>();
        inputs.add(new ApkInput("base", SyntheticApk.source(SyntheticApk.deviceLike("1.6.15.0", false), "path:/x/base.apk")));
        InstallationReport r = new DetectionPipeline(CompatDatabase.loadBundled()).inspect(b.build(), device(), inputs,
                new ArrayList<ProbeResult>(), log);
        String text = ReportFormatter.format("0.2.0-test", device(), Collections.singletonList(r), log);
        assertTrue(text.contains("Parsed game version: 1.6.15.0"));
        assertTrue(text.contains("Assemblies the runtime maps (shared store + the arm64-v8a store): 4 (2 LZ4-compressed, 2 plain)"));
        assertTrue(text.contains("Stardew Valley"));
        assertTrue(text.contains("xalz -> 8.8 KB"));
        assertTrue(text.contains("store 1 #0"));
        assertTrue(text.contains("lib/arm64-v8a: 4 files"));
        assertTrue(text.contains("lib/arm64-v8a files: libmonodroid.so"));
        assertTrue(text.contains("activities: [crc64f0.MainActivity]"));
        assertTrue(text.contains("Android release documented but untested: SMAPI 4.1.10.3"));
        assertFalse(text.toLowerCase().contains("is compatible"));
    }
}
