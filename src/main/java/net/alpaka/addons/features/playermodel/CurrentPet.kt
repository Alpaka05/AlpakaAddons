package net.alpaka.addons.features.playermodel

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.market.ItemIcons
import net.alpaka.addons.features.profile.PetIcons
import net.alpaka.addons.mixin.PlayerTabOverlayAccessor
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile

/**
 * The pet the player has out on SkyBlock, for the Player Model HUD: which one it is, and the head
 * it wears.
 *
 * Which pet comes from text ([PetText]): the chat lines say it the moment it changes, the tab
 * list's Pet widget says it at any time, also right after joining. The head is the one on the pet
 * beside the player when it is there, which is the only place a pet skin can be seen; with pets
 * hidden it is the pet's plain head from the NEU repository ([PetIcons]).
 *
 * Everything here reads what the client was sent. Nothing is asked of the server.
 */
object CurrentPet {

    /** Ticks between two looks at the tab list and the world: once a second. */
    private const val POLL_TICKS = 20

    /**
     * How long the tab list is not believed after a chat line. The widget follows a pet change a
     * moment late, and until then still names the pet before.
     */
    private const val CHAT_WINS_MS = 5_000L

    /** How far from the player its pet is looked for, squared. It follows within a few blocks. */
    private const val PET_RANGE_SQ = 10.0 * 10.0

    /** How far the stand with the head may be from the one with the name, squared. */
    private const val HEAD_RANGE_SQ = 2.5 * 2.5

    private const val TAB_HEADER = "Pet:"

    private var pet: PetText.Pet? = null
    private var chatAtMs = 0L
    private var ticks = 0

    /** Whether the last look found the player on SkyBlock; elsewhere there is no pet to show. */
    private var onSkyblock = false

    private var head: ResolvableProfile? = null

    /** Heads seen on the pet in the world, kept so a pet that goes out of sight keeps its skin. */
    private val seenHeads = HashMap<PetText.Pet, ResolvableProfile>()

    /** One profile per head texture: the skin cache holds a skin per profile it is asked about. */
    private val plainHeads = HashMap<String, ResolvableProfile>()

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
    fun sampleHead(): ResolvableProfile = plainHeads.getOrPut(SAMPLE_TEXTURE) { ItemIcons.profile(SAMPLE_TEXTURE) }

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
        resolveHead(Minecraft.getInstance())
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
        resolveHead(mc)
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

    private fun resolveHead(mc: Minecraft) {
        val current = pet
        if (current == null) {
            head = null
            return
        }
        val player = mc.player
        val level = mc.level
        val inWorld = if (player != null && level != null) headInWorld(level, player, current) else null
        if (inWorld != null) {
            // Bounded only in principle: a player owns a few dozen pets at most.
            if (seenHeads.size > 256) seenHeads.clear()
            seenHeads[current] = inWorld
            head = inWorld
            return
        }
        head = seenHeads[current] ?: plainHead(current)
    }

    /**
     * The head on the player's own pet. Hypixel builds a pet from armor stands: one wears the head,
     * and one carries the name tag, "[Lv100] Player's Golden Dragon". The name is what tells the
     * player's pet from everyone else's, so the head is taken from the stand nearest to that name.
     */
    private fun headInWorld(level: ClientLevel, player: LocalPlayer, current: PetText.Pet): ResolvableProfile? {
        val owned = player.scoreboardName + "'s " + current.name
        var named: ArmorStand? = null
        val stands = ArrayList<ArmorStand>()
        for (entity in level.entitiesForRendering()) {
            if (entity !is ArmorStand || entity.distanceToSqr(player) > PET_RANGE_SQ) continue
            stands.add(entity)
            if (named == null && entity.customName?.let { SkyblockUtils.cleanColor(it.string).contains(owned) } == true) {
                named = entity
            }
        }
        val tag = named ?: return null
        var best: ResolvableProfile? = null
        var bestDistance = HEAD_RANGE_SQ
        for (stand in stands) {
            val stack = stand.getItemBySlot(EquipmentSlot.HEAD)
            if (!stack.`is`(Items.PLAYER_HEAD)) continue
            val profile = stack.get(DataComponents.PROFILE) ?: continue
            val distance = stand.distanceToSqr(tag)
            if (distance <= bestDistance) {
                bestDistance = distance
                best = profile
            }
        }
        return best
    }

    /** The pet's head without a skin, from the NEU repository; null until it has answered. */
    private fun plainHead(current: PetText.Pet): ResolvableProfile? {
        val texture = PetIcons.headTexture(current.type, current.rarity) ?: return null
        return plainHeads.getOrPut(texture) { ItemIcons.profile(texture) }
    }
}
