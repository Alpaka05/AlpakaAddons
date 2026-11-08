package net.alpaka.addons.utils;

import net.alpaka.addons.mixin.CustomDataAccessor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Reads an item's custom data (where Hypixel keeps the SkyBlock id, uuid, pet info...) without
 * copying it. {@code CustomData.copyTag()} deep-copies the whole tag, and several features read one
 * field per item per tick or per frame.
 */
public final class ItemData {
    private ItemData() {}

    /** The custom data's own tag. Read only: it is shared with the item, never change it. */
    public static CompoundTag view(CustomData data) {
        return ((CustomDataAccessor) (Object) data).alpaka$tag();
    }

    /** One string field of the item's custom data, or "" when there is none. */
    public static String string(ItemStack stack, String key) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : view(data).getStringOr(key, "");
    }

    /** The SkyBlock id ("ASPECT_OF_THE_END"), or "" for an item without one. */
    public static String id(ItemStack stack) {
        return string(stack, "id");
    }
}
