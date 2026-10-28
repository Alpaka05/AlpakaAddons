package net.alpaka.addons.features.inventorybuttons

import com.google.common.collect.ImmutableMultimap
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import net.alpaka.addons.AlpakaAddons
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.market.LegacyItems
import net.alpaka.addons.utils.ModVersion
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.component.ResolvableProfile
import java.io.InputStreamReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID
import java.util.zip.GZIPInputStream

/**
 * SkyBlock's own items for the buttons, from Hypixel's public item list: what each is called and
 * what it looks like (material, head skin, glint, leather color). Fetched once a session, the first
 * time the icon picker or a button asks, and only while Network Features is on. The request names
 * nothing about the player.
 */
internal object SkyblockIcons {
    private const val ITEMS = "https://api.hypixel.net/v2/resources/skyblock/items"

    private class Look(val name: String, val material: String, val damage: Int, val skin: String?, val glowing: Boolean, val color: String?)

    @Volatile private var looks: Map<String, Look>? = null
    @Volatile private var names: Map<String, String>? = null
    @Volatile private var fetching = false
    @Volatile private var retryAt = 0L
    /** Built items by id; only touched on the render thread. */
    private val stacks = HashMap<String, ItemStack?>()

    /** Every SkyBlock item by id with its name; null while the list loads or Network Features is off. */
    fun items(): Map<String, String>? {
        if (!AlpakaConfig.instance.allowApiCalls) return null
        load()
        return names
    }

    /** The item with this SkyBlock id; null while the list loads, or for an id it does not have. */
    fun stack(id: String, @Suppress("UNUSED_PARAMETER") name: String?): ItemStack? {
        if (!AlpakaConfig.instance.allowApiCalls) return null
        val loaded = looks ?: run { load(); return null }
        return stacks.getOrPut(id) { loaded[id]?.let(::build) }
    }

    private fun build(look: Look): ItemStack? {
        val id = Identifier.tryParse("minecraft:" + LegacyItems.modernId(look.material, look.damage)) ?: return null
        val item = BuiltInRegistries.ITEM.getOptional(id).orElse(null) ?: return null
        val stack = if (item === Items.PLAYER_HEAD && look.skin != null) head(look.skin) else ItemStack(item)
        if (look.glowing) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        val rgb = look.color?.split(',')
        if (rgb != null && rgb.size == 3 && look.material.lowercase().startsWith("leather_")) {
            runCatching { rgb.map { it.trim().toInt() } }.getOrNull()?.let { (r, g, b) ->
                stack.set(DataComponents.DYED_COLOR, DyedItemColor(r shl 16 or (g shl 8) or b))
            }
        }
        return stack
    }

    /** A player head wearing the skin with this texture value, as Hypixel's heads carry it. */
    private fun head(texture: String): ItemStack {
        val uuid = UUID.nameUUIDFromBytes(texture.toByteArray(StandardCharsets.UTF_8))
        val properties = PropertyMap(ImmutableMultimap.of("textures", Property("textures", texture)))
        val stack = ItemStack(Items.PLAYER_HEAD)
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(GameProfile(uuid, "alpaka", properties)))
        return stack
    }

    @Synchronized
    private fun load() {
        if (fetching || looks != null || System.currentTimeMillis() < retryAt) return
        fetching = true
        val request = HttpRequest.newBuilder(URI.create(ITEMS))
            .timeout(Duration.ofSeconds(30))
            .header("User-Agent", "AlpakaAddons/" + ModVersion.mod())
            // About 4.8 MB as text, under 2 MB packed.
            .header("Accept-Encoding", "gzip")
            .GET()
            .build()
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
            .sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
            .whenComplete { response, error ->
                try {
                    if (error != null || response == null || response.statusCode() != 200) {
                        AlpakaAddons.LOGGER.warn("Could not load Hypixel's item list ({})", error?.toString() ?: "HTTP ${response?.statusCode()}")
                        retryAt = System.currentTimeMillis() + 120_000L
                        return@whenComplete
                    }
                    val gzip = response.headers().firstValue("Content-Encoding").map { it.contains("gzip") }.orElse(false)
                    response.body().use { raw ->
                        val body = if (gzip) GZIPInputStream(raw) else raw
                        InputStreamReader(body, StandardCharsets.UTF_8).use { index(it) }
                    }
                } catch (e: Exception) {
                    AlpakaAddons.LOGGER.warn("Could not read Hypixel's item list", e)
                    retryAt = System.currentTimeMillis() + 120_000L
                } finally {
                    fetching = false
                }
            }
    }

    /** SkyBlock's own items from Hypixel's answer; plain Minecraft ones (DIAMOND_SWORD, WOOL:6) are left out. */
    private fun index(reader: InputStreamReader) {
        val root = JsonParser.parseReader(reader)
        val byId = HashMap<String, Look>()
        val items = root.takeIf { it.isJsonObject }?.asJsonObject?.getAsJsonArray("items") ?: return
        for (element in items) {
            if (!element.isJsonObject) continue
            val o = element.asJsonObject
            val id = o.string("id")
            val name = o.string("name").replace(Regex("§."), "").trim()
            val material = o.string("material")
            val damage = if (o.has("durability")) o.get("durability").asInt else 0
            if (id.isEmpty() || name.isEmpty() || material.isEmpty()) continue
            val museum = o.has("museum") && o.get("museum").asBoolean
            if (!museum && id != "ENCHANTED_BOOK" && (id == material || id == "$material:$damage")) continue
            val skin = (o.get("skin") as? JsonObject)?.string("value")?.ifEmpty { null }
            val glowing = o.has("glowing") && o.get("glowing").asBoolean
            byId[id] = Look(name, material, damage, skin, glowing, o.string("color").ifEmpty { null })
        }
        names = byId.mapValues { it.value.name }
        looks = byId
    }

    private fun JsonObject.string(key: String): String {
        val e = get(key)
        return if (e == null || e.isJsonNull || !e.isJsonPrimitive) "" else e.asString
    }
}
