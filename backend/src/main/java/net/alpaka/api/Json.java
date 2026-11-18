package net.alpaka.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough JSON to cut Hypixel's answers down to what the dashboard shows. Objects become
 * {@link LinkedHashMap}s, arrays {@link List}s, numbers keep their text ({@link Num}) so nothing is
 * rounded on the way through. A reader of its own keeps the server free of libraries.
 */
final class Json {
    private Json() {}

    /** A number as Hypixel wrote it. */
    record Num(String text) {}

    /** Deeper than this is no answer of Hypixel's, and would only cost stack. */
    private static final int MAX_DEPTH = 64;

    static Object parse(String text) {
        Reader r = new Reader(text);
        r.space();
        Object value = r.value(0);
        r.space();
        if (r.at < text.length()) throw r.error("Trailing text");
        return value;
    }

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String s) {
            string(s, out);
        } else if (value instanceof Num n) {
            out.append(n.text());
        } else if (value instanceof Boolean b) {
            out.append(b);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) out.append(',');
                first = false;
                string(String.valueOf(e.getKey()), out);
                out.append(':');
                write(e.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof List<?> list) {
            out.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) out.append(',');
                write(list.get(i), out);
            }
            out.append(']');
        } else if (value instanceof Number n) {
            out.append(n);
        } else {
            throw new IllegalArgumentException("Not JSON: " + value.getClass());
        }
    }

    private static void string(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        out.append('"');
    }

    private static final class Reader {
        private final String s;
        private int at;

        Reader(String s) {
            this.s = s;
        }

        IllegalArgumentException error(String what) {
            return new IllegalArgumentException(what + " at " + at);
        }

        void space() {
            while (at < s.length() && Character.isWhitespace(s.charAt(at))) at++;
        }

        Object value(int depth) {
            if (depth > MAX_DEPTH) throw error("Too deep");
            if (at >= s.length()) throw error("Unexpected end");
            char c = s.charAt(at);
            return switch (c) {
                case '{' -> object(depth);
                case '[' -> array(depth);
                case '"' -> string();
                case 't' -> word("true", Boolean.TRUE);
                case 'f' -> word("false", Boolean.FALSE);
                case 'n' -> word("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object(int depth) {
            Map<String, Object> map = new LinkedHashMap<>();
            at++;
            space();
            if (peek() == '}') {
                at++;
                return map;
            }
            while (true) {
                space();
                if (peek() != '"') throw error("Expected a key");
                String key = string();
                space();
                expect(':');
                space();
                map.put(key, value(depth + 1));
                space();
                char c = next();
                if (c == '}') return map;
                if (c != ',') throw error("Expected , or }");
            }
        }

        private List<Object> array(int depth) {
            List<Object> list = new ArrayList<>();
            at++;
            space();
            if (peek() == ']') {
                at++;
                return list;
            }
            while (true) {
                space();
                list.add(value(depth + 1));
                space();
                char c = next();
                if (c == ']') return list;
                if (c != ',') throw error("Expected , or ]");
            }
        }

        private String string() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (true) {
                char c = next();
                if (c == '"') return out.toString();
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                char e = next();
                switch (e) {
                    case '"', '\\', '/' -> out.append(e);
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> {
                        if (at + 4 > s.length()) throw error("Short \\u escape");
                        try {
                            out.append((char) Integer.parseInt(s, at, at + 4, 16));
                        } catch (NumberFormatException ex) {
                            throw error("Bad \\u escape");
                        }
                        at += 4;
                    }
                    default -> throw error("Bad escape");
                }
            }
        }

        private Num number() {
            int start = at;
            while (at < s.length() && "+-0123456789.eE".indexOf(s.charAt(at)) >= 0) at++;
            if (start == at) throw error("Unexpected character");
            String text = s.substring(start, at);
            try {
                Double.parseDouble(text);
            } catch (NumberFormatException e) {
                throw error("Bad number");
            }
            return new Num(text);
        }

        private Object word(String word, Object value) {
            if (!s.startsWith(word, at)) throw error("Unexpected character");
            at += word.length();
            return value;
        }

        private char peek() {
            if (at >= s.length()) throw error("Unexpected end");
            return s.charAt(at);
        }

        private char next() {
            char c = peek();
            at++;
            return c;
        }

        private void expect(char c) {
            if (next() != c) throw error("Expected " + c);
        }
    }
}
