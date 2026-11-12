package com.universalsmapi.core.zip;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.universalsmapi.core.apk.SigningBlockInfo;
import com.universalsmapi.core.io.ByteArraySource;
import com.universalsmapi.core.io.SeekableSource;
import com.universalsmapi.core.testutil.MiniZip;
import com.universalsmapi.core.testutil.SigningBlocks;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;

public class ZipArchiveTest {
    private static byte[] jdkZip() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            byte[] stored = "stored entry payload".getBytes(StandardCharsets.UTF_8);
            ZipEntry s = new ZipEntry("lib/arm64-v8a/libfoo.so");
            s.setMethod(ZipEntry.STORED);
            s.setSize(stored.length);
            s.setCompressedSize(stored.length);
            CRC32 crc = new CRC32();
            crc.update(stored);
            s.setCrc(crc.getValue());
            z.putNextEntry(s);
            z.write(stored);
            z.closeEntry();

            StringBuilder big = new StringBuilder();
            for (int i = 0; i < 2000; i++) {
                big.append("deflate me please ");
            }
            ZipEntry d = new ZipEntry("assets/Données.txt");
            d.setMethod(ZipEntry.DEFLATED);
            z.putNextEntry(d);
            z.write(big.toString().getBytes(StandardCharsets.UTF_8));
            z.closeEntry();

            z.putNextEntry(new ZipEntry("assets/"));
            z.closeEntry();
            z.setComment("a comment after the directory");
        }
        return bos.toByteArray();
    }

    @Test
    public void readsStoredAndDeflatedEntriesWrittenByTheJdk() throws Exception {
        byte[] data = jdkZip();
        try (SeekableSource src = new ByteArraySource(data)) {
            ZipArchive zip = ZipArchive.parse(src);
            assertEquals(3, zip.entries().size());
            ZipEntryInfo stored = zip.find("lib/arm64-v8a/libfoo.so");
            assertNotNull(stored);
            assertTrue(stored.isStored());
            assertEquals("stored entry payload", new String(zip.readAll(stored, 1000), StandardCharsets.UTF_8));
            assertEquals("stored", new String(zip.readPrefix(stored, 6), StandardCharsets.UTF_8));

            ZipEntryInfo deflated = zip.find("assets/Données.txt");
            assertNotNull(deflated);
            assertEquals(ZipEntryInfo.METHOD_DEFLATED, deflated.method);
            assertEquals(2000 * 18, deflated.size);
            byte[] all = zip.readAll(deflated, 100000);
            assertEquals(deflated.size, all.length);
            assertEquals("deflate me", new String(zip.readPrefix(deflated, 10), StandardCharsets.UTF_8));
            assertTrue(zip.find("assets/").isDirectory());
            assertNull(zip.find("nope"));
            assertFalse(zip.isZip64());
        }
    }

    @Test
    public void sliceGivesRandomAccessToStoredEntriesOnly() throws Exception {
        try (SeekableSource src = new ByteArraySource(jdkZip())) {
            ZipArchive zip = ZipArchive.parse(src);
            try (SeekableSource slice = zip.slice(zip.find("lib/arm64-v8a/libfoo.so"))) {
                assertEquals(20, slice.size());
                assertArrayEquals("entry".getBytes(StandardCharsets.UTF_8), slice.readBytes(7, 5));
            }
            try {
                zip.slice(zip.find("assets/Données.txt"));
                fail("compressed entries cannot be sliced");
            } catch (ZipFormatException expected) {
                assertTrue(expected.getMessage().contains("compressed"));
            }
        }
    }

    @Test
    public void readAllEnforcesTheByteLimit() throws Exception {
        try (SeekableSource src = new ByteArraySource(jdkZip())) {
            ZipArchive zip = ZipArchive.parse(src);
            try {
                zip.readAll(zip.find("assets/Données.txt"), 100);
                fail("should refuse");
            } catch (ZipFormatException expected) {
                assertTrue(expected.getMessage().contains("limit"));
            }
        }
    }

    @Test
    public void readsZip64EndRecordsAndExtraFields() throws Exception {
        byte[] data = new MiniZip().zip64().add("a.txt", "hello zip64").add("b.txt", "second").build();
        try (SeekableSource src = new ByteArraySource(data)) {
            ZipArchive zip = ZipArchive.parse(src);
            assertTrue(zip.isZip64());
            assertEquals(2, zip.entries().size());
            ZipEntryInfo a = zip.find("a.txt");
            assertEquals(11, a.size);
            assertEquals("hello zip64", new String(zip.readAll(a, 100), StandardCharsets.UTF_8));
            assertEquals("second", new String(zip.readAll(zip.find("b.txt"), 100), StandardCharsets.UTF_8));
        }
    }

    @Test
    public void rejectsNonZipInputWithSpecificErrors() throws Exception {
        byte[][] bad = {new byte[0], new byte[10], "this is definitely not a zip file, just text padding..".getBytes(StandardCharsets.UTF_8)};
        for (byte[] b : bad) {
            try (SeekableSource src = new ByteArraySource(b)) {
                try {
                    ZipArchive.parse(src);
                    fail("should reject " + b.length + " bytes");
                } catch (ZipFormatException expected) {
                    assertTrue(expected.getMessage().length() > 10);
                }
            }
        }
        byte[] good = jdkZip();
        byte[] truncated = new byte[good.length - 12];
        System.arraycopy(good, 0, truncated, 0, truncated.length);
        try (SeekableSource src = new ByteArraySource(truncated)) {
            try {
                ZipArchive.parse(src);
                fail("truncated archive should be rejected");
            } catch (ZipFormatException expected) {
                // expected
            }
        }
    }

    @Test
    public void rejectsEntryDataThatRunsPastTheFile() throws Exception {
        byte[] data = new MiniZip().add("x.bin", new byte[64]).build();
        // corrupt the central directory's compressed size so the entry claims more data than the file holds
        int cd = indexOf(data, new byte[] {0x50, 0x4b, 0x01, 0x02});
        data[cd + 20] = (byte) 0xFF;
        data[cd + 21] = (byte) 0xFF;
        try (SeekableSource src = new ByteArraySource(data)) {
            ZipArchive zip = ZipArchive.parse(src);
            try {
                zip.readAll(zip.find("x.bin"), 1000);
                fail("should reject oversized entry");
            } catch (ZipFormatException expected) {
                assertTrue(expected.getMessage().contains("past the end"));
            }
        }
    }

    @Test
    public void findsSigningBlockBeforeTheCentralDirectory() throws Exception {
        byte[] data = new MiniZip().add("a", "1").add("b", "2")
                .signingBlock(SigningBlocks.build(0x7109871aL, 0xf05368c0L, 0x42726577L)).build();
        try (SeekableSource src = new ByteArraySource(data)) {
            ZipArchive zip = ZipArchive.parse(src);
            SigningBlockInfo info = SigningBlockInfo.find(src, zip.centralDirectoryOffset());
            assertNotNull(info);
            assertTrue(info.hasV2());
            assertTrue(info.hasV3());
            assertFalse(info.hasV31());
            assertEquals(3, info.pairIds.size());
            assertEquals("[v2, v3]", info.schemes().toString());
        }
        byte[] unsigned = new MiniZip().add("a", "1").build();
        try (SeekableSource src = new ByteArraySource(unsigned)) {
            ZipArchive zip = ZipArchive.parse(src);
            assertNull(SigningBlockInfo.find(src, zip.centralDirectoryOffset()));
        }
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
