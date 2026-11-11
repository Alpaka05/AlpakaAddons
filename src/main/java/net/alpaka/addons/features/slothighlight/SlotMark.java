package net.alpaka.addons.features.slothighlight;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;

/** Item Search's slot mark: the slot filled with the color, and a solid outline around it. */
public final class SlotMark {
    private SlotMark() {}

    /** Draws the mark; called from the slot's own drawing, where the pose starts at the menu's corner. */
    public static void draw(GuiGraphicsExtractor graphics, Slot slot, int color) {
        graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
        graphics.outline(slot.x - 1, slot.y - 1, 18, 18, color | 0xFF000000);
    }
}
