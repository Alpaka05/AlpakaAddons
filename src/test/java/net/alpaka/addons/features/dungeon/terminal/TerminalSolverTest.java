package net.alpaka.addons.features.dungeon.terminal;

import net.alpaka.addons.features.dungeon.terminal.TerminalSolution.Mark;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every terminal solved from realistic chests, in the 0.27.2 layouts (terminals.md 3). */
class TerminalSolverTest {

    private static final String PANES = "Correct all the panes!";
    private static final String RUBIX = "Change all to same color!";
    private static final String NUMBERS = "Click in order!";
    private static final String MELODY = "Click the button on time!";

    private static TerminalSolution solve(TerminalKind kind, String title, Chests chest, TerminalSession.State state) {
        return TerminalSolver.solve(kind, title, chest.list(), state);
    }

    // --- Correct All the Panes ---

    /** Five rows; red panes off, lime ones on, in the 3x5 play area. */
    private static Chests panes() {
        Chests chest = Chests.rows(5);
        for (int slot : TerminalKind.PANES.playSlots()) chest.pane(slot, "lime");
        for (int slot : new int[]{11, 13, 20, 24, 31, 33}) chest.pane(slot, "red");
        return chest;
    }

    @Test
    void panesMarksTheRedOnes() {
        TerminalSolution s = solve(TerminalKind.PANES, PANES, panes(), new TerminalSession.State());
        assertTrue(s.loaded());
        assertFalse(s.solved());
        for (int slot : new int[]{11, 13, 20, 24, 31, 33}) assertEquals(Mark.CLICK, s.mark(slot), "slot " + slot);
        assertEquals(Mark.DONE, s.mark(12));
        assertEquals(Mark.OTHER, s.mark(0), "filler");
        assertEquals(6, s.toClick());
        assertTrue(s.wrongClick(12, 0), "a lime pane would turn red again");
        assertFalse(s.wrongClick(11, 0));
    }

    @Test
    void panesLoadedAndSolved() {
        assertFalse(solve(TerminalKind.PANES, PANES, panes().clear(22), new TerminalSession.State()).loaded());
        Chests done = Chests.rows(5);
        for (int slot : TerminalKind.PANES.playSlots()) done.pane(slot, "lime");
        assertTrue(solve(TerminalKind.PANES, PANES, done, new TerminalSession.State()).solved());
    }

    @Test
    void panesPredictTurnsPendingPanesOver() {
        TerminalSession.State state = new TerminalSession.State().withPending(11, 100L).withPending(12, 100L);
        TerminalSolution confirmed = solve(TerminalKind.PANES, PANES, panes(), state);
        assertEquals(Mark.CLICK, confirmed.mark(11), "without Predict Clicks only the server's word counts");
        assertEquals(Mark.DONE, confirmed.mark(12));
        TerminalSolution predicted = solve(TerminalKind.PANES, PANES, panes(), state.withPredict(true));
        assertEquals(Mark.DONE, predicted.mark(11));
        assertEquals(Mark.CLICK, predicted.mark(12), "a click on a lime pane turns it red");
        // Twice is back where it was.
        state.withPending(11, 150L);
        assertEquals(Mark.CLICK, solve(TerminalKind.PANES, PANES, panes(), state).mark(11));
    }

    // --- Change All to Same Color ---

    @Test
    void rubixSignedClicks() {
        // Left steps forward red -> orange -> yellow -> green -> blue -> red; right steps back.
        assertEquals(0, TerminalSolver.rubixClicks(2, 2));
        assertEquals(1, TerminalSolver.rubixClicks(0, 1));
        assertEquals(2, TerminalSolver.rubixClicks(0, 2));
        assertEquals(-2, TerminalSolver.rubixClicks(0, 3));
        assertEquals(-1, TerminalSolver.rubixClicks(0, 4));
        assertEquals(1, TerminalSolver.rubixClicks(4, 0), "blue to red is one step forward");
        assertEquals(2, TerminalSolver.rubixClicks(3, 0));
        assertEquals(-2, TerminalSolver.rubixClicks(2, 0));
        assertEquals(-1, TerminalSolver.rubixClicks(1, 0));
    }

