package com.openprompt.opa;

import java.util.*;

/**
 * Minimal JSON parser and writer with no external dependencies.
 * Supports the subset of JSON needed for OPA session history and data index files.
 */
class Json {

    // ---- Writer ----

    static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, 0);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder sb, Object value, int indent) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString(sb, (String) value);
        } else if (value instanceof Number) {
            sb.append(value);
        } else if (value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map) {
            writeObject(sb, (Map<String, Object>) value, indent);
        } else if (value instanceof List) {
            writeArray(sb, (List<Object>) value, indent);
        } else {
            writeString(sb, value.toString());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    private static void writeObject(StringBuilder sb, Map<String, Object> map, int indent) {
        if (map.isEmpty()) {
            sb.append("{}");
            return;
        }
        sb.append("{\n");
        int newIndent = indent + 2;
        Iterator<Map.Entry<String, Object>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            appendIndent(sb, newIndent);
            writeString(sb, entry.getKey());
            sb.append(": ");
            writeValue(sb, entry.getValue(), newIndent);
            if (it.hasNext()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        appendIndent(sb, indent);
        sb.append("}");
    }

    @SuppressWarnings("unchecked")
    private static void writeArray(StringBuilder sb, List<Object> list, int indent) {
        if (list.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        int newIndent = indent + 2;
        for (int i = 0; i < list.size(); i++) {
            appendIndent(sb, newIndent);
            writeValue(sb, list.get(i), newIndent);
            if (i < list.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        appendIndent(sb, indent);
        sb.append("]");
    }

    private static void appendIndent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append(' ');
        }
    }

    // ---- Parser ----

    static Object parse(String json) {
        Parser parser = new Parser(json.trim());
        return parser.parseValue();
    }

    private static class Parser {
        private final String input;
        private int pos;

        Parser(String input) {
            this.input = input;
            this.pos = 0;
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= input.length()) {
                throw new IllegalArgumentException("Unexpected end of JSON");
            }
            char c = input.charAt(pos);
            switch (c) {
                case '"': return parseString();
                case '{': return parseObject();
                case '[': return parseArray();
                case 't': case 'f': return parseBoolean();
                case 'n': return parseNull();
                default:
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        return parseNumber();
                    }
                    throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + pos);
            }
        }

        String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (pos < input.length()) {
                char c = input.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= input.length()) throw new IllegalArgumentException("Unexpected end in string escape");
                    char esc = input.charAt(pos++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            String hex = input.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                            break;
                        default:
                            throw new IllegalArgumentException("Invalid escape: \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (pos < input.length() && input.charAt(pos) == ',') {
                    pos++;
                } else {
                    break;
                }
            }
            skipWhitespace();
            expect('}');
            return map;
        }

        List<Object> parseArray() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (pos < input.length() && input.charAt(pos) == ',') {
                    pos++;
                } else {
                    break;
                }
            }
            skipWhitespace();
            expect(']');
            return list;
        }

        Number parseNumber() {
            int start = pos;
            if (pos < input.length() && input.charAt(pos) == '-') pos++;
            while (pos < input.length() && input.charAt(pos) >= '0' && input.charAt(pos) <= '9') pos++;
            boolean isFloat = false;
            if (pos < input.length() && input.charAt(pos) == '.') {
                isFloat = true;
                pos++;
                while (pos < input.length() && input.charAt(pos) >= '0' && input.charAt(pos) <= '9') pos++;
            }
            if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
                isFloat = true;
                pos++;
                if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) pos++;
                while (pos < input.length() && input.charAt(pos) >= '0' && input.charAt(pos) <= '9') pos++;
            }
            String numStr = input.substring(start, pos);
            if (isFloat) {
                return Double.parseDouble(numStr);
            }
            long val = Long.parseLong(numStr);
            if (val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
                return (int) val;
            }
            return val;
        }

        Boolean parseBoolean() {
            if (input.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (input.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Expected boolean at position " + pos);
        }

        Object parseNull() {
            if (input.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Expected null at position " + pos);
        }

        void skipWhitespace() {
            while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        void expect(char c) {
            skipWhitespace();
            if (pos >= input.length() || input.charAt(pos) != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at position " + pos);
            }
            pos++;
        }
    }
}
