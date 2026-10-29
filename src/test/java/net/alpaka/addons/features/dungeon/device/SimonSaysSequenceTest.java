package net.alpaka.addons.features.dungeon.device;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Recording the Simon Says sequence as the display shows it, presses, and when a round is not shown (terminals.md 4.1). */
class SimonSaysSequenceTest {

    private static final BlockPos A = new BlockPos(111, 123, 92);
    private static final BlockPos B = new BlockPos(111, 120, 95);
    private static final BlockPos C = new BlockPos(111, 122, 93);
    private static final BlockPos D = new BlockPos(111, 121, 94);

    private static BlockPos button(BlockPos display) {
        return SimonSaysSequence.buttonOf(display);
    }

    /** One round as the server plays it: the buttons vanish, the blocks light up in order, the buttons come back. */
    private static void round(SimonSaysSequence s, BlockPos... lit) {
        for (int i = 0; i < 16; i++) s.buttonsHidden();
        for (BlockPos pos : lit) s.lanternShown(pos);
        for (int i = 0; i < 16; i++) s.buttonsShown();
    }

    @Test
    void theDeviceLayout() {
        assertEquals(new BlockPos(110, 121, 91), SimonSaysSequence.START_BUTTON);
        int display = 0;
        int buttons = 0;
        for (int x = 108; x <= 113; x++) {
            for (int y = 118; y <= 125; y++) {
                for (int z = 90; z <= 97; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (SimonSaysSequence.isDisplay(pos)) display++;
                    if (SimonSaysSequence.isButton(pos)) buttons++;
                    assertEquals(SimonSaysSequence.isDisplay(pos) || SimonSaysSequence.isButton(pos)
                            || pos.equals(SimonSaysSequence.START_BUTTON), SimonSaysSequence.isDevice(pos), pos.toString());
                }
            }
        }
        assertEquals(16, display);
        assertEquals(16, buttons);
        assertTrue(SimonSaysSequence.isDisplay(new BlockPos(111, 120, 92)));
        assertTrue(SimonSaysSequence.isDisplay(new BlockPos(111, 123, 95)));
        assertFalse(SimonSaysSequence.isButton(SimonSaysSequence.START_BUTTON));
        // The button in front of a display block is one block west, where the player stands.
        assertEquals(new BlockPos(110, 123, 92), SimonSaysSequence.buttonOf(A));
        assertEquals(A, SimonSaysSequence.displayOf(new BlockPos(110, 123, 92)));
    }

    @Test
    void recordsTheBlocksInTheOrderTheyLight() {
        SimonSaysSequence s = new SimonSaysSequence();
        s.start();
        s.lanternShown(A);
        s.buttonsShown();
        assertEquals(List.of(A), s.sequence());
        assertTrue(s.trusted());
        assertEquals(button(A), s.nextButton());
        assertNull(s.afterNextButton());

        s.pressed(button(A));
        assertNull(s.nextButton());

        round(s, A, B);
        assertEquals(List.of(A, B), s.sequence());
        assertEquals(button(A), s.nextButton());
        assertEquals(button(B), s.afterNextButton());

        round(s, A, B, C, D);
        assertEquals(List.of(A, B, C, D), s.sequence());
        assertEquals(0, s.progress());
    }

    @Test
    void aBlockSeenTwiceInARoundIsRecordedOnce() {
        SimonSaysSequence s = new SimonSaysSequence();
        round(s, C, A, C, B);
        assertEquals(List.of(C, A, B), s.sequence());
    }

    @Test
    void pressesMoveTheMarksOn() {
        SimonSaysSequence s = new SimonSaysSequence();
        round(s, A, B, C);
        assertEquals(button(A), s.nextButton());
        assertEquals(button(B), s.afterNextButton());

        s.pressed(button(A));
        assertEquals(button(B), s.nextButton());
        assertEquals(button(C), s.afterNextButton());

        s.pressed(button(B));
        assertEquals(button(C), s.nextButton());
        assertNull(s.afterNextButton());

        s.pressed(button(C));
        assertNull(s.nextButton());
        assertEquals(3, s.progress());
    }

    /** The player's own click and the server's report of it are the same press; a late report moves nothing back. */
    @Test
    void theSamePressSeenTwiceOrLateChangesNothing() {
        SimonSaysSequence s = new SimonSaysSequence();
        round(s, A, B, C);
        s.pressed(button(A));
        s.pressed(button(A));
        assertEquals(1, s.progress());
        s.pressed(button(B));
        s.pressed(button(A));
        assertEquals(2, s.progress());
        assertEquals(button(C), s.nextButton());
    }

    @Test
    void aButtonOutsideTheSequenceOrTheGridDoesNotCount() {
        SimonSaysSequence s = new SimonSaysSequence();
        round(s, A, B);
        s.pressed(button(D));
        s.pressed(SimonSaysSequence.START_BUTTON);
        s.pressed(new BlockPos(110, 119, 92));
        assertEquals(0, s.progress());
        assertEquals(button(A), s.nextButton());
    }

