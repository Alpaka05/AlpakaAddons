package net.alpaka.addons.features.inventorybuttons

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The heads a button can show that are no item of their own: the ones Hypixel's menus are made of,
 * such as the Sack of Sacks in Your Bags or an island in Fast Travel. An icon names one by its skin
 * on textures.minecraft.net ("head:80a077e2...").
 *
 * The picker gets their names from two lists. [BUILT_IN] is the handful NotEnoughUpdates and
 * Skyblocker offer too: nobody publishes a list of Hypixel's menu heads, so both mods carry these by
 * hand. Every other head is learned from the game ([see]): one in a menu the player has open on
 * SkyBlock is remembered under the name it has there, so opening the SkyBlock Menu once puts its
 * icons into the picker. Only the newest [MAX_SEEN] are kept.
 */
object MenuIcons {
    const val PREFIX = "head:"

    /** How many learned heads are kept; the ones seen longest ago go first. */
    const val MAX_SEEN = 1500
    /** How far the list may grow past [MAX_SEEN] before it is cut back, so that is not done per head. */
    private const val SLACK = 100
    /** How old a head's "last seen" may get before seeing it again is worth writing down. */
    private const val TOUCH_MS = 24 * 3_600_000L
    private const val MAX_NAME = 48

    /** A learned head: what its menu calls it and when it was last seen there. */
    class Seen(val name: String, val at: Long)

