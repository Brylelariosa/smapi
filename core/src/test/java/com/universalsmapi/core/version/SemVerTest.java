package com.universalsmapi.core.version;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Cases ported from SMAPI's own tests (src/SMAPI.Tests/Utilities/SemanticVersionTests.cs) to prove the port matches. */
public class SemVerTest {
    private static void parsesStandard(String input, String expected) {
        SemVer v = SemVer.parse(input, false);
        assertNotNull("should parse: '" + input + "'", v);
        assertEquals(expected, v.toString());
    }

    @Test
    public void parsesValidStandardVersions() {
        parsesStandard("1.0", "1.0.0");
        parsesStandard("1.0.0", "1.0.0");
        parsesStandard("3000.4000.5000", "3000.4000.5000");
        parsesStandard("1.2-some-tag.4", "1.2.0-some-tag.4");
        parsesStandard("1.2.3-some-tag.4", "1.2.3-some-tag.4");
        parsesStandard("1.2.3-SoME-tAg.4", "1.2.3-SoME-tAg.4");
        parsesStandard("1.2.3-some-tag.4      ", "1.2.3-some-tag.4");
        parsesStandard("1.2.3-some-tag.4+build.004", "1.2.3-some-tag.4+build.004");
        parsesStandard("1.2+3.4.5-build.004", "1.2.0+3.4.5-build.004");
    }

    @Test
    public void rejectsInvalidVersions() {
        String[] invalid = {null, "", "   ", "1", "01.0", "1.05", "1.5.06", "1.2.3.4", "1.apple", "1.2.apple",
                "1.2.3.apple", "1..2..3", "1.2.3-", "1.2.3--some-tag", "1.2.3-some-tag...", "1.2.3-some-tag...4",
                "1.2.3-some-tag.4+build...4", "apple", "-apple", "-5", "0.0.0"};
        for (String s : invalid) {
            assertNull("should reject: '" + s + "'", SemVer.parse(s, false));
        }
    }

    @Test
    public void nonStandardFourthNumberIsThePlatformRelease() {
        assertEquals("1.2.3", SemVer.parse("1.2.3", true).toString());
        assertEquals("1.0.0", SemVer.parse("1.0.0.0", true).toString());
        assertEquals("1.0.0.5", SemVer.parse("1.0.0.5", true).toString());
        assertEquals("1.2.3.4-some-tag.4", SemVer.parse("1.2.3.4-some-tag.4      ", true).toString());
        assertEquals(5, SemVer.parse("1.0.0.5", true).platformRelease);
        assertNull(SemVer.parse("1.0.0.0", false));
        assertNull(SemVer.parse("1.0.0.5", false));
        assertNull(SemVer.parse("1.2.3.4-some-tag.4      ", false));
    }

    private static void cmp(String a, String b, int expected) {
        SemVer va = SemVer.require(a, true);
        SemVer vb = SemVer.require(b, true);
        assertEquals(a + " vs " + b, expected, Integer.signum(va.compareTo(vb)));
    }

    @Test
    public void ordersLikeSmapi() {
        cmp("0.5.7", "0.5.7", 0);
        cmp("1.0", "1.0", 0);
        cmp("1.0-beta", "1.0-beta", 0);
        cmp("1.0-beta.10", "1.0-beta.10", 0);
        cmp("1.0-beta", "1.0-beta   ", 0);
        cmp("1.0-beta+build.001", "1.0-beta+build.006", 0); // build metadata must not affect precedence

        cmp("0.5.7", "0.5.8", -1);
        cmp("1.0", "1.1", -1);
        cmp("1.0-beta", "1.0", -1);
        cmp("1.0-beta", "1.0-beta.2", -1);
        cmp("1.0-beta.1", "1.0-beta.2", -1);
        cmp("1.0-beta.2", "1.0-beta.10", -1);
        cmp("1.0-beta-2", "1.0-beta-10", -1);
        cmp("1.0-unofficial.1", "1.0-beta.1", -1); // 'unofficial' has lower priority

        cmp("0.5.8", "0.5.7", 1);
        cmp("1.1", "1.0", 1);
        cmp("1.0", "1.0-beta", 1);
        cmp("1.0-beta.2", "1.0-beta", 1);
        cmp("1.0-beta.2", "1.0-beta.1", 1);
        cmp("1.0-beta.10", "1.0-beta.2", 1);
        cmp("1.0-beta-10", "1.0-beta-2", 1);
        assertEquals(1, SemVer.require("1.0.0", true).compareTo(null));
    }

    @Test
    public void platformReleaseOrdersAfterTheBaseVersion() {
        cmp("1.6.15.1", "1.6.15.0", 1);
        cmp("1.6.15.1", "1.6.15", 1);
        cmp("1.6.16", "1.6.15.9", 1);
        assertTrue(SemVer.require("1.6.15.1", true).core().equals(SemVer.require("1.6.15", true)));
        assertFalse(SemVer.require("1.6.15.1", true).equals(SemVer.require("1.6.15", true)));
    }

    @Test
    public void betweenIsInclusive() {
        SemVer v = SemVer.require("1.6.15", true);
        assertTrue(v.isBetween(SemVer.require("1.6.14", true), null));
        assertTrue(v.isBetween(SemVer.require("1.6.15", true), SemVer.require("1.6.15", true)));
        assertFalse(v.isBetween(SemVer.require("1.6.16", true), null));
    }
}
