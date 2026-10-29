package net.alpaka.addons.features.enchantments;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * In SkyBlock's anvil, once one enchanted book is in, the books in the inventory it can be combined
 * with are highlighted: those with exactly the same enchantments at the same levels, which is what
 * turns two Sharpness V into one Sharpness VI.
 *
 * SkyBlock's anvil is a chest menu titled "Anvil", with the two items to combine in slots 29 and 33
 * (SkyHanni reads the same slots). Books are compared by the "enchantments" compound of Hypixel's
 * custom data, so the lore's wording and colors don't matter.
 */
public final class AnvilCombineHelper {

    private AnvilCombineHelper() {
    }

    private static final int LEFT_SLOT = 29;
    private static final int RIGHT_SLOT = 33;
    private static final int CHEST_SLOTS = 54;

    /**
     * Enchantments per custom data, so the 36 inventory slots don't copy their tag every frame.
     * Custom data is immutable, so its identity stands for its contents; dropped with the screen.
     */
    private static final Map<CustomData, CompoundTag> CACHE = new IdentityHashMap<>();
    private static AbstractContainerScreen<?> cachedScreen;

    /** Draws the highlight behind a slot's item; called for every slot, inside the menu's frame. */
    public static void extractSlotBackground(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, Slot slot) {
        if (!AlpakaConfig.instance.anvilCombineHelperEnabled) return;
        if (!(slot.container instanceof Inventory) || !(screen instanceof ContainerScreen)) return;
        if (!"Anvil".equals(screen.getTitle().getString())) return;
        if (screen != cachedScreen) {
            CACHE.clear();
            cachedScreen = screen;
        }

        CompoundTag wanted = wanted(screen.getMenu());
        if (wanted == null) return;
        CompoundTag have = enchantments(slot.getItem());
        if (wanted.equals(have)) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, AlpakaConfig.instance.anvilCombineHelperColor);
        }
    }

    /** The enchantments of the one book in the anvil, or null if there is none or both sides are filled. */
    private static CompoundTag wanted(AbstractContainerMenu menu) {
        if (menu.slots.size() < CHEST_SLOTS) return null;
        ItemStack left = menu.slots.get(LEFT_SLOT).getItem();
        ItemStack right = menu.slots.get(RIGHT_SLOT).getItem();
        CompoundTag leftEnchants = enchantments(left);
        CompoundTag rightEnchants = enchantments(right);
        if (leftEnchants != null && isEmptySide(right)) return leftEnchants;
        if (rightEnchants != null && isEmptySide(left)) return rightEnchants;
        return null;
    }

    /** A side counts as free unless it holds a SkyBlock item, in case Hypixel puts a pane there. */
    private static boolean isEmptySide(ItemStack stack) {
        CompoundTag tag = tag(stack);
        return tag == null || tag.getStringOr("id", "").isEmpty();
    }

    /** An enchanted book's enchantments, or null for anything else. */

    private static CompoundTag enchantments(ItemStack stack) {
        CompoundTag tag = tag(stack);
        if (tag == null || !"ENCHANTED_BOOK".equals(tag.getStringOr("id", ""))) return null;
        CompoundTag enchants = tag.getCompoundOrEmpty("enchantments");
        return enchants.isEmpty() ? null : enchants;
    }

    private static CompoundTag tag(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null || data.isEmpty()) return null;
        if (CACHE.size() > 256) CACHE.clear();
        return CACHE.computeIfAbsent(data, CustomData::copyTag);
    }
}
