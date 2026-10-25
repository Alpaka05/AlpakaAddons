package net.alpaka.addons.features.dungeon.terminal;

/**
 * Turns presses and releases on the custom terminal window's cells into the clicks vanilla's chest
 * screen sends for the same presses (terminals.md 7.1, 7.3), one at most per press. Pure, so it
 * can be tested without a game; the window feeds it the cell under the mouse and whether the
 * cursor holds an item, and sends what comes back.
 *
 * Vanilla sends a left or right click on the press, as a quick move with Shift held - unless the
 * cursor holds an item. That happens on Hypixel's terminals for a moment after every click: the
 * clicked item goes onto the cursor until the server answers. Then vanilla waits for the release
 * and sends the click there, for the slot under the mouse at that moment. This does the same, with
 * two differences that only ever send less than vanilla: a release over another cell than the
 * press is a drag, which vanilla would turn into a QUICK_CRAFT and this drops; and the release of a
 * double click is the same plain click, where vanilla would send a PICKUP_ALL that
 * {@code Outbound.menuClick} does not allow.
 *
 * Middle and side buttons, a press on no cell, and anything after the cursor emptied in between
 * give nothing. There is no queue: what is not sent in the handler of its own press or release is
 * never sent.
 */
public final class TerminalGuiInput {

    /**
     * One click to send now.
     *
     * @param slot      the chest slot
     * @param button    0 left, 1 right, as the player pressed
     * @param quickMove Shift was held: vanilla's QUICK_MOVE instead of PICKUP, same button
     */
    public record Click(int slot, int button, boolean quickMove) {}

    private int heldSlot = -1;
    private int heldButton = -1;

    /**
     * A mouse button went down.
     *
     * @param slot     the clickable cell under the mouse, or -1 for none
     * @param carrying whether the cursor holds an item now
     * @return the click to send now, or null
     */
    public Click press(int slot, int button, boolean shift, boolean carrying) {
        if (button != 0 && button != 1) return null;
        heldSlot = -1;
        heldButton = -1;
        if (slot < 0) return null;
        if (!carrying) return new Click(slot, button, shift);
        heldSlot = slot;
        heldButton = button;
        return null;
    }

    /**
     * A mouse button came up.
     *
     * @param slot     the clickable cell under the mouse now, or -1 for none
     * @param shift    whether Shift is held now; vanilla reads it at the release too
     * @param carrying whether the cursor still holds an item
     * @return the click the press was waiting for, or null
     */
    public Click release(int slot, int button, boolean shift, boolean carrying) {
        if (heldButton < 0) return null;
        int expectedSlot = heldSlot;
        int expectedButton = heldButton;
        heldSlot = -1;
        heldButton = -1;
        // Letting go of another button ends the wait in vanilla too, and the held one's release then
        // does nothing.
        if (button != expectedButton) return null;
        if (!carrying || slot < 0 || slot != expectedSlot) return null;
        return new Click(slot, button, shift);
    }

    /** Whether a press is waiting for its release. */
    public boolean holding() {
        return heldButton >= 0;
    }

    /** Forgets a waiting press, as when the window changes. */
    public void reset() {
        heldSlot = -1;
        heldButton = -1;
    }
}
