package net.alpaka.addons.features.dungeonmobs

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.slayer.BossHighlightRenderState
import net.alpaka.addons.utils.AlpakaPerf
import net.alpaka.addons.utils.DungeonState
import net.alpaka.addons.utils.EntityHighlight
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB

/**
 * Highlights the starred mobs during a dungeon clear, the ones a room needs killed before it counts
 * as cleared, and the minibosses. Optionally hides the name tags of every other mob.
 *
 * Hypixel marks a starred mob with a ✯ on the name tag over it (`✯ Zombie Commander 1.2M❤`), which
 * is how Odin, NoFrills and Skyblocker all tell them. The tag is an invisible armor stand of its own;
 * the mob is the one just below it, found the way Boss Highlight finds a slayer boss under its
 * owner line. Minibosses are player NPCs, also known by name, as Skyblocker does.
 *
 * Only during the clear: in the boss room the starred mobs are gone and the boss has its own
 * features. Nothing runs outside a dungeon.
 */
object StarredMobsHighlight {

    /** How often the name tags are read, in ticks. A tag that changes in between keeps its mob. */
    private const val RESCAN_TICKS = 5

    /** How far below the bottom of its name tag stand a mob may stand, in blocks. */
    private const val MOB_BELOW = 3.0

    /** How far above the bottom of its stand the mob may reach; the stand stands inside its head. */
    private const val MOB_ABOVE = 1.0

    /** How far beside its name tag a mob may stand. Hypixel keeps the tag right over it. */
    private const val TAG_SLACK = 0.6

    /** Ticks until the name tags are read again. */
    private var rescanIn = 0

    /** Entity id to ARGB highlight color, for this tick. */
    private val targets = HashMap<Int, Int>()

    /** Ids of the mobs found under starred name tags by the last scan. */
    private val starredIds = HashSet<Int>()

    /** [AlpakaConfig.starredMobsHighlightStyle], taken once per tick with [targets]. */
    private var style = EntityHighlight.STYLE_OUTLINE

    /** Whether name tags of mobs without a star are hidden this tick. */
    private var hideNames = false

