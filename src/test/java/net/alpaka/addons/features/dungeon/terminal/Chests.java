package net.alpaka.addons.features.dungeon.terminal;

import java.util.ArrayList;
import java.util.List;

/**
 * Terminal chests for tests, built the way Hypixel fills them: black panes with a blank name
 * everywhere, the terminal's items put in by slot.
 */
final class Chests {

    private final TerminalItem[] items;

    private Chests(int rows) {
        items = new TerminalItem[rows * 9];
        for (int i = 0; i < items.length; i++) items[i] = filler(i);
    }

    static Chests rows(int rows) {
        return new Chests(rows);
    }

    static TerminalItem filler(int slot) {
        return TerminalItem.of(slot, "black_stained_glass_pane", "", 1, false);
    }

    /** An item with a name. */
    Chests put(int slot, String itemId, String name) {
        return put(slot, itemId, name, 1, false);
    }

    Chests put(int slot, String itemId, String name, int count, boolean glint) {
        items[slot] = TerminalItem.of(slot, itemId, name, count, glint);
        return this;
    }

    /** A stained glass pane of {@code color} with Hypixel's blank name. */
    Chests pane(int slot, String color) {
        return pane(slot, color, 1);
    }

    Chests pane(int slot, String color, int count) {
        items[slot] = TerminalItem.of(slot, color + "_stained_glass_pane", "", count, false);
        return this;
    }

    Chests clear(int slot) {
        items[slot] = TerminalItem.empty(slot);
        return this;
    }

    TerminalItem at(int slot) {
        return items[slot];
    }

    List<TerminalItem> list() {
        return new ArrayList<>(List.of(items));
    }
}
