package net.alpaka.addons.features.dungeon.device;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The Simon Says device (Goldor section 1) as a record of what it showed and what was pressed.
 * Pure: {@link SimonSays} feeds it the block changes the server sends, so the rules can be tested
 * without a game.
 *
 * The device is a 4 x 4 display of obsidian at X 111 (Y 120..123, Z 92..95) with a stone button in
 * front of each block at X 110, and a start button at (110, 121, 91). Pressing start plays the
 * first round. In each round the buttons vanish, the display lights the round's blocks one after
 * the other (obsidian turning to sea lantern and back), the buttons come back, and the players
 * press them in the order shown. Every round shows the whole sequence again with one block more;
 * there are four rounds since 0.27.2 (terminals.md 4.1).
 *
 * <p>What is recorded: a block of the display going back from sea lantern to obsidian, in order,
 * each block once per round. Vanishing buttons start a new round, so the sequence starts over and
 * the round's own display fills it again. A press is a button the server reports as pressed, or one
 * the player pressed themselves; either way the progress becomes "everything up to that button",
 * so the same press seen twice, or late, changes nothing. Presses by teammates only count if the
 * server sends them, which it may not; the marks then lag behind until the next round.
 *
 * <p>What is not shown: a round whose start was not seen (the player came in while it played, so
 * its first blocks are missing), and a round in which the start button was pressed while the
 * display was still lighting blocks. Both would give a wrong order, and a wrong mark makes the
 * player fail the device, which is worse than no mark.
 *
 * <p>Odin's first-round correction is not used. Odin reverses the sequence when the first display
 * after a start shows two blocks, and drops the middle one of three. Its code gives no reason, no
 * other reference does it, and nobody could confirm it for the four-round device; a normal first
 * round lights one block, so it only matters when the first display shows more (pressing start
 * several times is said to skip ahead). Instead of guessing an order for such a display, it is
 * not marked at all; the next round shows the whole sequence again and is marked as usual.
 */
public final class SimonSaysSequence {

    public static final int DISPLAY_X = 111;
    public static final int BUTTON_X = 110;
    public static final int MIN_Y = 120;
    public static final int MAX_Y = 123;
    public static final int MIN_Z = 92;
    public static final int MAX_Z = 95;
    public static final BlockPos START_BUTTON = new BlockPos(110, 121, 91);

    /** The display blocks lit this round, in order. */
    private final List<BlockPos> sequence = new ArrayList<>();
    /** How many of them have been pressed this round. */
    private int progress = 0;
    /** The round's display was seen from its start. */
    private boolean sawStart = false;
    /** Nothing made this round's order doubtful. */
    private boolean reliable = false;
    /** From a press of start until that round is over: the round that may come out garbled. */
    private boolean firstRound = false;
    /** The buttons are there (between rounds or while being pressed), as far as seen. */
    private boolean buttonsUp = false;

    // --- where things are ---

    /** One of the 16 display blocks. */
    public static boolean isDisplay(BlockPos pos) {
        return pos.getX() == DISPLAY_X && inGrid(pos);
    }

    /** One of the 16 buttons (not the start button). */
    public static boolean isButton(BlockPos pos) {
        return pos.getX() == BUTTON_X && inGrid(pos);
    }

    /** Anything of the device: display, buttons or start button. Cheap, for every block change in the world. */
    public static boolean isDevice(BlockPos pos) {
        return ((pos.getX() == DISPLAY_X || pos.getX() == BUTTON_X) && inGrid(pos)) || pos.equals(START_BUTTON);
    }

    private static boolean inGrid(BlockPos pos) {
        return pos.getY() >= MIN_Y && pos.getY() <= MAX_Y && pos.getZ() >= MIN_Z && pos.getZ() <= MAX_Z;
    }

    /** The button in front of a display block: one block west, on the side the player stands. */
    public static BlockPos buttonOf(BlockPos display) {
        return new BlockPos(BUTTON_X, display.getY(), display.getZ());
    }

    /** The display block behind a button. */
    public static BlockPos displayOf(BlockPos button) {
        return new BlockPos(DISPLAY_X, button.getY(), button.getZ());
    }

    // --- what happened ---

    /** The start button was pressed. */
    public void start() {
        if (displaying()) {
            // The display was already lighting blocks; what it does with a second start is unknown.
            reliable = false;
            return;
        }
        clearRound();
        sawStart = true;
        reliable = true;
        firstRound = true;
        // The display comes next; whether the buttons were up before or not, they are not now.
        buttonsUp = false;
    }

    /** A button vanished: a round's display begins. Arrives once per button; only the first one counts. */
    public void buttonsHidden() {
        if (displaying()) return;
        // A round with a display behind it is over, so the next one is no longer the first.
        if (!sequence.isEmpty()) firstRound = false;
        clearRound();
        buttonsUp = false;
        sawStart = true;
        reliable = true;
    }

    /** A display block went dark again (sea lantern back to obsidian). */
    public void lanternShown(BlockPos display) {
        if (!isDisplay(display)) return;
        if (buttonsUp) {
            // Lit while the buttons were up: their vanishing was missed, so this display began unseen.
            clearRound();
            sawStart = false;
            buttonsUp = false;
        }
        if (!sawStart) reliable = false;
        BlockPos pos = display.immutable();
        if (!sequence.contains(pos)) sequence.add(pos);
    }

    /** A button appeared: the display is over and the round can be pressed. */
    public void buttonsShown() {
        if (buttonsUp) return;
        buttonsUp = true;
    }

    /** {@code button} was pressed (the server says so, or the player pressed it). */
    public void pressed(BlockPos button) {
        if (!isButton(button)) return;
        // A press, right or wrong, means the buttons are up even if their return was not seen.
        buttonsUp = true;
        int index = sequence.indexOf(displayOf(button));
        if (index < 0) return;
        progress = Math.max(progress, index + 1);
        if (progress >= sequence.size()) firstRound = false;
    }

    /** World change, switched off, or the player left the device. */
    public void reset() {
        clearRound();
        sawStart = false;
        reliable = false;
        firstRound = false;
        buttonsUp = false;
    }

    private void clearRound() {
        sequence.clear();
        progress = 0;
    }

    /** Blocks have lit up and the buttons are not back yet. */
    private boolean displaying() {
        return !sequence.isEmpty() && !buttonsUp;
    }

    // --- what to show ---

    /**
     * Whether this round's order can be shown: seen from its start, nothing pressed into it, and
     * not a first round that lit more than one block.
     */
    public boolean trusted() {
        return reliable && !sequence.isEmpty() && !(firstRound && sequence.size() > 1);
    }

    /** The button to press now, or null. */
    public BlockPos nextButton() {
        return buttonAt(progress);
    }

    /** The button after that, or null. */
    public BlockPos afterNextButton() {
        return buttonAt(progress + 1);
    }

    private BlockPos buttonAt(int index) {
        if (!trusted() || index >= sequence.size()) return null;
        return buttonOf(sequence.get(index));
    }

    public List<BlockPos> sequence() {
        return List.copyOf(sequence);
    }

    public int progress() {
        return progress;
    }

    public boolean firstRound() {
        return firstRound;
    }
}
