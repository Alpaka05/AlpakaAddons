package net.alpaka.addons.features.slayer

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.utils.AlpakaPerf
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin
import net.minecraft.world.phys.AABB

/**
 * Highlights the player's own slayer boss, as a glowing outline, a box around its hitbox or a color
 * laid over its model.
 *
 * The boss is the one [SlayerBossEntityTracker] already follows, so another player's boss next to
 * ours is never highlighted. On the Blaze slayer the highlight takes the color of the boss's current
 * Hellion Shield attunement, which is the dagger attunement it has to be hit with. When the
 * Demonlord splits into its two demons it turns immune; then it loses the highlight and only the
 * demon that can be hit right now carries one, in that demon's attunement color.
 *
 * Attunements are read the way Skyblocker's `AttunementColors` reads them: Hypixel puts the word
 * (`ASHEN ♨8`, `IMMUNE`, ...) on a name tag stacked over the mob. Nothing is scanned beyond the
 * boss's own surroundings, and only while the sidebar says our Blaze boss is up.
 */
object SlayerBossHighlight {

    /** The choices of [AlpakaConfig.slayerBossHighlightStyle], in order. */
    @JvmField
    val STYLE_NAMES = arrayOf("Outline", "Hitbox", "Overlay")

    private const val STYLE_OUTLINE = 0
    private const val STYLE_HITBOX = 1
    private const val STYLE_OVERLAY = 2

    /**
     * How far from the Demonlord its demons may stand, in blocks. They appear next to it and the
     * fight stays in one arena, so this only has to keep another player's demons out.
     */
    private const val DEMON_RADIUS = 16.0

    /** How far above a mob its name tag stack reaches, in blocks. */
    private const val TAG_STACK_HEIGHT = 3.0

    /** How far beside a mob a name tag may float and still be its own. Hypixel stacks them dead on. */
    private const val TAG_SLACK = 0.3

    /** Thickness of the hitbox edges, in blocks. */
    private const val EDGE = 0.02f

    /** Alpha of the overlay at most, so the model's own texture still reads through it. */
    private const val OVERLAY_MAX = 0.75f

    /** The four Hellion Shield attunements and the colors Hypixel writes them in. */
    private enum class Attunement(val rgb: Int) {
        ASHEN(0x555555),
        SPIRIT(0xFFFFFF),
        CRYSTAL(0x55FFFF),
        AURIC(0xFFFF55),
    }

    /** Marks a name tag that says the mob cannot be hit right now. */
    private val IMMUNE = Any()

    /** Entity id to ARGB highlight color, for this tick. */
    private val targets = HashMap<Int, Int>()