    /** 4 red, 3 orange, 2 yellow: red is the most common, but orange needs fewer clicks (6 against 7). */
    private static Chests rubix() {
        Chests chest = Chests.rows(5);
        String[] colors = {"red", "red", "red", "red", "orange", "orange", "orange", "yellow", "yellow"};
        int[] slots = TerminalKind.RUBIX.playSlots();
        for (int i = 0; i < slots.length; i++) chest.pane(slots[i], colors[i]);
        return chest;
    }

    @Test
    void rubixTakesTheFewestClickedTargetNotTheMostCommon() {
        TerminalSession.State state = new TerminalSession.State();
        TerminalSolution s = solve(TerminalKind.RUBIX, RUBIX, rubix(), state);
        assertEquals(1, state.rubixTarget(), "orange");
        assertTrue(s.loaded());
        int total = 0;
        for (int slot : TerminalKind.RUBIX.playSlots()) total += Math.abs(s.clicks(slot));
        assertEquals(6, total);
        assertEquals(1, s.clicks(12));
        assertEquals("1", s.label(12));
        assertEquals(Mark.CLICK, s.mark(12));
        assertEquals(0, s.clicks(22));
        assertEquals(Mark.DONE, s.mark(22));
        assertNull(s.label(22));
        assertEquals(-1, s.clicks(31));
        assertEquals("-1", s.label(31));
        // The right button only.
        assertFalse(s.wrongClick(12, 0));
        assertTrue(s.wrongClick(12, 1));
        assertFalse(s.wrongClick(31, 1));
        assertTrue(s.wrongClick(31, 0));
        assertTrue(s.wrongClick(22, 0), "already there");
    }

    @Test
    void rubixTargetIsMinimalOnRandomBoards() {
        Random random = new Random(26_2);
        for (int round = 0; round < 500; round++) {
            int[] colors = new int[9];
            for (int i = 0; i < 9; i++) colors[i] = random.nextInt(5);
            int chosen = TerminalSolver.rubixTarget(colors);
            int best = Integer.MAX_VALUE;
            for (int t = 0; t < 5; t++) best = Math.min(best, cost(colors, t));
            assertEquals(best, cost(colors, chosen));
            for (int c : colors) assertTrue(Math.abs(TerminalSolver.rubixClicks(c, chosen)) <= 2);
        }
    }

    private static int cost(int[] colors, int target) {
        int sum = 0;
        for (int c : colors) sum += Math.min(Math.floorMod(target - c, 5), Math.floorMod(c - target, 5));
        return sum;
    }

    @Test
    void rubixTargetStaysLockedWhileThePlayerWorks() {
        TerminalSession.State state = new TerminalSession.State();
        solve(TerminalKind.RUBIX, RUBIX, rubix(), state);
        assertEquals(1, state.rubixTarget());
        // Overshot: now red would be the cheapest, but the target does not jump.
        Chests later = Chests.rows(5);
        for (int slot : TerminalKind.RUBIX.playSlots()) later.pane(slot, "red");
        later.pane(32, "blue");
        TerminalSolution s = solve(TerminalKind.RUBIX, RUBIX, later, state);
        assertEquals(1, state.rubixTarget());
        assertEquals(1, s.clicks(12));
        assertEquals(2, s.clicks(32), "blue to orange: two forward");
    }

