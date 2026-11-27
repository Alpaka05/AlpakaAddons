package net.alpaka.addons.features.inventorybuttons

import net.alpaka.addons.features.market.ItemIcons
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.Locale

/**
 * The items a button can show: a Minecraft item by id ("minecraft:ender_chest", or just
 * "ender_chest"), one of SkyBlock's own by its SkyBlock id ("skyblock:HYPERION"), or a head out of
 * one of Hypixel's menus by its skin ("head:80a077e2...", see [MenuIcons]).
 *
 * The Minecraft stacks are made once per id and kept: a button is drawn every frame. Before a world
 * is joined, 26.2 refuses to make any item stack ("Components not bound yet"), so the editor opened
 * from the title screen gets no stack and draws a letter instead. SkyBlock items come from Hypixel's
 * item list ([SkyblockIcons]), which loads in the background; until it is in, they show as a letter
 * too and the picker lists only the menu heads and Minecraft's.
 */
object InventoryButtonIcons {
    const val SKYBLOCK = "skyblock:"
    const val HEAD = MenuIcons.PREFIX

    private val stacks = HashMap<String, ItemStack?>()
    private var vanilla: List<String>? = null
    private var combined: List<String>? = null
    /** What [combined] was built from: SkyBlock's list, null before it is in, and the menu heads known then. */
    private var combinedFrom: Map<String, String>? = null
    private var combinedAt = -1
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
        if (key.startsWith(HEAD)) return stacks.getOrPut(key) { MenuIcons.hash(key)?.let { ItemIcons.head(MenuIcons.textureValue(it)) } }
        return stacks.getOrPut(key) { item(key)?.let { ItemStack(it) } }
    }

    /** The id without "minecraft:", "skyblock:" or "head:". */
    @JvmStatic
    fun path(icon: String?): String = icon.orEmpty().substringAfter(':')

    /**
     * What the picker calls an icon: a SkyBlock item's or a menu head's name, or a Minecraft item's
     * id in words. A head nobody has a name for (a config file from another player) is just a head.
     */
    @JvmStatic
    fun name(icon: String?): String {
        if (icon != null && icon.startsWith(HEAD)) return MenuIcons.name(icon) ?: "Head"
        return names[icon] ?: path(icon).replace('_', ' ')
    }

    /** The letter drawn where an icon has no item yet. */
    @JvmStatic
    fun letter(icon: String?): String = name(icon).trim().take(1).uppercase(Locale.ROOT).ifEmpty { "?" }

    /**
     * Every item a button can show, for the editor's picker: the heads of Hypixel's menus first, then
     * SkyBlock's own items, once Hypixel's list is in, then Minecraft's. The same list until any of
     * that changes, so the picker can tell when it has.
     */
    @JvmStatic
    fun all(): List<String> {
        val skyblock = SkyblockIcons.items()
        val revision = MenuIcons.revision
        combined?.let { if (combinedFrom === skyblock && combinedAt == revision) return it }
        val minecraft = vanilla ?: BuiltInRegistries.ITEM.keySet()
            .filter { it.path != "air" }
            .map { if (it.namespace == "minecraft") "minecraft:${it.path}" else it.toString() }
            .sortedWith(compareBy({ !it.startsWith("minecraft:") }, { it }))
            .also { vanilla = it }
        if (skyblock !== combinedFrom) names = skyblock.orEmpty().entries.associate { (id, name) -> SKYBLOCK + id to name }
        val list = MenuIcons.all().keys.toList() + names.entries.sortedBy { it.value.lowercase(Locale.ROOT) }.map { it.key } + minecraft
        combined = list
        combinedFrom = skyblock
        combinedAt = revision
        return list
    }

    /**
     * The icons that match what was typed into the picker's search: every word somewhere in the id,
     * or in a SkyBlock item's name. A menu head is found by its name alone: its id is a string of
     * hex digits, in which "bed" or "face" would turn up by chance.
     */
    @JvmStatic
    fun search(all: List<String>, query: String): List<String> {
        val words = query.trim().lowercase(Locale.ROOT).replace(' ', '_').split('_').filter { it.isNotEmpty() }
        if (words.isEmpty()) return all
        return all.filter { id ->
            val text = if (id.startsWith(HEAD)) MenuIcons.name(id)?.lowercase(Locale.ROOT).orEmpty()
            else path(id).lowercase(Locale.ROOT) + " " + names[id]?.lowercase(Locale.ROOT).orEmpty()
            words.all { text.contains(it) }
        }
    }
}
