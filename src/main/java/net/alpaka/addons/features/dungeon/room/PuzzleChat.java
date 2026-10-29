package net.alpaka.addons.features.dungeon.room;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The puzzle result lines Hypixel prints to the whole party (rooms.md 5.2, puzzles-chat.md 1.3),
 * for solvers that want an early hint before the tab list updates. The tab list stays the authority:
 * only Three Weirdos and Tic Tac Toe print a "solved" line at all.
 *
 * Lines are matched with formatting stripped and trimmed, anchored at the start, so a party member
 * quoting one ("Party > x: PUZZLE FAIL! ...") does not count.
 */
public final class PuzzleChat {

    /** {@code PUZZLE SOLVED! <name> wasn't fooled by Baxter! Good job!}; an optional rank before the name. */
    public static final Pattern SOLVED = Pattern.compile("^PUZZLE SOLVED! (?:\\[[^\\]]+] )?(?<player>\\w{1,16}) (?<rest>.+)$");
    /** {@code PUZZLE FAIL! <name> killed a Blaze in the wrong order! Yikes!} */
    public static final Pattern FAILED = Pattern.compile("^PUZZLE FAIL! (?:\\[[^\\]]+] )?(?<player>\\w{1,16}) (?<rest>.+)$");
    /** Oruo's two lines for a wrong Quiz answer; either one means the Quiz failed. */
    public static final Pattern QUIZ_FAILED = Pattern.compile(
            "^\\[STATUE] Oruo the Omniscient: (?:Yikes|(?<player>\\w{1,16}) chose the wrong answer!.*)$");

    private PuzzleChat() {}

    /** The player named in a "PUZZLE SOLVED!" line, or null if {@code plain} is not one. */
    public static String solvedBy(String plain) {
        Matcher m = SOLVED.matcher(plain);
        return m.matches() ? m.group("player") : null;
    }

    /** The player named in a "PUZZLE FAIL!" line, or null if {@code plain} is not one. */
    public static String failedBy(String plain) {
        Matcher m = FAILED.matcher(plain);
        return m.matches() ? m.group("player") : null;
    }
}
