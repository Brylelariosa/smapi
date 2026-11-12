package com.universalsmapi.core.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small dependency-free JSON reader that tolerates what SMAPI's own JSON loading tolerates in mod manifests and
 * config files: {@code //} and {@code /* *}{@code /} comments, trailing commas, single-quoted strings, unquoted
 * property names and a leading byte-order mark. (SMAPI's JsonHelper additionally retries after replacing curly
 * quotes; see {@link #parseSmapiStyle(String)}.)
 *
 * <p>Objects become {@link LinkedHashMap}, arrays {@link ArrayList}, integers {@link Long}, other numbers
 * {@link Double}.
 */
public final class LenientJson {
    private static final int MAX_DEPTH = 64;

    private final String s;
    private int i;

    private LenientJson(String s) {
        this.s = s;
    }

    /** Thrown for malformed input; carries the character offset. */
    public static final class JsonException extends Exception {
        private static final long serialVersionUID = 1L;
        public final int position;

        public JsonException(String message, int position) {
            super(message + " (at character " + position + ")");
            this.position = position;
        }
    }

    public static Object parse(String text) throws JsonException {
        LenientJson p = new LenientJson(text);
        if (p.s.startsWith("\uFEFF")) {
            p.i = 1;
        }
        p.skip();
        Object value = p.value(0);
        p.skip();
        if (p.i != p.s.length()) {
            throw new JsonException("Unexpected trailing content", p.i);
        }
        return value;
    }

    /** Parse like SMAPI: on failure, retry once with typographic quotes replaced by plain quotes. */
    public static Object parseSmapiStyle(String text) throws JsonException {
        try {
            return parse(text);
        } catch (JsonException first) {
            String repaired = text.replace('\u201C', '"').replace('\u201D', '"');
            if (repaired.equals(text)) {
                throw first;
            }
            return parse(repaired);
        }
    }

    // ---- typed accessors (never throw; wrong type yields the fallback) ----

    @SuppressWarnings("unchecked")
    public static Map<String, Object> asObject(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<String, Object>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> asArray(Object o) {
        return o instanceof List ? (List<Object>) o : new ArrayList<Object>();
    }

    public static String str(Map<String, Object> obj, String key) {
        Object v = obj.get(key);
        return v instanceof String ? (String) v : null;
    }

    public static boolean bool(Map<String, Object> obj, String key, boolean fallback) {
        Object v = obj.get(key);
        return v instanceof Boolean ? (Boolean) v : fallback;
    }

    public static long num(Map<String, Object> obj, String key, long fallback) {
        Object v = obj.get(key);
        return v instanceof Long ? (Long) v : (v instanceof Double ? (long) (double) (Double) v : fallback);
    }

    public static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        for (Object e : asArray(o)) {
            if (e instanceof String) {
                out.add((String) e);
            }
        }
        return out;
    }

    // ---- parser ----

    private Object value(int depth) throws JsonException {
        if (depth > MAX_DEPTH) {
            throw new JsonException("Nesting too deep", i);
        }
        if (i >= s.length()) {
            throw new JsonException("Unexpected end of input", i);
        }
        char c = s.charAt(i);
        if (c == '{') {
            return object(depth);
        }
        if (c == '[') {
            return array(depth);
        }
        if (c == '"' || c == '\'') {
            return string();
        }
        if (c == '-' || (c >= '0' && c <= '9')) {
            return number();
        }
        if (s.startsWith("true", i)) {
            i += 4;
            return Boolean.TRUE;
        }
        if (s.startsWith("false", i)) {
            i += 5;
            return Boolean.FALSE;
        }
        if (s.startsWith("null", i)) {
            i += 4;
            return null;
        }
        throw new JsonException("Unexpected character '" + c + "'", i);
    }

    private Map<String, Object> object(int depth) throws JsonException {
        Map<String, Object> map = new LinkedHashMap<>();
        i++; // {
        while (true) {
            skip();
            if (i >= s.length()) {
                throw new JsonException("Unterminated object", i);
            }
            if (s.charAt(i) == '}') {
                i++;
                return map;
            }
            String key = key();
            skip();
            if (i >= s.length() || s.charAt(i) != ':') {
                throw new JsonException("Expected ':' after property name", i);
            }
            i++;
            skip();
            map.put(key, value(depth + 1));
            skip();
            if (i < s.length() && s.charAt(i) == ',') {
                i++;
                continue;
            }
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                return map;
            }
            throw new JsonException("Expected ',' or '}'", i);
        }
    }

    private List<Object> array(int depth) throws JsonException {
        List<Object> list = new ArrayList<>();
        i++; // [
        while (true) {
            skip();
            if (i >= s.length()) {
                throw new JsonException("Unterminated array", i);
            }
            if (s.charAt(i) == ']') {
                i++;
                return list;
            }
            list.add(value(depth + 1));
            skip();
            if (i < s.length() && s.charAt(i) == ',') {
                i++;
                continue;
            }
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                return list;
            }
            throw new JsonException("Expected ',' or ']'", i);
        }
    }

    private String key() throws JsonException {
        char c = s.charAt(i);
        if (c == '"' || c == '\'') {
            return string();
        }
        int start = i;
        while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '_' || s.charAt(i) == '$')) {
            i++;
        }
        if (i == start) {
            throw new JsonException("Expected a property name", i);
        }
        return s.substring(start, i);
    }

    private String string() throws JsonException {
        char quote = s.charAt(i++);
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (i >= s.length()) {
                throw new JsonException("Unterminated string", i);
            }
            char c = s.charAt(i++);
            if (c == quote) {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (i >= s.length()) {
                throw new JsonException("Unterminated escape", i);
            }
            char e = s.charAt(i++);
            switch (e) {
                case 'n': sb.append('\n'); break;
                case 't': sb.append('\t'); break;
                case 'r': sb.append('\r'); break;
                case 'b': sb.append('\b'); break;
                case 'f': sb.append('\f'); break;
                case '/': sb.append('/'); break;
                case '\\': sb.append('\\'); break;
                case '"': sb.append('"'); break;
                case '\'': sb.append('\''); break;
                case 'u':
                    if (i + 4 > s.length()) {
                        throw new JsonException("Truncated \\u escape", i);
                    }
                    try {
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw new JsonException("Invalid \\u escape", i);
                    }
                    i += 4;
                    break;
                default:
                    throw new JsonException("Invalid escape '\\" + e + "'", i - 1);
            }
        }
    }

    private Object number() throws JsonException {
        int start = i;
        if (s.charAt(i) == '-') {
            i++;
        }
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            i++;
        }
        boolean isInt = true;
        if (i < s.length() && s.charAt(i) == '.') {
            isInt = false;
            i++;
            while (i < s.length() && Character.isDigit(s.charAt(i))) {
                i++;
            }
        }
        if (i < s.length() && (s.charAt(i) == 'e' || s.charAt(i) == 'E')) {
            isInt = false;
            i++;
            if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) {
                i++;
            }
            while (i < s.length() && Character.isDigit(s.charAt(i))) {
                i++;
            }
        }
        String text = s.substring(start, i);
        try {
            if (isInt) {
                return Long.valueOf(text);
            }
            return Double.valueOf(text);
        } catch (NumberFormatException e) {
            throw new JsonException("Invalid number '" + text + "'", start);
        }
    }

    /** Skip whitespace, // line comments and block comments. */
    private void skip() throws JsonException {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                i++;
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '/') {
                while (i < s.length() && s.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < s.length() && s.charAt(i + 1) == '*') {
                int end = s.indexOf("*/", i + 2);
                if (end < 0) {
                    throw new JsonException("Unterminated block comment", i);
                }
                i = end + 2;
            } else {
                return;
            }
        }
    }
}
