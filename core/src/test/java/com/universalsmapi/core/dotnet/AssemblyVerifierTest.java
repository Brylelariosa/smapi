package com.universalsmapi.core.dotnet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.universalsmapi.core.diag.DiagnosticLog;
import com.universalsmapi.core.io.ByteArraySource;
import com.universalsmapi.core.io.SeekableSource;
import com.universalsmapi.core.testutil.Bytes;
import com.universalsmapi.core.testutil.MiniZip;
import com.universalsmapi.core.testutil.PeBuilder;
import com.universalsmapi.core.testutil.StoreBuilder;
import com.universalsmapi.core.testutil.SyntheticApk;
import com.universalsmapi.core.zip.ZipArchive;

import org.junit.Test;

public class AssemblyVerifierTest {
    private static final String MANIFEST = "Hash 32     Hash 64             Blob ID  Blob idx  Name\n"
            + "0x00000001  0x0000000000000001  000      0000      Alpha\n"
            + "0x00000002  0x0000000000000002  000      0001      Beta\n"
            + "0x00000003  0x0000000000000003  000      0002      Gamma\n"
            + "0x00000004  0x0000000000000004  001      0000      CoreLib\n";

    private static byte[] apk(byte[] alpha, byte[] beta, byte[] gamma, byte[] coreLib) {
        return new MiniZip()
                .add("assemblies/assemblies.blob", StoreBuilder.v1(0, 4, true, alpha, beta, gamma))
                .add("assemblies/assemblies.arm64_v8a.blob", StoreBuilder.v1(1, 0, false, coreLib))
                .add("assemblies/assemblies.manifest", MANIFEST)
                .build();
    }

    private static AssemblyVerifier.Result run(byte[] apkBytes) throws Exception {
        AssemblyLayout layout;
        try (SeekableSource s = new ByteArraySource(apkBytes)) {
            layout = AssemblyLayoutProbe.probe(ZipArchive.parse(s), new DiagnosticLog());
        }
        return AssemblyVerifier.verify(SyntheticApk.source(apkBytes, "test"), layout, "arm64-v8a", new DiagnosticLog());
    }

    @Test
    public void verifiesCompressedAndPlainManagedImages() throws Exception {
        byte[] image = PeBuilder.managedAnyCpu(5000);
        AssemblyVerifier.Result r = run(apk(StoreBuilder.xalzOf(image), PeBuilder.managedAnyCpu(900),
                StoreBuilder.xalzOf(PeBuilder.pe(true, 0xAA64, true, 3000)), StoreBuilder.xalzOf(image)));
        assertEquals(4, r.items.size());
        assertEquals(4, r.ok);
        assertEquals(0, r.failed);
        for (AssemblyVerifier.Item it : r.items) {
            assertTrue(it.name + ": " + it.reason, it.ok);
            assertTrue(it.managed);
            assertEquals(16, it.sha256.length());
        }
        assertEquals("Alpha", r.items.get(0).name);
        assertEquals(5000, r.items.get(0).expandedSize);
        assertTrue(r.items.get(0).storedSize < 5000);
        assertEquals("arm64", r.items.get(2).machine);
        assertEquals("CoreLib", r.items.get(3).name);
        assertEquals(5000 + 900 + 3000 + 5000, r.expanded);
        assertEquals(r.items.get(0).sha256, r.items.get(3).sha256); // same image, same hash
    }

    @Test
    public void flagsAWrongDeclaredLength() throws Exception {
        byte[] image = PeBuilder.managedAnyCpu(3000);
        AssemblyVerifier.Result r = run(apk(StoreBuilder.xalzOf(image), StoreBuilder.xalzWithDeclared(image, 3010),
                StoreBuilder.xalzOf(image), StoreBuilder.xalzOf(image)));
        assertEquals(1, r.failed);
        AssemblyVerifier.Item bad = r.items.get(1);
        assertFalse(bad.ok);
        assertTrue(bad.reason, bad.reason.contains("header declares 3010"));
    }

    @Test
    public void flagsCorruptCompressedData() throws Exception {
        byte[] corrupt = new Bytes().ascii("XALZ").u32(7).u32(500).u8(0x11).ascii("a").u16(0).toByteArray();
        byte[] good = StoreBuilder.xalzOf(PeBuilder.managedAnyCpu(500));
        AssemblyVerifier.Result r = run(apk(good, corrupt, good, good));
        assertFalse(r.items.get(1).ok);
        assertTrue(r.items.get(1).reason, r.items.get(1).reason.contains("LZ4"));
        assertEquals(3, r.ok);
    }

    @Test
    public void flagsImagesThatAreNotManagedPe() throws Exception {
        byte[] good = StoreBuilder.xalzOf(PeBuilder.managedAnyCpu(500));
        byte[] notPe = StoreBuilder.xalzOf(new byte[] {'M', 'Z', 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49,
                50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 65, 66});
        byte[] nativePe = StoreBuilder.xalzOf(PeBuilder.pe(true, 0x8664, false, 500));
        AssemblyVerifier.Result r = run(apk(good, notPe, nativePe, good));
        assertTrue(r.items.get(1).reason, r.items.get(1).reason.contains("not a PE image"));
        assertTrue(r.items.get(2).reason, r.items.get(2).reason.contains("no CLR header"));
        assertEquals(2, r.ok);
        assertEquals(2, r.failed);
    }

    @Test
    public void reportsAStoreThatIsMissingFromTheApkItWasReadFrom() throws Exception {
        byte[] good = StoreBuilder.xalzOf(PeBuilder.managedAnyCpu(500));
        byte[] full = apk(good, good, good, good);
        AssemblyLayout layout;
        try (SeekableSource s = new ByteArraySource(full)) {
            layout = AssemblyLayoutProbe.probe(ZipArchive.parse(s), new DiagnosticLog());
        }
        byte[] other = new MiniZip().add("readme.txt", "no stores here").build();
        AssemblyVerifier.Result r = AssemblyVerifier.verify(SyntheticApk.source(other, "other"), layout, "arm64-v8a", new DiagnosticLog());
        assertEquals(0, r.ok);
        assertTrue(r.items.get(0).reason.contains("store entry missing"));
    }
}
