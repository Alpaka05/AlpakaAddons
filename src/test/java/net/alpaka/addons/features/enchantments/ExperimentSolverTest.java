package net.alpaka.addons.features.enchantments;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExperimentSolverTest {
    private static final int NOW = AlpakaConfig.instance.experimentSolverNextColor;
    private static final int LATER = AlpakaConfig.instance.experimentSolverLaterColor;

    /** Items need their components bound, which normally happens when a world loads. */
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.ITEM.listElements().forEach(holder -> {
            if (!holder.areComponentsBound()) {
                holder.bindComponents(DataComponentMap.builder()
                        .set(DataComponents.MAX_STACK_SIZE, 64)
                        .set(DataComponents.ITEM_NAME, Component.literal(holder.key().identifier().getPath()))
                        .build());
            }
        });
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id)));
    }

    private static ItemStack named(String id, String name, int count) {
        ItemStack stack = item(id);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        stack.setCount(count);
        return stack;
    }

    /** A menu as Hypixel fills it: every update goes through the solver's session like in the game. */
    private static final class Board {
        final ExperimentSolver.Session session;
        final ItemStack[] slots = new ItemStack[54];

        Board(ExperimentSolver.Game game) {
            session = new ExperimentSolver.Session(game);
            for (int i = 0; i < slots.length; i++) slots[i] = item("black_stained_glass_pane");
        }

        void set(int slot, ItemStack stack) {
            slots[slot] = stack;
            session.onSlotSet(slot, stack, i -> slots[i]);
        }

        void contents() {
            session.onContentsSet(List.of(slots));
        }

        void showing() {
            set(49, named("glowstone", "Remember the pattern!", 1));
        }

        void answering() {
            set(49, named("clock", "Timer: 10s", 1));
        }

        int highlight(int slot) {
            return session.highlight(slot, slots[slot]);
        }

        void click(int slot) {
            session.onClicked(slot, slots[slot]);
        }

        boolean misclick(int slot) {
            return session.isMisclick(slot, slots[slot]);
        }
    }

    /** Chronomatron (Metaphysical): each color is a column of two blocks. */
    private static final Map<String, int[]> COLUMNS = Map.of(
            "blue", new int[]{20, 29}, "purple", new int[]{21, 30}, "cyan", new int[]{22, 31},
            "light_blue", new int[]{23, 32}, "orange", new int[]{24, 33});

    private static Board chronomatron() {
        Board board = new Board(ExperimentSolver.Game.CHRONOMATRON);
        COLUMNS.forEach((color, slots) -> {
            for (int slot : slots) board.slots[slot] = item(color + "_stained_glass");
        });
        board.slots[49] = named("glowstone", "Remember the pattern!", 1);
        board.contents();
        return board;
    }

    private static void light(Board board, String color) {
        for (int slot : COLUMNS.get(color)) {
            ItemStack lit = item(color + "_terracotta");
            lit.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            board.set(slot, lit);
        }
        for (int slot : COLUMNS.get(color)) board.set(slot, item(color + "_stained_glass"));
    }

    private static void answerAll(Board board, List<String> chain) {
        for (int i = 0; i < chain.size(); i++) {
            int[] now = COLUMNS.get(chain.get(i));
            assertEquals(NOW, board.highlight(now[0]), "note " + (i + 1) + " " + chain.get(i));
            assertEquals(NOW, board.highlight(now[1]));
            if (i + 1 < chain.size() && !chain.get(i + 1).equals(chain.get(i))) {
                assertEquals(LATER, board.highlight(COLUMNS.get(chain.get(i + 1))[0]));
            }
            assertFalse(board.misclick(now[1]));
            for (Map.Entry<String, int[]> other : COLUMNS.entrySet()) {
                if (!other.getKey().equals(chain.get(i))) assertTrue(board.misclick(other.getValue()[0]));
            }
            assertTrue(board.misclick(10), "a background pane is never the next step");
            board.click(now[0]);
        }
        assertFalse(board.misclick(COLUMNS.get("orange")[0]), "after the last step nothing is blocked");
    }

    @Test
    void gamesByTitle() {
        assertEquals(ExperimentSolver.Game.CHRONOMATRON, ExperimentSolver.gameOf("Chronomatron (Supreme)"));
        assertEquals(ExperimentSolver.Game.ULTRASEQUENCER, ExperimentSolver.gameOf("Ultrasequencer (Metaphysical)"));
        assertEquals(ExperimentSolver.Game.SUPERPAIRS, ExperimentSolver.gameOf("Superpairs (Transcendent)"));
        assertNull(ExperimentSolver.gameOf("Experimentation Table"));
        assertNull(ExperimentSolver.gameOf("Chronomatron ➜ Stakes"));
    }

    /**
     * The game of 2026-11-06 01:36 from the player's log: the chain grew blue, purple, cyan,
     * light_blue, and at the start of every pattern the block clicked last lit up once more.
     */
    @Test
    void chronomatronAsRecordedOnHypixel() {
        Board board = chronomatron();
        List<String> chain = new ArrayList<>();
        String clickedLast = null;
        for (String note : List.of("blue", "purple", "cyan", "light_blue", "orange")) {
            if (clickedLast != null) {
                board.showing();
                light(board, clickedLast);
            }
            chain.add(note);
            for (String shown : chain) light(board, shown);
            board.answering();
            assertEquals(chain, board.session.chronomatron.sequence);
            answerAll(board, chain);
            clickedLast = chain.getLast();
        }
    }

    @Test
    void chronomatronWithoutTheClickedBlockLightingUp() {
        Board board = chronomatron();
        List<String> chain = new ArrayList<>();
        for (String note : List.of("orange", "orange", "cyan")) {
            board.showing();
            assertFalse(board.misclick(COLUMNS.get("blue")[0]), "nothing is blocked while the pattern plays");
            chain.add(note);
            for (String shown : chain) light(board, shown);
            board.answering();
            assertEquals(chain, board.session.chronomatron.sequence);
            answerAll(board, chain);
        }
    }

    @Test
    void chronomatronIgnoresTerracottaWithoutGlint() {
        Board board = chronomatron();
        light(board, "blue");
        board.set(21, item("purple_terracotta"));
        board.answering();
        assertEquals(List.of("blue"), board.session.chronomatron.sequence);
    }

    @Test
    void chronomatronMissingNoteKeepsTheOldChain() {
        ExperimentSolver.Chronomatron chrono = new ExperimentSolver.Chronomatron();
        chrono.onShown(20, "red");
        chrono.onShown(20, null);
        chrono.startAnswering();
        chrono.startShowing();
        // Two reds lit back to back without going dark read as one note.
        chrono.onShown(20, "red");
        chrono.onShown(20, null);
        chrono.startAnswering();
        assertEquals(List.of("red", "red"), chrono.sequence);
    }

    @Test
    void ultrasequencerAsShownOnHypixel() {
        Board board = new Board(ExperimentSolver.Game.ULTRASEQUENCER);
        for (int i = 9; i <= 44; i++) board.slots[i] = item("light_blue_stained_glass_pane");
        board.slots[49] = named("glowstone", "Remember the pattern!", 1);
        board.slots[30] = named("red_dye", "2", 2);
        board.slots[12] = named("lime_dye", "1", 1);
        board.contents();
        board.set(40, named("blue_dye", "3", 3));
        board.answering();
        for (int slot : new int[]{12, 30, 40}) board.set(slot, item("light_blue_stained_glass_pane"));

        assertEquals(NOW, board.highlight(12));
        assertEquals(LATER, board.highlight(30));
        assertEquals(0, board.highlight(40));
        assertTrue(board.misclick(30));
        assertTrue(board.misclick(9));
        assertFalse(board.misclick(12));
        assertEquals("1", board.session.displayed(12, board.slots[12]).getHoverName().getString());
        board.click(30);
        assertEquals(NOW, board.highlight(12), "clicking out of order does not advance");
        board.click(12);
        assertEquals(NOW, board.highlight(30));
        assertEquals(LATER, board.highlight(40));

        // The next round is a new sequence; last round's numbers are gone.
        board.set(49, named("glowstone", "Remember the pattern!", 1));
        board.set(15, named("lime_dye", "1", 1));
        board.answering();
        assertEquals(NOW, board.highlight(15));
        assertEquals(0, board.highlight(30));
        assertSame(board.slots[40], board.session.displayed(40, board.slots[40]));
    }

    @Test
    void superpairsRemembersAndPairsCards() {
        Board board = new Board(ExperimentSolver.Game.SUPERPAIRS);
        for (int i = 9; i <= 44; i++) board.slots[i] = item("cyan_stained_glass");
        board.contents();
        board.set(10, named("enchanted_book", "Protection VI", 1));
        board.set(10, item("cyan_stained_glass"));
        board.set(25, named("experience_bottle", "Titanic Experience Bottle", 1));
        board.set(25, item("cyan_stained_glass"));
        assertEquals(0, board.highlight(10));
        board.set(33, named("enchanted_book", "Protection VI", 1));
        assertEquals(NOW, board.highlight(10));
        assertEquals(0, board.highlight(25));
        assertFalse(board.misclick(25), "Superpairs has no wrong clicks to block");
        assertEquals("Protection VI", board.session.displayed(10, board.slots[10]).getHoverName().getString());
    }
}
