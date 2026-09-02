package net.alpaka.addons.features.nametag

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.util.ARGB
import net.minecraft.util.FormattedCharSequence
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Draws the local player's own name tag, with animated colour and motion effects, in place of the
 * vanilla one.
 *
 * ### What vanilla does, and what this changes
 *
 * Vanilla never shows a player their own name tag: `LivingEntityRenderer.shouldShowName` rejects the
 * camera entity outright, so in third person the model above the camera is nameless. This feature
 * answers that check with "yes" for the local player (see [shouldForceShowName]), and then takes over
 * the drawing itself in `AvatarRenderer.submitNameDisplay` (see [submit]) so the text can be coloured
 * and moved letter by letter - something the single-string vanilla path cannot do.
 *
 * Only the *own* tag is touched. Other players' tags go through vanilla untouched, and nothing here
 * is sent anywhere: the effect exists purely in this client's frame buffer, which is what keeps it
 * on the right side of Hypixel's rules - it is a cosmetic, like a cape mod.
 *
 * ### Why the world-extraction flag exists
 *
 * The very same render state object is filled for the world *and* for every GUI preview of the
 * player - the inventory screen's avatar and this mod's HUD model both call `createRenderState` on
 * the same renderer. Forcing the name on everywhere would put a name tag on the inventory preview.
 * `EntityRenderDispatcher.extractEntity` is the one entry point the world uses and the previews do
 * not, so the override is armed only while that method is running.
 */
object CustomNameTagFeature {

    const val COLOR_VANILLA = 0
    const val COLOR_RAINBOW = 1
    const val COLOR_GRADIENT = 2
    const val COLOR_PULSE = 3

    const val MOTION_NONE = 0
    const val MOTION_WAVE = 1
    const val MOTION_BOUNCE = 2
    const val MOTION_SHAKE = 3

    /** Names for the config slider, indexed by the COLOR_* constants. */
    @JvmField
    val COLOR_MODE_NAMES = arrayOf("Vanilla", "Rainbow", "Gradient", "Pulse")

    /** Names for the config slider, indexed by the MOTION_* constants. */
    @JvmField
    val MOTION_MODE_NAMES = arrayOf("None", "Wave", "Bounce", "Shake")

    /** Vanilla's `EntityRenderer.NAMETAG_SCALE`: world units per text pixel. */
    private const val NAMETAG_SCALE = 0.025f

    /** Vanilla lifts the tag half a block above the attachment point. */
    private const val ATTACHMENT_LIFT = 0.5

    /** The faint colour vanilla uses for the see-through pass: white at alpha 0x20. */
    private const val SEE_THROUGH_COLOR = 0x20FFFFFF

    /** Height of a text row in text pixels, which is also the backdrop height vanilla draws. */
    private const val ROW_HEIGHT = 9f

    /** Vanilla pads the backdrop one pixel around the text. */
    private const val BACKDROP_PAD = 1f

    /** Thickness of the chroma frame around the backdrop, in text pixels. */
    private const val FRAME_THICKNESS = 0.6f

    /** Depth vanilla's own text background sits at, so ours layers the same way against the text. */
    private const val BACKDROP_Z = 0.01f

    /**
     * True while `EntityRenderDispatcher.extractEntity` is running, i.e. while the state being
     * filled belongs to an entity the world is about to draw rather than to a GUI preview.
     */
    @JvmStatic
    var extractingWorldEntity: Boolean = false

    @JvmStatic
    fun isEnabled(): Boolean = AlpakaConfig.instance.customNameTagEnabled

    /**
     * Whether the vanilla "should this entity show its name" check must be overridden to true.
     *
     * Only for the local player, only while the world is extracting them, and only when vanilla
     * would otherwise draw names at all: names are hidden with F1, the player is not drawn in first
     * person, and an invisible or spectating player has no body for a tag to float over.
     */
    @JvmStatic
    fun shouldForceShowName(entity: Entity): Boolean {
        if (!isEnabled() || !extractingWorldEntity) return false
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        if (entity !== player) return false
        if (!Minecraft.renderNames()) return false
        if (mc.options.cameraType.isFirstPerson) return false
        return !player.isSpectator && !player.isInvisible
    }

