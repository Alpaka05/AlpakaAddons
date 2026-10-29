package net.alpaka.addons.features.garden

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos
import net.minecraft.gizmos.TextGizmo
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket
import net.minecraft.util.ARGB
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pest Finder: helps find the pests on the Garden.
 *
 * - **Vacuum guess.** A left click with a vacuum makes Hypixel draw a short particle trail towards
 *   the nearest pest. The trail is read as it arrives and extended to its end ([PestTrailGuess]),
 *   which is marked with a waypoint (box, beam and label) and, optionally, a line from the
 *   crosshair. It goes away when the player gets there, or after a set time.
 * - **Pest highlight.** Pests the client has loaded get a glowing outline, and while a vacuum is in
 *   hand a line from the crosshair to each of them.
 *
 * Everything here only reads what the server sends and draws on top of it; nothing is sent.
 * Only on the Garden.
 */
object PestFinder {

    /** How long after a click the trail's particles are read, in ms. */
    private const val TRAIL_WINDOW_MS = 5_000L

    /** How near the player a trail's first particle has to be, in blocks. */
    private const val TRAIL_START_RADIUS = 5.0

    /** Within this horizontal distance of the guess it counts as reached, in blocks. */
    private const val REACHED_RADIUS = 8.0

    /** A guess is not cleared for being reached in the first second, while the trail still grows. */
    private const val REACHED_GRACE_MS = 1_000L

    /** How often the pests around are looked for, in ticks. */
    private const val RESCAN_TICKS = 5

    /** Garden plots are 96 blocks wide, in a 5 by 5 grid centered on the origin. */
    private const val PLOT_SIZE = 96.0

    /** A guess this close to a plot's center is Hypixel pointing at the plot, not at a pest. */
    private const val PLOT_MIDDLE_RADIUS = 1.5

    private const val PLOT_MIDDLE_COLOR = 0xFFFFFF55.toInt()

    /** How high the guess's beam reaches, in blocks. */
    private const val BEAM_HEIGHT = 60.0

    /**
     * The pests, as Hypixel names them on their name tags (`ൠ Beetle 5❤`). The ൠ is enough on its
     * own; the names are the fallback in case the symbol changes.
     */
    private val PEST_NAME = Regex(
        "\\b(?:Beetle|Cricket|Earthworm|Field Mouse|Fly|Locust|Lunar Moth|Mite|Mosquito|Moth|Rat|Slug|" +
            "Praying Mantis|Firefly|Dragonfly)\\b"
    )
    private const val PEST_SYMBOL = "ൠ"

    private val trail = PestTrailGuess()

    /** When the vacuum was last left-clicked, or 0. */
    private var clickedAtMs = 0L

    /** The current guess, or null. */
    private var guess: Vec3? = null
    private var guessIsPlotMiddle = false

    /** Ticks until the pests are looked for again. */
    private var rescanIn = 0

    /** Ids of the pests' name tags, for the lines; refreshed with [outlined]. */
    private val pestTags = ArrayList<Int>()

    /** Ids of the entities that make up the pests the client has loaded. */
    private val outlined = HashSet<Int>()

