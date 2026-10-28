package net.alpaka.addons.features.slayer

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.utils.AlpakaPerf
import net.alpaka.addons.utils.EntityHighlight
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB

/**
 * Highlights the player's own slayer boss, as a glowing outline, a box around its hitbox or a color
 * laid over its model.
 *
 * Only mobs with this player's owner line over them count ("<player>'s SLAYER BOSS", see
 * [SlayerBossEntityTracker.ownerLine]), plus the boss [SlayerBossEntityTracker] already follows, so
 * another player's boss next to ours is never highlighted. On the Blaze slayer the highlight takes the color of the boss's current
 * Hellion Shield attunement, which is the dagger attunement it has to be hit with. When the
 * Demonlord splits into its two demons it turns immune; then it loses the highlight and only the
 * demon that can be hit right now carries one, in that demon's attunement color.
 *
 * Attunements are read the way Skyblocker's `AttunementColors` reads them: Hypixel puts the word
 * (`ASHEN ♨8`, `IMMUNE`, ...) on a name tag stacked over the mob. Nothing is searched for outside
 * a boss fight of ours, and then only the name tags around the player, twice a second.
 */
object SlayerBossHighlight {

    /**
     * How far from the player an owner line is looked for, in blocks. The boss and its demons stay
     * close to the player who fights them.
     */
    private const val OWNER_SEARCH_RADIUS = 16.0

    /**
     * How often the owner lines are looked for, in ticks. Between searches the mobs found are
     * followed by id, which costs nothing; a demon that appears is picked up within this.
     */
    private const val RESCAN_TICKS = 10

    /** How far below its owner line a mob may stand, in blocks. */
    private const val MOB_BELOW = 4.0

    /** How far above a mob its name tag stack reaches, in blocks. */
    private const val TAG_STACK_HEIGHT = 3.0

    /** How far beside a mob a name tag may float and still be its own. Hypixel stacks them dead on. */
    private const val TAG_SLACK = 0.3

    /** The four Hellion Shield attunements and the colors Hypixel writes them in. */
    private enum class Attunement(val rgb: Int) {
        ASHEN(0x555555),
        SPIRIT(0xFFFFFF),
        CRYSTAL(0x55FFFF),
        AURIC(0xFFFF55),
    }

    /** Marks a name tag that says the mob cannot be hit right now. */
    private val IMMUNE = Any()

    /** Ids of the mobs found under this player's owner lines by the last search. */
    private val ownedMobIds = ArrayList<Int>()

    /** Ticks until the owner lines are searched again. */
    private var rescanIn = 0

    /** Entity id to ARGB highlight color, for this tick. */
    private val targets = HashMap<Int, Int>()

    /** [AlpakaConfig.slayerBossHighlightStyle], taken once per tick with [targets]. */
    private var style = EntityHighlight.STYLE_OUTLINE

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

    private fun tick(mc: Minecraft) {
        targets.clear()
        val cfg = AlpakaConfig.instance
        if (!cfg.slayerBossHighlightEnabled || !SlayerQuestDetector.inBossFight) {
            ownedMobIds.clear()
            rescanIn = 0
            return
        }
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (!SkyblockUtils.isOnSkyblock()) return
        style = EntityHighlight.style(cfg.slayerBossHighlightStyle)
        val color = cfg.slayerBossHighlightColor

        if (--rescanIn <= 0) {
            rescanIn = RESCAN_TICKS
            rescanOwned(level, player)
        }

        val mobs = LinkedHashSet<LivingEntity>()
        SlayerBossEntityTracker.bossMob(level)?.let { mobs.add(it) }
        for (id in ownedMobIds) {
            val mob = level.getEntity(id) as? LivingEntity ?: continue
            if (!mob.isRemoved && mob.isAlive) mobs.add(mob)
        }
        if (mobs.isEmpty()) return

        if (SlayerQuestDetector.currentOrRecent() != SlayerType.BLAZE) {
            for (mob in mobs) targets[mob.id] = color
            return
        }
        for (mob in mobs) {
            // An immune Demonlord is mid-split, and of the two demons only one can be hit at a
            // time; whatever says IMMUNE is not worth looking at right now.
            val tag = readAttunement(level, mob)
            if (tag == IMMUNE) continue
            targets[mob.id] = attunementColor(tag, color, cfg)
        }
    }

