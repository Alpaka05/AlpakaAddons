package net.alpaka.addons.features.teammateglow

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.dungeonmap.DungeonParty
import net.alpaka.addons.features.dungeonmap.PartyMember
import net.alpaka.addons.utils.DungeonState
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

/**
 * Teammate Class Glow: in a dungeon, Hypixel makes the teammates glow in the color of their
 * scoreboard team, which is their rank. This colors that glow by their dungeon class instead, in the
 * colors the Dungeon Map and the Leap Menu use.
 *
 * It only changes the color of a glow the server already gave a player and never makes anyone glow:
 * vanilla fills in the outline in EntityRenderer.extractRenderState only for an entity that appears
 * glowing, and this runs after it and leaves a render state without an outline alone. Odin's
 * Teammate Class Glow works the same way, through the team color.
 *
 * The classes come from the party lines of the tab list ([DungeonParty]), read once a second during
 * a run and remembered for it, because a dead teammate's line shows `(DEAD)` instead of the class.
 */
object TeammateClassGlow {

    /** How often the tab list is read, in ticks. */
    private const val READ_TICKS = 20

    /** Full alpha, as vanilla gives the outline color it sets. */
    private const val OPAQUE = 0xFF000000.toInt()

    /** Lowercase player name to the color of their class, for this run. */
    private val colors = HashMap<String, Int>()

    /** Ticks until the tab list is read again. */
    private var readIn = 0

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
    }

    private fun tick(mc: Minecraft) {
        if (!AlpakaConfig.instance.teammateClassGlowEnabled || !SkyblockUtils.isOnSkyblock() || !DungeonState.inDungeon()) {
            if (colors.isNotEmpty()) colors.clear()
            readIn = 0
            return
        }
        if (--readIn > 0) return
        readIn = READ_TICKS
        remember(DungeonParty.read(mc))
    }

    /** Takes the class of every party member that shows one; a dead member keeps the one read before. */
    @JvmStatic
    fun remember(party: List<PartyMember>) {
        for (member in party) {
            val color = DungeonParty.classColor(member.dungeonClass) ?: continue
            colors[member.name.lowercase()] = color
        }
    }

    /** The class color remembered for this player name, or null. */
    @JvmStatic
    fun colorOf(name: String): Int? = colors[name.lowercase()]

    /** Forgets the run's classes. */
    @JvmStatic
    fun reset() {
        colors.clear()
        readIn = 0
    }

    /**
     * The outline for a player called [name] whose outline vanilla set to [outline]: their class
     * color, opaque as vanilla's, if they glow and their class is known; otherwise [outline] as it
     * was, so a player without an outline never gets one.
     */
    @JvmStatic
    fun recolor(outline: Int, name: String): Int {
        if (outline == EntityRenderState.NO_OUTLINE) return outline
        val color = colorOf(name) ?: return outline
        return color or OPAQUE
    }

    /** Called from EntityRendererMixin once vanilla has set the outline: a glowing teammate's new color. */
    @JvmStatic
    fun applyOutline(entity: Entity, state: EntityRenderState) {
        if (state.outlineColor == EntityRenderState.NO_OUTLINE || colors.isEmpty() || entity !is Player) return
        // Real players only: the dungeon's NPC players (Lost Adventurer, Shadow Assassin) have a
        // version 2 id, and one that happened to share a teammate's name must keep its own color.
        if (!AlpakaConfig.instance.teammateClassGlowEnabled || entity.uuid.version() == 2) return
        state.outlineColor = recolor(state.outlineColor, entity.gameProfile.name)
    }
}
