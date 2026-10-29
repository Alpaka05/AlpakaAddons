package net.alpaka.addons.features.dungeon.puzzle;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The logic of Three Weirdos, apart from the game so it can be tested.
 *
 * Hypixel builds the puzzle from six fixed sets of three statements (puzzles-chat.md 2.3, the
 * official wiki's case table). The weirdos' names and who says which line are random, but in every
 * set exactly one chest holds the reward, and it is always the chest of the weirdo who says one of
 * six particular lines. So each of the 18 lines tells us what the speaker's own chest holds: six
 * mean "the reward is in my chest", the other twelve mean "my chest is empty". Two empty chests also
 * give the third away.
 *
 * A line is only taken when it matches one of the 18 statements in full. Several are the start of
 * another ("The reward is not in my chest" is how "The reward is not in my chest. They are both
 * lying" begins), and an unknown line must never count as either kind.
 */
public final class ThreeWeirdosRiddle {

    /** What a weirdo's statement says about the speaker's own chest. */
    public enum Verdict {
        /** The reward is in the speaker's chest. */
        REWARD,
        /** The speaker's chest is empty. */
        EMPTY
    }

    /** One weirdo's statement, recognised. */
    public record Statement(String npc, Verdict verdict) {}

    /**
     * An NPC's answer in chat, formatting stripped: {@code [NPC] Baxter: The reward is not in my chest!}.
     * Anchored at the start, so a party member quoting one ("Party > x: [NPC] Baxter: ...") is not taken.
     */
    public static final Pattern NPC_LINE = Pattern.compile("^\\[NPC] (?<name>[A-Z][a-z]+): (?<text>.+)$");

    /** The names Hypixel gives the weirdos (official wiki); only used to log a statement we do not know. */
    public static final List<String> NAMES = List.of(
            "Ardis", "Baxter", "Benson", "Carver", "Elmo", "Eveleth", "Hope", "Hugo", "Lino", "Luverne",
            "Madelia", "Marshall", "Melrose", "Montgomery", "Morris", "Ramsey", "Rose", "Victoria",
            "Virginia", "Willmar", "Winona");

    /**
     * Where the three weirdos stand, in the room frame (Y absolute), by slot. Each one's chest is the
     * block one step further along room X (puzzles-chat.md 2.5; Odin and Skyblocker agree).
     */
    private static final int[][] NPC_REL = {{13, 69, 24}, {15, 69, 25}, {17, 69, 24}};

    /** "Another weirdo's name" inside a statement. */
    private static final String NAME = "[A-Za-z]+";
    /**
     * Between two sentences of one statement. The sources disagree on the punctuation (the fandom
     * wiki has "reward we are all ..."), and the meaning does not depend on it.
     */
    private static final String STOP = "[.,!]? ";

    private static final Pattern[] REWARD = {
            statement("The reward is not in my chest"),
            statement("At least one of them is lying, and the reward is not in " + NAME + "'s chest"),
            statement("My chest doesn't have the reward" + STOP + "We are all telling the truth"),
            statement("My chest has the reward and I'm telling the truth"),
            statement("The reward isn't in any of our chests"),
            statement("Both of them are telling the truth" + STOP + "Also, " + NAME + " has the reward in their chest"),
    };

    private static final Pattern[] EMPTY = {
            statement("One of us is telling the truth"),
            statement("They are both telling the truth" + STOP + "The reward isn't in " + NAME + "'s chest"),
            statement("We are all telling the truth"),
            statement(NAME + " is telling the truth and the reward is in his chest"),
            statement("My chest doesn't have the reward" + STOP + "At least one of the others is telling the truth"),
            statement("One of the others is lying"),
            statement("They are both telling the truth, the reward is in " + NAME + "'s chest"),
            statement("They are both lying, the reward is in my chest"),
            statement("The reward is in my chest"),
            statement("The reward is not in my chest" + STOP + "They are both lying"),
            statement(NAME + " is telling the truth"),
            statement("My chest has the reward"),
    };

    private ThreeWeirdosRiddle() {}

    private static Pattern statement(String text) {
        return Pattern.compile(text.replace(" ", "\\s+"), Pattern.CASE_INSENSITIVE);
    }

    /** The statement in a chat line, or null if the line is not an NPC speaking one of the 18 lines. */
    public static Statement parse(String plainLine) {
        if (plainLine == null) return null;
        Matcher m = NPC_LINE.matcher(plainLine.trim());
        if (!m.matches()) return null;
        Verdict verdict = classify(m.group("text"));
        return verdict == null ? null : new Statement(m.group("name"), verdict);
    }

    /** The NPC name in a chat line shaped like an NPC answer, whatever it says, or null. */
    public static String speaker(String plainLine) {
        if (plainLine == null) return null;
        Matcher m = NPC_LINE.matcher(plainLine.trim());
        return m.matches() ? m.group("name") : null;
    }

    /**
     * What a statement says about the speaker's chest, or null for a line that is none of the 18.
     * Trailing punctuation is ignored (the sources differ between "." and "!"), and so is the
     * typographic apostrophe.
     */
    public static Verdict classify(String text) {
        if (text == null) return null;
        String t = text.replace('’', '\'').replace('‘', '\'').trim();
        t = t.replaceAll("[.!]+$", "").trim();
        if (t.isEmpty()) return null;
        Verdict found = null;
        int matches = 0;
        for (Pattern p : REWARD) {
            if (p.matcher(t).matches()) {
                found = Verdict.REWARD;
                matches++;
            }
        }
        for (Pattern p : EMPTY) {
            if (p.matcher(t).matches()) {
                found = Verdict.EMPTY;
                matches++;
            }
        }
        // The templates are written so that no line fits two; if one ever did, it decides nothing.
        return matches == 1 ? found : null;
    }

    /**
     * The slot (0..2) whose chest holds the reward, or -1 while that is not known yet.
     *
     * @param bySlot what each slot's weirdo said about their chest; null where they have not been asked
     */
    public static int answer(Verdict[] bySlot) {
        int reward = -1;
        int empty = 0;
        for (int i = 0; i < 3; i++) {
            Verdict v = bySlot[i];
            if (v == Verdict.REWARD) {
                // Two weirdos both pointing at their own chest cannot happen; trust neither.
                if (reward >= 0) return -1;
                reward = i;
            } else if (v == Verdict.EMPTY) {
                empty++;
            }
        }
        if (reward >= 0) return reward;
        // Exactly one chest holds the reward, so the last one left does.
        if (empty == 2) {
            for (int i = 0; i < 3; i++) {
                if (bySlot[i] == null) return i;
            }
        }
        return -1;
    }

    /**
     * The slots whose chest is known to be empty: the ones whose weirdo said so, and once the
     * reward's chest is known, every other one.
     */
    public static boolean[] empty(Verdict[] bySlot) {
        boolean[] out = new boolean[3];
        int reward = answer(bySlot);
        for (int i = 0; i < 3; i++) {
            out[i] = reward >= 0 ? i != reward : bySlot[i] == Verdict.EMPTY;
        }
        return out;
    }

    // --- the room ---

    /** The block a weirdo stands on in slot {@code slot}, in the room frame. */
    public static BlockPos npcRel(int slot) {
        int[] p = NPC_REL[slot];
        return new BlockPos(p[0], p[1], p[2]);
    }

    /** The chest of the weirdo in slot {@code slot}, in the room frame: one block further along room X. */
    public static BlockPos chestRel(int slot) {
        return npcRel(slot).offset(1, 0, 0);
    }

    /** The slot of a weirdo standing at room X/Z ({@code relX}, {@code relZ}), or -1 if no weirdo stands there. */
    public static int slotAt(int relX, int relZ) {
        for (int i = 0; i < NPC_REL.length; i++) {
            if (NPC_REL[i][0] == relX && NPC_REL[i][2] == relZ) return i;
        }
        return -1;
    }

    /** Whether {@code name} is one of the weirdo names Hypixel is known to use. */
    public static boolean knownName(String name) {
        return name != null && NAMES.contains(name.trim());
    }
}
