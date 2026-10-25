package net.alpaka.addons.features.dungeon.terminal;

import net.alpaka.addons.features.dungeon.terminal.TerminalGuiInput.Click;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Presses on the custom terminal window become exactly the clicks vanilla's chest screen sends for
 * them (terminals.md 7.1, 7.3): one at most per press, the same button, a quick move with Shift,
 * on the release while the cursor holds an item - and nothing for anything else.
 */
class TerminalGuiInputTest {

    private static final boolean SHIFT = true;
    private static final boolean CARRYING = true;

    @Test
    void leftAndRightClickOnThePress() {
        TerminalGuiInput input = new TerminalGuiInput();
        assertEquals(new Click(12, 0, false), input.press(12, 0, false, false));
        assertNull(input.release(12, 0, false, false), "vanilla skips the release of a press it sent on");
        assertEquals(new Click(13, 1, false), input.press(13, 1, false, false));
        assertNull(input.release(13, 1, false, false));
    }

    @Test
    void shiftMakesAQuickMoveWithTheSameButton() {
        TerminalGuiInput input = new TerminalGuiInput();
        assertEquals(new Click(20, 0, true), input.press(20, 0, SHIFT, false));
        assertEquals(new Click(20, 1, true), input.press(20, 1, SHIFT, false));
    }

    @Test
    void otherButtonsAndMissesSendNothing() {
        TerminalGuiInput input = new TerminalGuiInput();
        for (int button = 2; button <= 7; button++) {
            assertNull(input.press(12, button, false, false), "button " + button);
            assertNull(input.release(12, button, false, false));
            assertNull(input.press(12, button, false, CARRYING));
            assertNull(input.release(12, button, false, CARRYING));
        }
        assertNull(input.press(-1, 0, false, false), "between cells, outside the grid, on filler");
        assertNull(input.release(-1, 0, false, false));
        assertNull(input.press(-1, 0, false, CARRYING));
        assertNull(input.release(-1, 0, false, CARRYING));
        assertFalse(input.holding());
    }

    @Test
    void withAnItemOnTheCursorTheClickWaitsForTheRelease() {
        TerminalGuiInput input = new TerminalGuiInput();
        assertNull(input.press(21, 0, false, CARRYING));
        assertTrue(input.holding());
        assertEquals(new Click(21, 0, false), input.release(21, 0, false, CARRYING));
        assertFalse(input.holding());
        // Right click the same way.
        assertNull(input.press(22, 1, false, CARRYING));
        assertEquals(new Click(22, 1, false), input.release(22, 1, false, CARRYING));
    }

    @Test
    void theReleaseReadsShiftAsVanillaDoes() {
        TerminalGuiInput input = new TerminalGuiInput();
        assertNull(input.press(21, 0, false, CARRYING));
        assertEquals(new Click(21, 0, true), input.release(21, 0, SHIFT, CARRYING));
        assertNull(input.press(21, 1, SHIFT, CARRYING));
        assertEquals(new Click(21, 1, false), input.release(21, 1, false, CARRYING));
    }

    @Test
    void aDragOrAnEmptiedCursorSendsNothing() {
        TerminalGuiInput input = new TerminalGuiInput();
        // Released over another cell: a drag, which is never emulated.
        assertNull(input.press(21, 0, false, CARRYING));
        assertNull(input.release(22, 0, false, CARRYING));
        // Released off the grid.
        assertNull(input.press(21, 0, false, CARRYING));
        assertNull(input.release(-1, 0, false, CARRYING));
        // The server cleared the cursor in between: vanilla sends nothing on that release either.
        assertNull(input.press(21, 0, false, CARRYING));
        assertNull(input.release(21, 0, false, false));
        assertFalse(input.holding());
    }

    @Test
    void lettingGoOfAnotherButtonEndsTheWait() {
        TerminalGuiInput input = new TerminalGuiInput();
        assertNull(input.press(21, 0, false, CARRYING));
        assertNull(input.release(21, 1, false, CARRYING), "another button's release");
        assertNull(input.release(21, 0, false, CARRYING), "and then the held one's");
        assertNull(input.press(21, 0, false, CARRYING));
        assertNull(input.release(21, 2, false, CARRYING), "the middle button's release");
        assertNull(input.release(21, 0, false, CARRYING));
    }

    @Test
    void aSecondButtonWhileAPressWaitsSendsNothing() {
        // Vanilla's quick craft: the left press with a full cursor locks out the right one, and the
        // right release cancels it; nothing is sent for either press.
        TerminalGuiInput input = new TerminalGuiInput();
        assertNull(input.press(12, 0, false, CARRYING));
        assertNull(input.press(13, 1, false, CARRYING));
        assertNull(input.press(13, 1, false, false), "the lock holds after the cursor emptied too");
        assertNull(input.release(13, 1, false, CARRYING));
        assertNull(input.release(12, 0, false, CARRYING));
        assertFalse(input.holding());

        // The server emptied the cursor between the two presses: still no click on the right press.
        assertNull(input.press(12, 0, false, CARRYING));
        assertNull(input.press(13, 1, false, false));
        assertNull(input.release(13, 1, false, false));
        assertNull(input.release(12, 0, false, false));
    }

    @Test
    void aPressBetweenCellsLocksToo() {
        TerminalGuiInput input = new TerminalGuiInput();
        assertNull(input.press(-1, 0, false, CARRYING));
        assertTrue(input.holding());
        assertNull(input.press(13, 1, false, false), "vanilla's lock starts for a miss as well");
        assertNull(input.release(-1, 0, false, CARRYING));
        assertFalse(input.holding());
        // Once released, presses work as before.
        assertEquals(new Click(13, 1, false), input.press(13, 1, false, false));
    }

    @Test
    void theWaitingPressStillFiresOnItsOwnRelease() {
        TerminalGuiInput input = new TerminalGuiInput();
        assertNull(input.press(12, 0, false, CARRYING));
        assertNull(input.press(13, 1, false, CARRYING));
        assertEquals(new Click(12, 0, false), input.release(12, 0, false, CARRYING), "vanilla sends this one too");
        assertNull(input.release(13, 1, false, CARRYING));
    }

    @Test
    void oneClickAtMostPerPress() {
        TerminalGuiInput input = new TerminalGuiInput();
        int sent = 0;
        boolean[] carrying = {false, true, true, false, true};
        int presses = 0;
        for (int round = 0; round < 40; round++) {
            boolean carry = carrying[round % carrying.length];
            int slot = 11 + round % 5;
            int button = round % 3 == 0 ? 1 : 0;
            presses++;
            if (input.press(slot, button, round % 4 == 0, carry) != null) sent++;
            // A second release, as a duplicated input event would deliver it.
            if (input.release(slot, button, false, carry) != null) sent++;
            if (input.release(slot, button, false, carry) != null) sent++;
        }
        assertEquals(presses, sent, "every press here lands on a cell, so each sends exactly one");
    }

    @Test
    void resetForgetsAWaitingPress() {
        TerminalGuiInput input = new TerminalGuiInput();
        assertNull(input.press(21, 0, false, CARRYING));
        input.reset();
        assertFalse(input.holding());
        assertNull(input.release(21, 0, false, CARRYING));
    }
}