    /**
     * Name tag stand id to whether it is hidden, together with the name it was decided for. Hypixel
     * sets a new name whenever the health changes, so a stale entry is told by the name object.
     */
    private val hiddenTags = HashMap<Int, Pair<Component, Boolean>>()

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
        LevelRenderEvents.COLLECT_SUBMITS.register { context ->
            if (style != EntityHighlight.STYLE_HITBOX || targets.isEmpty()) return@register
            val t = AlpakaPerf.begin(AlpakaPerf.Section.LEVEL_OVERLAYS)
            try {
                EntityHighlight.submitHitboxes(context, targets)
            } finally {
                AlpakaPerf.end(AlpakaPerf.Section.LEVEL_OVERLAYS, t)
            }
        }
    }

    @JvmStatic
    fun reset() {
        targets.clear()
        starredIds.clear()
        hiddenTags.clear()
        hideNames = false
        rescanIn = 0
    }

    private fun inClear(): Boolean =
        DungeonState.inDungeon() && !DungeonState.inBossRoom && SkyblockUtils.isOnSkyblock()

    private fun tick(mc: Minecraft) {
        targets.clear()
        val cfg = AlpakaConfig.instance
        val level = mc.level
        val player = mc.player
        if (!cfg.starredMobsHighlightEnabled || level == null || player == null || !inClear()) {
            if (starredIds.isNotEmpty() || hiddenTags.isNotEmpty() || hideNames) reset()
            return
        }
        hideNames = cfg.starredMobsHideNames
        style = EntityHighlight.style(cfg.starredMobsHighlightStyle)

        if (--rescanIn <= 0) {
            rescanIn = RESCAN_TICKS
            rescan(level, player)
            if (hiddenTags.size > 512) hiddenTags.clear()
        }

        val color = cfg.starredMobsHighlightColor
        for (id in starredIds) {
            val mob = level.getEntity(id) as? LivingEntity ?: continue
            if (!mob.isRemoved && mob.isAlive) targets[id] = color
        }
    }

    private fun rescan(level: ClientLevel, player: Player) {
        starredIds.clear()
        for (entity in level.entitiesForRendering()) {
            if (entity is Player && entity !== player && isNpc(entity) &&
                StarredMobs.isMiniboss(entity.gameProfile.name)) {
                if (entity.isAlive) starredIds.add(entity.id)
                continue
            }
            if (entity !is ArmorStand || !entity.isAlive || !entity.hasCustomName()) continue
            val name = entity.customName?.string ?: continue
            if (!StarredMobs.isStarredTag(name)) continue
            mobUnder(level, entity, player)?.let { starredIds.add(it.id) }
        }
    }

    /**
     * The mob a name tag floats over. Hypixel spawns a mob's name tag straight after the mob, so the
     * few ids below the stand are tried first; the nearest mob underneath otherwise.
     */
    private fun mobUnder(level: ClientLevel, stand: ArmorStand, player: Player): LivingEntity? {
        val below = AABB(stand.x - TAG_SLACK, stand.y - MOB_BELOW, stand.z - TAG_SLACK,
            stand.x + TAG_SLACK, stand.y + MOB_ABOVE, stand.z + TAG_SLACK)
        for (offset in 1..3) {
            val mob = level.getEntity(stand.id - offset) as? LivingEntity ?: continue
            if (!isCandidate(mob, player)) continue
            if (mob.boundingBox.intersects(below)) return mob
        }
        var best: LivingEntity? = null
        var bestDistance = Double.MAX_VALUE
        for (mob in level.getEntitiesOfClass(LivingEntity::class.java, below) { isCandidate(it, player) }) {
            val distance = mob.distanceToSqr(stand)
            if (distance < bestDistance) {
                bestDistance = distance
                best = mob
            }
        }
        return best
    }

    /**
     * A mob a name tag can belong to. Never a real player (a teammate standing under a tag), and
     * never an invisible helper entity; dungeon NPC players, like a Dreadlord or a Lost Adventurer,
     * have a version 2 id where real players have version 4.
     */
    private fun isCandidate(mob: LivingEntity, player: Player): Boolean = when (mob) {
        is ArmorStand, is WitherBoss -> false
        is Player -> mob !== player && isNpc(mob) && !mob.isInvisible
        else -> !mob.isInvisible
    }

    private fun isNpc(player: Player): Boolean = player.uuid.version() == 2

    /** The highlight color of [entity] this tick, or null when it has none. */
    private fun colorOf(entity: Entity): Int? = if (targets.isEmpty()) null else targets[entity.id]

    /** Called from EntityRendererMixin after Boss Highlight: the outline style. */
    @JvmStatic
    fun applyOutline(entity: Entity, state: EntityRenderState) {
        if (style != EntityHighlight.STYLE_OUTLINE) return
        EntityHighlight.outline(state, colorOf(entity) ?: return)
    }

    /**
     * Called from LivingEntityRendererMixin after Boss Highlight, which clears the overlay of every
     * mob it does not highlight; this only ever sets one.
     */
    @JvmStatic
    fun applyOverlay(entity: Entity, state: BossHighlightRenderState) {
        if (style != EntityHighlight.STYLE_OVERLAY) return
        EntityHighlight.overlay(state, colorOf(entity) ?: return)
    }

    /** Whether [entity] is the name tag of a mob without a star, to be left undrawn. */
    @JvmStatic
    fun shouldHideEntity(entity: Entity): Boolean {
        if (!hideNames || entity !is ArmorStand || !entity.isInvisible) return false
        val name = entity.customName ?: return false
        val cached = hiddenTags[entity.id]
        if (cached != null && cached.first === name) return cached.second
        val hide = StarredMobs.isPlainMobTag(name.string)
        hiddenTags[entity.id] = name to hide
        return hide
    }
}
