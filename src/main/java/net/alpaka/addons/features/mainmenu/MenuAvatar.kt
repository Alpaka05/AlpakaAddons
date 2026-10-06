package net.alpaka.addons.features.mainmenu

import net.alpaka.addons.client.gui.GuiSkinStyle
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.model.Model
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.entity.player.PlayerModelType
import net.minecraft.world.entity.player.PlayerSkin
import java.util.function.Function
import java.util.function.Supplier
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import net.minecraft.util.Unit as McUnit

/**
 * The player's own skin as a 3D model on the main menu, standing in the scene like a lobby's
 * character: it looks at the mouse pointer - right at it, wherever it is - breathes, turns when
 * dragged and waves when clicked.
 *
 * It is seen from eye level, as the camera of the menu's background picture sees everything in it.
 *
 * Drawn with vanilla's skin preview (GuiGraphicsExtractor.skin, the one the skin customisation
 * screen uses), which needs no world - the menu has none. That preview poses the model fresh for
 * every frame it draws, wiping any angle set on a part beforehand, so the model is a subclass that
 * puts this frame's angles back in [PosedPlayerModel.setupAnim], right where the reset happens.
 *
 * On opening it lands on its spot: it drops in from a little above and settles with a small give.
 *
 * Every turn follows the mouse through a critically damped spring rather than a plain easing: it
 * picks up speed and slows into place the way a body does, and never stops dead mid-move. It is
 * drawn between pixels too (see GuiSkinStyle) - at a large GUI scale a whole-pixel step is a jump
 * several screen pixels wide.
 */
internal class MenuAvatar(private val mc: Minecraft) {

    private val wide = PosedPlayerModel(mc.entityModels.bakeLayer(ModelLayers.PLAYER))
    private val slim = PosedPlayerModel(mc.entityModels.bakeLayer(ModelLayers.PLAYER_SLIM))
    private val skin: Supplier<PlayerSkin> = mc.skinManager.createLookup(mc.gameProfile, false)

    /** The model's box on screen, for the mouse; set on every draw. */
    private var boxX0 = 0
    private var boxY0 = 0
    private var boxX1 = 0
    private var boxY1 = 0

    private var lastFrameMs = 0L
    private val bodyYaw = Spring(BASE_YAW)
    private val bodyPitch = Spring(PITCH_DEGREES)
    private val headYaw = Spring(0f)
    private val headPitch = Spring(0f)

    /** Extra turn from dragging; springs back to zero once the mouse lets go. */
    private val dragYaw = Spring(0f)
    private var dragging = false
    private var dragDistance = 0.0
    private var waveStartMs = 0L