    /** The heads known without the game, by skin; in the order the picker lists them. */
    @JvmField
    val BUILT_IN: Map<String, String> = linkedMapOf(
        "1a11a7f11bcd5784903c5201d08261c4df8379109d6e611c1cd3ededf031afed" to "Accessory Bag",
        "80a077e248d142772ea800864f8c578b9d36885b29daf836b64a706882b6ec10" to "Sack of Sacks",
        "eab810b1b4ff598d1d99336e71da4e356d3ad321e169dbd1fa9d506aae61717d" to "Fishing Bag",
        "2fc626b3a12d099c44da04b5f15c95f2e360e0789bccecf842ef09c72b93d842" to "Potion Bag",
        "44e1843df8f91a7033e4c810f10b003e07ade5100079ac44df74a4e46a489447" to "Quiver",
        "e36e94f6c34a35465fce4a90f2e25976389eb9709a12273574ff70fd4daa6852" to "Personal Bank",
        "c232e3820897429157619b0ee099fec0628f602fff12b695de54aef11d923ad7" to "Bazaar",
        "438cf3f8e54afc3b3f91d20a49f324dca1486007fe545399055524c17941f4dc" to "Museum",
        "86f06eaa3004aeed09b3d5b45d976de584e691c0e9cade133635de93d23b9edb" to "Heart of the Mountain",
        "c9c8881e42915a9d29bb61a16fb26d059913204d265df5b439b3d792acd56" to "Private Island",
        "f4880d2c1e7b86e87522e20882656f45bafd42f94932b2c5e0d6ecaa490cb4c" to "Garden",
        "d7cc6687423d0570d556ac53e0676cb563bbdd9717cd8269bdebed6f6d4e7bf8" to "SkyBlock Hub",
        "f4559d75464b2e40a518e4de8e6cf3085f0a3ca0b1b7012614c4cd96fed60378" to "Castle",
        "7ab83858ebc8ee85c3e54ab13aabfcc1ef2ad446d6a900e471c3f33b78906a5b" to "Sirius' Shack",
        "25d2f31ba162fe6272e831aed17f53213db6fa1c4cbe4fc827f3963cc98b9" to "Crypts",
        "838564e28aba98301dbda5fafd86d1da4e2eaeef12ea94dcf440b883e559311c" to "Wizard Tower",
        "4d3a6bd98ac1833c664c4909ff8d2dc62ce887bdcf3cc5b3848651ae5af6b" to "The Barn",
        "6b20b23c1aa2be0270f016b4c90d6ee6b8330a17cfef87869d6ad60b2ffbf3b5" to "Mushroom Desert",
        "6102f82148461ced1f7b62e326eb2db3a94a33cba81d4281452af4d8aeca4991" to "Trapper's Den",
        "a221f813dacee0fef8c59f76894dbb26415478d9ddfc44c2e708a6d3b7549b" to "The Park",
        "79ca3540621c1c79c32bf42438708ff1f5f7d0af9b14a074731107edfeb691c" to "The Park Jungle",
        "1832d53997b451635c9cf9004b0f22bb3d99ab5a093942b5b5f6bb4e4de47065" to "Howling Cave",
        "c754318a3376f470e481dfcd6c83a59aa690ad4b4dd7577fdad1c2ef08d8aee6" to "Spider's Den",
        "9d7e3b19ac4f3dee9c5677c135333b9d35a7f568b63d1ef4ada4b068b5a25" to "Top of the Nest",
        "35e248da2e108f09813a6b848a0fcef111300978180eda41d3d1a7a8e4dba3c3" to "Arachne's Sanctuary",
        "7840b87d52271d2a755dedc82877e0ed3df67dcc42ea479ec146176b02779a5" to "The End",
        "a1cd6d2d03f135e7c6b5d6cdae1b3a68743db4eb749faf7341e9fb347aa283b" to "Dragon's Nest",
        "eb07594e2df273921a77c101d0bfdfa1115abed5b9b2029eb496ceba9bdbb4b3" to "Void Sepulture",
        "c3687e25c632bce8aa61e0d64c24e694c3eea629ea944f4cf30dcfb4fbce071" to "Crimson Isle",
        "6becc645f129c8bc2faa4d8145481fab11ad2ee75749d628dcd999aa94e7" to "Forgotten Skull",
        "73bc965d579c3c6039f0a17eb7c2e6faf538c7a5de8e60ec7a719360d0a857a9" to "Gold Mines",
        "569a1f114151b4521373f34bc14c2963a5011cdc25a6554c48c708cd96ebfc" to "Deep Caverns",
        "51539dddf9ed255ece6348193cd75012c82c93aec381f05572cecf7379711b3b" to "Dwarven Mines",
        "5cbd9f5ec1ed007259996491e69ff649a3106cf920227b1bb3a71ee7a89863f" to "Dwarven Forge",
        "2461ec3bd654f62ca9a393a32629e21b4e497c877d3f3380bcf2db0e20fc0244" to "Dwarven Base Camp",
        "21dbe30b027acbceb612563bd877cd7ebb719ea6ed1399027dcee58bb9049d4a" to "Crystal Hollows",
        "34d42f9c461cee1997b67bf3610c6411bf852b9e5db607bbf626527cfb42912c" to "Crystal Nucleus",
        "9b56895b9659896ad647f58599238af532d46db9c1b0389b8bbeb70999dab33d" to "Dungeon Hub",
        "6dd663136cafa11806fdbca6b596afd85166b4ec02142c8d5ac8941d89ab7" to "Winter Island",
    )

    /** The learned heads by skin. Filled on the client thread, read and written to disk on another. */
    private val seen = ConcurrentHashMap<String, Seen>()

    private val revisions = AtomicInteger()

    /** Goes up whenever the picker's list of heads changes, so the picker can tell. */
    @JvmStatic
    val revision: Int
        get() = revisions.get()

    private val HASH = Regex("[0-9a-f]{16,64}")
    /**
     * The skin in a head's decoded texture value. Hypixel sends some of them as loose JSON without
     * quotes around the keys, so this reads the one address rather than parsing the whole thing.
     */
    private val SKIN = Regex("SKIN\"?\\s*:\\s*\\{\\s*\"?url\"?\\s*:\\s*\"https?://textures\\.minecraft\\.net/texture/([0-9a-fA-F]{16,64})\"")
    private val FORMAT = Regex("§.")
    private val PLAYER_NAME = Regex("\\w{3,16}")

    /** The skin an icon string names, or null if it is no head or names nothing usable. */
    @JvmStatic
    fun hash(icon: String?): String? {
        if (icon == null || !icon.startsWith(PREFIX)) return null
        return icon.substring(PREFIX.length).takeIf { HASH.matches(it) }
    }

