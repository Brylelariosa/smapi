package com.universalsmapi.core.dotnet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.universalsmapi.core.testutil.PeBuilder;

import java.util.Arrays;
import org.junit.Test;

public class PeSummaryTest {
    @Test
    public void recognisesManagedPe32AndPe32Plus() {
        PeSummary a = PeSummary.parse(PeBuilder.managedAnyCpu(600));
        assertTrue(a.valid);
        assertTrue(a.managed);
        assertFalse(a.pe32Plus);
        assertEquals("i386/AnyCPU", a.machineName());
        assertEquals(1, a.sections);

        PeSummary b = PeSummary.parse(PeBuilder.pe(true, 0xAA64, true, 700));
        assertTrue(b.valid);
        assertTrue(b.managed);
        assertTrue(b.pe32Plus);
        assertEquals("arm64", b.machineName());
        assertEquals("x64", PeSummary.parse(PeBuilder.pe(true, 0x8664, true, 700)).machineName());
        assertEquals("0x1234", PeSummary.parse(PeBuilder.pe(false, 0x1234, true, 700)).machineName());
    }

    @Test
    public void nativeImagesAreNotManaged() {
        PeSummary p = PeSummary.parse(PeBuilder.pe(true, 0x8664, false, 700));
        assertTrue(p.valid);
        assertFalse(p.managed);
    }

    @Test
    public void rejectsBrokenImages() {
        assertFalse(PeSummary.parse(new byte[10]).valid);
        byte[] noMz = PeBuilder.managedAnyCpu(600);
        noMz[0] = 'X';
        assertTrue(PeSummary.parse(noMz).problem.contains("MZ"));
        byte[] badLfanew = PeBuilder.managedAnyCpu(600);
        badLfanew[0x3C] = (byte) 0xFF;
        badLfanew[0x3D] = (byte) 0xFF;
        assertTrue(PeSummary.parse(badLfanew).problem.contains("e_lfanew"));
        byte[] noSig = PeBuilder.managedAnyCpu(600);
        noSig[0x80] = 'Q';
        assertTrue(PeSummary.parse(noSig).problem.contains("PE signature"));
        byte[] badMagic = PeBuilder.managedAnyCpu(600);
        badMagic[0x80 + 24] = 0x55;
        badMagic[0x80 + 25] = 0x55;
        assertTrue(PeSummary.parse(badMagic).problem.contains("magic"));
        byte[] cut = Arrays.copyOf(PeBuilder.managedAnyCpu(600), 0x80 + 30);
        assertTrue(PeSummary.parse(cut).problem.contains("optional header"));
    }
}
