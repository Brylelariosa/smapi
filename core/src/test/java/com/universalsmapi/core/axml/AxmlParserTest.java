package com.universalsmapi.core.axml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.universalsmapi.core.testutil.AxmlBuilder;
import com.universalsmapi.core.testutil.AxmlBuilder.A;
import com.universalsmapi.core.testutil.SyntheticApk;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.Test;

public class AxmlParserTest {
    private static void assertStardewFacts(ManifestFacts m) {
        assertEquals("com.chucklefish.stardewvalley", m.packageName);
        assertEquals("1.6.15.1", m.versionName);
        assertEquals(321, m.versionCode);
        assertEquals("23", m.minSdk);
        assertEquals("34", m.targetSdk);
        assertEquals(Arrays.asList("android.permission.INTERNET", "android.permission.VIBRATE"), m.permissions);
        assertEquals(Arrays.asList("android.hardware.touchscreen (optional)"), m.features);
        assertEquals("crc64f0.MainApplication", m.applicationClass);
        assertEquals("crc64f0.MainActivity", m.launcherActivity);
        assertEquals(Boolean.FALSE, m.extractNativeLibs);
        assertEquals(Boolean.TRUE, m.largeHeap);
        assertNull(m.hasCode);
        assertEquals(1, m.activities);
        assertEquals(1, m.services);
        assertNull(m.splitName);
    }

    @Test
    public void readsManifestFactsFromUtf16StringPool() throws Exception {
        assertStardewFacts(ManifestFacts.parse(SyntheticApk.manifest("com.chucklefish.stardewvalley", "1.6.15.1", 321, null, false)));
    }

    @Test
    public void readsManifestFactsFromUtf8StringPool() throws Exception {
        assertStardewFacts(ManifestFacts.parse(SyntheticApk.manifest("com.chucklefish.stardewvalley", "1.6.15.1", 321, null, true)));
    }

    @Test
    public void readsSplitName() throws Exception {
        ManifestFacts m = ManifestFacts.parse(SyntheticApk.manifest("com.chucklefish.stardewvalley", "1.6.15.1", 321, "config.arm64_v8a", false));
        assertEquals("config.arm64_v8a", m.splitName);
    }

    @Test
    public void versionCodeIsUnsigned32Bit() throws Exception {
        byte[] hex = new AxmlBuilder(false).start("manifest", A.hex("versionCode", 0xFFFFFFFEL), A.str("package", "p")).end("manifest").build();
        assertEquals(4294967294L, ManifestFacts.parse(hex).versionCode);
        byte[] dec = new AxmlBuilder(false).start("manifest", A.integer("versionCode", -2), A.str("package", "p")).end("manifest").build();
        assertEquals(4294967294L, ManifestFacts.parse(dec).versionCode);
    }

    @Test
    public void reportsReferencesAndBooleansAsText() throws Exception {
        byte[] data = new AxmlBuilder(true).start("manifest", A.str("package", "p")).start("application", A.ref("icon", 0x7f0e0001L), A.bool("hasCode", false))
                .end("application").end("manifest").build();
        XmlNode root = AxmlParser.parse(data);
        XmlNode app = root.firstChild("application");
        assertEquals("@0x7f0e0001", app.attr("icon"));
        assertEquals("false", app.attr("hasCode"));
    }

    @Test
    public void rejectsPlainTextXml() {
        try {
            AxmlParser.parse("<manifest package=\"x\"/>".getBytes(StandardCharsets.UTF_8));
            fail("plain XML is not binary XML");
        } catch (AxmlException e) {
            assertTrue(e.getMessage().contains("Not binary XML"));
        }
    }

    @Test
    public void rejectsTruncatedAndEmptyDocuments() throws Exception {
        byte[] good = SyntheticApk.manifest("p", "1.0", 1, null, false);
        byte[] cut = Arrays.copyOf(good, good.length / 2);
        try {
            AxmlParser.parse(cut);
            fail("truncated document should be rejected");
        } catch (AxmlException expected) {
            // expected
        }
        try {
            AxmlParser.parse(new byte[] {3, 0, 8, 0, 8, 0, 0, 0});
            fail("document without elements should be rejected");
        } catch (AxmlException e) {
            assertTrue(e.getMessage().contains("no elements"));
        }
        try {
            AxmlParser.parse(new byte[3]);
            fail("tiny input should be rejected");
        } catch (AxmlException expected) {
            // expected
        }
    }

    @Test
    public void rejectsNonManifestRoots() {
        byte[] data = new AxmlBuilder(false).start("layout").end("layout").build();
        try {
            ManifestFacts.parse(data);
            fail("root must be <manifest>");
        } catch (AxmlException e) {
            assertTrue(e.getMessage().contains("<layout>"));
        }
    }
}
