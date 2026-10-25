package net.alpaka.addons.features.dungeon.terminal;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;

/**
 * The terminal solution drawn on the vanilla chest screen, and the refusal of clicks there
 * (design.md 4.4 and 5.1). Called from AbstractContainerScreenMixin, only while the Terminal Solvers
 * master is on; every method returns at once for a screen whose menu has no
 * {@link TerminalSession}.
 *
 * What it will draw: a fill behind the item of each solution slot in its Mark's color, the label
 * over it, the pending mark on a clicked cell, and with Hide Other Items no item at all on OTHER
 * slots. Display only; the menu is never written to. The custom window's own grid never passes
 * through here, but a vanilla-drawn view inside it (its Classic View) does, guards included.
 *
 * {@link #allowSlotClick} sees every click vanilla is about to send from the screen, whatever its
 * kind: a left or right PICKUP, QUICK_MOVE with Shift, PICKUP_ALL on a double click's release,
 * SWAP from a number key, THROW from the drop key or outside the window ({@code slot} null).
 *
 * Not built yet: draws nothing and lets every click through.
 */
public final class TerminalOverlay {

    private TerminalOverlay() {}

    /**
     * Before vanilla draws a slot (pose already at the screen's corner, so slot.x/slot.y are right).
     * Returns true to skip vanilla's drawing of the item - Hide Other Items - in which case
     * {@link #overSlot} is not called for that slot either.
     */
    public static boolean underSlot(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, Slot slot) {
        return false;
    }

    /** After vanilla drew a slot's item: labels and marks that go on top of it. */
    public static void overSlot(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, Slot slot) {
    }

    /**
     * A click about to be sent from the vanilla chest; false refuses it (nothing is sent).
     * When it returns true it records the click on the session ({@link TerminalSession#onClickSent}).
     * {@code slot} is null for a click outside the window.
     */
    public static boolean allowSlotClick(AbstractContainerScreen<?> screen, Slot slot, int slotId, int button,
                                         ContainerInput input) {
        return true;
    }
}
