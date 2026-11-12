package com.universalsmapi.core.axml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lightweight element tree produced by {@link AxmlParser}. Attribute keys are local names (namespace dropped). */
public final class XmlNode {
    public final String name;
    public final Map<String, String> attrs = new LinkedHashMap<>();
    public final List<XmlNode> children = new ArrayList<>();

    public XmlNode(String name) {
        this.name = name;
    }

    public String attr(String key) {
        return attrs.get(key);
    }

    public List<XmlNode> childrenNamed(String childName) {
        List<XmlNode> out = new ArrayList<>();
        for (XmlNode c : children) {
            if (c.name.equals(childName)) {
                out.add(c);
            }
        }
        return out;
    }

    public XmlNode firstChild(String childName) {
        for (XmlNode c : children) {
            if (c.name.equals(childName)) {
                return c;
            }
        }
        return null;
    }
}