    /**
     * Whether a render state describes the local player.
     *
     * Compared by entity id, which `AvatarRenderer.extractRenderState` copies into the state.
     * The state object cannot be compared by identity: one instance is reused for every player the
     * renderer draws.
     */
    @JvmStatic
    fun isLocalPlayerState(state: AvatarRenderState): Boolean {
        val player = Minecraft.getInstance().player ?: return false
        return state.id == player.id
    }

    /** One glyph of the tag, with its width measured once. */
    private class Glyph(val codepoint: Int, val style: Style, val width: Int) {
        val sequence: FormattedCharSequence = FormattedCharSequence.codepoint(codepoint, style)
        var ownName: Boolean = false
    }

    /**
     * Draws the tag. Replaces vanilla's `submitNameDisplay` for the local player.
     *
     * The transform is vanilla's exactly - attachment point, half a block up, billboarded to the
     * camera, scaled to [NAMETAG_SCALE] with the y axis flipped so text reads downwards - so with
     * every effect switched off the tag sits precisely where vanilla would have put it.
     */
    @JvmStatic
    fun submit(state: AvatarRenderState, poseStack: PoseStack, collector: SubmitNodeCollector, camera: CameraRenderState) {
        val attachment = state.nameTagAttachment ?: return
        val text = state.nameTag ?: return

        val mc = Minecraft.getInstance()
        val font = mc.font
        val cfg = AlpakaConfig.instance

        val glyphs = collectGlyphs(text, font)
        if (glyphs.isEmpty()) return
        markOwnName(glyphs, mc.player?.gameProfile?.name)

        val totalWidth = glyphs.sumOf { it.width }
        val time = animationTime(cfg.nameTagAnimationSpeed)

        poseStack.pushPose()
        poseStack.translate(attachment.x, attachment.y + ATTACHMENT_LIFT + cfg.nameTagHeightOffset, attachment.z)
        poseStack.mulPose(camera.orientation)
        val scale = NAMETAG_SCALE * cfg.nameTagScale
        poseStack.scale(scale, -scale, scale)

        if (cfg.nameTagMotionMode == MOTION_BOUNCE) {
            poseStack.translate(0f, bounceOffset(time), 0f)
        }

        val x0 = -totalWidth / 2f
        val light = state.lightCoords

        // Vanilla's pairing: a faint pass that is drawn through walls, then the full one that is
        // not. Sneaking ("discrete") keeps only the faint pass, and stops it seeing through walls.
        val fullyVisible = !state.isDiscrete

        submitBackdrop(poseStack, collector, cfg, x0, totalWidth.toFloat(), light, time, fullyVisible)

        val faintMode = if (fullyVisible) Font.DisplayMode.SEE_THROUGH else Font.DisplayMode.NORMAL
        val count = glyphs.size
        var x = x0
        for ((index, glyph) in glyphs.withIndex()) {
            val effectRgb = effectColor(cfg, glyph, index, count, time)
            val sequence = if (effectRgb == null) glyph.sequence
                else FormattedCharSequence.codepoint(glyph.codepoint, glyph.style.withColor(effectRgb))

            var dx = 0f
            var dy = 0f
            when (cfg.nameTagMotionMode) {
                MOTION_WAVE -> dy = waveOffset(time, index)
                MOTION_SHAKE -> {
                    dx = shakeOffset(time, index, 37.0, 13.0)
                    dy = shakeOffset(time, index, 29.0, 7.0)
                }
            }

            poseStack.pushPose()
            if (dx != 0f || dy != 0f) poseStack.translate(dx, dy, 0f)

            collector.submitText(poseStack, x, 0f, sequence, false, faintMode, light, SEE_THROUGH_COLOR, 0, 0)
            if (fullyVisible) {
                val outline = if (cfg.nameTagOutlineEnabled) outlineColor(effectRgb) else 0
                // The outline pass has no shadow of its own, so the two are exclusive.
                val shadow = cfg.nameTagShadowEnabled && outline == 0
                collector.submitText(poseStack, x, 0f, sequence, shadow, Font.DisplayMode.NORMAL, light, -1, 0, outline)
            }
            poseStack.popPose()

            x += glyph.width
        }

        poseStack.popPose()
    }

    /** Splits the tag into glyphs in visual order, keeping each one's style. */
    private fun collectGlyphs(text: Component, font: Font): List<Glyph> {
        val out = ArrayList<Glyph>()
        text.visualOrderText.accept { _, style, codepoint ->
            val sequence = FormattedCharSequence.codepoint(codepoint, style)
            out.add(Glyph(codepoint, style, font.width(sequence)))
            true
        }
        return out
    }

