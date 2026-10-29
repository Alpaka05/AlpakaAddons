package net.alpaka.addons.features.inventorybuttons

import net.alpaka.addons.features.market.ItemIcons
import net.minecraft.world.item.ItemStack

/**
 * SkyBlock's own items for the buttons, from the list the market screens already load
 * ([ItemIcons]): Hypixel's item list, with the NotEnoughUpdates repository's look where it has one.
 */
internal object SkyblockIcons {
    /** Every SkyBlock item by id with its name; null while the list loads or Network Features is off. */
    fun items(): Map<String, String>? = ItemIcons.skyblockItems()

    /** The item with this SkyBlock id; null while it is still being looked up. */
    fun stack(id: String, name: String?): ItemStack? = ItemIcons.forItem(id, name).takeUnless { it.isEmpty }
}
