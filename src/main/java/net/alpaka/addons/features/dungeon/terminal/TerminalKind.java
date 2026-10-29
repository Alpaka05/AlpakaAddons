package net.alpaka.addons.features.dungeon.terminal;

import net.alpaka.addons.config.AlpakaConfig;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The six F7/M7 terminals, told apart by their chest title (terminals.md 3).
 *
 * Titles are matched in full, formatting stripped and trimmed, so a chest whose name merely
 * contains one never counts. The play area is where the terminal's items sit, by chest slot
 * (row * 9 + column), in the 0.27.2 layout: Click in Order has two rows of five (10 panes, was 14),
 * Click the Button on Time three rows (was four). It is for drawing a fixed grid only; solvers read
 * the whole chest, since layouts have changed before. {@link #rows()} is the chest size Hypixel
 * uses, which the real menu may still differ from.
 */
public enum TerminalKind {
    PANES("Correct All the Panes", "^Correct all the panes!$", 5, area(1, 3, 2, 6), c -> c.solvePanesTerminal),
    RUBIX("Change All to Same Color", "^Change all to same color!$", 5, area(1, 3, 3, 5), c -> c.solveRubixTerminal),
    NUMBERS("Click in Order", "^Click in order!$", 4, area(1, 2, 2, 6), c -> c.solveNumbersTerminal),
    STARTS_WITH("What Starts With", "^What starts with: '(\\w)'\\?$", 5, area(1, 3, 1, 7), c -> c.solveStartsWithTerminal),
    SELECT_ALL("Select All", "^Select all the ([\\w ]+) items!$", 6, area(1, 4, 1, 7), c -> c.solveSelectAllTerminal),
    /**
     * Since 0.27.2: rows 0 and 4 hold the magenta target pane among columns 1-5, rows 1-3 the panes
     * the lime one moves through (columns 1-5), and column 7 of those rows the three buttons, slots
     * 16, 25 and 34 (terminals.md 3.6). Column 6 and the old fourth button, slot 43, are filler now,
     * so they are not part of it; the grid it spans is still rows 0-4, columns 1-7, as Odin draws it.
     */
    MELODY("Click the Button on Time", "^Click the button on time!$", 6,
            join(area(0, 4, 1, 5), new int[]{16, 25, 34}), c -> c.solveMelodyTerminal);

    private final String displayName;
    private final Pattern title;
    private final int rows;
    private final int[] playSlots;
    private final Predicate<AlpakaConfig> toggle;

    TerminalKind(String displayName, String title, int rows, int[] playSlots, Predicate<AlpakaConfig> toggle) {
        this.displayName = displayName;
        this.title = Pattern.compile(title);
        this.rows = rows;
        // Ascending, so isPlaySlot can search it and "row by row" holds for every kind.
        this.playSlots = playSlots.clone();
        Arrays.sort(this.playSlots);
        this.toggle = toggle;
    }

    /** The slots of rows {@code r0..r1} and columns {@code c0..c1}, row by row. */
    private static int[] area(int r0, int r1, int c0, int c1) {
        int[] slots = new int[(r1 - r0 + 1) * (c1 - c0 + 1)];
        int i = 0;
        for (int r = r0; r <= r1; r++) {
            for (int c = c0; c <= c1; c++) slots[i++] = r * 9 + c;
        }
        return slots;
    }

    private static int[] join(int[] a, int[] b) {
        int[] all = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, all, a.length, b.length);
        return all;
    }

    /** The name in the settings and the custom window's header, in Title Case. */
    public String displayName() {
        return displayName;
    }

    /** The chest rows Hypixel opens this terminal with. */
    public int rows() {
        return rows;
    }

    /** The play area's slots in ascending order, i.e. row by row; a copy. */
    public int[] playSlots() {
        return playSlots.clone();
    }

    public boolean isPlaySlot(int slot) {
        return Arrays.binarySearch(playSlots, slot) >= 0;
    }

    /**
     * The rows and columns the play area spans, for laying out a fixed grid: {r0, r1, c0, c1}.
     * Click the Button on Time has gaps in it (column 6), which stay empty in such a grid.
     */
    public int[] playArea() {
        int r0 = Integer.MAX_VALUE, r1 = Integer.MIN_VALUE, c0 = Integer.MAX_VALUE, c1 = Integer.MIN_VALUE;
        for (int slot : playSlots) {
            r0 = Math.min(r0, slot / 9);
            r1 = Math.max(r1, slot / 9);
            c0 = Math.min(c0, slot % 9);
            c1 = Math.max(c1, slot % 9);
        }
        return new int[]{r0, r1, c0, c1};
    }

    public boolean isMelody() {
        return this == MELODY;
    }

    /** The Terminal Solvers master and this terminal's own switch. */
    public boolean enabled(AlpakaConfig cfg) {
        return cfg.terminalSolversEnabled && toggle.test(cfg);
    }

    public boolean enabled() {
        return enabled(AlpakaConfig.instance);
    }

    /** Whether {@code plainTitle} is this terminal's title. */
    public boolean matches(String plainTitle) {
        return plainTitle != null && title.matcher(plainTitle.trim()).matches();
    }

    /**
     * What the title asks for: the letter of What Starts With ("A"), the color words of Select All
     * ("LIGHT BLUE"); null for the others or another title.
     */
    public String argument(String plainTitle) {
        if (plainTitle == null) return null;
        Matcher m = title.matcher(plainTitle.trim());
        return m.matches() && m.groupCount() >= 1 ? m.group(1) : null;
    }

    /** The terminal with this title (formatting stripped), or null for any other chest. */
    public static TerminalKind byTitle(String plainTitle) {
        if (plainTitle == null) return null;
        String trimmed = plainTitle.trim();
        for (TerminalKind kind : values()) {
            if (kind.title.matcher(trimmed).matches()) return kind;
        }
        return null;
    }
}