    @Test
    void rubixLocksAgainOnlyWhenPanesMove() {
        TerminalSession.State state = new TerminalSession.State();
        solve(TerminalKind.RUBIX, RUBIX, rubix(), state);
        // A blank update on one pane: not a reset; that slot just has no mark for the moment.
        TerminalSolution blank = solve(TerminalKind.RUBIX, RUBIX, rubix().clear(22), state);
        assertFalse(blank.loaded());
        assertEquals(1, state.rubixTarget());
        assertEquals(Mark.OTHER, blank.mark(22));
        assertEquals(1, blank.clicks(12));
        assertFalse(blank.wrongClick(22, 0), "nothing is refused while the chest is not loaded");

        // The panes now sit one column further left: a new board, so a new target (all blue here).
        Chests moved = Chests.rows(5);
        for (int slot : TerminalKind.RUBIX.playSlots()) moved.pane(slot - 1, "blue");
        moved.pane(14, "blue").pane(23, "blue").pane(32, "blue");
        solve(TerminalKind.RUBIX, RUBIX, moved, state);
        assertEquals(4, state.rubixTarget());
    }

    @Test
    void rubixWaitsForAllNinePanes() {
        TerminalSession.State state = new TerminalSession.State();
        TerminalSolution s = solve(TerminalKind.RUBIX, RUBIX, rubix().clear(32), state);
        assertFalse(s.loaded());
        assertEquals(-1, state.rubixTarget());
        assertEquals(Mark.OTHER, s.mark(12));
        assertEquals(0, s.clicks(12));
    }

    @Test
    void rubixPredictCountsPendingSteps() {
        TerminalSession.State state = new TerminalSession.State();
        solve(TerminalKind.RUBIX, RUBIX, rubix(), state);
        state.withPending(12, 0, 100L).withPending(31, 1, 100L).withPending(31, 1, 120L);
        TerminalSolution confirmed = solve(TerminalKind.RUBIX, RUBIX, rubix(), state);
        assertEquals(1, confirmed.clicks(12));
        // The refusal always counts them: a further left click on 12 would overshoot.
        TerminalSolution refusal = TerminalSolver.solve(TerminalKind.RUBIX, RUBIX, rubix().list(), state, true);
        assertEquals(0, refusal.clicks(12));
        assertTrue(refusal.wrongClick(12, 0));
        // Two right clicks from yellow are red, which is one left click from orange.
        assertEquals(1, refusal.clicks(31));
        assertEquals(TerminalSolver.solve(TerminalKind.RUBIX, RUBIX, rubix().list(), state.withPredict(true)).clicks(31),
                refusal.clicks(31));
    }

    // --- Click in Order ---

    /** Four rows, ten numbered panes since 0.27.2; 1 and 2 already clicked (lime). */
    private static Chests numbers() {
        Chests chest = Chests.rows(4);
        int[] slots = TerminalKind.NUMBERS.playSlots();
        int[] counts = {7, 3, 10, 1, 5, 2, 9, 4, 8, 6};
        for (int i = 0; i < slots.length; i++) chest.pane(slots[i], counts[i] <= 2 ? "lime" : "red", counts[i]);
        return chest;
    }

    @Test
    void numbersNextThree() {
        TerminalSolution s = solve(TerminalKind.NUMBERS, NUMBERS, numbers(), new TerminalSession.State());
        assertTrue(s.loaded());
        assertEquals(Mark.CLICK, s.mark(12));
        assertEquals("3", s.label(12));
        assertEquals(Mark.SECOND, s.mark(22));
        assertEquals("4", s.label(22));
        assertEquals(Mark.THIRD, s.mark(15));
        assertEquals("5", s.label(15));
        assertEquals(Mark.OTHER, s.mark(24), "6 is not to click yet");
        assertEquals(Mark.DONE, s.mark(14));
        assertEquals(Mark.DONE, s.mark(20));
        assertTrue(s.wrongClick(22, 0));
        assertFalse(s.wrongClick(12, 0));
        assertFalse(s.solved());
    }

