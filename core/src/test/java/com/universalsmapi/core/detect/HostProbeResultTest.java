package com.universalsmapi.core.detect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.universalsmapi.core.dotnet.AssemblyVerifier;
import com.universalsmapi.core.diag.DiagnosticLog;
import com.universalsmapi.core.dotnet.AssemblyLayout;
import com.universalsmapi.core.dotnet.AssemblyLayoutProbe;
import com.universalsmapi.core.io.ByteArraySource;
import com.universalsmapi.core.io.SeekableSource;
import com.universalsmapi.core.testutil.MiniZip;
import com.universalsmapi.core.testutil.PeBuilder;
import com.universalsmapi.core.testutil.StoreBuilder;
import com.universalsmapi.core.testutil.SyntheticApk;
import com.universalsmapi.core.zip.ZipArchive;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class HostProbeResultTest {
    @Test
    public void summarisesExtractionAndNativeLoadOutcomes() throws Exception {
        byte[] good = StoreBuilder.xalzOf(PeBuilder.managedAnyCpu(800));
        byte[] apk = new MiniZip()
                .add("assemblies/assemblies.blob", StoreBuilder.v1(0, 1, true, good))
                .add("assemblies/assemblies.manifest", "0x1 0x2 000 0000 StardewValley\n")
                .build();
        AssemblyLayout layout;
        try (SeekableSource s = new ByteArraySource(apk)) {
            layout = AssemblyLayoutProbe.probe(ZipArchive.parse(s), new DiagnosticLog());
        }
        AssemblyVerifier.Result r = AssemblyVerifier.verify(SyntheticApk.source(apk, "t"), layout, "arm64-v8a", new DiagnosticLog());
        List<ProbeResult> natives = new ArrayList<>();
        natives.add(ProbeResult.info("native-read:libmonosgen-2.0.so", "exists=true length=100 canRead=true elfMagic=true"));
        natives.add(ProbeResult.ok("native-load:libmonosgen-2.0.so", "System.load succeeded"));
        natives.add(ProbeResult.failed("native-load:libxamarin-app.so", "UnsatisfiedLinkError: dlopen failed: not accessible for the namespace"));

        HostProbeResult h = new HostProbeResult(r, null, natives);
        assertEquals("Assemblies: 1 of 1 decompressed and verified in " + r.millis + " ms. Native libraries loaded from the game's folder: 1 of 2.", h.headline());
        String text = h.toText();
        assertTrue(text.contains("== Host probes"));
        assertTrue(text.contains("1 of 1 assemblies decompressed in memory and verified as managed PE images"));
        assertTrue(text.contains("ok    StardewValley"));
        assertTrue(text.contains("i386/AnyCPU"));
        assertTrue(text.contains("[FAILED] native-load:libxamarin-app.so - UnsatisfiedLinkError"));

        HostProbeResult none = new HostProbeResult(null, "IOException: nope", new ArrayList<ProbeResult>());
        assertTrue(none.headline().contains("check did not run (IOException: nope)"));
        assertTrue(none.toText().contains("did not run: IOException: nope"));
    }
}
