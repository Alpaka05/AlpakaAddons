// Select All's item name prefixes (NAME_PREFIXES below): solution data adapted from Odin by
// odtheking, BSD-3-Clause, see CREDITS.md. The rest of this file is the mod's own.
package net.alpaka.addons.features.dungeon.terminal;

import net.alpaka.addons.features.dungeon.terminal.TerminalSolution.Mark;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Works out a terminal's solution from its chest (terminals.md 3, critic.md C3-C6). Pure, so every
 * terminal can be tested from plain slot data.
 *
 * Every solve reads the whole chest as it is now; nothing depends on the order or the kind of the
 * packets that filled it, and nothing assumes the items sit in the play area, since Hypixel has
 * moved them before (the play area only decides when the chest counts as loaded). Filler - black
 * panes with a blank name - is never part of a solution.
 *
 * - Correct All the Panes: the red panes are to click, the lime ones done.
 * - Change All to Same Color: the target color is the one that needs the fewest clicks in total. It
 *   is locked in the session's state once every play slot is filled, and chosen again only when
 *   panes turn up in other slots, so the counts never jump while the player works through them.
 *   Each pane gets its signed count: left clicks step forward through red, orange, yellow, green,
 *   blue and back to red, right clicks step back, whichever is shorter.
 * - Click in Order: the three red panes with the lowest stack counts are CLICK, SECOND and THIRD,
 *   labelled with their number; lime panes are done.
 * - What Starts With and Select All: the matching items not clicked yet. An item counts as clicked
 *   once it glints, which is how Hypixel marks it. The few items that always glint (a nether star,
 *   an enchanted book) cannot show that, so for them the session's own record decides: the player
 *   clicked it and the server answered.
 * - Click the Button on Time: the melody fields (target column, moving pane, active row, its
 *   button) with the button marked CLICK. Nothing about it changes at the moment the pane lines up
 *   (design.md 1.8).
 *
 * Clicks the server has not answered yet count as done only with {@code predict}: a pending red
 * pane as lime, a pending Change All to Same Color pane as one step further per click, and so on.
 * Without it the solution is what the server confirmed, and the pending mark is drawn on its own.
 * Click the Button on Time is never predicted.
 */
public final class TerminalSolver {

    /** Change All to Same Color's colors in the order a left click steps through them. */
    static final String[] RUBIX_CYCLE = {"red", "orange", "yellow", "green", "blue"};

