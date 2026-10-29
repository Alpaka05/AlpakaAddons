package net.alpaka.addons.features.dungeon.room;

import net.alpaka.addons.features.dungeon.puzzle.DungeonPuzzle;
import net.alpaka.addons.utils.SkyblockUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The "Puzzles" section of the tab list during a run: which puzzles this dungeon has, and whether
 * each is open, solved or failed (rooms.md 4).
 *
 * Hypixel writes one line per puzzle, {@code " Creeper Beams: [✦]"}, with ✦ open, ✔ solved and ✖
 * failed (then the player who failed it in brackets), and {@code " ???: [✦]"} for one nobody has
 * found yet; above them a header {@code "Puzzles: (3)"}. The lines arrive unordered here
 * ({@link SkyblockUtils#getTabListLines()} reads a hash set), so each one is matched on its own with
 * an anchored pattern; nothing is read by position. A puzzle that is not listed at all - tab list
 * hidden, or not parsed yet - has no status, and nothing is blocked because of it.
 */
public final class PuzzleTab {

    /** What the tab shows for a puzzle. */
    public enum Status {
        /** ✦: found, not solved yet. Also every "???" line. */
        OPEN,
        /** ✔ */
        SOLVED,
        /** ✖ */
        FAILED;

        static Status of(char symbol) {
            return switch (symbol) {
                case '✔' -> SOLVED;
                case '✖' -> FAILED;
                default -> OPEN;
            };
        }

        /** Whether a solver for this puzzle has nothing left to show. */
        public boolean done() {
            return this != OPEN;
        }
    }

    /** One puzzle line: the puzzle, its status, and who failed it if anyone (else null). */
    public record Entry(DungeonPuzzle puzzle, Status status, String player) {}

    /**
     * The parsed section. {@code count} is the header's number, -1 without a header;
     * {@code undiscovered} counts the "???" lines.
     */
    public record Parsed(int count, Map<DungeonPuzzle, Entry> entries, int undiscovered) {
        static final Parsed NONE = new Parsed(-1, Collections.emptyMap(), 0);

        /** Whether the tab shows a Puzzles section at all. */
        public boolean present() {
            return count >= 0 || !entries.isEmpty() || undiscovered > 0;
        }

        /** The puzzle's status, or null when the tab does not list it. */
        public Status statusOf(DungeonPuzzle puzzle) {
            Entry entry = entries.get(puzzle);
            return entry == null ? null : entry.status();
        }

        public List<Entry> list() {
            return new ArrayList<>(entries.values());
        }
    }

    /** {@code "Three Weirdos: [✦]"}, {@code "Higher Or Lower: [✖] (Player)"}, {@code "???: [✦]"}; trimmed. */
    static final Pattern LINE = Pattern.compile(
            "^(?<name>[A-Za-z]+(?: [A-Za-z]+)*|\\?\\?\\?): \\[(?<status>[✦✔✖])] ?(?:\\((?<player>\\w{1,16})\\))?$");
    static final Pattern HEADER = Pattern.compile("^Puzzles: \\((?<count>\\d+)\\)$");

    private static List<String> lastLines = null;
    private static Parsed lastParsed = Parsed.NONE;

    private PuzzleTab() {}

    /**
     * Parses tab lines, in any order. Lines are trimmed first. Names that are not one of the ten
     * puzzles are ignored, so another widget's line that happens to look alike cannot get in.
     */
    public static Parsed parse(List<String> lines) {
        int count = -1;
        int undiscovered = 0;
        Map<DungeonPuzzle, Entry> entries = new EnumMap<>(DungeonPuzzle.class);
        for (String raw : lines) {
            if (raw == null) continue;
            String line = raw.trim();
            if (line.isEmpty()) continue;
            Matcher header = HEADER.matcher(line);
            if (header.matches()) {
                count = Integer.parseInt(header.group("count"));
                continue;
            }
            Matcher m = LINE.matcher(line);
            if (!m.matches()) continue;
            String name = m.group("name");
            if (name.equals("???")) {
                undiscovered++;
                continue;
            }
            DungeonPuzzle puzzle = DungeonPuzzle.byTabName(name);
            if (puzzle == null) continue;
            entries.put(puzzle, new Entry(puzzle, Status.of(m.group("status").charAt(0)), m.group("player")));
        }
        return new Parsed(count, Collections.unmodifiableMap(entries), undiscovered);
    }

    /**
     * The tab as it is now. The tab list snapshot is shared per tick, so the parse is redone only
     * when a new snapshot arrives.
     */
    public static Parsed current() {
        List<String> lines = SkyblockUtils.getTabListLines();
        if (lines != lastLines) {
            lastLines = lines;
            lastParsed = parse(lines);
        }
        return lastParsed;
    }

    /** The puzzle's live status, or null when the tab does not list it. */
    public static Status status(DungeonPuzzle puzzle) {
        return current().statusOf(puzzle);
    }

    /** World change or disconnect. */
    public static void reset() {
        lastLines = null;
        lastParsed = Parsed.NONE;
    }
}
