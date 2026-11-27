package net.alpaka.addons.features.playermodel

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.market.ItemIcons
import net.alpaka.addons.features.profile.KeptPets
import net.alpaka.addons.features.profile.PetIcons
import net.alpaka.addons.mixin.PlayerTabOverlayAccessor
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.item.component.ResolvableProfile

/**
 * The pet the player has out on SkyBlock, for the Player Model HUD: which one it is, and the head
 * it wears.
 *
 * Which pet comes from text ([PetText]): the chat lines say it the moment it changes, the tab
 * list's Pet widget says it at any time, also right after joining. The head comes from the NEU
 * repository ([PetIcons]) by the pet's type. Text only marks a skin with a ✦, so which skin it is
 * comes from the player's own profile as the Profile Dashboard last kept it ([KeptPets]).
 *
 * The pet in the world is no help: its name tag, "[Lv100] Silverfish", names no owner and is
 * usually switched off, so nothing tells the player's pet from the one of somebody standing by.
 *
 * Everything here reads what the client was sent. Nothing is asked of the server.
 */
object CurrentPet {

    /** Ticks between two looks at the tab list: once a second. */
    private const val POLL_TICKS = 20

    /**
     * How long the tab list is not believed after a chat line. The widget follows a pet change a
     * moment late, and until then still names the pet before.
     */
    private const val CHAT_WINS_MS = 5_000L

    private const val TAB_HEADER = "Pet:"

    private var pet: PetText.Pet? = null
    private var chatAtMs = 0L
    private var ticks = 0

    /** Whether the last look found the player on SkyBlock; elsewhere there is no pet to show. */
    private var onSkyblock = false

    private var head: ResolvableProfile? = null

    /** One profile per head texture: the skin cache holds a skin per profile it is asked about. */
    private val heads = HashMap<String, ResolvableProfile>()

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay -> if (!overlay) onChat(message) }
        // A line another mod hid still says which pet is out.
        ClientReceiveMessageEvents.GAME_CANCELED.register { message, overlay -> if (!overlay) onChat(message) }
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
    }

    /** The head of the pet that is out, or null with no pet, off SkyBlock, or before it is known. */
    @JvmStatic
    fun head(): ResolvableProfile? = if (onSkyblock && wanted()) head else null

    /** A Golden Dragon's head, for the HUD editor while no pet is out. */
    @JvmStatic
    fun sampleHead(): ResolvableProfile = profileOf(SAMPLE_TEXTURE)

    private const val SAMPLE_TEXTURE =
        "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmU5ZjliMWZj" +
            "MDE0MTY2Y2I0NmEwOTNlNTM0OWIyYmY2ZWRkMjAxYjY4MGQ2MmU0OGRiZjNhZjliMDQ1OTExNiJ9fX0="

    private fun wanted(): Boolean {
        val cfg = AlpakaConfig.instance
        return cfg.playerModelEnabled && cfg.playerModelShowPet
    }

    private fun onChat(message: Component) {
        if (!wanted()) return
        // All three lines hold this; the rest of the chat is not looked at any closer.
        if (!message.string.contains("ed your ")) return
        val update = PetText.parseChat(PetText.Colored.of(message)) ?: return
        chatAtMs = System.currentTimeMillis()
        setPet(update.pet)
        resolveHead()
    }

    private fun tick(mc: Minecraft) {
        if (++ticks < POLL_TICKS) return
        ticks = 0
        if (!wanted()) return
        if (mc.player == null) return
        onSkyblock = SkyblockUtils.isOnSkyblock()
        if (!onSkyblock) return

        if (System.currentTimeMillis() - chatAtMs >= CHAT_WINS_MS) {
            readTab(mc)?.let { setPet(it.pet) }
        }
        resolveHead()
    }

    /** The row under the Pet widget's header, or null while the tab list has no such widget. */
    private fun readTab(mc: Minecraft): PetText.Update? {
        // In the order they are drawn: the pet is the row after the header, and other widgets
        // (Pet Training) list pets in the same form.
        val rows = try {
            (mc.gui.hud.tabList as PlayerTabOverlayAccessor).`alpaka$getPlayerInfos`()
        } catch (e: RuntimeException) {
            return null
        }
        for (i in 0 until rows.size - 1) {
            val name = rows[i].tabListDisplayName ?: continue
            if (name.string.trim() != TAB_HEADER) continue
            val row = rows[i + 1].tabListDisplayName ?: return null
            return PetText.parseTab(PetText.Colored.of(row))
        }
        return null
    }

    private fun setPet(next: PetText.Pet?) {
        val now = pet
        if (next == now) return
        // The same pet from a line without a color is not news; the rarity it had stays.
        if (next != null && now != null && next.rarity < 0 &&
            next.name == now.name && next.skinned == now.skinned
        ) return
        pet = next
        head = null
    }

    /**
     * Looked at again every second: the repository and the kept profile answer in the background,
     * and a skin's head replaces the plain one once both have.
     */
    private fun resolveHead() {
        val current = pet
        head = if (current == null) null else skinHead(current) ?: plainHead(current)
    }

    /** The head of the skin the pet wears, or null without one or while it is not known. */
    private fun skinHead(current: PetText.Pet): ResolvableProfile? {
        if (!current.skinned) return null
        val skin = KeptPets.skin(current.type, current.rarity) ?: return null
        return PetIcons.skinTexture(skin)?.let { profileOf(it) }
    }

    /** The pet's head without a skin; null until the repository has answered. */
    private fun plainHead(current: PetText.Pet): ResolvableProfile? =
        PetIcons.headTexture(current.type, current.rarity)?.let { profileOf(it) }

    private fun profileOf(texture: String): ResolvableProfile = heads.getOrPut(texture) { ItemIcons.profile(texture) }
}