    /**
     * Draws the model standing with its feet at ([centerX], [feetY]), [modelHeight] tall, looking
     * towards ([mouseX], [mouseY]) - all in GUI pixels, fractions included - and tinted with [tint]
     * to sit in the scene's light. [arrive] 0 → 1 is the landing on opening; nothing shows before
     * it starts.
     */
    fun draw(
        graphics: GuiGraphicsExtractor, centerX: Float, feetY: Float, modelHeight: Int,
        mouseX: Float, mouseY: Float, screenWidth: Int, screenHeight: Int, arrive: Float, tint: Int,
    ) {
        if (arrive <= 0f) return
        val now = System.currentTimeMillis()
        val dt = if (lastFrameMs == 0L) 0f else ((now - lastFrameMs) / 1000f).coerceAtMost(0.1f)
        lastFrameMs = now
        val t = MenuAmbience.seconds()

        // Dropping in from above and settling, with a little overshoot into the landing.
        val feet = feetY - (1f - landing(arrive)) * LAND_DROP_PIXELS

        // The box is taller and wider than the model, so a raised arm or a turned shoulder is not
        // cut off: the preview renders into a texture the size of the box. It starts on a whole
        // pixel; the fraction left over is carried by the blit (GuiSkinStyle).
        val boxHeight = (modelHeight * BOX_HEIGHT_FACTOR).toInt()
        val boxWidth = (modelHeight * BOX_WIDTH_FACTOR).toInt()
        // The preview stands the model a little below its box's centre; lifted by that much, its
        // soles land on [feet].
        val modelCenterY = feet - modelHeight / 2f - modelHeight * FEET_CORRECTION
        val left = centerX - boxWidth / 2f
        val top = modelCenterY - boxHeight / 2f
        boxX0 = floor(left).toInt()
        boxY0 = floor(top).toInt()
        boxX1 = boxX0 + boxWidth
        boxY1 = boxY0 + boxHeight

        // Looking at the pointer: the direction from the eyes to it, as if the screen were a pane a
        // little way in front of the face - 0 straight at the viewer, positive to the screen's
        // right. The body turns part of the way from its resting angle and the head the rest, so
        // the face ends up pointing at the pointer, as far as a neck allows.
        val eyeY = feet - modelHeight * EYE_HEIGHT
        val reach = modelHeight * LOOK_DEPTH
        val lookYaw = Math.toDegrees(atan2((mouseX - centerX).toDouble(), reach.toDouble())).toFloat()
        val lookPitch = atan2(mouseY - eyeY, reach)
        val bodyTarget = BASE_YAW + ((lookYaw - BASE_YAW) * BODY_SHARE).coerceIn(-BODY_TURN_LIMIT, BODY_TURN_LIMIT)

        // No idle sway of the whole body: turning a pixel-art model by fractions of a degree
        // re-rasterises its every edge each frame, and at rest it read as a shiver. The breathing
        // arms keep it alive.
        bodyYaw.update(bodyTarget, dt, BODY_STIFFNESS)
        bodyPitch.update(PITCH_DEGREES - lookPitch * BODY_LEAN_DEGREES, dt, BODY_STIFFNESS)
        // The head's own yaw turns the other way round from the preview's: positive is towards the
        // screen's left, where the preview's positive turns the body to the right.
        val headTurn = -Math.toRadians((lookYaw - bodyTarget).toDouble()).toFloat()
        headYaw.update(headTurn.coerceIn(-HEAD_YAW_LIMIT, HEAD_YAW_LIMIT), dt, HEAD_STIFFNESS)
        headPitch.update(lookPitch.coerceIn(-HEAD_PITCH_LIMIT, HEAD_PITCH_LIMIT), dt, HEAD_STIFFNESS)
        if (!dragging) dragYaw.update(0f, dt, SPRING_BACK_STIFFNESS)

        val skin = this.skin.get()
        val model = if (skin.model() == PlayerModelType.SLIM) slim else wide
        pose(model, t, now)

        GuiSkinStyle.begin(tint, left - boxX0, top - boxY0)
        try {
            graphics.skin(
                model, skin.body().texturePath(), modelHeight / MODEL_UNITS, bodyPitch.value,
                bodyYaw.value + dragYaw.value, PIVOT_Y, boxX0, boxY0, boxX1, boxY1,
            )
        } finally {
            GuiSkinStyle.end()
        }
    }

    /** Eased out with a little overshoot: down past the spot and back up onto it. */
    private fun landing(t: Float): Float {
        val c1 = 1.4f
        val c3 = c1 + 1f
        val u = t.coerceIn(0f, 1f) - 1f
        return 1f + c3 * u * u * u + c1 * u * u
    }

    /** Breathing, the head on the mouse, and the wave when one is running. */
    private fun pose(model: PosedPlayerModel, t: Float, now: Long) {
        val breath = sin(t * BREATH_SPEED)
        model.headYaw = headYaw.value
        model.headPitch = headPitch.value
        model.rightArmZ = ARM_REST + breath * ARM_BREATH
        model.leftArmZ = -(ARM_REST + breath * ARM_BREATH)
        model.rightArmX = breath * ARM_SWAY
        model.leftArmX = -breath * ARM_SWAY

        val waveAge = now - waveStartMs
        if (waveStartMs != 0L && waveAge < WAVE_MS) {
            // Up quickly, a few waves, and down again: a smooth envelope over the whole gesture.
            val p = waveAge / WAVE_MS.toFloat()
            val envelope = sin(p * Math.PI.toFloat()).let { it * it }.coerceIn(0f, 1f) * 1.15f
            val raised = WAVE_RAISE + sin(t * WAVE_SPEED) * WAVE_SWING
            model.rightArmZ += (raised - model.rightArmZ) * envelope.coerceAtMost(1f)
            model.rightArmX *= 1f - envelope.coerceAtMost(1f)
        }
    }

    fun mouseClicked(x: Double, y: Double): Boolean {
        if (x < boxX0 || x >= boxX1 || y < boxY0 || y >= boxY1) return false
        dragging = true
        dragDistance = 0.0
        return true
    }