    @Test
    void numbersPredictMovesOnOnlyWithPredict() {
        TerminalSession.State state = new TerminalSession.State().withPending(12, 100L);
        TerminalSolution confirmed = solve(TerminalKind.NUMBERS, NUMBERS, numbers(), state);
        assertEquals(Mark.CLICK, confirmed.mark(12), "the highlight waits for the server");
        assertTrue(confirmed.wrongClick(22, 0));
        TerminalSolution refusal = TerminalSolver.solve(TerminalKind.NUMBERS, NUMBERS, numbers().list(), state, true);
        assertFalse(refusal.wrongClick(22, 0), "a fast but right next click is never refused");
        assertTrue(refusal.wrongClick(12, 0), "nor is the same one taken twice");
        TerminalSolution predicted = solve(TerminalKind.NUMBERS, NUMBERS, numbers(), state.withPredict(true));
        assertEquals(Mark.DONE, predicted.mark(12));
        assertEquals(Mark.CLICK, predicted.mark(22));
        assertEquals(Mark.SECOND, predicted.mark(15));
        assertEquals(Mark.THIRD, predicted.mark(24));
    }

    @Test
    void numbersWaitForTheLowestWhileLoading() {
        // 3 has not arrived yet: 4 is not the next one.
        TerminalSolution gap = solve(TerminalKind.NUMBERS, NUMBERS, numbers().clear(12), new TerminalSession.State());
        assertFalse(gap.loaded());
        assertEquals(Mark.OTHER, gap.mark(22));
        assertEquals(0, gap.toClick());
        // 9 is missing instead: 3, 4 and 5 are sure.
        TerminalSolution late = solve(TerminalKind.NUMBERS, NUMBERS, numbers().clear(21), new TerminalSession.State());
        assertFalse(late.loaded());
        assertEquals(Mark.CLICK, late.mark(12));
        assertEquals(Mark.THIRD, late.mark(15));
    }

    @Test
    void numbersBeforeTheMinisterUpdateStillSolve() {
        // Fourteen panes in rows 1-2, columns 1-7, as before 0.27.2.
        Chests chest = Chests.rows(4);
        int n = 14;
        for (int i = 0; i < 7; i++) {
            chest.pane(10 + i, "red", n--);
            chest.pane(19 + i, "red", n--);
        }
        TerminalSolution s = solve(TerminalKind.NUMBERS, NUMBERS, chest, new TerminalSession.State());
        assertTrue(s.loaded());
        assertEquals(Mark.CLICK, s.mark(25), "count 1");
        assertEquals(Mark.SECOND, s.mark(16), "count 2");
    }

    @Test
    void numbersAllLimeIsSolved() {
        Chests chest = Chests.rows(4);
        int count = 1;
        for (int slot : TerminalKind.NUMBERS.playSlots()) chest.pane(slot, "lime", count++);
        assertTrue(solve(TerminalKind.NUMBERS, NUMBERS, chest, new TerminalSession.State()).solved());
    }

    // --- What Starts With ---

    private static Chests startsWith() {
        Chests chest = Chests.rows(5);
        for (int slot : TerminalKind.STARTS_WITH.playSlots()) chest.put(slot, "apple", "Apple");
        chest.put(10, "stone", "Stone");
        chest.put(12, "sugar", "Sugar", 1, true);
        chest.put(20, "snowball", "Snowball");
        chest.put(33, "spider_eye", "Spider Eye");
        chest.put(25, "nether_star", "Nether Star", 1, true);
        chest.put(28, "sand", "  Sand");
        return chest;
    }

    @Test
    void startsWithMatchesNamesAndGlintMeansClicked() {
        TerminalSolution s = solve(TerminalKind.STARTS_WITH, "What starts with: 'S'?", startsWith(), new TerminalSession.State());
        assertTrue(s.loaded());
        assertEquals(Mark.CLICK, s.mark(10));
        assertEquals(Mark.CLICK, s.mark(20));
        assertEquals(Mark.CLICK, s.mark(33));
        assertEquals(Mark.CLICK, s.mark(28), "padding before the name");
        assertEquals(Mark.DONE, s.mark(12), "Hypixel marks a clicked item with the glint");
        assertEquals(Mark.OTHER, s.mark(11));
        assertEquals(Mark.OTHER, s.mark(25));
        assertEquals(Mark.OTHER, s.mark(0));
        assertEquals(4, s.toClick());
    }

