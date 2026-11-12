package com.universalsmapi.core.testutil;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Encoder for Android binary XML, following the chunk layout in AOSP's ResourceTypes.h. Used to create manifest fixtures;
 * the parser under test is deliberately written independently of this class.
 */
public final class AxmlBuilder {
    /** One attribute. */
    public static final class A {
        final String name;
        final int type;
        final String text;
        final long data;

        private A(String name, int type, String text, long data) {
            this.name = name;
            this.type = type;
            this.text = text;
            this.data = data;
        }

        public static A str(String name, String value) {
            return new A(name, 0x03, value, 0);
        }

        public static A integer(String name, long value) {
            return new A(name, 0x10, null, value);
        }

        public static A hex(String name, long value) {
            return new A(name, 0x11, null, value);
        }

        public static A bool(String name, boolean value) {
            return new A(name, 0x12, null, value ? 0xFFFFFFFFL : 0);
        }

        public static A ref(String name, long resId) {
            return new A(name, 0x01, null, resId);
        }
    }

    private final boolean utf8;
    private final List<String> strings = new ArrayList<>();
    private final Bytes body = new Bytes();

    public AxmlBuilder(boolean utf8) {
        this.utf8 = utf8;
    }

    private int s(String v) {
        int i = strings.indexOf(v);
        if (i < 0) {
            strings.add(v);
            i = strings.size() - 1;
        }
        return i;
    }

    public AxmlBuilder start(String element, A... attrs) {
        int nameIdx = s(element);
        int[] attrNames = new int[attrs.length];
        int[] raws = new int[attrs.length];
        for (int i = 0; i < attrs.length; i++) {
            attrNames[i] = s(attrs[i].name);
            raws[i] = attrs[i].text != null ? s(attrs[i].text) : -1;
        }
        body.u16(0x0102).u16(16).u32(16 + 20 + attrs.length * 20L).u32(1).u32(0xFFFFFFFFL);
        body.u32(0xFFFFFFFFL).u32(nameIdx).u16(20).u16(20).u16(attrs.length).u16(0).u16(0).u16(0);
        for (int i = 0; i < attrs.length; i++) {
            long data = attrs[i].text != null ? raws[i] : attrs[i].data;
            body.u32(0xFFFFFFFFL).u32(attrNames[i]).u32(raws[i] == -1 ? 0xFFFFFFFFL : raws[i])
                    .u16(8).u8(0).u8(attrs[i].type).u32(data);
        }
        return this;
    }

    public AxmlBuilder end(String element) {
        body.u16(0x0103).u16(16).u32(24).u32(1).u32(0xFFFFFFFFL).u32(0xFFFFFFFFL).u32(s(element));
        return this;
    }

    public byte[] build() {
        int prefix = s("android");
        int uri = s("http://schemas.android.com/apk/res/android");
        Bytes ns = new Bytes();
        ns.u16(0x0100).u16(16).u32(24).u32(1).u32(0xFFFFFFFFL).u32(prefix).u32(uri);
        Bytes nsEnd = new Bytes();
        nsEnd.u16(0x0101).u16(16).u32(24).u32(1).u32(0xFFFFFFFFL).u32(prefix).u32(uri);

        byte[] pool = stringPool();
        Bytes file = new Bytes();
        file.u16(0x0003).u16(8).u32(8L + pool.length + ns.size() + body.size() + nsEnd.size());
        file.bytes(pool).bytes(ns.toByteArray()).bytes(body.toByteArray()).bytes(nsEnd.toByteArray());
        return file.toByteArray();
    }

    private byte[] stringPool() {
        Bytes data = new Bytes();
        int[] offsets = new int[strings.size()];
        for (int i = 0; i < strings.size(); i++) {
            offsets[i] = data.size();
            String str = strings.get(i);
            if (utf8) {
                byte[] b = str.getBytes(StandardCharsets.UTF_8);
                data.u8(str.length()).u8(b.length).bytes(b).u8(0);
            } else {
                data.u16(str.length()).bytes(str.getBytes(StandardCharsets.UTF_16LE)).u16(0);
            }
        }
        data.padTo4();
        int headerAndOffsets = 28 + 4 * strings.size();
        Bytes chunk = new Bytes();
        chunk.u16(0x0001).u16(28).u32(headerAndOffsets + data.size()).u32(strings.size()).u32(0)
                .u32(utf8 ? 0x100 : 0).u32(headerAndOffsets).u32(0);
        for (int off : offsets) {
            chunk.u32(off);
        }
        chunk.bytes(data.toByteArray());
        return chunk.toByteArray();
    }
}
