package com.universalsmapi.core.version;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GameVersionTest {
    @Test
    public void androidFourPartVersionKeepsPlatformRelease() {
        GameVersion g = GameVersion.parse("1.6.15.1");
        assertNotNull(g);
        assertEquals("1.6.15", g.core().toString());
        assertEquals(1, g.platformRelease());
        assertEquals("1.6.15.1", g.toString());
    }

    @Test
    public void oldNonSemanticVersionsAreMappedLikeSmapi() {
        assertEquals("1.1.1", GameVersion.parse("1.11").toString());
        assertEquals("1.0.5", GameVersion.parse("1.05").toString());
        assertEquals("1.0.5.1", GameVersion.parse("1.051").toString());
        assertEquals("1.0.5.2", GameVersion.parse("1.051b").toString());
        assertEquals("1.1.0", GameVersion.parse("1.1").toString());
    }

    @Test
    public void garbageIsRejected() {
        assertNull(GameVersion.parse(null));
        assertNull(GameVersion.parse(""));
        assertNull(GameVersion.parse("stardew"));
        assertNull(GameVersion.parse("1"));
    }

    @Test
    public void whitespaceIsTrimmedAndRawIsKept() {
        GameVersion g = GameVersion.parse("  1.6.15.0 ");
        assertEquals("1.6.15.0", g.raw);
        assertTrue(g.version.compareTo(GameVersion.parse("1.6.15.1").version) < 0);
    }

    @Test
    public void displayKeepsAPlatformReleaseOfZero() {
        assertEquals("1.6.15", GameVersion.parse("1.6.15.0").toString()); // SMAPI's own ToString drops it
        assertEquals("1.6.15.0", GameVersion.parse("1.6.15.0").display());
        assertEquals("1.6.15.1", GameVersion.parse("1.6.15.1").display());
        assertEquals("1.6.15", GameVersion.parse("1.6.15").display());
        assertEquals("1.1.1", GameVersion.parse("1.11").display());
    }
}