    /** See EtherwarpOverlayFeature: the custom data last read, and whether it was a vacuum. */
    private var cachedData: CustomData? = null
    private var cachedIsVacuum = false

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { tick(it) }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> reset() }
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
    }

    @JvmStatic
    fun reset() {
        clearGuess()
        pestTags.clear()
        outlined.clear()
        rescanIn = 0
    }

    private fun clearGuess() {
        trail.clear()
        guess = null
        guessIsPlotMiddle = false
        clickedAtMs = 0L
    }

    @JvmStatic
    fun isEnabled(): Boolean = AlpakaConfig.instance.pestFinderEnabled

    private fun active(): Boolean = isEnabled() && SkyblockUtils.isOnGarden()

    // ---------------------------------------------------------------- vacuum guess

    /**
     * Called from MinecraftAttackMixin when the attack key starts an attack, which is when the
     * vacuum's tracker goes off. A new click starts a new trail.
     */
    @JvmStatic
    fun onAttack() {
        if (!active()) return
        val player = Minecraft.getInstance().player ?: return
        if (player.isShiftKeyDown || !isVacuum(player.mainHandItem.get(DataComponents.CUSTOM_DATA))) return
        clearGuess()
        clickedAtMs = System.currentTimeMillis()
    }

    /**
     * Called for every particle packet on the client thread. Reads the trail while a click is fresh,
     * and returns whether the packet should be dropped (Hide Trail Particles).
     */
    @JvmStatic
    fun onParticles(packet: ClientboundLevelParticlesPacket): Boolean {
        if (clickedAtMs == 0L || System.currentTimeMillis() - clickedAtMs > TRAIL_WINDOW_MS) return false
        if (!isEnabled()) return false
        val hide = AlpakaConfig.instance.pestFinderHideParticles
        val type = packet.particle.type
        val still = packet.maxSpeed == 0f && packet.xDist == 0f && packet.yDist == 0f && packet.zDist == 0f

        // The swirl of enchanting glyphs that goes with the trail.
        if (type == ParticleTypes.ENCHANT && packet.count == 10 && packet.maxSpeed == -2f) return hide
        if (type != ParticleTypes.ANGRY_VILLAGER || packet.count != 1 || !still) return false

        val player = Minecraft.getInstance().player ?: return hide
        if (trail.isEmpty() && player.position().distanceTo(Vec3(packet.x, packet.y, packet.z)) > TRAIL_START_RADIUS) {
            return hide
        }
        if (trail.tryAdd(packet.x, packet.y, packet.z)) {
            trail.solve()?.let { (x, y, z) ->
                guess = Vec3(x, y, z)
                guessIsPlotMiddle = isPlotMiddle(x, z)
            }
        }
        return hide
    }

    private fun isPlotMiddle(x: Double, z: Double): Boolean {
        val cx = (x / PLOT_SIZE).roundToInt()
        val cz = (z / PLOT_SIZE).roundToInt()
        if (abs(cx) > 2 || abs(cz) > 2) return false
        val dx = x - cx * PLOT_SIZE
        val dz = z - cz * PLOT_SIZE
        return dx * dx + dz * dz <= PLOT_MIDDLE_RADIUS * PLOT_MIDDLE_RADIUS
    }

    private fun isVacuum(data: CustomData?): Boolean {
        if (data == null) return false
        if (data !== cachedData) {
            cachedData = data
            val id = data.copyTag().getStringOr("id", "")
            cachedIsVacuum = id.endsWith("VACUUM") || id.startsWith("INFINI_VACUUM")
        }
        return cachedIsVacuum
    }

    private fun holdingVacuum(mc: Minecraft): Boolean =
        isVacuum(mc.player?.mainHandItem?.get(DataComponents.CUSTOM_DATA))

    // ---------------------------------------------------------------- tick

    private fun tick(mc: Minecraft) {
        val level = mc.level
        val player = mc.player
        if (level == null || player == null || !active()) {
            if (clickedAtMs != 0L || outlined.isNotEmpty() || pestTags.isNotEmpty()) reset()
            return
        }
        val cfg = AlpakaConfig.instance

        if (clickedAtMs != 0L) {
            val age = System.currentTimeMillis() - clickedAtMs
            val g = guess
            if (age > cfg.pestFinderGuessSeconds * 1000L) {
                clearGuess()
            } else if (g != null && age > REACHED_GRACE_MS) {
                val dx = g.x - player.x
                val dz = g.z - player.z
                if (dx * dx + dz * dz <= REACHED_RADIUS * REACHED_RADIUS) clearGuess()
            }
        }

        if (!cfg.pestFinderHighlight && !cfg.pestFinderLineToPests) {
            if (outlined.isNotEmpty() || pestTags.isNotEmpty()) {
                outlined.clear()
                pestTags.clear()
            }
            return
        }
        if (--rescanIn <= 0) {
            rescanIn = RESCAN_TICKS
            rescan(level)
        }
    }

    private fun rescan(level: ClientLevel) {
        outlined.clear()
        pestTags.clear()
        for (entity in level.entitiesForRendering()) {
            if (entity !is ArmorStand || !entity.isAlive || !entity.hasCustomName()) continue
            val name = SkyblockUtils.cleanColor(entity.customName?.string ?: continue)
            if (!isPestTag(name)) continue
            pestTags.add(entity.id)
            addBody(level, entity)
        }
    }

    /** Whether a name tag is a pest's: the pest symbol, or a pest's name with a health number. */
    private fun isPestTag(name: String): Boolean =
        name.contains(PEST_SYMBOL) || (name.contains('❤') && PEST_NAME.containsMatchIn(name))

    /**
     * Notes the entities a pest is made of, under its name tag. A pest is a stand wearing the pest's
     * head over an invisible mob that carries its hitbox; the head is what the player sees, so it is
     * what gets the outline. The mob only when there is no head, so the pest is never left unmarked.
     */
    private fun addBody(level: ClientLevel, tag: ArmorStand) {
        val below = AABB(tag.x - 0.8, tag.y - 3.0, tag.z - 0.8, tag.x + 0.8, tag.y + 0.5, tag.z + 0.8)
        var foundHead = false
        val mobs = ArrayList<Int>(2)
        for (entity in level.getEntitiesOfClass(LivingEntity::class.java, below)) {
            if (entity === tag || entity is Player) continue
            if (entity is ArmorStand) {
                if (!entity.getItemBySlot(EquipmentSlot.HEAD).isEmpty) {
                    outlined.add(entity.id)
                    foundHead = true
                }
            } else {
                mobs.add(entity.id)
            }
        }
        if (!foundHead) outlined.addAll(mobs)
    }

    /** Called from EntityRendererMixin: the outline on the pests. */
    @JvmStatic
    fun applyOutline(entity: Entity, state: EntityRenderState) {
        if (outlined.isEmpty() || !outlined.contains(entity.id)) return
        val cfg = AlpakaConfig.instance
        if (!cfg.pestFinderEnabled || !cfg.pestFinderHighlight) return
        state.outlineColor = (cfg.pestFinderHighlightColor and 0xFFFFFF).takeIf { it != 0 } ?: 0x010101
    }

    // ---------------------------------------------------------------- drawing

    /** Whether there is anything to draw this frame; checked before [collectGizmos]. */
    @JvmStatic
    fun hasGizmos(): Boolean = isEnabled() && (guess != null || pestTags.isNotEmpty())

    /**
     * Adds the guess's waypoint and the lines to the frame's gizmos, like EtherwarpOverlayFeature.
     * Everything is drawn on top of the terrain: a pest behind a hill is the whole point.
     */
    @JvmStatic
    fun collectGizmos() {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val camera = mc.gameRenderer.mainCamera()
        if (!camera.isInitialized) return
        val cfg = AlpakaConfig.instance
        val eye = camera.position()
        val forward = camera.forwardVector()
        // Half a block ahead of the camera, so the line seems to leave from the crosshair.
        val crosshair = eye.add(forward.x() * 0.5, forward.y() * 0.5, forward.z() * 0.5)

        guess?.let { g ->
            val color = if (guessIsPlotMiddle) PLOT_MIDDLE_COLOR else cfg.pestFinderGuessColor
            val block = BlockPos.containing(g)
            val fill = ARGB.color((ARGB.alpha(color) * 0.3f).roundToInt(), color)
            Gizmos.cuboid(AABB(block), GizmoStyle.strokeAndFill(color, 2.0f, fill)).setAlwaysOnTop()
            val center = Vec3(block.x + 0.5, block.y + 0.5, block.z + 0.5)
            Gizmos.line(Vec3(center.x, block.y + 1.0, center.z), Vec3(center.x, block.y + 1.0 + BEAM_HEIGHT, center.z),
                ARGB.color(0x80, color), 6.0f).setAlwaysOnTop()

            val distance = sqrt(eye.distanceToSqr(center))
            val label = (if (guessIsPlotMiddle) "Pest Guess (Plot Middle)" else "Pest Guess") +
                String.format(Locale.ROOT, " %.0fm", distance)
            text(label, center.add(0.0, 1.6, 0.0), color, distance)

            if (cfg.pestFinderGuessLine) Gizmos.line(crosshair, center, color, 3.0f).setAlwaysOnTop()
        }

        if (cfg.pestFinderLineToPests && pestTags.isNotEmpty() && holdingVacuum(mc)) {
            for (id in pestTags) {
                val tag = level.getEntity(id) ?: continue
                // The tag floats over the pest; aim at the pest itself.
                val target = tag.position().add(0.0, -0.8, 0.0)
                Gizmos.line(crosshair, target, cfg.pestFinderHighlightColor or 0xFF000000.toInt(), 2.0f).setAlwaysOnTop()
            }
        }
    }

    /** A label that stays readable from afar: scaled up with the distance beyond a few blocks. */
    private fun text(label: String, at: Vec3, color: Int, distance: Double) {
        val scale = TextGizmo.Style.DEFAULT_SCALE * (distance / 8.0).coerceIn(1.0, 25.0).toFloat()
        Gizmos.billboardText(label, at, TextGizmo.Style.forColorAndCentered(color or 0xFF000000.toInt()).withScale(scale))
            .setAlwaysOnTop()
    }
}