    /** The sixteen dye colors as TerminalItem names them. */
    private static final List<String> DYE_COLORS = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
            "cyan", "purple", "blue", "brown", "green", "red", "black");

    /*
     * Select All: how Hypixel's (Minecraft 1.8) item names start for each color, beyond the color
     * word itself. Bone Meal, Ink Sac, Lapis Lazuli, Cocoa Beans, Cactus Green, Rose Red and
     * Dandelion Yellow are the 1.8 dyes; plain "Wool" is white wool; "Silver" is 1.8's light gray.
     * Solution data adapted from Odin by odtheking, BSD-3-Clause, see CREDITS.md.
     */
    private static final Map<String, List<String>> NAME_PREFIXES = Map.of(
            "white", List.of("white", "bone", "wool"),
            "black", List.of("black", "ink"),
            "blue", List.of("blue", "lapis"),
            "brown", List.of("brown", "cocoa"),
            "green", List.of("green", "cactus"),
            "red", List.of("red", "rose"),
            "yellow", List.of("yellow", "dandelion"),
            "light_gray", List.of("light gray", "silver"));

    private TerminalSolver() {}

    /**
     * @param kind       the terminal
     * @param plainTitle its chest title, formatting stripped (the letter or color it asks for)
     * @param chest      every chest slot from 0, in order: {@code chest.get(i).slot() == i}
     * @param state      the session's clicks, pending clicks and locks
     */
    public static TerminalSolution solve(TerminalKind kind, String plainTitle, List<TerminalItem> chest,
                                         TerminalSession.State state) {
        return solve(kind, plainTitle, chest, state, state != null && state.predict());
    }

    /**
     * The same with pending clicks counted as done or not, whatever the state says: the
     * wrong-click refusal always counts them, so that a fast but right second click is not
     * refused for a first one the server has not answered yet (design.md 4.7).
     */
    public static TerminalSolution solve(TerminalKind kind, String plainTitle, List<TerminalItem> chest,
                                         TerminalSession.State state, boolean predict) {
        TerminalSession.State st = state == null ? new TerminalSession.State() : state;
        TerminalItem[] items = bySlot(chest);
        boolean loaded = loaded(kind, items);
        TerminalSolution.Builder out = TerminalSolution.builder(kind).loaded(loaded);
        switch (kind) {
            case PANES -> panes(items, loaded, st, predict, out);
            case RUBIX -> rubix(items, loaded, st, predict, out);
            case NUMBERS -> numbers(items, loaded, st, predict, out);
            case STARTS_WITH -> startsWith(kind.argument(plainTitle), items, loaded, st, predict, out);
            case SELECT_ALL -> selectAll(selectColor(kind.argument(plainTitle)), items, loaded, st, predict, out);
            case MELODY -> melody(items, out);
        }
        return out.build();
    }

    // --- reading the chest ---

    /** The chest by slot index; a slot the list does not have reads as empty. */
    private static TerminalItem[] bySlot(List<TerminalItem> chest) {
        TerminalItem[] items = new TerminalItem[TerminalSolution.MAX_SLOTS];
        if (chest != null) {
            for (TerminalItem item : chest) {
                if (item != null && item.slot() >= 0 && item.slot() < items.length) items[item.slot()] = item;
            }
        }
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) items[i] = TerminalItem.empty(i);
        }
        return items;
    }

    /** Every play slot holds something; until then a solution may be missing pieces. */
    private static boolean loaded(TerminalKind kind, TerminalItem[] items) {
        for (int slot : kind.playSlots()) {
            if (items[slot].empty()) return false;
        }
        return true;
    }

    /** Neither empty nor filler. */
    private static boolean real(TerminalItem item) {
        return !item.empty() && !item.filler();
    }

    private static boolean pane(TerminalItem item, String color) {
        return real(item) && (color + "_stained_glass_pane").equals(item.itemId());
    }

    // --- Correct All the Panes ---

    private static void panes(TerminalItem[] items, boolean loaded, TerminalSession.State state, boolean predict,
                              TerminalSolution.Builder out) {
        int puzzle = 0;
        int toDo = 0;
        for (TerminalItem item : items) {
            boolean red = pane(item, "red");
            if (!red && !pane(item, "lime")) continue;
            puzzle++;
            // Every click turns a pane over, so an odd number of unanswered ones turns it once more.
            if (predict && state.pendingCount(item.slot()) % 2 == 1) red = !red;
            out.mark(item.slot(), red ? Mark.CLICK : Mark.DONE);
            if (red) toDo++;
        }
        out.solved(loaded && puzzle > 0 && toDo == 0);
    }

    // --- Change All to Same Color ---

    /** A pane's place in {@link #RUBIX_CYCLE}, or -1 for anything else. */
    static int rubixColor(TerminalItem item) {
        if (!real(item) || !item.itemId().endsWith("_stained_glass_pane")) return -1;
        for (int i = 0; i < RUBIX_CYCLE.length; i++) {
            if (RUBIX_CYCLE[i].equals(item.dyeColor())) return i;
        }
        return -1;
    }

    /** Signed clicks from {@code color} to {@code target}: up to two left clicks forward, else right clicks back. */
    static int rubixClicks(int color, int target) {
        int n = RUBIX_CYCLE.length;
        int forward = Math.floorMod(target - color, n);
        return forward <= n / 2 ? forward : forward - n;
    }

    /** The target with the fewest clicks in total over {@code colors} (-1 entries skipped); the first such on a tie. */
    static int rubixTarget(int[] colors) {
        int best = -1;
        int bestCost = Integer.MAX_VALUE;
        for (int target = 0; target < RUBIX_CYCLE.length; target++) {
            int cost = 0;
            for (int color : colors) {
                if (color >= 0) cost += Math.abs(rubixClicks(color, target));
            }
            if (cost < bestCost) {
                bestCost = cost;
                best = target;
            }
        }
        return best;
    }

    private static void rubix(TerminalItem[] items, boolean loaded, TerminalSession.State state, boolean predict,
                              TerminalSolution.Builder out) {
        int[] colors = new int[items.length];
        long paneSlots = 0L;
        long filledSlots = 0L;
        for (int i = 0; i < items.length; i++) {
            colors[i] = rubixColor(items[i]);
            if (colors[i] >= 0) paneSlots |= 1L << i;
            if (!items[i].empty()) filledSlots |= 1L << i;
        }
        // Lock the target on the confirmed colors once they are all there. Choose again only if
        // panes now sit where there were none or the other way round; a slot that is empty for
        // a moment (Hypixel sends blank updates) changes nothing.
        boolean moved = state.rubixTarget() >= 0 && ((paneSlots ^ state.rubixSlots()) & filledSlots) != 0;
        if (loaded && paneSlots != 0 && (state.rubixTarget() < 0 || moved)) {
            state.lockRubixTarget(rubixTarget(colors), paneSlots);
        }
        int target = state.rubixTarget();
        if (target < 0) return;

        boolean allThere = true;
        for (int i = 0; i < items.length; i++) {
            int color = colors[i];
            if (color < 0) continue;
            if (predict) color = Math.floorMod(color + state.pendingSteps(i), RUBIX_CYCLE.length);
            int clicks = rubixClicks(color, target);
            out.clicks(i, clicks);
            if (clicks == 0) {
                out.mark(i, Mark.DONE);
            } else {
                allThere = false;
                out.mark(i, Mark.CLICK).label(i, Integer.toString(clicks));
            }
        }
        out.solved(loaded && paneSlots != 0 && allThere);
    }

    // --- Click in Order ---

    private static void numbers(TerminalItem[] items, boolean loaded, TerminalSession.State state, boolean predict,
                                TerminalSolution.Builder out) {
        List<TerminalItem> toDo = new ArrayList<>();
        int puzzle = 0;
        for (TerminalItem item : items) {
            boolean red = pane(item, "red");
            if (!red && !pane(item, "lime")) continue;
            puzzle++;
            // A click turns the pane lime; with Predict Clicks one the server has not answered already counts.
            if (red && !(predict && state.isPending(item.slot()))) {
                toDo.add(item);
            } else {
                out.mark(item.slot(), Mark.DONE);
            }
        }
        // The number is the stack size; equal ones (which should not happen) keep their chest order.
        toDo.sort(Comparator.comparingInt(TerminalItem::count).thenComparingInt(TerminalItem::slot));
        // While the chest is still filling, a lower number may simply not be there yet; mark the
        // order only once it is loaded or the lowest red one follows on from the lime ones.
        int done = puzzle - toDo.size();
        boolean sure = loaded || toDo.isEmpty() || toDo.get(0).count() == done + 1;
        Mark[] order = {Mark.CLICK, Mark.SECOND, Mark.THIRD};
        for (int i = 0; sure && i < toDo.size(); i++) {
            TerminalItem item = toDo.get(i);
            if (i < order.length) {
                out.mark(item.slot(), order[i]).label(item.slot(), Integer.toString(item.count()));
            }
            // The fourth and later are part of the puzzle but not to click yet: OTHER, like Odin.
        }
        out.solved(loaded && puzzle > 0 && toDo.isEmpty());
    }

    // --- What Starts With and Select All ---

    /** Whether {@code item} was clicked already: see the class description. */
    static boolean clicked(TerminalItem item, TerminalSession.State state, boolean predict) {
        if (predict && state.isPending(item.slot())) return true;
        // The glint is the server's own word. An answered click without it was not taken (the
        // server sends the slot back either way), so it only counts where a glint cannot show.
        if (!item.alwaysGlints()) return item.glint();
        TerminalItem answered = state.confirmedAs(item.slot());
        // An answer for what is still the same item; after a reset the slot holds something else.
        return answered != null && answered.itemId().equals(item.itemId()) && answered.name().equals(item.name());
    }

    private static void startsWith(String letter, TerminalItem[] items, boolean loaded, TerminalSession.State state,
                                   boolean predict, TerminalSolution.Builder out) {
        if (letter == null || letter.isEmpty()) return;
        markMatching(items, loaded, state, predict, out, item -> startsWithIgnoreCase(item.name().trim(), letter));
    }

    /** The dye color Select All asks for ("LIGHT BLUE" -> "light_blue", "SILVER" -> "light_gray"), or null. */
    static String selectColor(String titleWords) {
        if (titleWords == null) return null;
        String color = titleWords.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "_");
        if (color.equals("silver")) color = "light_gray";
        return DYE_COLORS.contains(color) ? color : null;
    }

    /** Whether {@code item} has the {@code color} Select All asks for, by its id or its name. */
    static boolean hasColor(TerminalItem item, String color) {
        if (color == null || !real(item) || "black_stained_glass_pane".equals(item.itemId())) return false;
        if (color.equals(item.dyeColor())) return true;
        String name = item.name().trim().toLowerCase(Locale.ROOT);
        List<String> prefixes = NAME_PREFIXES.getOrDefault(color, List.of(color.replace('_', ' ')));
        for (String prefix : prefixes) {
            // A whole word, so "red" does not take "Redstone" and "blue" never "Light Blue Wool".
            if (name.startsWith(prefix) && (name.length() == prefix.length() || name.charAt(prefix.length()) == ' ')) {
                return true;
            }
        }
        return false;
    }

    private static void selectAll(String color, TerminalItem[] items, boolean loaded, TerminalSession.State state,
                                  boolean predict, TerminalSolution.Builder out) {
        if (color == null) return;
        markMatching(items, loaded, state, predict, out, item -> hasColor(item, color));
    }

    private static void markMatching(TerminalItem[] items, boolean loaded, TerminalSession.State state, boolean predict,
                                     TerminalSolution.Builder out, Predicate<TerminalItem> matches) {
        int puzzle = 0;
        int toDo = 0;
        for (TerminalItem item : items) {
            if (!real(item) || item.name().isBlank() || !matches.test(item)) continue;
            puzzle++;
            if (clicked(item, state, predict)) {
                out.mark(item.slot(), Mark.DONE);
            } else {
                out.mark(item.slot(), Mark.CLICK);
                toDo++;
            }
        }
        out.solved(loaded && puzzle > 0 && toDo == 0);
    }

    private static boolean startsWithIgnoreCase(String text, String prefix) {
        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    // --- Click the Button on Time ---

    private static void melody(TerminalItem[] items, TerminalSolution.Builder out) {
        int target = -1;
        int moving = -1;
        int button = -1;
        for (TerminalItem item : items) {
            if (!real(item)) continue;
            int slot = item.slot();
            switch (item.itemId()) {
                // The magenta panes above and below the rows mark the column; the top one decides.
                case "magenta_stained_glass_pane" -> {
                    if (target < 0 || slot / 9 == 0) target = slot;
                }
                case "lime_stained_glass_pane" -> moving = slot;
                case "lime_terracotta" -> button = slot;
                default -> { }
            }
        }
        int activeRow = moving >= 0 ? moving / 9 : button >= 0 ? button / 9 : -1;
        if (button < 0 && activeRow >= 0) {
            // No lime button in sight: the active row's terracotta in the button column, if any.
            int guess = activeRow * 9 + 7;
            if (guess < items.length && items[guess].itemId().endsWith("_terracotta")) button = guess;
        }
        out.melody(target >= 0 ? target % 9 : -1, moving >= 0 ? moving % 9 : -1, activeRow, button);
        if (button >= 0) out.mark(button, Mark.CLICK);
    }
}
