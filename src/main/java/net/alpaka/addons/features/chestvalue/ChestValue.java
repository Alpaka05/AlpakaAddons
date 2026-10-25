package net.alpaka.addons.features.chestvalue;

import net.alpaka.addons.features.market.Coins;
import net.alpaka.addons.features.market.MarketKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a whole menu is worth: every SkyBlock item in it at what {@link
 * net.alpaka.addons.features.itemvalue.ItemValue} says one is worth, the same items added up into
 * one line, and each line's share of the total.
 *
 * Kept free of the game, so the sums can be checked in tests: the panel hands over what it read
 * from the slots.
 */
public final class ChestValue {
    private ChestValue() {}

    /** How the lines are ordered. */
    public enum Sort {
        VALUE("Value"),
        EACH("Price Each"),
        AMOUNT("Amount");

        public final String title;

        Sort(String title) {
            this.title = title;
        }

        public Sort next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public static Sort of(int index) {
            return values()[Math.floorMod(index, values().length)];
        }
    }

    /**
     * One slot's stack: its SkyBlock id, the name it shows, how many, and what one is worth - NaN for
     * no price. {@code estimated} when some part of it has no price, so it is worth at least this.
     * {@code market} is where the price comes from when the item is priced as a whole, null when it
     * was added up from its parts. {@code handle} is the game's stack, which the sums ignore.
     */
    public record Stack(int slot, String id, String name, int count, double each, boolean estimated,
                        MarketKind market, Object handle) {
        boolean priced() {
            return !Double.isNaN(each) && each > 0;
        }
    }

    /** The same item from one or more slots, as one line of the list. */
    public static final class Entry {
        public final String id;
        public final String name;
        public final double each;
        public final MarketKind market;
        /** The first stack, for its icon and its colored name. */
        public final Object handle;
        public final List<Integer> slots = new ArrayList<>();
        public int count;
        public boolean estimated;

        Entry(Stack first) {
            this.id = first.id();
            this.name = first.name();
            this.each = first.each();
            this.market = first.market();
            this.handle = first.handle();
        }

        public boolean priced() {
            return !Double.isNaN(each) && each > 0;
        }

        public double total() {
            return priced() ? each * count : 0;
        }
    }

    /** The menu's lines, the priced ones in the chosen order, and what they come to. */
    public record Summary(List<Entry> priced, List<Entry> unpriced, double total, int stacks, int items) {
        public static final Summary EMPTY = new Summary(List.of(), List.of(), 0, 0, 0);

        /** Whether some item has no price, or is only partly priced, so the menu is worth at least the total. */
        public boolean incomplete() {
            if (!unpriced.isEmpty()) return true;
            for (Entry e : priced) if (e.estimated) return true;
            return false;
        }

        public double share(Entry entry) {
            return total > 0 ? entry.total() / total : 0;
        }

        /** The entry holding this slot, or null. */
        public Entry entryOf(int slot) {
            for (Entry e : priced) if (e.slots.contains(slot)) return e;
            for (Entry e : unpriced) if (e.slots.contains(slot)) return e;
            return null;
        }

        public boolean isEmpty() {
            return priced.isEmpty() && unpriced.isEmpty();
        }
    }

    /**
     * Adds the stacks up. Stacks are the same line when they are the same item under the same name
     * at the same price: sixty-four Enchanted Diamonds in five slots are one line, two Hyperions with
     * different upgrades are two.
     */
    public static Summary of(List<Stack> stacks, Sort sort) {
        Map<String, Entry> lines = new LinkedHashMap<>();
        int items = 0;
        for (Stack stack : stacks) {
            String key = stack.id() + '\u0000' + stack.name() + '\u0000'
                    + (stack.priced() ? Math.round(stack.each()) : "-");
            Entry entry = lines.computeIfAbsent(key, k -> new Entry(stack));
            entry.count += Math.max(1, stack.count());
            entry.estimated |= stack.estimated();
            entry.slots.add(stack.slot());
            items += Math.max(1, stack.count());
        }
        List<Entry> priced = new ArrayList<>();
        List<Entry> unpriced = new ArrayList<>();
        double total = 0;
        for (Entry e : lines.values()) {
            if (e.priced()) {
                priced.add(e);
                total += e.total();
            } else {
                unpriced.add(e);
            }
        }
        priced.sort(comparator(sort));
        unpriced.sort(Comparator.comparing((Entry e) -> -e.count).thenComparing(e -> e.name));
        return new Summary(List.copyOf(priced), List.copyOf(unpriced), total, stacks.size(), items);
    }

    static Comparator<Entry> comparator(Sort sort) {
        Comparator<Entry> byValue = Comparator.comparingDouble((Entry e) -> -e.total());
        Comparator<Entry> then = Comparator.comparing((Entry e) -> e.name).thenComparingInt(e -> e.slots.get(0));
        return switch (sort) {
            case VALUE -> byValue.thenComparing(then);
            case EACH -> Comparator.comparingDouble((Entry e) -> -e.each).thenComparing(byValue).thenComparing(then);
            case AMOUNT -> Comparator.comparingInt((Entry e) -> -e.count).thenComparing(byValue).thenComparing(then);
        };
    }

    /** The list as plain text, for the clipboard: a title line with the total, then one line per item. */
    public static String text(String title, Summary summary) {
        StringBuilder out = new StringBuilder();
        out.append(title).append(": ").append(summary.incomplete() ? "at least " : "")
                .append(Coins.full(Math.rint(summary.total()))).append(" coins (")
                .append(Coins.compact(summary.total())).append(")\n");
        for (Entry e : summary.priced()) {
            long percent = Math.round(summary.share(e) * 100);
            out.append(e.count > 1 ? e.count + "x " : "").append(e.name).append(" - ")
                    .append(e.estimated ? "~" : "").append(Coins.compact(e.total()))
                    .append(" (").append(percent < 1 ? "<1" : String.valueOf(percent)).append("%)\n");
        }
        for (Entry e : summary.unpriced()) {
            out.append(e.count > 1 ? e.count + "x " : "").append(e.name).append(" - no price\n");
        }
        return out.toString().stripTrailing();
    }
}