    /**
     * Finds every mob with this player's owner line over it: the boss, and on the Blaze slayer its
     * demons, which carry "<player>'s SLAYER BOSS" as well since Hypixel's update of 2026-10-28.
     */
    private fun rescanOwned(level: ClientLevel, player: Player) {
        ownedMobIds.clear()
        val playerName = player.gameProfile?.name ?: return
        val stands = level.getEntitiesOfClass(ArmorStand::class.java, player.boundingBox.inflate(OWNER_SEARCH_RADIUS)) {
            it.hasCustomName()
        }
        for (stand in stands) {
            val raw = stand.customName?.string ?: continue
            // Only the slayer boss line: a Primal Fear says "Spawned by: <player>" too.
            if (SlayerBossEntityTracker.ownerLine(raw, playerName, legacy = false) != true) continue
            mobUnder(level, stand, player)?.let { ownedMobIds.add(it.id) }
        }
    }

    /**
     * The mob an owner line floats over. Hypixel numbers a mob's name tag stack straight after the
     * mob, so the few ids below the line are tried first; the nearest mob underneath otherwise.
     *
     * Invisible mobs are never it. Quazii's beams come from an invisible guardian Hypixel keeps right
     * next to the demon, with an id close to its stack, and that guardian used to take the highlight.
     */
    private fun mobUnder(level: ClientLevel, stand: ArmorStand, player: Player): LivingEntity? {
        val below = AABB(stand.x - TAG_SLACK * 2, stand.y - MOB_BELOW, stand.z - TAG_SLACK * 2,
            stand.x + TAG_SLACK * 2, stand.y, stand.z + TAG_SLACK * 2)
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

    private fun isCandidate(mob: LivingEntity, player: Player): Boolean =
        mob !is ArmorStand && mob !== player && !mob.isInvisible

    /**
     * What the name tags over [mob] say about its shield: an [Attunement], [IMMUNE], or null when
     * none of them mentions one.
     */
    private fun readAttunement(level: ClientLevel, mob: LivingEntity): Any? {
        val box = mob.boundingBox.inflate(TAG_SLACK, 0.0, TAG_SLACK).expandTowards(0.0, TAG_STACK_HEIGHT, 0.0)
        for (stand in level.getEntitiesOfClass(ArmorStand::class.java, box) { it.hasCustomName() }) {
            val raw = stand.customName?.string ?: continue
            if (SkyblockUtils.containsIgnoringFormatting(raw, "IMMUNE")) return IMMUNE
            for (attunement in Attunement.entries) {
                if (SkyblockUtils.containsIgnoringFormatting(raw, attunement.name)) return attunement
            }
        }
        return null
    }

    /** The attunement's color with the configured color's alpha, or the configured color itself. */
    private fun attunementColor(tag: Any?, fallback: Int, cfg: AlpakaConfig): Int {
        if (!cfg.slayerBossHighlightAttunement || tag !is Attunement) return fallback
        return (fallback and 0xFF000000.toInt()) or tag.rgb
    }

    /** The highlight color of [entity] this tick, or null when it has none. */
    private fun colorOf(entity: Entity): Int? = if (targets.isEmpty()) null else targets[entity.id]

    /**
     * Called from EntityRendererMixin once vanilla has extracted an entity's render state: gives a
     * highlighted entity the glowing outline vanilla draws for the Glowing effect, in our color.
     */
    @JvmStatic
    fun applyOutline(entity: Entity, state: EntityRenderState) {
        if (style != EntityHighlight.STYLE_OUTLINE) return
        val color = colorOf(entity) ?: return
        EntityHighlight.outline(state, color)
    }

    /**
     * Called from LivingEntityRendererMixin once vanilla has extracted a mob's render state: notes
     * the overlay on the state, or clears it. Render states are reused from one entity to the next,
     * so this runs for every mob, and before anything else that sets an overlay.
     */
    @JvmStatic
    fun applyOverlay(entity: Entity, state: BossHighlightRenderState) {
        val color = if (style == EntityHighlight.STYLE_OVERLAY) colorOf(entity) else null
        if (color == null) {
            state.`alpaka$setHighlight`(0, 0.0f)
            return
        }
        EntityHighlight.overlay(state, color)
    }
}
