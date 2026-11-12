package com.universalsmapi.core.axml;

import com.universalsmapi.core.io.Le;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Parser for Android's compiled binary XML (what {@code AndroidManifest.xml} is inside an APK).
 *
 * <p>Chunk layout (little-endian): every chunk starts with {@code u16 type, u16 headerSize, u32 size}. The file is a
 * RES_XML (0x0003) chunk containing a string pool (0x0001), an optional resource-id map (0x0180) and a stream of
 * namespace/element/cdata chunks. Only the pieces needed to read manifest facts are interpreted; unknown chunks are
 * skipped by size. Any structural problem raises {@link AxmlException} rather than returning guesses.
 */
public final class AxmlParser {
    private static final int RES_STRING_POOL = 0x0001;
    private static final int RES_XML = 0x0003;
    private static final int RES_XML_RESOURCE_MAP = 0x0180;
    private static final int RES_XML_START_ELEMENT = 0x0102;
    private static final int RES_XML_END_ELEMENT = 0x0103;

    private static final int TYPE_REFERENCE = 0x01;
    private static final int TYPE_STRING = 0x03;
    private static final int TYPE_FLOAT = 0x04;
    private static final int TYPE_INT_DEC = 0x10;
    private static final int TYPE_INT_HEX = 0x11;
    private static final int TYPE_INT_BOOLEAN = 0x12;

    private static final int MAX_NODES = 200_000;
    private static final int MAX_DEPTH = 256;

    private AxmlParser() {
    }

    public static XmlNode parse(byte[] d) throws AxmlException {
        try {
            return parseUnchecked(d);
        } catch (AxmlException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AxmlException("Malformed or truncated binary XML: " + e, e);
        }
    }

    private static XmlNode parseUnchecked(byte[] d) throws AxmlException {
        if (d.length < 8) {
            throw new AxmlException("Binary XML too short (" + d.length + " bytes)");
        }
        int type = Le.u16(d, 0);
        if (type != RES_XML) {
            throw new AxmlException("Not binary XML: first chunk type is 0x" + Integer.toHexString(type)
                    + " (plain-text XML manifests are not what an installed APK contains)");
        }
        int headerSize = Le.u16(d, 2);
        long declared = Le.u32(d, 4);
        int limit = (int) Math.min(declared, (long) d.length);

        List<String> strings = new ArrayList<>();
        long[] resMap = null;
        Deque<XmlNode> stack = new ArrayDeque<>();
        XmlNode root = null;
        int nodes = 0;

        int p = headerSize;
        while (p + 8 <= limit) {
            int ctype = Le.u16(d, p);
            long csize = Le.u32(d, p + 4);
            if (csize < 8 || p + csize > limit) {
                throw new AxmlException("Chunk 0x" + Integer.toHexString(ctype) + " at offset " + p
                        + " has invalid size " + csize);
            }
            if (ctype == RES_STRING_POOL) {
                strings = readStringPool(d, p, (int) csize);
            } else if (ctype == RES_XML_RESOURCE_MAP) {
                int count = (int) ((csize - 8) / 4);
                resMap = new long[count];
                for (int i = 0; i < count; i++) {
                    resMap[i] = Le.u32(d, p + 8 + i * 4);
                }
            } else if (ctype == RES_XML_START_ELEMENT) {
                if (++nodes > MAX_NODES || stack.size() > MAX_DEPTH) {
                    throw new AxmlException("Document too large or too deeply nested");
                }
                XmlNode node = startElement(d, p, strings, resMap);
                if (stack.isEmpty()) {
                    if (root == null) {
                        root = node;
                    }
                } else {
                    stack.peek().children.add(node);
                }
                stack.push(node);
            } else if (ctype == RES_XML_END_ELEMENT) {
                if (!stack.isEmpty()) {
                    stack.pop();
                }
            }
            p += (int) csize;
        }
        if (root == null) {
            throw new AxmlException("Binary XML contains no elements");
        }
        return root;
    }

    private static XmlNode startElement(byte[] d, int p, List<String> strings, long[] resMap) {
        int nameIdx = Le.i32(d, p + 20);
        int attrStart = Le.u16(d, p + 24);
        int attrSize = Le.u16(d, p + 26);
        int attrCount = Le.u16(d, p + 28);
        XmlNode node = new XmlNode(stringAt(strings, nameIdx));
        int a = p + 16 + attrStart;
        for (int i = 0; i < attrCount; i++, a += attrSize) {
            int nameRef = Le.i32(d, a + 4);
            int raw = Le.i32(d, a + 8);
            int dataType = Le.u8(d, a + 15);
            long data = Le.u32(d, a + 16);
            node.attrs.put(attributeName(strings, resMap, nameRef), valueText(strings, raw, dataType, data));
        }
        return node;
    }