    @Test
    void startsWithItemsThatAlwaysGlintGoByAnsweredClicks() {
        String title = "What starts with: 'N'?";
        TerminalItem star = startsWith().at(25);
        assertTrue(star.alwaysGlints());
        TerminalSession.State state = new TerminalSession.State();
        assertEquals(Mark.CLICK, solve(TerminalKind.STARTS_WITH, title, startsWith(), state).mark(25),
                "a nether star glints anyway");
        // Pending: done only with Predict Clicks.
        state.withPending(25, 100L);
        assertEquals(Mark.CLICK, solve(TerminalKind.STARTS_WITH, title, startsWith(), state).mark(25));
        assertEquals(Mark.DONE, TerminalSolver.solve(TerminalKind.STARTS_WITH, title, startsWith().list(), state, true).mark(25));
        // Answered: done.
        TerminalSession.State answered = new TerminalSession.State().withConfirmed(25, star);
        assertEquals(Mark.DONE, solve(TerminalKind.STARTS_WITH, title, startsWith(), answered).mark(25));
        // The slot holds something else since (a reset): the old answer no longer counts.
        TerminalSession.State stale = new TerminalSession.State()
                .withConfirmed(25, TerminalItem.of(25, "nether_wart", "Nether Wart", 1, false));
        assertEquals(Mark.CLICK, solve(TerminalKind.STARTS_WITH, title, startsWith(), stale).mark(25));
    }

    @Test
    void startsWithAnAnsweredClickWithoutGlintWasNotTaken() {
        // The server sends a slot back whether or not it took the click; for an item that can
        // glint, only the glint says it did.
        String title = "What starts with: 'S'?";
        Chests chest = startsWith();
        TerminalSession.State state = new TerminalSession.State().withConfirmed(10, chest.at(10));
        assertEquals(Mark.CLICK, solve(TerminalKind.STARTS_WITH, title, chest, state).mark(10));
        chest.put(10, "stone", "Stone", 1, true);
        assertEquals(Mark.DONE, solve(TerminalKind.STARTS_WITH, title, chest, state).mark(10));
    }

    @Test
    void startsWithIgnoresCaseAndOtherTitles() {
        Chests chest = Chests.rows(5);
        for (int slot : TerminalKind.STARTS_WITH.playSlots()) chest.put(slot, "apple", "apple pie");
        assertEquals(21, solve(TerminalKind.STARTS_WITH, "What starts with: 'A'?", chest, new TerminalSession.State()).toClick());
        assertEquals(0, solve(TerminalKind.STARTS_WITH, "Correct all the panes!", chest, new TerminalSession.State()).toClick());
    }

    // --- Select All ---

    /** Six rows of 1.8-named dyed items. */
    private static Chests selectAll() {
        Chests chest = Chests.rows(6);
        for (int slot : TerminalKind.SELECT_ALL.playSlots()) chest.put(slot, "white_stained_glass", "White Stained Glass");
        chest.put(10, "light_gray_wool", "Light Gray Wool");
        chest.put(11, "gray_wool", "Gray Wool");
        chest.put(12, "light_gray_dye", "Light Gray Dye");
        chest.put(13, "white_wool", "Wool");
        chest.put(14, "bone_meal", "Bone Meal");
        chest.put(15, "ink_sac", "Ink Sac");
        chest.put(16, "lapis_lazuli", "Lapis Lazuli");
        chest.put(19, "light_blue_wool", "Light Blue Wool");
        chest.put(20, "blue_terracotta", "Blue Stained Clay");
        chest.put(21, "cocoa_beans", "Cocoa Beans");
        chest.put(22, "red_dye", "Rose Red");
        chest.put(23, "green_dye", "Cactus Green");
        chest.put(24, "yellow_dye", "Dandelion Yellow");
        chest.put(25, "redstone", "Redstone");
        chest.put(28, "red_stained_glass", "Red Stained Glass", 1, true);
        chest.put(29, "black_stained_glass_pane", "Black Stained Glass Pane");
        chest.put(30, "orange_wool", "Orange Wool");
        chest.put(31, "magenta_dye", "Magenta Dye");
        chest.put(32, "lime_terracotta", "Lime Stained Clay");
        chest.put(33, "purple_stained_glass", "Purple Stained Glass");
        return chest;
    }

