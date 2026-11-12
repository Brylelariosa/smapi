package com.universalsmapi.core.json;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;
import org.junit.Test;

public class LenientJsonTest {
    @Test
    public void toleratesCommentsTrailingCommasSingleQuotesAndBareKeys() throws Exception {
        String text = "\uFEFF{ // line comment\n"
                + "  \"Name\": \"Example Mod\", /* block */\n"
                + "  'Version': '1.2.3',\n"
                + "  Dependencies: [ { \"UniqueID\": \"a.b\", \"IsRequired\": false, }, ],\n"
                + "}";
        Map<String, Object> o = LenientJson.asObject(LenientJson.parse(text));
        assertEquals("Example Mod", LenientJson.str(o, "Name"));
        assertEquals("1.2.3", LenientJson.str(o, "Version"));
        List<Object> deps = LenientJson.asArray(o.get("Dependencies"));
        assertEquals(1, deps.size());
        Map<String, Object> dep = LenientJson.asObject(deps.get(0));
        assertEquals("a.b", LenientJson.str(dep, "UniqueID"));
        assertEquals(false, LenientJson.bool(dep, "IsRequired", true));
    }

    @Test
    public void parsesNumbersLiteralsAndEscapes() throws Exception {
        Map<String, Object> o = LenientJson.asObject(LenientJson.parse(
                "{\"i\": -12, \"d\": 1.5, \"e\": 1e3, \"t\": true, \"n\": null, \"s\": \"a\\n\\u0041\\\"\"}"));
        assertEquals(Long.valueOf(-12), o.get("i"));
        assertEquals(Double.valueOf(1.5), o.get("d"));
        assertEquals(Double.valueOf(1000.0), o.get("e"));
        assertEquals(Boolean.TRUE, o.get("t"));
        assertNull(o.get("n"));
        assertTrue(o.containsKey("n"));
        assertEquals("a\nA\"", o.get("s"));
        assertEquals(-12L, LenientJson.num(o, "i", 0));
    }

    @Test
    public void smapiStyleRetryFixesCurlyQuotes() throws Exception {
        String curly = "{\u201CUniqueID\u201D: \u201Cx.y\u201D}";
        try {
            LenientJson.parse(curly);
            fail("plain parse should reject curly quotes");
        } catch (LenientJson.JsonException expected) {
            // expected
        }
        Map<String, Object> o = LenientJson.asObject(LenientJson.parseSmapiStyle(curly));
        assertEquals("x.y", LenientJson.str(o, "UniqueID"));
    }

    @Test
    public void reportsPositionsForBrokenInput() {
        String[] broken = {"{\"a\": }", "{\"a\": \"x", "[1, 2", "{\"a\" 1}", "{} trailing", "/* open", "{\"a\": tru}"};
        for (String b : broken) {
            try {
                LenientJson.parse(b);
                fail("should reject: " + b);
            } catch (LenientJson.JsonException e) {
                assertTrue(e.position >= 0);
            }
        }
    }

    @Test
    public void rejectsAbsurdNesting() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            sb.append('[');
        }
        try {
            LenientJson.parse(sb.toString());
            fail("deep nesting should be rejected");
        } catch (LenientJson.JsonException expected) {
            // expected
        }
    }

    @Test
    public void typedAccessorsNeverThrow() {
        Map<String, Object> empty = LenientJson.asObject("not an object");
        assertTrue(empty.isEmpty());
        assertEquals(0, LenientJson.asArray(5).size());
        assertNull(LenientJson.str(empty, "x"));
        assertEquals(7, LenientJson.num(empty, "x", 7));
    }
}