    /** [AlpakaConfig.slayerBossHighlightStyle], taken once per tick with [targets]. */
    private var style = STYLE_OUTLINE

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
        LevelRenderEvents.COLLECT_SUBMITS.register { context ->
            if (style != STYLE_HITBOX || targets.isEmpty()) return@register
            val t = AlpakaPerf.begin(AlpakaPerf.Section.LEVEL_OVERLAYS)
            try {
                submitHitboxes(context)
            } finally {
                AlpakaPerf.end(AlpakaPerf.Section.LEVEL_OVERLAYS, t)
            }
        }
    }

    private fun tick(mc: Minecraft) {
        targets.clear()
        val cfg = AlpakaConfig.instance
        if (!cfg.slayerBossHighlightEnabled || !SlayerQuestDetector.inBossFight) return
        val level = mc.level ?: return
        if (!SkyblockUtils.isOnSkyblock()) return
        val boss = SlayerBossEntityTracker.bossMob(level) ?: return
        style = cfg.slayerBossHighlightStyle.coerceIn(STYLE_OUTLINE, STYLE_OVERLAY)
        val color = cfg.slayerBossHighlightColor

        if (SlayerQuestDetector.currentOrRecent() != SlayerType.BLAZE) {
            targets[boss.id] = color
            return
        }

        // An immune Demonlord is mid-split; the demons are what can be hit.
        val bossTag = readAttunement(level, boss)
        if (bossTag != IMMUNE) targets[boss.id] = attunementColor(bossTag, color, cfg)

        val demons = level.getEntitiesOfClass(LivingEntity::class.java, boss.boundingBox.inflate(DEMON_RADIUS)) {
            it is WitherSkeleton || it is ZombifiedPiglin
        }
        for (demon in demons) {
            // A wither skeleton or pigman without an attunement over it is not a demon at all.
            val tag = readAttunement(level, demon) ?: continue
            if (tag == IMMUNE) continue
            targets[demon.id] = attunementColor(tag, color, cfg)
        }
    }

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
        if (style != STYLE_OUTLINE) return
        val color = colorOf(entity) ?: return
        // The outline is drawn opaque, and 0 means "no outline", so pure black is nudged off it.
        state.outlineColor = (color and 0xFFFFFF).takeIf { it != 0 } ?: 0x010101
    }

    /**
     * Called from LivingEntityRendererMixin once vanilla has extracted a mob's render state: notes
     * the overlay tint on the state, where the model is drawn from later in the frame.
     */
    @JvmStatic
    fun applyOverlay(entity: Entity, state: BossHighlightRenderState) {
        state.`alpaka$setHighlightTint`(overlayTint(entity))
    }

    /**
     * The overlay color for a highlighted entity: the color mixed into white by its alpha, since
     * the model tint multiplies the texture. 0 when the entity is not highlighted with an overlay.
     */
    private fun overlayTint(entity: Entity): Int {
        if (style != STYLE_OVERLAY) return 0
        val color = colorOf(entity) ?: return 0
        val strength = (color ushr 24 and 0xFF) / 255.0f * OVERLAY_MAX
        fun mix(shift: Int): Int {
            val channel = color ushr shift and 0xFF
            return (255 + (channel - 255) * strength).toInt().coerceIn(0, 255) shl shift
        }
        return 0xFF000000.toInt() or mix(16) or mix(8) or mix(0)
    }

    private fun submitHitboxes(context: LevelRenderContext) {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val camera = context.levelState().cameraRenderState.pos

        // Worked out now, relative to the camera in double precision; the geometry is built later
        // in the frame, and far from the origin a float cannot hold an absolute position finely.
        val boxes = ArrayList<Pair<AABB, Int>>(targets.size)
        for ((id, color) in targets) {
            val entity = level.getEntity(id) ?: continue
            val pos = entity.getPosition(partialTick)
            val box = entity.boundingBox
                .move(pos.x - entity.x - camera.x, pos.y - entity.y - camera.y, pos.z - entity.z - camera.z)
            boxes.add(box to color)
        }
        if (boxes.isEmpty()) return

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads()) { pose, buffer ->
            for ((box, color) in boxes) drawBox(buffer, pose, box, color)
        }
    }

    /** The twelve edges of [box], each as a thin bar. */
    private fun drawBox(buffer: VertexConsumer, pose: PoseStack.Pose, box: AABB, color: Int) {
        val x0 = box.minX.toFloat()
        val y0 = box.minY.toFloat()
        val z0 = box.minZ.toFloat()
        val x1 = box.maxX.toFloat()
        val y1 = box.maxY.toFloat()
        val z1 = box.maxZ.toFloat()
        val e = EDGE
        for (y in floatArrayOf(y0, y1)) {
            for (z in floatArrayOf(z0, z1)) bar(buffer, pose, color, x0 - e, y - e, z - e, x1 + e, y + e, z + e)
            for (x in floatArrayOf(x0, x1)) bar(buffer, pose, color, x - e, y - e, z0 - e, x + e, y + e, z1 + e)
        }
        for (x in floatArrayOf(x0, x1)) {
            for (z in floatArrayOf(z0, z1)) bar(buffer, pose, color, x - e, y0 - e, z - e, x + e, y1 + e, z + e)
        }
    }

    /** A solid cuboid from (ax, ay, az) to (bx, by, bz): six faces. */
    private fun bar(buffer: VertexConsumer, pose: PoseStack.Pose, color: Int,
                    ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float) {
        face(buffer, pose, color, ax, ay, az, bx, ay, az, bx, ay, bz, ax, ay, bz)
        face(buffer, pose, color, ax, by, az, ax, by, bz, bx, by, bz, bx, by, az)
        face(buffer, pose, color, ax, ay, az, ax, by, az, bx, by, az, bx, ay, az)
        face(buffer, pose, color, ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz)
        face(buffer, pose, color, ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az)
        face(buffer, pose, color, bx, ay, az, bx, by, az, bx, by, bz, bx, ay, bz)
    }

    /** One quad in both windings, since debug quads may be culled. */
    private fun face(buffer: VertexConsumer, pose: PoseStack.Pose, color: Int,
                     x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float,
                     x3: Float, y3: Float, z3: Float, x4: Float, y4: Float, z4: Float) {
        buffer.addVertex(pose, x1, y1, z1).setColor(color)
        buffer.addVertex(pose, x2, y2, z2).setColor(color)
        buffer.addVertex(pose, x3, y3, z3).setColor(color)
        buffer.addVertex(pose, x4, y4, z4).setColor(color)
        buffer.addVertex(pose, x4, y4, z4).setColor(color)
        buffer.addVertex(pose, x3, y3, z3).setColor(color)
        buffer.addVertex(pose, x2, y2, z2).setColor(color)
        buffer.addVertex(pose, x1, y1, z1).setColor(color)
    }
}
