package com.universalsmapi.core.dotnet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.universalsmapi.core.testutil.Bytes;
import com.universalsmapi.core.testutil.Lz4Encoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import org.junit.Test;

public class Lz4Test {
    private static byte[] decode(byte[] block, int outLen) throws IOException {
        byte[] out = new byte[outLen];
        int n = Lz4.decompress(block, 0, block.length, out, 0, out.length);
        assertEquals(outLen, n);
        return out;
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    private static void rejects(byte[] block, int outLen, String messagePart) {
        try {
            Lz4.decompress(block, 0, block.length, new byte[outLen], 0, outLen);
            fail("should reject: " + messagePart);
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(messagePart));
        }
    }

    @Test
    public void decodesALiteralOnlyBlock() throws Exception {
        assertArrayEquals(ascii("hello"), decode(new byte[] {0x50, 'h', 'e', 'l', 'l', 'o'}, 5));
    }

    @Test
    public void decodesMatchesFollowedByTrailingLiterals() throws Exception {
        // literals 'abc', match offset 3 length 9, then literals 'XYZ'
        byte[] block = new Bytes().u8(0x35).ascii("abc").u16(3).u8(0x30).ascii("XYZ").toByteArray();
        assertArrayEquals(ascii("abcabcabcabcXYZ"), decode(block, 15));
    }

    @Test
    public void overlappingMatchesRepeatTheirOwnOutput() throws Exception {
        // one literal 'x', then offset 1 with length 4 + 15 + 255 + 10 = 284 (two extension bytes)
        byte[] block = new Bytes().u8(0x1F).ascii("x").u16(1).u8(255).u8(10).toByteArray();
        byte[] out = decode(block, 285);
        for (byte b : out) {
            assertEquals('x', b);
        }
    }

    @Test
    public void longLiteralRunsUseLengthExtensionBytes() throws Exception {
        byte[] data = ascii("0123456789ABCDEFGHIJ"); // 20 literals = 15 + 5
        byte[] block = new Bytes().u8(0xF0).u8(5).bytes(data).toByteArray();
        assertArrayEquals(data, decode(block, 20));
    }

    @Test
    public void roundTripsDataWithRunsThroughTheTestEncoder() throws Exception {
        Random rnd = new Random(42);
        for (int round = 0; round < 50; round++) {
            Bytes b = new Bytes();
            int parts = 1 + rnd.nextInt(8);
            for (int p = 0; p < parts; p++) {
                if (rnd.nextBoolean()) {
                    byte[] noise = new byte[rnd.nextInt(60)];
                    rnd.nextBytes(noise);
                    b.bytes(noise);
                } else {
                    int v = rnd.nextInt(256);
                    int len = 10 + rnd.nextInt(700);
                    for (int i = 0; i < len; i++) {
                        b.u8(v);
                    }
                }
            }
            byte[] data = b.toByteArray();
            assertArrayEquals("round " + round, data, decode(Lz4Encoder.encode(data), data.length));
            assertArrayEquals(data, decode(Lz4Encoder.literalsOnly(data), data.length));
        }
    }

    @Test
    public void rejectsMalformedBlocks() {
        rejects(new byte[] {0x11, 'a', 0, 0, 0x10}, 20, "invalid match offset 0");
        rejects(new byte[] {0x11, 'a', 5, 0}, 20, "invalid match offset 5");
        rejects(new byte[] {(byte) 0xF0, 100}, 200, "exceeds the input");
        rejects(new byte[] {(byte) 0xF0, (byte) 255}, 400, "runs past the input");
        rejects(new byte[] {0x10, 'a', 1}, 20, "truncated match offset");
        rejects(new byte[] {0x1F, 'a', 1, 0}, 20, "match length runs past the input");
        rejects(new byte[] {0x1F, 'a', 1, 0, 100}, 20, "match of 119 exceeds the output buffer");
        rejects(new byte[] {0x50, 'h', 'e', 'l', 'l', 'o'}, 3, "exceeds the output buffer");
    }
}