    /**
     * Marks the glyphs that spell the player's own name.
     *
     * On Hypixel the tag is `[MVP+] Name`, rank prefix and all. Unless the whole tag is opted in,
     * the effects are applied to the name only and the rank keeps the colour Hypixel gave it - a
     * rainbow over "[MVP+]" reads as a fake rank rather than a decoration. When the name cannot be
     * found in the tag at all, everything counts as the name rather than nothing.
     */
    private fun markOwnName(glyphs: List<Glyph>, playerName: String?) {
        var start = -1
        var end = -1
        if (!playerName.isNullOrEmpty()) {
            val plain = StringBuilder()
            for (glyph in glyphs) plain.appendCodePoint(glyph.codepoint)
            val at = plain.indexOf(playerName)
            if (at >= 0) {
                start = plain.codePointCount(0, at)
                end = start + playerName.codePointCount(0, playerName.length)
            }
        }
        for ((index, glyph) in glyphs.withIndex()) {
            glyph.ownName = start < 0 || (index >= start && index < end)
        }
    }

    /**
     * The effect colour for one glyph as RGB, or null to leave the glyph's own colour alone.
     *
     * Returned as a plain RGB value because it goes into the glyph's [Style]; the alpha each pass
     * needs comes from the colour argument of the text submission, as in vanilla.
     */
    private fun effectColor(cfg: AlpakaConfig, glyph: Glyph, index: Int, count: Int, time: Double): Int? {
        if (!glyph.ownName && !cfg.nameTagColorWholeTag) return null
        val position = if (count > 1) index.toFloat() / (count - 1) else 0f
        return when (cfg.nameTagColorMode) {
            COLOR_RAINBOW -> {
                // A whole spectrum across the name, drifting sideways over time.
                val hue = fract(time * 0.12 + position * 0.75)
                Mth.hsvToRgb(hue.toFloat(), 0.85f, 1.0f) and RGB_MASK
            }
            COLOR_GRADIENT -> {
                // The two colours swap ends slowly, so the gradient appears to flow along the tag.
                val mix = 0.5f + 0.5f * sin(time * 1.5 + position * Math.PI).toFloat()
                lerpRgb(cfg.nameTagGradientStart, cfg.nameTagGradientEnd, mix)
            }
            COLOR_PULSE -> {
                val base = glyph.style.color?.value ?: 0xFFFFFF
                val brightness = 0.55f + 0.45f * (0.5f + 0.5f * sin(time * 3.0).toFloat())
                scaleRgb(base, brightness)
            }
            else -> null
        }
    }

    /** A darker shade of the glyph colour for the outline, or plain black for a vanilla glyph. */
    private fun outlineColor(effectRgb: Int?): Int {
        if (effectRgb == null) return 0xFF000000.toInt()
        return ARGB.color(255, scaleRgb(effectRgb, 0.22f))
    }

    /**
     * The backdrop behind the text, and the chroma frame around it.
     *
     * Vanilla draws its backdrop through the font's background colour, one quad from a pixel before
     * the text to a pixel after it. Doing it here instead means the letters can be submitted one at a
     * time without each dragging its own little box along, and leaves room for the frame, whose
     * colours sweep along its length.
     */
    private fun submitBackdrop(
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        cfg: AlpakaConfig,
        x0: Float,
        width: Float,
        light: Int,
        time: Double,
        seeThrough: Boolean,
    ) {
        val backdropAlpha = if (!cfg.nameTagBackgroundEnabled) 0
            else (cfg.nameTagBackgroundOpacity / 100f * 255f).toInt().coerceIn(0, 255)
        val frame = cfg.nameTagChromaBorderEnabled
        if (backdropAlpha == 0 && !frame) return

        val left = x0 - BACKDROP_PAD
        val right = x0 + width + BACKDROP_PAD
        val top = -BACKDROP_PAD
        val bottom = ROW_HEIGHT

        val renderType = if (seeThrough) RenderTypes.textBackgroundSeeThrough() else RenderTypes.textBackground()
        collector.submitCustomGeometry(poseStack, renderType) { pose, consumer ->
            if (backdropAlpha > 0) {
                val color = ARGB.color(backdropAlpha, 0)
                consumer.addVertex(pose, left, top, BACKDROP_Z).setColor(color).setLight(light)
                consumer.addVertex(pose, left, bottom, BACKDROP_Z).setColor(color).setLight(light)
                consumer.addVertex(pose, right, bottom, BACKDROP_Z).setColor(color).setLight(light)
                consumer.addVertex(pose, right, top, BACKDROP_Z).setColor(color).setLight(light)
            }
            if (frame) {
                val t = FRAME_THICKNESS
                // Hue runs along x, so the four sides meet in matching colours at the corners.
                val span = right - left
                val leftColor = frameColor(time, 0f)
                val rightColor = frameColor(time, 1f)
                // Top and bottom bars.
                frameQuad(pose, consumer, left - t, top - t, right + t, top, leftColor, rightColor, light)
                frameQuad(pose, consumer, left - t, bottom, right + t, bottom + t, leftColor, rightColor, light)
                // Left and right bars, each a single colour.
                frameQuad(pose, consumer, left - t, top, left, bottom, leftColor, leftColor, light)
                frameQuad(pose, consumer, right, top, right + t, bottom, rightColor, rightColor, light)
                // span is only needed if the frame ever gains more segments; kept for that.
                @Suppress("UNUSED_VARIABLE") val unused = span
            }
        }
    }

