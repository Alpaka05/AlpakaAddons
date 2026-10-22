package net.alpaka.addons.features.cosmetics

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.mixin.RenderPipelinesAccessor
import net.alpaka.addons.mixin.RenderTypeInvoker
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import org.joml.Vector3f
import kotlin.math.exp

/**
 * An animated aura of light around the local player, in third person.
 *
 * Client-side cosmetic only, like the sensei hat: the geometry is built by this client every frame
 * and nothing is sent, so nobody else sees it. The effects themselves are in [AuraEffects]; this
 * object decides whether and where to draw, keeps the animation clock and picks the colors.
 *
 * ### How it is drawn
 *
 * The aura is not made of Minecraft particles. Every light is a quad submitted with the level's
 * other geometry (Fabric's COLLECT_SUBMITS, as the fire pit highlight does) through a render type of
 * the mod's own, whose fragment shader shapes the glow - see `aura.fsh`. That keeps the light soft at
 * any distance, allows ribbons and trails that particles cannot do, and costs a few hundred quads.
 *
 * The depth test is on and depth writes are off: the player's body hides the part of the aura behind
 * it, which is what makes it read as being *around* the player, while the lights never hide each
 * other. The test is vanilla's own, greater-or-equal: 26.2 keeps its depth buffer reversed, near
 * being 1, so the usual less-or-equal would only let through what is *behind* something.
 *
 * It follows the player's interpolated position rather than the model, so it does not tilt with the
 * body or turn with the head - an aura belongs to the space around the player, not to a limb.
 */
object AuraFeature {

    const val EFFECT_SPIRIT_ORBS = 0
    const val EFFECT_TWIN_HELIX = 1
    const val EFFECT_ARCANE_SIGIL = 2
    const val EFFECT_INFERNO = 3
    const val EFFECT_GALAXY = 4
    const val EFFECT_FIREFLIES = 5
    const val EFFECT_STORM = 6

    /** Names for the effect dropdown, indexed by the EFFECT_* constants. */
    @JvmField
    val EFFECT_NAMES = arrayOf("Spirit Orbs", "Twin Helix", "Arcane Sigil", "Inferno", "Galaxy", "Fireflies", "Storm")

    const val COLORS_EFFECT = 0
    const val COLORS_RAINBOW = 1
    const val COLORS_CUSTOM = 2

    /** Names for the color dropdown, indexed by the COLORS_* constants. */
    @JvmField
    val COLOR_MODE_NAMES = arrayOf("Effect Colors", "Rainbow", "Custom")

    /**
     * Each effect's own colors, as a gradient from 0 to 1. What a point of an effect means by its
     * gradient position differs per effect: the height on the helix, the age of a flame, the distance
     * from a galaxy's center.
     */
    private val PALETTES = arrayOf(
        intArrayOf(0x6FE8FF, 0xA77BFF, 0xFF7AD9), // Spirit Orbs: cyan, violet, pink
        intArrayOf(0x47FFC2, 0x3FA9FF, 0xB06BFF), // Twin Helix: mint to blue to purple
        intArrayOf(0xFFE7A0, 0xFFC23D, 0xFF8A2B), // Arcane Sigil: gold
        intArrayOf(0xFFF6C8, 0xFFC13A, 0xFF6A1A, 0xC8162E), // Inferno: white-hot to deep red
        intArrayOf(0xFFF1D6, 0x9FC4FF, 0x8A5CFF, 0xFF6FD8), // Galaxy: warm core, blue and violet arms
        intArrayOf(0xE4FF70, 0xFFE070, 0x9CFF8A), // Fireflies: yellow-green
        intArrayOf(0xE8F8FF, 0x7FD8FF, 0x9B7BFF), // Storm: white, electric blue, violet
    )

    /** How quickly the drawn height follows a change of pose, per second. Smooths the sneak dip. */
    private const val HEIGHT_FOLLOW = 12.0

    /** Aura time, in seconds at speed 1. Advanced each frame by the frame's length times the speed. */
    private var time = 0.0
    private var lastFrameNanos = 0L
    private var shownHeight = -1f

    private var renderType: RenderType? = null

    @JvmStatic
    fun register() {
        LevelRenderEvents.COLLECT_SUBMITS.register { submit(it) }
    }

    /** Builds the pipeline up front, so it is precompiled with vanilla's rather than on first use. */
    @JvmStatic
    fun init() {
        renderType()
    }