    private static List<Integer> toClick(String color) {
        TerminalSolution s = solve(TerminalKind.SELECT_ALL, "Select all the " + color + " items!", selectAll(),
                new TerminalSession.State());
        assertTrue(s.loaded());
        return java.util.stream.IntStream.range(0, 54).filter(i -> s.mark(i) == Mark.CLICK).boxed().toList();
    }

    @Test
    void selectAllColorWords() {
        assertEquals(List.of(10, 12), toClick("SILVER"), "silver is light gray, and gray is not");
        assertEquals(List.of(10, 12), toClick("LIGHT GRAY"));
        assertEquals(List.of(11), toClick("GRAY"));
        assertEquals(List.of(15), toClick("BLACK"), "never a black pane, named or not");
        assertEquals(List.of(16, 20), toClick("BLUE"), "lapis, and not light blue");
        assertEquals(List.of(19), toClick("LIGHT BLUE"));
        assertEquals(List.of(21), toClick("BROWN"));
        assertEquals(List.of(22), toClick("RED"), "rose red; redstone is no dye and the glinting glass is clicked");
        assertEquals(List.of(23), toClick("GREEN"));
        assertEquals(List.of(24), toClick("YELLOW"));
        assertEquals(List.of(30), toClick("ORANGE"));
        assertEquals(List.of(31), toClick("MAGENTA"));
        assertEquals(List.of(32), toClick("LIME"));
        assertEquals(List.of(33), toClick("PURPLE"));
        // White: plain "Wool", bone meal, and every white glass filling the rest.
        List<Integer> white = toClick("WHITE");
        assertTrue(white.containsAll(List.of(13, 14, 34, 37)));
        assertFalse(white.contains(10));
        assertEquals(List.of(), toClick("PINK"));
    }

    @Test
    void selectAllClickedItemsAreDone() {
        TerminalSolution s = solve(TerminalKind.SELECT_ALL, "Select all the RED items!", selectAll(), new TerminalSession.State());
        assertEquals(Mark.DONE, s.mark(28));
        assertEquals(Mark.OTHER, s.mark(25));
        assertFalse(s.solved());
    }

    @Test
    void selectColorWords() {
        assertEquals("light_gray", TerminalSolver.selectColor("SILVER"));
        assertEquals("light_blue", TerminalSolver.selectColor("LIGHT BLUE"));
        assertEquals("light_gray", TerminalSolver.selectColor("LIGHT GRAY"));
        assertEquals("white", TerminalSolver.selectColor("WHITE"));
        assertNull(TerminalSolver.selectColor("RAINBOW"));
        assertNull(TerminalSolver.selectColor(null));
    }

    @Test
    void colorByIdOrName() {
        assertTrue(TerminalSolver.hasColor(TerminalItem.of(0, "light_gray_terracotta", "Silver Stained Clay", 1, false), "light_gray"));
        assertTrue(TerminalSolver.hasColor(TerminalItem.of(0, "stone", "Silver Thing", 1, false), "light_gray"));
        assertFalse(TerminalSolver.hasColor(TerminalItem.of(0, "red_mushroom", "Redcap", 1, false), "red"));
        assertFalse(TerminalSolver.hasColor(TerminalItem.of(0, "blackstone", "Blackstone", 1, false), "black"));
        assertFalse(TerminalSolver.hasColor(TerminalItem.empty(0), "red"));
    }

    // --- Click the Button on Time ---

