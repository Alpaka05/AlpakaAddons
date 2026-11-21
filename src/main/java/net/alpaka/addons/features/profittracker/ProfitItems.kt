package net.alpaka.addons.features.profittracker

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.itemvalue.ItemPrices
import net.alpaka.addons.features.itemvalue.ItemValue
import net.alpaka.addons.features.market.ItemIcons
import net.alpaka.addons.utils.ItemData
import net.alpaka.addons.utils.SkyblockUtils
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack
import java.util.Locale

/**
 * Which item a stack is, for the tracker, and what it is worth.
 *
 * Items are filed under the key the price lists know them by, the one Item Value prices with: the
 * SkyBlock id for most, "TYPE;rarity" for a pet ("ENDER_DRAGON;4"), "ENCHANTMENT_NAME_LEVEL" for a
 * book with one enchantment, "RUNE_NAME_LEVEL" for a rune. So a Legendary and an Epic pet of the same kind are two lines, and
 * every book is priced as what is written in it.
 */
object ProfitItems {

    /** The key coins are written under in the CSV files. */
    const val COINS = "COINS"

    /** Pet rarities in the order NEU's and SkyCofl's keys number them. */
    private val RARITIES = listOf("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC")

    /**
     * Items that are never income: the SkyBlock menu, the game's own tools for moving around, and
     * things Hypixel puts in and takes out of the inventory itself.
     */
    private val IGNORED = setOf("SKYBLOCK_MENU", "ABIPHONE_CONTACTS", "GAME_MENU")

    /** The stack's key, or null for something that is not a SkyBlock item (or not one to count). */
    @JvmStatic
    fun key(stack: ItemStack): String? {
        if (stack.isEmpty) return null
        val data = stack.get(DataComponents.CUSTOM_DATA) ?: return null
        val tag = ItemData.view(data)
        val id = tag.getStringOr("id", "")
        if (id.isEmpty() || id in IGNORED) return null
        return when (id) {
            "PET" -> petKey(tag.getStringOr("petInfo", "")) ?: id
            "ENCHANTED_BOOK" -> {
                val enchants = tag.getCompoundOrEmpty("enchantments")
                val names = enchants.keySet()
                if (names.size == 1) {
                    val name = names.first()
                    "ENCHANTMENT_" + name.uppercase(Locale.ROOT) + "_" + enchants.getIntOr(name, 0)
                } else id
            }
            // Priced like Item Value prices a rune: "RUNE_LAVATEARS_1".
            "RUNE", "UNIQUE_RUNE" -> {
                val runes = tag.getCompoundOrEmpty("runes")
                val names = runes.keySet()
                if (names.size == 1) {
                    val name = names.first()
                    "RUNE_" + name.uppercase(Locale.ROOT) + "_" + runes.getIntOr(name, 0)
                } else id
            }
            else -> id
        }
    }

    /** "ENDER_DRAGON;4" from a pet's info; null if it can't be read. */
    fun petKey(petInfo: String): String? {
        if (petInfo.isEmpty()) return null
        return try {
            val root = JsonParser.parseString(petInfo)
            if (!root.isJsonObject) return null
            val o: JsonObject = root.asJsonObject
            val type = o.get("type")?.asString ?: return null
            val rarity = RARITIES.indexOf(o.get("tier")?.asString?.uppercase(Locale.ROOT) ?: return null)
            if (rarity < 0) null else "$type;$rarity"
        } catch (e: RuntimeException) {
            null
        }
    }

    /** The stack's name without colors or a pet's level, for the HUD and the CSV. */
    @JvmStatic
    fun name(stack: ItemStack): String {
        val raw = SkyblockUtils.cleanColor(stack.hoverName.string).trim()
        return PET_LEVEL.replace(raw, "").trim()
    }

    private val PET_LEVEL = Regex("""^\[Lvl \d+]\s*""")

    /** A readable name for a key nobody named yet: Hypixel's list, else the key itself. */
    fun nameOf(key: String): String {
        ItemIcons.name(key)?.let { return it }
        return try {
            ItemValue.nameOf(key, CATALOG)
        } catch (e: RuntimeException) {
            key
        }
    }

    private val CATALOG = object : ItemValue.Catalog {
        override fun name(id: String): String? = ItemIcons.name(id)
        override fun upgrades(id: String): ItemIcons.Upgrades? = ItemIcons.upgrades(id)
    }

    /**
     * What one is worth now: its Bazaar price at the side the settings pick, else its lowest BIN,
     * else what an NPC pays; NaN while the lists load or for an item no one buys.
     *
     * With "Prefer NPC Price" an item counts at the NPC's price wherever that is the higher one,
     * since selling it to the NPC is what a player then does.
     */
    @JvmStatic
    fun price(key: String): Double {
        if (key == COINS) return 1.0
        val cfg = AlpakaConfig.instance
        val market = ItemPrices.quote(key, cfg.profitTrackerPriceSide == 0)?.price() ?: Double.NaN
        val npc = ItemIcons.npcPrice(key)
        return when {
            market.isNaN() -> npc
            npc.isNaN() -> market
            cfg.profitTrackerPreferNpc -> maxOf(market, npc)
            else -> market
        }
    }
}
