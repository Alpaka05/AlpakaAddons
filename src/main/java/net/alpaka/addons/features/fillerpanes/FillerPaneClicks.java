package net.alpaka.addons.features.fillerpanes;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.regex.Pattern;

/**
 * Ignore Filler Panes: SkyBlock menus fill their empty cells with black glass panes that have no
 * name. A click that misses a button by a few pixels lands on one, and the client picks the pane up
 * until the server puts it back. Such a click is dropped before anything is sent, so the menu stays
 * as it is. Only ever sends less.
 */
public final class FillerPaneClicks {
    private static final Pattern FORMATTING = Pattern.compile("§.");

    private FillerPaneClicks() {}

    /** Whether a click on this slot should be ignored. */
    public static boolean ignores(Slot slot) {
        if (!AlpakaConfig.instance.ignoreFillerPanesEnabled || slot == null || !slot.hasItem()) return false;
        // The player's own inventory holds real items, even a black pane the player owns.
        if (slot.container instanceof Inventory) return false;
        if (!isFiller(slot.getItem())) return false;
        return SkyblockUtils.isOnSkyblock();
    }

    static boolean isFiller(ItemStack stack) {
        return stack.is(Items.STAINED_GLASS_PANE.black()) && isBlankName(stack.getHoverName().getString());
    }

    /** Hypixel names its fillers " " (sometimes with color codes only); a plain pane keeps its item name. */
    static boolean isBlankName(String name) {
        return FORMATTING.matcher(name).replaceAll("").isBlank();
    }
}
