package com.universalsmapi.core.dotnet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.universalsmapi.core.io.ByteArraySource;
import com.universalsmapi.core.io.SeekableSource;
import com.universalsmapi.core.testutil.Bytes;
import com.universalsmapi.core.testutil.StoreBuilder;

import org.junit.Test;

public class AssemblyStoreReaderTest {
    private static AssemblyStoreReader.StoreDecode decode(byte[] data) throws Exception {
        try (SeekableSource s = new ByteArraySource(data)) {
            return AssemblyStoreReader.decode(s, "assemblies/test.blob");
        }
    }

    @Test
    public void decodesDescriptorsAndPayloadSignatures() throws Exception {
        byte[] store = StoreBuilder.v1(0, 5, true, StoreBuilder.pe(100), StoreBuilder.xalz(5000, 60), StoreBuilder.pe(40));
        AssemblyStoreReader.StoreDecode d = decode(store);
        assertNull(d.problem);
        assertEquals(1, d.version);
        assertEquals(3, d.localCount);
        assertEquals(5, d.globalCount);
        assertEquals(0, d.storeId);
        assertEquals(3, d.entries.size());
        assertEquals("pe", d.entries.get(0).payload);
        assertEquals("xalz", d.entries.get(1).payload);
        assertEquals(5000, d.entries.get(1).uncompressedSize);
        assertEquals(60, d.entries.get(1).dataSize);
        assertTrue(d.entries.get(1).isCompressed());
        assertEquals(40, d.entries.get(2).dataSize);
        assertEquals(40, d.entries.get(2).uncompressedSize);
        assertEquals(2, d.entries.get(2).localIndex);
        assertTrue(d.entries.get(0).dataOffset >= 20 + 24 * 3 + 5 * 20 * 2);
        assertTrue(d.summary().contains("3 assemblies decoded"));
    }

    @Test
    public void decodesArchitectureStoresThatHaveNoIndex() throws Exception {
        AssemblyStoreReader.StoreDecode d = decode(StoreBuilder.v1(1, 0, false, StoreBuilder.xalz(777, 50), StoreBuilder.pe(30)));
        assertNull(d.problem);
        assertEquals(1, d.storeId);
        assertEquals(0, d.globalCount);
        assertEquals(2, d.entries.size());
        assertEquals(1, d.entries.get(0).storeId);
    }

    @Test
    public void otherStoreVersionsAreReportedNotGuessed() throws Exception {
        AssemblyStoreReader.StoreDecode d = decode(StoreBuilder.build(2, 0, 1, false, StoreBuilder.pe(20)));
        assertTrue(d.problem.contains("version 2"));
        assertTrue(d.entries.isEmpty());
    }

    @Test
    public void invalidDescriptorsAreFlaggedAndTheRestStillDecode() throws Exception {
        byte[] store = StoreBuilder.v1(0, 3, false, StoreBuilder.pe(50), StoreBuilder.pe(50), StoreBuilder.pe(50));
        // descriptor 1 starts at 20 + 24; point its image offset past the end of the file
        int d1 = 20 + 24;
        store[d1] = (byte) 0xFF;
        store[d1 + 1] = (byte) 0xFF;
        store[d1 + 2] = (byte) 0xFF;
        AssemblyStoreReader.StoreDecode d = decode(store);
        assertEquals(2, d.entries.size());
        assertTrue(d.problem.contains("1 of 3 descriptors failed validation"));
        assertTrue(d.problem.contains("descriptor 1"));
        assertFalse(d.entries.get(0).localIndex == d.entries.get(1).localIndex);
    }

    @Test
    public void unknownPayloadsAreLabelledUnknown() throws Exception {
        byte[] junk = new byte[40];
        junk[0] = 'A';
        AssemblyStoreReader.StoreDecode d = decode(StoreBuilder.v1(0, 1, false, junk));
        assertNull(d.problem);
        assertEquals("unknown", d.entries.get(0).payload);
    }

    @Test
    public void rejectsBadMagicTruncationAndAbsurdCounts() throws Exception {
        assertTrue(decode(new Bytes().u32(0xDEADBEEFL).u32(1).u32(0).u32(0).u32(0).toByteArray()).problem.contains("bad magic"));
        assertTrue(decode(new byte[10]).problem.contains("shorter"));
        assertTrue(decode(new Bytes().u32(0x41424158L).u32(1).u32(900_000).u32(0).u32(0).toByteArray()).problem.contains("does not fit"));
        byte[] store = StoreBuilder.v1(0, 1, false, StoreBuilder.pe(50));
        byte[] cut = java.util.Arrays.copyOf(store, 30); // header ok, descriptor table cut
        assertTrue(decode(cut).problem.contains("does not fit"));
    }
}