    private static String attributeName(List<String> strings, long[] resMap, int nameRef) {
        String s = stringAt(strings, nameRef);
        if (!s.isEmpty()) {
            return s;
        }
        if (resMap != null && nameRef >= 0 && nameRef < resMap.length) {
            long id = resMap[nameRef];
            // Fallback for stripped attribute names: a few framework attribute ids the manifest reader cares about.
            if (id == 0x0101021bL) return "versionCode";
            if (id == 0x0101021cL) return "versionName";
            if (id == 0x0101020cL) return "minSdkVersion";
            if (id == 0x01010270L) return "targetSdkVersion";
            if (id == 0x01010003L) return "name";
            if (id == 0x0101028eL) return "required";
            if (id == 0x010104eaL) return "extractNativeLibs";
            if (id == 0x0101000cL) return "hasCode";
            return "res:0x" + Long.toHexString(id);
        }
        return "";
    }

    private static String valueText(List<String> strings, int raw, int dataType, long data) {
        if (dataType == TYPE_STRING) {
            return stringAt(strings, (int) data);
        }
        if (raw != -1 && dataType != TYPE_INT_BOOLEAN && dataType != TYPE_INT_DEC && dataType != TYPE_INT_HEX
                && dataType != TYPE_REFERENCE && dataType != TYPE_FLOAT) {
            return stringAt(strings, raw);
        }
        switch (dataType) {
            case TYPE_REFERENCE:
                return "@0x" + Long.toHexString(data);
            case TYPE_INT_DEC:
                return Integer.toString((int) data);
            case TYPE_INT_HEX:
                return "0x" + Long.toHexString(data);
            case TYPE_INT_BOOLEAN:
                return data != 0 ? "true" : "false";
            case TYPE_FLOAT:
                return Float.toString(Float.intBitsToFloat((int) data));
            default:
                return "type0x" + Integer.toHexString(dataType) + ":0x" + Long.toHexString(data);
        }
    }

    private static String stringAt(List<String> strings, int idx) {
        return idx >= 0 && idx < strings.size() ? strings.get(idx) : "";
    }

    private static List<String> readStringPool(byte[] d, int p, int csize) throws AxmlException {
        int headerSize = Le.u16(d, p + 2);
        long count = Le.u32(d, p + 8);
        long flags = Le.u32(d, p + 16);
        long stringsStart = Le.u32(d, p + 20);
        boolean utf8 = (flags & 0x100) != 0;
        if (count > 2_000_000L || headerSize + count * 4 > csize) {
            throw new AxmlException("String pool declares " + count + " strings, which does not fit its chunk");
        }
        int end = p + csize;
        List<String> out = new ArrayList<>((int) count);
        for (int i = 0; i < count; i++) {
            long rel = Le.u32(d, p + headerSize + i * 4);
            long off = p + stringsStart + rel;
            if (off < 0 || off >= end) {
                out.add("");
                continue;
            }
            out.add(utf8 ? readUtf8(d, (int) off, end) : readUtf16(d, (int) off, end));
        }
        return out;
    }

    private static String readUtf8(byte[] d, int o, int end) {
        int b = Le.u8(d, o++);
        if ((b & 0x80) != 0) {
            o++; // two-byte UTF-16 length; value unused
        }
        int bytes = Le.u8(d, o++);
        if ((bytes & 0x80) != 0) {
            bytes = ((bytes & 0x7F) << 8) | Le.u8(d, o++);
        }
        if (bytes < 0 || o + bytes > end) {
            return "";
        }
        return new String(d, o, bytes, StandardCharsets.UTF_8);
    }

    private static String readUtf16(byte[] d, int o, int end) {
        int len = Le.u16(d, o);
        o += 2;
        if ((len & 0x8000) != 0) {
            len = ((len & 0x7FFF) << 16) | Le.u16(d, o);
            o += 2;
        }
        if (len < 0 || (long) o + (long) len * 2 > end) {
            return "";
        }
        return new String(d, o, len * 2, StandardCharsets.UTF_16LE);
    }
}
