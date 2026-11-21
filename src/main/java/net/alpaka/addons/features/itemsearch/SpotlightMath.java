package net.alpaka.addons.features.itemsearch;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * The search window's calculator: what's typed is worked out when it reads as a sum, the way a
 * launcher's search does. Knows + - * / ^, brackets, the k/m/b/t suffixes SkyBlock prices come in
 * (2.5m), percent (20%), x and × for times, a few functions (sqrt, round, min, ...) and the price
 * of an item, {@code bz(enchanted diamond)} or {@code ah(hyperion)}, as the market lists it now.
 */
public final class SpotlightMath {
    private SpotlightMath() {}

    /** What a price function answers for an item name, or null when it does not know the item or price. */
    public interface Prices extends Function<String, Double> {}

    /**
     * The result of what was typed, or null when it isn't a sum: it does not parse, or it is a plain
     * number with nothing worked out. Price functions ask {@code prices}, which may be null.
     */
    public static Double evaluate(String input, Prices prices) {
        if (input == null) return null;
        String text = input.trim();
        if (text.isEmpty() || text.length() > 200) return null;
        Parser parser = new Parser(text.toLowerCase(Locale.ROOT), prices);
        try {
            double value = parser.expression();
            parser.skipSpaces();
            if (parser.pos != parser.text.length()) return null;
            if (!parser.worked) return null;
            if (Double.isNaN(value) || Double.isInfinite(value)) return null;
            return value;
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * A result as it reads best: grouped thousands and no trailing zeros; 2 decimals from a thousand
     * up, where coins are counted and more would be noise, else up to 6.
     */
    public static String format(double value) {
        if (Math.abs(value) >= 1e15) return String.format(Locale.ROOT, "%.6e", value);
        int scale = Math.abs(value) >= 1000 ? 2 : 6;
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros();
        if (rounded.scale() < 0) rounded = rounded.setScale(0);
        String plain = rounded.toPlainString();
        int dot = plain.indexOf('.');
        String whole = dot < 0 ? plain : plain.substring(0, dot);
        String fraction = dot < 0 ? "" : plain.substring(dot);
        boolean negative = whole.startsWith("-");
        if (negative) whole = whole.substring(1);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < whole.length(); i++) {
            if (i > 0 && (whole.length() - i) % 3 == 0) grouped.append(',');
            grouped.append(whole.charAt(i));
        }
        return (negative ? "-" : "") + grouped + fraction;
    }

    /** The result without grouping, for the clipboard, where it is pasted into a sign or a chat. */
    public static String plain(double value) {
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
        if (rounded.scale() < 0) rounded = rounded.setScale(0);
        return rounded.toPlainString();
    }

    private static final class Parser {
        final String text;
        final Prices prices;
        int pos = 0;
        /** Whether anything was worked out, not just a number read back. */
        boolean worked = false;

        Parser(String text, Prices prices) {
            this.text = text;
            this.prices = prices;
        }

        void skipSpaces() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) pos++;
        }