    /** The buttons vanishing start a new round: the old sequence and its presses are gone. */
    @Test
    void aNewDisplayStartsOver() {
        SimonSaysSequence s = new SimonSaysSequence();
        round(s, A, B);
        s.pressed(button(A));
        s.buttonsHidden();
        assertEquals(List.of(), s.sequence());
        assertEquals(0, s.progress());
        assertNull(s.nextButton());
        s.lanternShown(C);
        assertEquals(List.of(C), s.sequence());
    }

    /** A button vanishing late, after the display already lit a block, does not wipe that block. */
    @Test
    void aLateVanishingButtonKeepsTheRound() {
        SimonSaysSequence s = new SimonSaysSequence();
        s.buttonsHidden();
        s.lanternShown(A);
        s.buttonsHidden();
        s.lanternShown(B);
        s.buttonsShown();
        assertEquals(List.of(A, B), s.sequence());
        assertTrue(s.trusted());
    }

    /** A normal first round lights one block and is shown. */
    @Test
    void theFirstRoundAfterStartIsShown() {
        SimonSaysSequence s = new SimonSaysSequence();
        s.start();
        round(s, B);
        assertTrue(s.firstRound());
        assertEquals(button(B), s.nextButton());
        s.pressed(button(B));
        assertFalse(s.firstRound());
    }

    /**
     * A first display after start that lights more than one block (start pressed several times)
     * is not shown, rather than guessing its order the way Odin does; the next round is.
     */
    @Test
    void aFirstDisplayWithSeveralBlocksIsNotShown() {
        SimonSaysSequence s = new SimonSaysSequence();
        s.start();
        s.lanternShown(A);
        s.lanternShown(B);
        s.lanternShown(C);
        s.buttonsShown();
        // Recorded as shown; Odin would have turned this into (B, C).
        assertEquals(List.of(A, B, C), s.sequence());
        assertFalse(s.trusted());
        assertNull(s.nextButton());
        assertNull(s.afterNextButton());

        round(s, A, B, C, D);
        assertFalse(s.firstRound());
        assertTrue(s.trusted());
        assertEquals(button(A), s.nextButton());
        assertEquals(button(B), s.afterNextButton());
    }

    /** Nobody visible pressed the first round, and the next display still ends it. */
    @Test
    void theNextDisplayEndsTheFirstRound() {
        SimonSaysSequence s = new SimonSaysSequence();
        s.start();
        round(s, A);
        assertTrue(s.firstRound());
        round(s, A, B);
        assertFalse(s.firstRound());
        assertEquals(button(A), s.nextButton());
    }

    @Test
    void startPressedDuringTheDisplayHidesThatRound() {
        SimonSaysSequence s = new SimonSaysSequence();
        round(s, A, B);
        s.buttonsHidden();
        s.lanternShown(A);
        s.start();
        s.lanternShown(B);
        s.buttonsShown();
        assertFalse(s.trusted());
        assertNull(s.nextButton());

        round(s, A, B, C);
        assertTrue(s.trusted());
        assertEquals(button(A), s.nextButton());
    }

    /** Pressing start between rounds restarts the device: everything recorded so far is gone. */
    @Test
    void startBetweenRoundsStartsOver() {
        SimonSaysSequence s = new SimonSaysSequence();
        round(s, A, B);
        s.pressed(button(A));
        s.start();
        assertEquals(List.of(), s.sequence());
        assertEquals(0, s.progress());
        assertTrue(s.firstRound());
        s.lanternShown(D);
        s.buttonsShown();
        assertEquals(button(D), s.nextButton());
    }

    /** Coming in while a display plays: its first blocks were missed, so the round is not shown. */
    @Test
    void aDisplaySeenOnlyFromTheMiddleIsNotShown() {
        SimonSaysSequence s = new SimonSaysSequence();
        s.lanternShown(C);
        s.lanternShown(D);
        s.buttonsShown();
        assertFalse(s.trusted());
        assertNull(s.nextButton());

        round(s, A, B, C, D);
        assertTrue(s.trusted());
        assertEquals(button(A), s.nextButton());
    }

    /** Blocks lighting while the buttons are up mean the buttons' vanishing was missed: not shown either. */
    @Test
    void aDisplayWhoseStartWasMissedIsNotShown() {
        SimonSaysSequence s = new SimonSaysSequence();
        round(s, A, B);
        s.pressed(button(A));
        s.lanternShown(A);
        s.lanternShown(B);
        assertEquals(List.of(A, B), s.sequence());
        assertEquals(0, s.progress());
        assertFalse(s.trusted());
    }

    @Test
    void resetForgetsEverything() {
        SimonSaysSequence s = new SimonSaysSequence();
        s.start();
        round(s, A);
        s.reset();
        assertEquals(List.of(), s.sequence());
        assertFalse(s.firstRound());
        assertFalse(s.trusted());
        // After a reset a display is only shown once its start is seen.
        s.lanternShown(B);
        assertFalse(s.trusted());
    }

    @Test
    void onlyDisplayBlocksAreRecorded() {
        SimonSaysSequence s = new SimonSaysSequence();
        s.buttonsHidden();
        s.lanternShown(new BlockPos(110, 121, 93));
        s.lanternShown(new BlockPos(111, 124, 93));
        assertEquals(List.of(), s.sequence());
    }
}
