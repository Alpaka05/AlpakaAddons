package net.alpaka.addons.features.dungeon.terminal;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which chest is which terminal, and where its items sit (terminals.md 3, the 0.27.2 layouts). */
class TerminalKindTest {

    @Test
    void everyTitle() {
        assertEquals(TerminalKind.PANES, TerminalKind.byTitle("Correct all the panes!"));
        assertEquals(TerminalKind.RUBIX, TerminalKind.byTitle("Change all to same color!"));
        assertEquals(TerminalKind.NUMBERS, TerminalKind.byTitle("Click in order!"));
        assertEquals(TerminalKind.STARTS_WITH, TerminalKind.byTitle("What starts with: 'A'?"));
        assertEquals(TerminalKind.STARTS_WITH, TerminalKind.byTitle("What starts with: 'S'?"));
        assertEquals(TerminalKind.SELECT_ALL, TerminalKind.byTitle("Select all the RED items!"));
        assertEquals(TerminalKind.SELECT_ALL, TerminalKind.byTitle("Select all the LIGHT BLUE items!"));
        assertEquals(TerminalKind.SELECT_ALL, TerminalKind.byTitle("Select all the SILVER items!"));
        assertEquals(TerminalKind.MELODY, TerminalKind.byTitle("Click the button on time!"));
        // Trailing padding is trimmed.
        assertEquals(TerminalKind.PANES, TerminalKind.byTitle("Correct all the panes!  "));
    }

    @Test
    void otherChestsAreNotTerminals() {
        assertNull(TerminalKind.byTitle(null));
        assertNull(TerminalKind.byTitle(""));
        assertNull(TerminalKind.byTitle("Large Chest"));
        assertNull(TerminalKind.byTitle("Auction House"));
        // Anchored: a title that only contains one does not count.
        assertNull(TerminalKind.byTitle("Correct all the panes! (Simulator)"));
        assertNull(TerminalKind.byTitle("Practice: Click in order!"));
        assertNull(TerminalKind.byTitle("What starts with: 'AB'?"));
        assertNull(TerminalKind.byTitle("Select all the  items!"));
    }

    @Test
    void theTitleArgument() {
        assertEquals("A", TerminalKind.STARTS_WITH.argument("What starts with: 'A'?"));
        assertEquals("LIGHT BLUE", TerminalKind.SELECT_ALL.argument("Select all the LIGHT BLUE items!"));
        assertNull(TerminalKind.PANES.argument("Correct all the panes!"));
        assertNull(TerminalKind.SELECT_ALL.argument("Click in order!"));
        assertTrue(TerminalKind.NUMBERS.matches("Click in order!"));
        assertFalse(TerminalKind.NUMBERS.matches("Correct all the panes!"));
    }

    @Test
    void playAreas() {
        assertArrayEquals(new int[]{11, 12, 13, 14, 15, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33}, TerminalKind.PANES.playSlots());
        assertArrayEquals(new int[]{12, 13, 14, 21, 22, 23, 30, 31, 32}, TerminalKind.RUBIX.playSlots());
        // 0.27.2: ten panes in two rows.
        assertArrayEquals(new int[]{11, 12, 13, 14, 15, 20, 21, 22, 23, 24}, TerminalKind.NUMBERS.playSlots());
        assertEquals(21, TerminalKind.STARTS_WITH.playSlots().length);
        assertEquals(28, TerminalKind.SELECT_ALL.playSlots().length);
        // 0.27.2 (terminals.md 3.6): five rows of five panes (the magenta target rows 0 and 4 and the
        // three rows the lime pane moves in) plus the three buttons in column 7.
        assertEquals(25 + 3, TerminalKind.MELODY.playSlots().length);
        for (int button : new int[]{16, 25, 34}) assertTrue(TerminalKind.MELODY.isPlaySlot(button), "button " + button);
        for (int pane : new int[]{1, 5, 10, 32, 37, 41}) assertTrue(TerminalKind.MELODY.isPlaySlot(pane), "pane " + pane);
        // The old fourth button, and column 6 between the panes and the buttons, are filler.
        assertFalse(TerminalKind.MELODY.isPlaySlot(43));
        for (int filler : new int[]{6, 7, 15, 24, 33, 42}) assertFalse(TerminalKind.MELODY.isPlaySlot(filler), "filler " + filler);
        assertArrayEquals(new int[]{1, 3, 1, 7}, TerminalKind.STARTS_WITH.playArea());
        assertArrayEquals(new int[]{0, 4, 1, 7}, TerminalKind.MELODY.playArea());
        assertArrayEquals(new int[]{1, 2, 2, 6}, TerminalKind.NUMBERS.playArea());
        for (TerminalKind kind : TerminalKind.values()) {
            int[] slots = kind.playSlots();
            assertTrue(Arrays.stream(slots).allMatch(s -> s < kind.rows() * 9), kind + " fits its chest");
            int[] sorted = slots.clone();
            Arrays.sort(sorted);
            assertArrayEquals(sorted, slots, kind + " row by row");
            for (int s : slots) assertTrue(kind.isPlaySlot(s), kind + " slot " + s);
            assertTrue(kind.isPlaySlot(slots[0]) && kind.isPlaySlot(slots[slots.length - 1]), kind.name());
            assertEquals(kind == TerminalKind.MELODY, kind.isMelody());
        }
        assertEquals(4, TerminalKind.NUMBERS.rows());
        assertEquals(6, TerminalKind.SELECT_ALL.rows());
    }

    @Test
    void displayNamesMatchTheSettings() {
        assertEquals("Correct All the Panes", TerminalKind.PANES.displayName());
        assertEquals("Change All to Same Color", TerminalKind.RUBIX.displayName());
        assertEquals("Click in Order", TerminalKind.NUMBERS.displayName());
        assertEquals("What Starts With", TerminalKind.STARTS_WITH.displayName());
        assertEquals("Select All", TerminalKind.SELECT_ALL.displayName());
        assertEquals("Click the Button on Time", TerminalKind.MELODY.displayName());
    }
}