    /** The skin of a head from its texture value, as the item carries it; null if it has none. */
    @JvmStatic
    fun hashOf(textureValue: String?): String? {
        if (textureValue.isNullOrEmpty()) return null
        val json = try {
            String(Base64.getDecoder().decode(textureValue.trim()), StandardCharsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            return null
        }
        return SKIN.find(json)?.groupValues?.get(1)?.lowercase(Locale.ROOT)
    }

    /** The texture value a head with this skin carries. */
    @JvmStatic
    fun textureValue(hash: String): String {
        val json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/$hash\"}}}"
        return Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
    }

    /** What the picker calls the head an icon string names; null for one it has no name for. */
    @JvmStatic
    fun name(icon: String?): String? {
        val hash = hash(icon) ?: return null
        return BUILT_IN[hash] ?: seen[hash]?.name
    }

    /**
     * Whether a head shows a player rather than something of the menu's: its profile says whose it
     * is and the item is named after them, as in the Party Finder. Those are not worth remembering,
     * and there are a great many of them.
     */
    @JvmStatic
    fun showsPlayer(profileName: String?, itemName: String): Boolean =
        profileName != null && PLAYER_NAME.matches(profileName) && itemName.contains(profileName, ignoreCase = true)

    /**
     * Remembers a head from a menu under the name it has there. True when that is news worth
     * writing to disk: a head not known yet, a new name for one, or one last seen long ago.
     */
    @JvmStatic
    fun see(hash: String, itemName: String, now: Long): Boolean {
        if (!HASH.matches(hash) || BUILT_IN.containsKey(hash)) return false
        val name = FORMAT.replace(itemName, "").trim().take(MAX_NAME)
        if (name.isEmpty()) return false
        val known = seen[hash]
        if (known != null && known.name == name && now - known.at < TOUCH_MS) return false
        seen[hash] = Seen(name, now)
        if (known == null || known.name != name) revisions.incrementAndGet()
        if (seen.size > MAX_SEEN + SLACK) trim()
        return true
    }

    private fun trim() {
        val oldestFirst = seen.entries.sortedBy { it.value.at }
        for (entry in oldestFirst.take(seen.size - MAX_SEEN)) seen.remove(entry.key)
        revisions.incrementAndGet()
    }

    /**
     * Every head with its name, by icon string: the built-in ones in their order, then the learned
     * ones by name.
     */
    @JvmStatic
    fun all(): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((hash, name) in BUILT_IN) out[PREFIX + hash] = name
        seen.entries.sortedWith(compareBy({ it.value.name.lowercase(Locale.ROOT) }, { it.key }))
            .forEach { out[PREFIX + it.key] = it.value.name }
        return out
    }

    /** The learned heads as they are written to disk. */
    @JvmStatic
    fun json(): String {
        val icons = JsonObject()
        for ((hash, head) in seen.entries.sortedBy { it.key }) {
            val o = JsonObject()
            o.addProperty("name", head.name)
            o.addProperty("at", head.at)
            icons.add(hash, o)
        }
        val root = JsonObject()
        root.add("icons", icons)
        return root.toString()
    }

    /** Takes the learned heads from the file's text; one learned meanwhile stays. */
    @JvmStatic
    fun read(json: String) {
        val root = JsonParser.parseString(json) as? JsonObject ?: return
        val icons = root.get("icons") as? JsonObject ?: return
        var added = false
        for ((hash, value) in icons.entrySet()) {
            val o = value as? JsonObject ?: continue
            if (!HASH.matches(hash) || BUILT_IN.containsKey(hash)) continue
            val name = o.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.take(MAX_NAME) ?: continue
            val at = o.get("at")?.takeIf { it.isJsonPrimitive }?.asLong ?: continue
            if (name.isEmpty()) continue
            if (seen.putIfAbsent(hash, Seen(name, at)) == null) added = true
        }
        if (seen.size > MAX_SEEN) trim()
        if (added) revisions.incrementAndGet()
    }

    /** Forgets every learned head; for tests. */
    internal fun clear() {
        seen.clear()
        revisions.incrementAndGet()
    }
}