        boolean eat(char c) {
            skipSpaces();
            if (pos < text.length() && text.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        double expression() {
            double value = term();
            while (true) {
                if (eat('+')) {
                    value += term();
                    worked = true;
                } else if (eat('-')) {
                    value -= term();
                    worked = true;
                } else {
                    return value;
                }
            }
        }

        double term() {
            double value = power();
            while (true) {
                if (eat('*') || eat('x') || eat('×')) {
                    value *= power();
                    worked = true;
                } else if (eat('/') || eat('÷') || eat(':')) {
                    double by = power();
                    if (by == 0) throw new IllegalArgumentException("division by zero");
                    value /= by;
                    worked = true;
                } else {
                    return value;
                }
            }
        }

        double power() {
            double base = unary();
            if (eat('^')) {
                worked = true;
                return Math.pow(base, power());
            }
            return base;
        }

        double unary() {
            if (eat('-')) return -unary();
            if (eat('+')) return unary();
            return postfix();
        }

        double postfix() {
            double value = primary();
            while (true) {
                skipSpaces();
                if (pos >= text.length()) return value;
                char c = text.charAt(pos);
                double factor = switch (c) {
                    case 'k' -> 1e3;
                    case 'm' -> 1e6;
                    case 'b' -> 1e9;
                    case 't' -> 1e12;
                    case '%' -> 0.01;
                    default -> 0;
                };
                // A suffix only when no letter follows: "5m" but not the start of "5 max(...)".
                if (factor == 0 || (pos + 1 < text.length() && Character.isLetter(text.charAt(pos + 1)))) return value;
                pos++;
                value *= factor;
                worked = true;
            }
        }

        double primary() {
            skipSpaces();
            if (pos >= text.length()) throw new IllegalArgumentException("end");
            char c = text.charAt(pos);
            if (c == '(') {
                pos++;
                double value = expression();
                if (!eat(')')) throw new IllegalArgumentException(")");
                return value;
            }
            if (Character.isDigit(c) || c == '.') return number();
            if (Character.isLetter(c)) return function();
            throw new IllegalArgumentException("unexpected " + c);
        }

        /** A number, 1,000,000 with its thousands grouped or not. */
        double number() {
            int start = pos;
            StringBuilder digits = new StringBuilder();
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (Character.isDigit(c) || c == '.' || c == '_') {
                    if (c != '_') digits.append(c);
                    pos++;
                } else if (c == ',' && pos + 3 < text.length() + 1 && groupFollows(pos + 1)) {
                    pos++;
                } else {
                    break;
                }
            }
            if (digits.isEmpty() || pos == start) throw new IllegalArgumentException("number");
            return Double.parseDouble(digits.toString());
        }

        /** Whether exactly three digits come at {@code at}, and no fourth: a thousands group. */
        boolean groupFollows(int at) {
            if (at + 3 > text.length()) return false;
            for (int i = at; i < at + 3; i++) if (!Character.isDigit(text.charAt(i))) return false;
            return at + 3 == text.length() || !Character.isDigit(text.charAt(at + 3));
        }

        double function() {
            int start = pos;
            while (pos < text.length() && Character.isLetter(text.charAt(pos))) pos++;
            String name = text.substring(start, pos);
            switch (name) {
                case "pi" -> {
                    return Math.PI;
                }
                case "e" -> {
                    return Math.E;
                }
                default -> {
                }
            }
            if (!eat('(')) throw new IllegalArgumentException("function");
            if (name.equals("bz") || name.equals("ah") || name.equals("lb") || name.equals("bin") || name.equals("price")) {
                int close = text.indexOf(')', pos);
                if (close < 0 || prices == null) throw new IllegalArgumentException("price");
                String item = text.substring(pos, close).trim();
                pos = close + 1;
                Double price = prices.apply(item);
                if (price == null) throw new IllegalArgumentException("no price");
                worked = true;
                return price;
            }
            List<Double> args = new ArrayList<>();
            if (!eat(')')) {
                do {
                    args.add(expression());
                } while (eat(';') || eat(','));
                if (!eat(')')) throw new IllegalArgumentException(")");
            }
            worked = true;
            return apply(name, args);
        }

        static double apply(String name, List<Double> args) {
            if (args.isEmpty()) throw new IllegalArgumentException("arguments");
            double a = args.get(0);
            return switch (name) {
                case "sqrt" -> Math.sqrt(a);
                case "abs" -> Math.abs(a);
                case "round" -> args.size() > 1 ? Math.round(a * Math.pow(10, args.get(1))) / Math.pow(10, args.get(1)) : Math.round(a);
                case "floor" -> Math.floor(a);
                case "ceil" -> Math.ceil(a);
                case "log" -> Math.log10(a);
                case "ln" -> Math.log(a);
                case "min" -> args.stream().mapToDouble(Double::doubleValue).min().orElse(a);
                case "max" -> args.stream().mapToDouble(Double::doubleValue).max().orElse(a);
                default -> throw new IllegalArgumentException("unknown function " + name);
            };
        }
    }
}