    fun mouseDragged(dx: Double): Boolean {
        if (!dragging) return false
        dragDistance += abs(dx)
        dragYaw.value += (dx * DRAG_DEGREES_PER_PIXEL).toFloat()
        dragYaw.velocity = 0f
        return true
    }

    /** Ends a drag; a press that hardly moved is a click, and the model waves. */
    fun mouseReleased(): Boolean {
        if (!dragging) return false
        dragging = false
        if (dragDistance < CLICK_SLOP) waveStartMs = System.currentTimeMillis()
        return true
    }

    /**
     * A critically damped spring: it moves [value] towards a target as fast as it can without
     * overshooting, accelerating out of rest and settling softly. [stiffness] sets how quickly.
     */
    private class Spring(var value: Float) {
        var velocity = 0f

        fun update(target: Float, dt: Float, stiffness: Float) {
            if (dt <= 0f) return
            val omega = sqrt(stiffness)
            // Small steps keep the integration steady at a low frame rate.
            var remaining = dt
            while (remaining > 0f) {
                val step = minOf(remaining, MAX_STEP)
                val acceleration = stiffness * (target - value) - 2f * omega * velocity
                velocity += acceleration * step
                value += velocity * step
                remaining -= step
            }
        }

        companion object {
            const val MAX_STEP = 1f / 120f
        }
    }

    /** The player model with this frame's angles put back on after the renderer's reset. */
    internal class PosedPlayerModel(root: ModelPart) :
        Model.Simple(root, Function { texture -> RenderTypes.entityTranslucent(texture) }) {

        private val head = root.getChild("head")
        private val rightArm = root.getChild("right_arm")
        private val leftArm = root.getChild("left_arm")

        var headYaw = 0f
        var headPitch = 0f
        var rightArmX = 0f
        var rightArmZ = 0f
        var leftArmX = 0f
        var leftArmZ = 0f

        override fun setupAnim(state: McUnit) {
            super.setupAnim(state)
            head.yRot = headYaw
            head.xRot = headPitch
            rightArm.xRot = rightArmX
            rightArm.zRot = rightArmZ
            leftArm.xRot = leftArmX
            leftArm.zRot = leftArmZ
        }
    }

    private companion object {
        /** The preview's model is this many units tall, and turns around this height. */
        const val MODEL_UNITS = 2.125f
        const val PIVOT_Y = -1.0625f
        /**
         * Seen from eye level, looking straight ahead, as the background picture was taken. Positive
         * would tip the model's top away from the viewer, as seen from below.
         */
        const val PITCH_DEGREES = 0f

        const val BOX_HEIGHT_FACTOR = 1.3f
        const val BOX_WIDTH_FACTOR = 0.95f

        /**
         * At rest the body faces a little towards the menu card on the left; the preview's yaw is 0
         * facing the viewer and positive towards the screen's right. Looking at the pointer,
         * it turns by a share of the look ([BODY_SHARE], up to a limit) and the head turns the rest,
         * within a neck's reach; it leans a few degrees per radian of looking up or down. The eyes
         * are this high on the model, and the pointer is taken to be this far in front of them,
         * in model heights. The head's spring is stiffer, so it leads and the body follows, as
         * people turn.
         */
        const val BASE_YAW = -18f
        const val BODY_SHARE = 0.4f
        const val BODY_TURN_LIMIT = 40f
        const val BODY_LEAN_DEGREES = 5f
        const val HEAD_YAW_LIMIT = 1.2f
        const val HEAD_PITCH_LIMIT = 0.85f
        const val EYE_HEIGHT = 0.86f
        const val LOOK_DEPTH = 0.9f
        const val BODY_STIFFNESS = 26f
        const val HEAD_STIFFNESS = 48f
        const val SPRING_BACK_STIFFNESS = 18f
        const val DRAG_DEGREES_PER_PIXEL = 2.5
        const val CLICK_SLOP = 3.0

        /** How far above its spot the model starts its landing. */
        const val LAND_DROP_PIXELS = 18f

        const val BREATH_SPEED = 1.7f
        const val ARM_REST = 0.07f
        const val ARM_BREATH = 0.035f
        const val ARM_SWAY = 0.05f

        const val WAVE_MS = 1600L
        const val WAVE_RAISE = 2.55f
        const val WAVE_SWING = 0.28f
        const val WAVE_SPEED = 13f

        /** Measured on screen: how far below the box's centre the preview puts the model, per height. */
        const val FEET_CORRECTION = 0.115f

    }
}
