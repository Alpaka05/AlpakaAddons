package net.alpaka.addons.features.storagepreview;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Draws a {@link StoragePreviewTooltip} as a chest-like grid of slots under the item's name. */
public final class ClientStoragePreviewTooltip implements ClientTooltipComponent {

    static final int COLUMNS = 9;
    static final int SLOT = 18;
    /** Space between the name line above and the grid, and below the grid before any lore. */
    static final int MARGIN = 2;

    private static final int SLOT_COLOR = 0x40000000;

    /** How long after it was drawn a tooltip still counts as on screen, for the freeze key. */
    private static final long SHOWN_NANOS = 250_000_000L;

    /** Where a preview was drawn as part of an item's tooltip; see {@link #lastShown}. */
    record Shown(List<ItemStack> items, int x, int y, long nanos) {}

    private static Shown lastShown = null;

    private final StoragePreviewTooltip preview;
    private final int rows;
    /** Drawn by the freeze key's pinned tooltip, not by the game's tooltip code. */
    private final boolean pinned;

    public ClientStoragePreviewTooltip(StoragePreviewTooltip preview) {
        this(preview, false);
    }

    ClientStoragePreviewTooltip(StoragePreviewTooltip preview, boolean pinned) {
        this.preview = preview;
        this.pinned = pinned;
        this.rows = (preview.items().size() + COLUMNS - 1) / COLUMNS;
    }

    /**
     * Where the game drew this page's preview a moment ago (the left edge of the tooltip's content and
     * the top of the grid's line), or null when it is not on screen. The freeze key pins the tooltip
     * exactly there, however the game or another mod placed it.
     */
    static Shown lastShown(List<ItemStack> items) {
        Shown shown = lastShown;
        if (shown == null || shown.items() != items || System.nanoTime() - shown.nanos() > SHOWN_NANOS) return null;
        return shown;
    }

    @Override
    public int getHeight(Font font) {
        return rows * SLOT + 2 * MARGIN;
    }

    @Override
    public int getWidth(Font font) {
        return COLUMNS * SLOT;
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
        if (!pinned) lastShown = new Shown(preview.items(), x, y, System.nanoTime());
        int top = y + MARGIN;
        for (int i = 0; i < preview.items().size(); i++) {
            int sx = x + (i % COLUMNS) * SLOT;
            int sy = top + (i / COLUMNS) * SLOT;
            graphics.fill(sx, sy, sx + SLOT - 1, sy + SLOT - 1, SLOT_COLOR);
            ItemStack stack = preview.items().get(i);
            if (stack.isEmpty()) continue;
            graphics.item(stack, sx + 1, sy + 1);
            graphics.itemDecorations(font, stack, sx + 1, sy + 1);
        }
    }
}