    private fun renderType(): RenderType {
        renderType?.let { return it }
        val pipeline = RenderPipelinesAccessor.`alpaka$register`(
            RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("alpaka", "pipeline/aura"))
                .withVertexShader(Identifier.fromNamespaceAndPath("alpaka", "core/aura"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("alpaka", "core/aura"))
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withCull(false)
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withDepthStencilState(DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                .build()
        )
        val type = RenderTypeInvoker.`alpaka$create`(
            "alpaka_aura",
            RenderSetup.builder(pipeline).sortOnUpload().createRenderSetup()
        )
        renderType = type
        return type
    }

    private fun submit(context: LevelRenderContext) {
        val cfg = AlpakaConfig.instance
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0.0 else ((now - lastFrameNanos) / 1e9).coerceIn(0.0, 0.1)
        lastFrameNanos = now
        if (!cfg.auraEnabled) {
            shownHeight = -1f
            return
        }
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        // Only where the player's own model is drawn: not in first person, and not while invisible
        // or spectating, when there is no body for the aura to surround.
        if (mc.options.cameraType.isFirstPerson && mc.cameraEntity === player) return
        if (player.isSpectator || player.isInvisible) return

        time += dt * cfg.auraSpeed.coerceIn(0.05f, 5f)

        val scaleY = if (cfg.playerScaleEnabled) cfg.playerScaleY else 1f
        val targetHeight = player.bbHeight * scaleY
        shownHeight = if (shownHeight < 0f) targetHeight
        else (shownHeight + (targetHeight - shownHeight) * (1.0 - exp(-dt * HEIGHT_FOLLOW))).toFloat()

        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(true)
        val feet = player.getPosition(partialTick)
        val camera = context.levelState().cameraRenderState
        val ox = (feet.x - camera.pos.x).toFloat()
        val oy = (feet.y - camera.pos.y).toFloat()
        val oz = (feet.z - camera.pos.z).toFloat()
        val right = camera.orientation.transform(Vector3f(1f, 0f, 0f))
        val up = camera.orientation.transform(Vector3f(0f, 1f, 0f))

        val effect = cfg.auraEffect.coerceIn(0, EFFECT_NAMES.size - 1)
        val frame = AuraFrame(
            (time % 100_000.0).toFloat(), shownHeight, cfg.auraSize.coerceIn(0.3f, 3f),
            colorFunction(effect, cfg, (time % 100_000.0).toFloat()),
        )
        val strength = (cfg.auraBrightness / 100f).coerceIn(0.05f, 1f)

        context.submitNodeCollector().submitCustomGeometry(context.poseStack(), renderType()) { pose, buffer ->
            val canvas = AuraCanvas(pose, buffer, ox, oy, oz, right.x, right.y, right.z, up.x, up.y, up.z, strength)
            when (effect) {
                EFFECT_SPIRIT_ORBS -> AuraEffects.spiritOrbs(canvas, frame)
                EFFECT_TWIN_HELIX -> AuraEffects.twinHelix(canvas, frame)
                EFFECT_ARCANE_SIGIL -> AuraEffects.arcaneSigil(canvas, frame)
                EFFECT_INFERNO -> AuraEffects.inferno(canvas, frame)
                EFFECT_GALAXY -> AuraEffects.galaxy(canvas, frame)
                EFFECT_FIREFLIES -> AuraEffects.fireflies(canvas, frame)
                else -> AuraEffects.storm(canvas, frame)
            }
        }
    }

    /** The gradient an effect is colored by this frame, from the Colors setting. */
    private fun colorFunction(effect: Int, cfg: AlpakaConfig, time: Float): (Float) -> Int {
        val palette = PALETTES[effect]
        val primary = cfg.auraPrimaryColor and 0xFFFFFF
        val secondary = cfg.auraSecondaryColor and 0xFFFFFF
        return when (cfg.auraColorMode) {
            // The hue runs along the effect and drifts with time, so every part cycles the rainbow.
            COLORS_RAINBOW -> { k -> Mth.hsvToRgb(fract(k * 0.75f + time * 0.08f), 0.75f, 1f) }
            COLORS_CUSTOM -> { k -> AuraEffects.mix(primary, secondary, k.coerceIn(0f, 1f)) }
            else -> { k -> colorAt(palette, k) }
        }
    }

    /** The color at [k] (0..1) of a gradient through the [palette]'s colors, evenly spaced. */
    @JvmStatic
    fun colorAt(palette: IntArray, k: Float): Int {
        if (palette.size == 1) return palette[0]
        val pos = k.coerceIn(0f, 1f) * (palette.size - 1)
        val i = pos.toInt().coerceAtMost(palette.size - 2)
        return AuraEffects.mix(palette[i], palette[i + 1], pos - i)
    }

    private fun fract(x: Float): Float = x - kotlin.math.floor(x)
}
