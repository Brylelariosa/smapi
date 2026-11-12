package com.universalsmapi.core.dotnet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.universalsmapi.core.diag.DiagnosticLog;
import com.universalsmapi.core.io.ByteArraySource;
import com.universalsmapi.core.io.SeekableSource;
import com.universalsmapi.core.testutil.Bytes;
import com.universalsmapi.core.testutil.ElfBuilder;
import com.universalsmapi.core.testutil.MiniZip;
import com.universalsmapi.core.testutil.SyntheticApk;
import com.universalsmapi.core.testutil.SyntheticApk.Layout;
import com.universalsmapi.core.zip.ZipArchive;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

public class AssemblyLayoutProbeTest {
    private static AssemblyLayout probe(byte[] apk) throws Exception {
        try (SeekableSource s = new ByteArraySource(apk)) {
            return AssemblyLayoutProbe.probe(ZipArchive.parse(s), new DiagnosticLog());
        }
    }

    @Test
    public void storeBlobsWithManifestExposeAssemblyNames() throws Exception {
        AssemblyLayout l = probe(SyntheticApk.stardewLike(Layout.BLOB_WITH_MANIFEST, "1.6.15.1", true, true));
        assertEquals(AssemblyLayout.Kind.STORE_BLOB, l.kind);
        assertEquals(2, l.stores.size());
        for (AssemblyLayout.StoreFile s : l.stores) {
            assertTrue(s.headerRead);
            assertTrue(s.magicOk);
            assertEquals(2, s.version);
            assertEquals("raw-blob", s.format);
        }
        assertTrue(l.manifestPresent);
        assertEquals(7, l.manifestNames.size());
        assertTrue(l.canListNames());
        assertTrue(l.hasAssembly("Stardew Valley"));
        assertTrue(l.hasAssembly("stardewvalley.gamedata"));
        assertTrue(l.hasAssembly("xTile"));
        assertFalse(l.hasAssembly("GalaxyCSharp"));
    }

    @Test
    public void storeBlobsWithoutManifestCannotAnswerPresenceQuestions() throws Exception {
        AssemblyLayout l = probe(SyntheticApk.stardewLike(Layout.BLOB_NO_MANIFEST, "1.6.15.1", true, true));
        assertEquals(AssemblyLayout.Kind.STORE_BLOB, l.kind);
        assertFalse(l.canListNames());
        assertFalse(l.hasAssembly("Stardew Valley"));
        assertFalse(l.notes.isEmpty());
        assertTrue(l.notes.get(0).contains("unknown"));
    }

    @Test
    public void looseAssembliesAreSampledForXalzCompression() throws Exception {
        AssemblyLayout l = probe(SyntheticApk.stardewLike(Layout.LOOSE, "1.6.15.1", true, true));
        assertEquals(AssemblyLayout.Kind.LOOSE_ASSEMBLIES, l.kind);
        assertEquals(3, l.looseAssemblyEntries.size());
        assertEquals(3, l.looseSampled);
        assertEquals(1, l.looseXalzCompressed);
        assertEquals(2, l.looseRawPe);
        assertTrue(l.hasAssembly("Stardew Valley"));
        assertTrue(l.canListNames());
    }

    @Test
    public void readsTheStoreHeaderInsideAnElfSharedObject() throws Exception {
        AssemblyLayout l = probe(SyntheticApk.stardewLike(Layout.ELF_STORE, "1.6.15.1", false, false));
        assertEquals(AssemblyLayout.Kind.STORE_IN_ELF, l.kind);
        AssemblyLayout.StoreFile s = l.stores.get(0);
        assertEquals("elf-payload", s.format);
        assertTrue(s.headerRead);
        assertTrue(s.magicOk);
        assertEquals(3, s.version);
        assertTrue(s.detail.contains("payload section"));
    }

    @Test
    public void readsElf32StoresToo() throws Exception {
        byte[] apk = new MiniZip().add("lib/armeabi-v7a/libassemblies.armeabi-v7a.blob.so", ElfBuilder.elf32(SyntheticApk.xabaHeader(4))).build();
        AssemblyLayout l = probe(apk);
        assertEquals(AssemblyLayout.Kind.STORE_IN_ELF, l.kind);
        assertTrue(l.stores.get(0).magicOk);
        assertEquals(4, l.stores.get(0).version);
    }

    @Test
    public void compressedElfStoreIsReportedNotGuessed() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            z.putNextEntry(new ZipEntry("lib/arm64-v8a/libassembly-store.so"));
            z.write(ElfBuilder.elf64(SyntheticApk.xabaHeader(3)));
            z.closeEntry();
        }
        AssemblyLayout l = probe(bos.toByteArray());
        AssemblyLayout.StoreFile s = l.stores.get(0);
        assertFalse(s.headerRead);
        assertFalse(s.storedUncompressed);
        assertTrue(s.detail.contains("compressed"));
    }

    @Test
    public void wrongMagicIsFlaggedAndDeflatedBlobHeadersStillRead() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            z.putNextEntry(new ZipEntry("assemblies/assemblies.blob"));
            z.write(new Bytes().u32(0xDEADBEEFL).u32(9).u32(1).u32(2).u32(3).zeros(30).toByteArray());
            z.closeEntry();
            z.putNextEntry(new ZipEntry("assemblies/assemblies.arm64_v8a.blob"));
            z.write(SyntheticApk.xabaHeader(2));
            z.closeEntry();
        }
        AssemblyLayout l = probe(bos.toByteArray());
        assertEquals(2, l.stores.size());
        AssemblyLayout.StoreFile bad = l.stores.get(0);
        assertTrue(bad.headerRead);
        assertFalse(bad.magicOk);
        assertEquals(9, bad.version);
        assertTrue(l.stores.get(1).magicOk);
    }

    @Test
    public void apkWithoutAssembliesSaysSo() throws Exception {
        AssemblyLayout l = probe(SyntheticApk.stardewLike(Layout.NONE, "1.6.15.1", false, false));
        assertEquals(AssemblyLayout.Kind.NONE, l.kind);
        assertFalse(l.canListNames());
        assertTrue(l.notes.get(0).contains("split"));
    }

    @Test
    public void mixedLayoutsAreLabelledMixed() throws Exception {
        byte[] apk = new MiniZip().add("assemblies/Foo.dll", new byte[] {'M', 'Z'}).add("assemblies/assemblies.blob", SyntheticApk.xabaHeader(2)).build();
        assertEquals(AssemblyLayout.Kind.MIXED, probe(apk).kind);
    }

    @Test
    public void parsesManifestRowsWithSpacesAndIgnoresHeaders() {
        java.util.List<String> names = AssemblyLayoutProbe.parseManifestNames(
                "Hash 32     Hash 64             Blob ID  Blob idx  Name\n\n0x1  0x2  000  0000  Stardew Valley.dll\nnot a row\n0x3 0x4 000");
        assertEquals(1, names.size());
        assertEquals("Stardew Valley", names.get(0));
    }
}
