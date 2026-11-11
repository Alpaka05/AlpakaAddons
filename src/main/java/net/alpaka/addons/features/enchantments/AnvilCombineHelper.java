package net.alpaka.addons.features.enchantments;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.ItemData;
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

import java.lang.ref.WeakReference;

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

    /** Weak, so a closed chest screen (and through its menu the world) is not kept alive. */
    private static WeakReference<AbstractContainerScreen<?>> cachedScreen = new WeakReference<>(null);
    /** Whether {@link #cachedScreen} is the anvil; a screen's title never changes, so it is read once, not per slot. */
    private static boolean cachedIsAnvil;

    /** Draws the highlight behind a slot's item; called for every slot, inside the menu's frame. */
    public static void extractSlotBackground(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, Slot slot) {
        if (!AlpakaConfig.instance.anvilCombineHelperEnabled) return;
        if (!(slot.container instanceof Inventory) || !(screen instanceof ContainerScreen)) return;
        if (screen != cachedScreen.get()) {
            cachedScreen = new WeakReference<>(screen);
            // A new menu: nothing of the last one's inputs carries over.
            wantedValid = false;
            wantedLeft = null;
            wantedRight = null;
            wantedTag = null;
            MATCHES.clear();
            cachedIsAnvil = "Anvil".equals(screen.getTitle().getString());
        }
        if (!cachedIsAnvil) return;

        CompoundTag wanted = wantedMemo(screen.getMenu());
        if (wanted == null) return;
        if (matches(wanted, slot.getItem())) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, AlpakaConfig.instance.anvilCombineHelperColor);
        }
    }

    /**
     * {@link #wanted} for the anvil's two inputs, worked out again only when one of them changes:
     * every inventory slot asks for it, every frame. It depends on the inputs' data alone, and an
     * item's data is replaced, never changed, so its object stands for its contents.
     */
    private static CompoundTag wantedMemo(AbstractContainerMenu menu) {
        if (menu.slots.size() < CHEST_SLOTS) return null;
        CustomData left = dataOf(menu.slots.get(LEFT_SLOT).getItem());
        CustomData right = dataOf(menu.slots.get(RIGHT_SLOT).getItem());
        if (wantedValid && left == wantedLeft && right == wantedRight) return wantedTag;
        wantedLeft = left;
        wantedRight = right;
        wantedTag = wanted(menu);
        wantedValid = true;
        MATCHES.clear();
        return wantedTag;
    }

    /** Whether the item holds exactly the wanted enchantments, kept per item data while the wanted ones stay. */
    private static boolean matches(CompoundTag wanted, ItemStack stack) {
        CustomData data = dataOf(stack);
        if (data == null) return wanted.equals(enchantments(stack));
        Boolean known = MATCHES.get(data);
        if (known != null) return known;
        boolean match = wanted.equals(enchantments(stack));
        if (MATCHES.size() >= 256) MATCHES.clear();
        MATCHES.put(data, match);
        return match;
    }

    /** The item's custom data as {@link #tag} reads it, or null when {@link #tag} would give null. */
    private static CustomData dataOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null || data.isEmpty() ? null : data;
    }

    // Render thread only (slot backgrounds).
    private static boolean wantedValid;
    private static CustomData wantedLeft;
    private static CustomData wantedRight;
    private static CompoundTag wantedTag;
    private static final java.util.Map<CustomData, Boolean> MATCHES = new java.util.IdentityHashMap<>();

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
        // Read in place, never changed: no copy per slot and frame.
        return ItemData.view(data);
    }
}
