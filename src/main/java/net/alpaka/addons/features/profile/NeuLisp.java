package net.alpaka.addons.features.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Just enough of NEU's little Lisp to read the skill tree layouts: their perks keep their stats,
 * costs and the conditions of their lore lines as expressions like
 * {@code (+ 50 (/ (* (- level 1) 350) 199))}.
 *
 * It knows the functions those files use - arithmetic, comparisons, {@code if}, {@code pow},
 * {@code round}, {@code floor}, {@code list.new} and {@code list.at}, and the helpers NEU's prelude
 * defines ({@code min}, {@code max}, {@code round-decimals}, {@code id}). A value is a Double, a
 * String or a List; {@code :NAME} is the string "NAME", and a name with no value is its own name,
 * which is how a powder's name ({@code MITHRIL}) comes out of {@code powder}. Anything it does not
 * understand evaluates to null, and the line it was for is left as NEU would show an error.
 */
final class NeuLisp {
    private NeuLisp() {}

    /** Evaluates the expression with these variables; null when it cannot be read. */
    static Object eval(String source, Map<String, Object> vars, Function<String, Double> perkLevel) {
        if (source == null || source.isBlank()) return null;
        try {
            int[] at = {0};
            Object tree = read(source, at, 0);
            return eval(tree, vars, perkLevel);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    /** A name in the source, as opposed to a string literal. */
    private record Symbol(String name) {}

    /**
     * The deepest nesting read. The layouts come from NEU's repository over the network, so a broken
     * or hostile file could nest deep enough to overflow the stack, which no catch here would stop;
     * 64 is far more than any real perk needs. As eval only walks what read built, this bounds it too.
     */
    static final int MAX_DEPTH = 64;

    private static Object read(String s, int[] at, int depth) {
        skip(s, at);
        char c = s.charAt(at[0]);
        if (c == '(') {
            if (depth >= MAX_DEPTH) throw new IllegalArgumentException("nested too deep");
            at[0]++;
            List<Object> list = new ArrayList<>();
            while (true) {
                skip(s, at);
                if (s.charAt(at[0]) == ')') {
                    at[0]++;
                    return list;
                }
                list.add(read(s, at, depth + 1));
            }
        }
        if (c == '"') {
            int end = s.indexOf('"', at[0] + 1);
            String text = s.substring(at[0] + 1, end);
            at[0] = end + 1;
            return text;
        }
        int start = at[0];
        while (at[0] < s.length() && !Character.isWhitespace(s.charAt(at[0])) && s.charAt(at[0]) != '(' && s.charAt(at[0]) != ')') at[0]++;
        String atom = s.substring(start, at[0]);
        if (atom.startsWith(":")) return atom.substring(1);
        try {
            return Double.parseDouble(atom);
        } catch (NumberFormatException notANumber) {
            return new Symbol(atom);
        }
    }

    private static void skip(String s, int[] at) {
        while (Character.isWhitespace(s.charAt(at[0]))) at[0]++;
    }

    private static Object eval(Object node, Map<String, Object> vars, Function<String, Double> perkLevel) {
        if (node instanceof Symbol sym) {
            if (sym.name().equals("true")) return 1.0;
            if (sym.name().equals("false") || sym.name().equals("nil")) return 0.0;
            return vars.containsKey(sym.name()) ? vars.get(sym.name()) : sym.name();
        }
        if (!(node instanceof List<?> list)) return node;
        if (list.isEmpty() || !(list.get(0) instanceof Symbol head)) return null;
        if (head.name().equals("if")) {
            Object test = eval(list.get(1), vars, perkLevel);
            return eval(truthy(test) ? list.get(2) : list.size() > 3 ? list.get(3) : 0.0, vars, perkLevel);
        }
        List<Object> args = new ArrayList<>();
        for (int i = 1; i < list.size(); i++) args.add(eval(list.get(i), vars, perkLevel));
        return switch (head.name()) {
            case "+" -> {
                double sum = 0;
                for (Object a : args) sum += num(a);
                yield sum;
            }
            case "*" -> {
                double product = 1;
                for (Object a : args) product *= num(a);
                yield product;
            }
            case "-" -> args.size() == 1 ? -num(args.get(0)) : num(args.get(0)) - num(args.get(1));
            case "/" -> num(args.get(0)) / num(args.get(1));
            case "pow" -> Math.pow(num(args.get(0)), num(args.get(1)));
            case "round" -> (double) Math.round(num(args.get(0)));
            case "floor" -> Math.floor(num(args.get(0)));
            case "ceil" -> Math.ceil(num(args.get(0)));
            case "min" -> Math.min(num(args.get(0)), num(args.get(1)));
            case "max" -> Math.max(num(args.get(0)), num(args.get(1)));
            case "round-decimals" -> {
                double scale = Math.pow(10, num(args.get(1)));
                yield Math.round(num(args.get(0)) * scale) / scale;
            }
            case "lt" -> num(args.get(0)) < num(args.get(1)) ? 1.0 : 0.0;
            case "gt" -> num(args.get(0)) > num(args.get(1)) ? 1.0 : 0.0;
            case "=" -> args.get(0) instanceof Double a && args.get(1) instanceof Double b
                    ? (a.doubleValue() == b.doubleValue() ? 1.0 : 0.0)
                    : (String.valueOf(args.get(0)).equals(String.valueOf(args.get(1))) ? 1.0 : 0.0);
            case "id" -> args.get(0);
            case "list.new" -> args;
            case "list.at" -> ((List<?>) args.get(0)).get((int) num(args.get(1)));
            case "other-perk-level" -> perkLevel.apply(String.valueOf(args.get(0)));
            default -> null;
        };
    }

    private static double num(Object value) {
        if (value instanceof Double d) return d;
        if (value instanceof String s) return Double.parseDouble(s);
        throw new IllegalArgumentException("not a number: " + value);
    }

    static boolean truthy(Object value) {
        if (value == null) return false;
        if (value instanceof Double d) return d != 0;
        if (value instanceof String s) return !s.isEmpty();
        return true;
    }
}