    /** Six rows, three play rows since 0.27.2: target column 3, row 2 active with its lime pane in column {@code at}. */
    private static Chests melody(int at) {
        Chests chest = Chests.rows(6);
        chest.pane(3, "magenta").pane(39, "magenta");
        for (int col = 1; col <= 5; col++) {
            chest.pane(9 + col, "white");
            chest.pane(18 + col, col == at ? "lime" : "red");
            chest.pane(27 + col, "white");
        }
        chest.put(16, "red_terracotta", "").put(25, "lime_terracotta", "").put(34, "red_terracotta", "");
        return chest;
    }

    @Test
    void melodyFindsColumnPaneRowAndButton() {
        TerminalSolution s = solve(TerminalKind.MELODY, MELODY, melody(5), new TerminalSession.State());
        assertTrue(s.loaded());
        assertEquals(3, s.columnTarget());
        assertEquals(5, s.movingColumn());
        assertEquals(2, s.activeRow());
        assertEquals(25, s.buttonSlot());
        assertEquals(23, s.movingSlot());
        assertFalse(s.aligned());
        assertEquals(Mark.CLICK, s.mark(25));
        assertEquals(Mark.OTHER, s.mark(16));
        assertEquals(Mark.OTHER, s.mark(34));
        for (int slot : new int[]{3, 12, 21, 30, 39}) assertTrue(s.inTargetColumn(slot), "slot " + slot);
        assertFalse(s.inTargetColumn(48), "below the bottom magenta pane");
        assertFalse(s.inTargetColumn(4));
        assertFalse(s.solved());
    }

    @Test
    void melodyLooksTheSameWhenItLinesUp() {
        TerminalPalette palette = new TerminalPalette(0xFF00FF00, 2, 3, 4, 5, 0xFFFF00FF);
        TerminalSolution apart = solve(TerminalKind.MELODY, MELODY, melody(5), new TerminalSession.State());
        TerminalSolution aligned = solve(TerminalKind.MELODY, MELODY, melody(3), new TerminalSession.State());
        assertTrue(aligned.aligned());
        // No "click now": the button, the column and the pane keep their colors.
        assertEquals(palette.fill(apart, 25), palette.fill(aligned, 25));
        assertEquals(palette.solution(), palette.fill(aligned, 25));
        assertEquals(palette.melody(), palette.fill(apart, 21));
        assertEquals(palette.melody(), palette.fill(aligned, 21));
        assertEquals(palette.melody(), palette.fill(apart, 23));
        assertEquals(apart.mark(25), aligned.mark(25));
        // Never a wrong click, aligned or not, loaded or not.
        for (int slot = 0; slot < 54; slot++) {
            assertFalse(apart.wrongClick(slot, 0));
            assertFalse(aligned.wrongClick(slot, 0));
        }
    }

    @Test
    void melodyWithoutTheLimeButtonUsesTheActiveRowsTerracotta() {
        Chests chest = melody(2).put(25, "red_terracotta", "");
        TerminalSolution s = solve(TerminalKind.MELODY, MELODY, chest, new TerminalSession.State());
        assertEquals(2, s.activeRow());
        assertEquals(25, s.buttonSlot());
    }

    @Test
    void melodyIsNeverPredicted() {
        TerminalSession.State state = new TerminalSession.State().withPending(25, 100L).withPredict(true);
        TerminalSolution s = solve(TerminalKind.MELODY, MELODY, melody(5), state);
        assertEquals(Mark.CLICK, s.mark(25));
        assertEquals(25, s.buttonSlot());
    }

    // --- every kind ---

    @Test
    void anEmptyChestSolvesToNothing() {
        for (TerminalKind kind : TerminalKind.values()) {
            TerminalSolution s = TerminalSolver.solve(kind, "", List.of(), new TerminalSession.State());
            assertFalse(s.loaded(), kind.name());
            assertFalse(s.solved(), kind.name());
            assertEquals(0, s.toClick(), kind.name());
            assertEquals(TerminalSolution.empty(kind).toString(), s.toString(), kind.name());
        }
        assertEquals(TerminalKind.PANES, TerminalSolver.solve(TerminalKind.PANES, PANES, null, null).kind());
    }
}
