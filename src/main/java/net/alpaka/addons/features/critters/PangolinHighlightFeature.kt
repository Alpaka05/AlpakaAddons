package net.alpaka.addons.features.critters

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.utils.SkyblockUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.ARGB
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.animal.armadillo.Armadillo

/**
 * Gives Pangolins on Torrhus Canyon the vanilla glowing outline, so the critter is easier to pick
 * out of the terrain.
 *
 * Pangolins are the only critter that uses the vanilla armadillo mob, so recognising them needs
 * nothing but the entity type - no name-tag parsing, no NBT, no hidden server data.
 *
 * ### Why this stays within Hypixel's rules
 *
 * The highlight only ever marks a mob the player can already see with their own eyes:
 *
 *  * It is driven from the render-state extraction, which vanilla only runs for entities it is
 *    already drawing - so anything outside the view frustum or the entity render distance is never
 *    even considered.
 *  * Every candidate is then checked with [LocalPlayer.hasLineOfSight], vanilla's own
 *    block-occlusion raycast. A mob with terrain between it and the player's eyes is skipped, so
 *    this cannot be used to find anything through walls or underground.
 *  * Invisible mobs are skipped, and the search is capped at [MAX_DISTANCE] blocks.
 *
 * Nothing is scanned, tracked, or reported: no chunk or block sweeps, no entity list walking, no
 * state kept about mobs the player cannot see. The feature adds a colour to something already on
 * screen and nothing else.
 */
object PangolinHighlightFeature {

    /**
     * Matches the area line Hypixel puts on the Skyblock sidebar (e.g. "⏣ Torrhus Canyon").
     *
     * Spelling is kept loose because the region is written both "Torrhus" and "Torhus" in different
     * places, and the sub-area "Pangolin Hideaway" is reported instead of the parent region while
     * inside it.
     *
     * This area line is the only location check the feature makes. It deliberately does not also
     * require [SkyblockUtils.isOnSkyblock], which additionally insists on a "SKYBLOCK" sidebar title
     * and a hypixel.net address - the sidebar title changes during events (it reads "BLAZE
     * SIMULATOR" during one, for instance), which silently disabled the highlight. The area name is
     * specific enough on its own that a false positive is not a realistic concern.
     */
    private val TORRHUS_CANYON = Regex("torr?hus|pangolin hideaway", RegexOption.IGNORE_CASE)

    /** Sidebar parsing is far too heavy to redo per entity per frame, so the answer is cached. */
    private const val LOCATION_REFRESH_MS = 500L

    /** Likewise for the occlusion raycast, which is rerun a couple of times a second per mob. */
    private const val VISIBILITY_TTL_MS = 100L

    /** Hard cap on how far away a mob may be to qualify, in blocks. */
    private const val MAX_DISTANCE = 64.0
    private const val MAX_DISTANCE_SQ = MAX_DISTANCE * MAX_DISTANCE

    private var locationCheckedAtMs = 0L
    private var onTorrhusCanyon = false
    private var trackedLevel: ClientLevel? = null

    private class Visibility(var checkedAtMs: Long, var visible: Boolean)

    /** Keyed by entity id. Pruned whenever the location check refreshes; cleared on world change. */
    private val visibilityCache = HashMap<Int, Visibility>()

    /**
     * Called for every entity vanilla extracts a render state for. Sets the outline colour when the
     * entity is a Pangolin the player can currently see, and otherwise leaves the state untouched.
     */
    @JvmStatic
    fun applyOutline(entity: Entity, state: EntityRenderState) {
        val cfg = AlpakaConfig.instance
        if (!cfg.pangolinHighlightEnabled) return
        if (entity !is Armadillo) return
        // An invisible mob is not visible to the player either, so it must not be revealed.
        if (entity.isInvisible) return

        val mc = Minecraft.getInstance()
        val player = mc.player ?: return

        if (!isOnTorrhusCanyon(mc)) return
        if (entity.distanceToSqr(player) > MAX_DISTANCE_SQ) return
        if (!isVisibleToPlayer(player, entity)) return

        // The outline pass treats any non-zero colour as glowing, and expects it opaque - exactly
        // how vanilla applies a team colour for the glowing effect.
        state.outlineColor = ARGB.opaque(cfg.pangolinHighlightColor)
    }

    /**
     * Whether the player is on Torrhus Canyon, read from the Skyblock sidebar the server already
     * sends. Refreshed at most every [LOCATION_REFRESH_MS].
     */
    private fun isOnTorrhusCanyon(mc: Minecraft): Boolean {
        val level = mc.level
        if (level !== trackedLevel) {
            // Server or lobby switch: the cached area and every cached sightline are meaningless,
            // and entity ids are about to be reused by unrelated mobs.
            trackedLevel = level
            visibilityCache.clear()
            locationCheckedAtMs = 0L
            onTorrhusCanyon = false
        }
        if (level == null) return false

        val now = System.currentTimeMillis()
        if (now - locationCheckedAtMs >= LOCATION_REFRESH_MS) {
            locationCheckedAtMs = now
            onTorrhusCanyon = SkyblockUtils.getSidebarLines().any { TORRHUS_CANYON.containsMatchIn(it) }
            pruneVisibilityCache(now)
        }
        return onTorrhusCanyon
    }

    /**
     * Vanilla's own eyes-to-eyes occlusion raycast, cached briefly. This is the check that keeps
     * the feature honest: terrain in the way means no highlight.
     */
    private fun isVisibleToPlayer(player: LocalPlayer, entity: Entity): Boolean {
        val now = System.currentTimeMillis()
        val cached = visibilityCache[entity.id]

        if (cached != null && now - cached.checkedAtMs < VISIBILITY_TTL_MS) {
            return cached.visible
        }

        val visible = player.hasLineOfSight(entity)
        if (cached != null) {
            cached.checkedAtMs = now
            cached.visible = visible
        } else {
            visibilityCache[entity.id] = Visibility(now, visible)
        }
        return visible
    }

    /** Drops entries for mobs that are no longer being rendered, so the cache cannot grow forever. */
    private fun pruneVisibilityCache(now: Long) {
        if (visibilityCache.isEmpty()) return
        visibilityCache.entries.removeIf { now - it.value.checkedAtMs > LOCATION_REFRESH_MS }
    }
}
