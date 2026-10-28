package net.alpaka.addons.features.storagepreview;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Draws a {@link StoragePreviewTooltip} as a chest-like grid of slots under the item's name. */
public final class ClientStoragePreviewTooltip implements ClientTooltipComponent {

    private static final int COLUMNS = 9;
    private static final int SLOT = 18;
    /** Space between the name line above and the grid, and below the grid before any lore. */
    private static final int MARGIN = 2;

    private static final int SLOT_COLOR = 0x40000000;

    private final StoragePreviewTooltip preview;
    private final int rows;

    public ClientStoragePreviewTooltip(StoragePreviewTooltip preview) {
        this.preview = preview;
        this.rows = (preview.items().size() + COLUMNS - 1) / COLUMNS;
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
