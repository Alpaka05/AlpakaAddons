package net.alpaka.addons.features.inventorybuttons

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.Locale

/**
 * The items a button can show: a Minecraft item by id ("minecraft:ender_chest", or just
 * "ender_chest"), or one of SkyBlock's own by its SkyBlock id ("skyblock:HYPERION").
 *
 * The Minecraft stacks are made once per id and kept: a button is drawn every frame. Before a world
 * is joined, 26.2 refuses to make any item stack ("Components not bound yet"), so the editor opened
 * from the title screen gets no stack and draws a letter instead. SkyBlock items come from Hypixel's
 * item list ([SkyblockIcons]), which loads in the background; until it is in, they show as a letter
 * too and the picker lists only Minecraft's.
 */
object InventoryButtonIcons {
    const val SKYBLOCK = "skyblock:"

    private val stacks = HashMap<String, ItemStack?>()
    private var vanilla: List<String>? = null
    private var combined: List<String>? = null
    /** SkyBlock items' names by their icon string, for the picker's search and tooltips. */
    private var names: Map<String, String> = emptyMap()

    @JvmStatic
    fun item(icon: String?): Item? {
        val id = Identifier.tryParse(icon?.trim()?.lowercase().orEmpty()) ?: return null
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null)?.takeIf { it !== Items.AIR }
    }

    @JvmStatic
    fun stack(icon: String?): ItemStack? {
        if (!Items.STONE.builtInRegistryHolder().areComponentsBound()) return null
        val key = icon.orEmpty()
        if (key.startsWith(SKYBLOCK)) return SkyblockIcons.stack(key.substring(SKYBLOCK.length), names[key])
        return stacks.getOrPut(key) { item(key)?.let { ItemStack(it) } }
    }

    /** The id without "minecraft:" or "skyblock:", for the first letter of a missing icon. */
    @JvmStatic
    fun path(icon: String?): String = icon.orEmpty().substringAfter(':')

    /** What the picker calls an icon: a SkyBlock item's name, or a Minecraft item's id in words. */
    @JvmStatic
    fun name(icon: String?): String = names[icon] ?: path(icon).replace('_', ' ')

    /**
     * Every item a button can show, for the editor's picker: SkyBlock's own first, once Hypixel's list
     * is in, then Minecraft's. The same list until that changes, so the picker can tell when it has.
     */
    @JvmStatic
    fun all(): List<String> {
        combined?.let { return it }
        val minecraft = vanilla ?: BuiltInRegistries.ITEM.keySet()
            .filter { it.path != "air" }
            .map { if (it.namespace == "minecraft") "minecraft:${it.path}" else it.toString() }
            .sortedWith(compareBy({ !it.startsWith("minecraft:") }, { it }))
            .also { vanilla = it }
        val skyblock = SkyblockIcons.items() ?: return minecraft
        names = skyblock.entries.associate { (id, name) -> SKYBLOCK + id to name }
        return (names.entries.sortedBy { it.value.lowercase(Locale.ROOT) }.map { it.key } + minecraft)
            .also { combined = it }
    }

    /**
     * The icons that match what was typed into the picker's search: every word somewhere in the id,
     * or in a SkyBlock item's name.
     */
    @JvmStatic
    fun search(all: List<String>, query: String): List<String> {
        val words = query.trim().lowercase(Locale.ROOT).replace(' ', '_').split('_').filter { it.isNotEmpty() }
        if (words.isEmpty()) return all
        return all.filter { id ->
            val text = path(id).lowercase(Locale.ROOT) + " " + names[id]?.lowercase(Locale.ROOT).orEmpty()
            words.all { text.contains(it) }
        }
    }
}
