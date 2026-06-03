package com.offercatcher.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {
    }

    public static Object parse(String raw) {
        return new Parser(raw).parseValue();
    }

    public static String stringify(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) return "\"" + escape(s) + "\"";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> parts = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                parts.add(stringify(String.valueOf(entry.getKey())) + ":" + stringify(entry.getValue()));
            }
            return "{" + String.join(",", parts) + "}";
        }
        if (value instanceof Iterable<?> items) {
            List<String> parts = new ArrayList<>();
            for (Object item : items) parts.add(stringify(item));
            return "[" + String.join(",", parts) + "]";
        }
        return stringify(String.valueOf(value));
    }

    private static String escape(String s) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                default -> {
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }

    static class Parser {
        private final String raw;
        private int pos;

        Parser(String raw) {
            this.raw = raw == null ? "" : raw;
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= raw.length()) return Map.of();
            char c = raw.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBoolean();
            if (c == 'n') {
                pos += 4;
                return null;
            }
            return parseNumber();
        }

        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++;
            skipWhitespace();
            while (pos < raw.length() && raw.charAt(pos) != '}') {
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (pos < raw.length() && raw.charAt(pos) == ',') {
                    pos++;
                    skipWhitespace();
                }
            }
            expect('}');
            return map;
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++;
            skipWhitespace();
            while (pos < raw.length() && raw.charAt(pos) != ']') {
                list.add(parseValue());
                skipWhitespace();
                if (pos < raw.length() && raw.charAt(pos) == ',') {
                    pos++;
                    skipWhitespace();
                }
            }
            expect(']');
            return list;
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < raw.length()) {
                char c = raw.charAt(pos++);
                if (c == '"') break;
                if (c == '\\' && pos < raw.length()) {
                    char next = raw.charAt(pos++);
                    switch (next) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case '/' -> sb.append('/');
                        case 'u' -> {
                            if (pos + 4 > raw.length()) {
                                throw new IllegalArgumentException("Invalid unicode escape near position " + pos);
                            }
                            String hex = raw.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> sb.append(next);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Boolean parseBoolean() {
            if (raw.startsWith("true", pos)) {
                pos += 4;
                return true;
            }
            pos += 5;
            return false;
        }

        Number parseNumber() {
            int start = pos;
            while (pos < raw.length() && "-0123456789.eE".indexOf(raw.charAt(pos)) >= 0) pos++;
            String number = raw.substring(start, pos);
            if (number.contains(".") || number.contains("e") || number.contains("E")) return Double.parseDouble(number);
            return Long.parseLong(number);
        }

        void skipWhitespace() {
            while (pos < raw.length() && Character.isWhitespace(raw.charAt(pos))) pos++;
        }

        void expect(char expected) {
            if (pos >= raw.length() || raw.charAt(pos) != expected) {
                throw new IllegalArgumentException("Invalid JSON near position " + pos);
            }
            pos++;
        }
    }
}