    /** The frame's colour at a position along its width, 0 being the left end and 1 the right. */
    private fun frameColor(time: Double, position: Float): Int =
        ARGB.color(255, Mth.hsvToRgb(fract(time * 0.2 + position * 0.5).toFloat(), 0.9f, 1.0f))

    /** One frame bar, coloured [leftColor] at its left edge and [rightColor] at its right. */
    private fun frameQuad(
        pose: PoseStack.Pose,
        consumer: VertexConsumer,
        x0: Float, y0: Float, x1: Float, y1: Float,
        leftColor: Int, rightColor: Int, light: Int,
    ) {
        consumer.addVertex(pose, x0, y0, BACKDROP_Z).setColor(leftColor).setLight(light)
        consumer.addVertex(pose, x0, y1, BACKDROP_Z).setColor(leftColor).setLight(light)
        consumer.addVertex(pose, x1, y1, BACKDROP_Z).setColor(rightColor).setLight(light)
        consumer.addVertex(pose, x1, y0, BACKDROP_Z).setColor(rightColor).setLight(light)
    }

    /** Letters ride a sine wave, each a little behind the one before it. */
    private fun waveOffset(time: Double, index: Int): Float =
        (sin(time * 5.0 + index * 0.55) * 1.6).toFloat()

    /** The whole tag hops: a rectified sine, so it lifts and lands rather than sinking below its line. */
    private fun bounceOffset(time: Double): Float =
        (-abs(sin(time * 3.0)) * 2.5).toFloat()

    /** A small jitter that differs per letter; two incommensurate frequencies keep it from looping visibly. */
    private fun shakeOffset(time: Double, index: Int, frequency: Double, spread: Double): Float =
        (cos(time * frequency + index * spread) * 0.45).toFloat()

    /**
     * Seconds of animation elapsed, scaled by the speed setting.
     *
     * Wrapped hourly so the value stays small enough for float trigonometry; the wrap is a single
     * phase jump an hour apart, which nobody will be watching for.
     */
    private fun animationTime(speed: Float): Double =
        (System.currentTimeMillis() % 3_600_000L) / 1000.0 * speed

    private fun fract(value: Double): Double = value - Math.floor(value)

    /** Plain RGB, no alpha byte: these values go into text styles, which carry colour only. */
    private const val RGB_MASK = 0xFFFFFF

    private fun lerpRgb(from: Int, to: Int, mix: Float): Int {
        val r = Mth.lerp(mix, ARGB.red(from).toFloat(), ARGB.red(to).toFloat()).toInt()
        val g = Mth.lerp(mix, ARGB.green(from).toFloat(), ARGB.green(to).toFloat()).toInt()
        val b = Mth.lerp(mix, ARGB.blue(from).toFloat(), ARGB.blue(to).toFloat()).toInt()
        return ARGB.color(r, g, b) and RGB_MASK
    }

    private fun scaleRgb(rgb: Int, factor: Float): Int {
        val r = (ARGB.red(rgb) * factor).toInt().coerceIn(0, 255)
        val g = (ARGB.green(rgb) * factor).toInt().coerceIn(0, 255)
        val b = (ARGB.blue(rgb) * factor).toInt().coerceIn(0, 255)
        return ARGB.color(r, g, b) and RGB_MASK
    }
}
