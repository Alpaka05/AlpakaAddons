package net.alpaka.addons.features.inventorybuttons

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

/**
 * The items a button can show, by id ("minecraft:ender_chest", or just "ender_chest").
 *
 * The stacks are made once per id and kept: a button is drawn every frame. Before a world is
 * joined, 26.2 refuses to make any item stack ("Components not bound yet"), so the editor opened
 * from the title screen gets no stack and draws a letter instead.
 */
object InventoryButtonIcons {
    private val stacks = HashMap<String, ItemStack?>()

    @JvmStatic
    fun item(icon: String?): Item? {
        val id = Identifier.tryParse(icon?.trim()?.lowercase().orEmpty()) ?: return null
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null)?.takeIf { it !== Items.AIR }
    }

    @JvmStatic
    fun stack(icon: String?): ItemStack? {
        if (!Items.STONE.builtInRegistryHolder().areComponentsBound()) return null
        val key = icon.orEmpty()
        return stacks.getOrPut(key) { item(key)?.let { ItemStack(it) } }
    }

    /** The id without "minecraft:", for labels and the first letter of a missing icon. */
    @JvmStatic
    fun path(icon: String?): String = icon.orEmpty().substringAfter(':')

    /** Every item a button can show, as ids, for the editor's picker. */
    @JvmStatic
    fun all(): List<String> = BuiltInRegistries.ITEM.keySet()
        .filter { it.path != "air" }
        .map { if (it.namespace == "minecraft") "minecraft:${it.path}" else it.toString() }
        .sortedWith(compareBy({ !it.startsWith("minecraft:") }, { it }))

    /** The ids that match what was typed into the picker's search: every word somewhere in the id. */
    @JvmStatic
    fun search(all: List<String>, query: String): List<String> {
        val words = query.trim().lowercase().replace(' ', '_').split('_').filter { it.isNotEmpty() }
        if (words.isEmpty()) return all
        return all.filter { id -> words.all { id.substringAfter(':').contains(it) } }
    }
}
