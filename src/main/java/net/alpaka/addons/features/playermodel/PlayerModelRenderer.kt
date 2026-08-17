package net.alpaka.addons.features.playermodel

import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Pose
import net.minecraft.world.item.ItemStack
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.atan

/**
 * Draws a miniature 3D avatar of the local player on the HUD, fading and sliding in from the
 * nearest screen edge.
 *
 * Everything the feature suppresses - armor, the burning overlay, movement-driven pose - is
 * applied to the freshly extracted [EntityRenderState], never to the live player. The state is
 * a throwaway object created per submission, so mutating it cannot touch the real entity's
 * simulation state or be observed by anything else.
 */
object PlayerModelRenderer {

    /** Default HUD placement, shared with the editor screen's Reset button. */
    const val DEFAULT_X = 40
    const val DEFAULT_Y = 85
    const val DEFAULT_SCALE = 30

    /** Fade/slide speed in alpha per second; 5.0 gives a ~200ms transition. */
    private const val FADE_SPEED = 5.0f
    private const val ALPHA_EPSILON = 0.001f

    /** Avatar bounding box, as multiples of the configured scale. */
    private const val BOX_HALF_WIDTH = 0.8f
    private const val BOX_TOP = 2.4f
    private const val BOX_BOTTOM = 0.2f

    /** Vanilla inventory-preview constants, from InventoryScreen.extractEntityInInventoryFollowsMouse. */
    private const val Y_OFFSET = 0.0625f
    private const val LOOK_DIVISOR = 40.0f
    private const val LOOK_STRENGTH = 20.0f
    private val RAD_PER_DEG = Math.PI.toFloat() / 180.0f
    private val BODY_FLIP_ANGLE = Math.PI.toFloat()

    /** Offsets of the imaginary cursor that makes the avatar face slightly forward-right. */
    private const val LOOK_OFFSET_X = 20.0f
    private const val LOOK_OFFSET_Y_SCALE = 1.1f
    private const val LOOK_OFFSET_Y = 15.0f

    /** Squared horizontal speed above which the player counts as moving. */
    private const val MOVE_EPSILON = 0.002

    private var alpha = 0.0f
    private var lastTimeMs = System.currentTimeMillis()

    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val mc = Minecraft.getInstance()
        if (mc.options.hideGui) return
        val player = mc.player ?: return

        // The two early returns above deliberately leave the timestamp stale: a long stretch
        // behind F1 then yields a large dt, so the avatar snaps back instead of sliding in.
        val now = System.currentTimeMillis()
        val dt = (now - lastTimeMs) / 1000.0f
        lastTimeMs = now

        val cfg = AlpakaConfig.instance
        alpha = if (shouldShowModel(mc, player, cfg)) {
            (alpha + dt * FADE_SPEED).coerceAtMost(1.0f)
        } else {
            (alpha - dt * FADE_SPEED).coerceAtLeast(0.0f)
        }
        if (alpha <= ALPHA_EPSILON) return

        val targetX = cfg.playerModelX
        val scale = cfg.playerModelScale

        val x = if (alpha >= 1.0f) {
            // Fully faded in, so the eased slide has converged on the target - no need to ask
            // the window for the off-screen start position on the steady-state path.
            targetX
        } else {
            // Slide in from whichever horizontal edge the HUD sits closest to.
            val screenWidth = mc.window.guiScaledWidth
            val startX = if (targetX < screenWidth / 2) -scale else screenWidth + scale
            val eased = 1.0f - (1.0f - alpha) * (1.0f - alpha) // ease-out quadratic
            (startX + (targetX - startX) * eased).toInt()
        }

        renderPlayerModel(graphics, x, cfg.playerModelY, scale, player)
    }

    private fun shouldShowModel(mc: Minecraft, player: LocalPlayer, cfg: AlpakaConfig): Boolean {
        if (!cfg.playerModelEnabled) return false

        // Hidden behind menus unless explicitly allowed; the chat screen never counts as a menu.
        val screen = mc.screen
        if (screen != null && screen !is ChatScreen && !cfg.playerModelShowInGuis) return false

        if (!cfg.playerModelOnlyActions) return true

        // Cheapest predicates first - || short-circuits, so the common cases cost one read
        // instead of evaluating all eight.
        if (!player.onGround() ||
            player.isCrouching ||
            player.isSprinting ||
            player.isPassenger ||
            player.swingTime > 0 ||
            player.isSwimming ||
            player.isFallFlying
        ) {
            return true
        }

        val motion = player.deltaMovement
        return motion.x * motion.x + motion.z * motion.z > MOVE_EPSILON
    }

    @JvmStatic
    fun renderPlayerModel(graphics: GuiGraphicsExtractor, x: Int, y: Int, scale: Int, player: LocalPlayer) {
        val halfWidth = (scale * BOX_HALF_WIDTH).toInt()
        val top = y - (scale * BOX_TOP).toInt()
        val bottom = y + (scale * BOX_BOTTOM).toInt()

        // Imaginary cursor position fed through vanilla's look-at math.
        val lookX = x - LOOK_OFFSET_X
        val lookY = y - scale * LOOK_OFFSET_Y_SCALE - LOOK_OFFSET_Y

        try {
            renderAvatar(graphics, x - halfWidth, top, x + halfWidth, bottom, scale, lookX, lookY, player)
        } catch (e: Exception) {
            // Safety catch: an invalid render state must never take the game down with it.
        }
    }

    /** Hit-tests the avatar's HUD bounding box. Used by the editor screen. */
    @JvmStatic
    fun isOverModel(mouseX: Double, mouseY: Double, x: Int, y: Int, scale: Int): Boolean {
        val halfWidth = (scale * BOX_HALF_WIDTH).toInt()
        return mouseX >= x - halfWidth && mouseX <= x + halfWidth &&
            mouseY >= y - (scale * BOX_TOP).toInt() && mouseY <= y + (scale * BOX_BOTTOM).toInt()
    }

    /** Draws a 1px guide around the avatar's HUD bounding box. Used by the editor screen. */
    @JvmStatic
    fun outlineModel(graphics: GuiGraphicsExtractor, x: Int, y: Int, scale: Int, color: Int) {
        val halfWidth = (scale * BOX_HALF_WIDTH).toInt()
        val top = y - (scale * BOX_TOP).toInt()
        val bottom = y + (scale * BOX_BOTTOM).toInt()
        ModernGuiUtils.drawOutline(graphics, x - halfWidth, top, halfWidth * 2, bottom - top, color)
    }

    /**
     * Reimplementation of vanilla's InventoryScreen.extractEntityInInventoryFollowsMouse that
     * additionally lets us suppress fields of the extracted render state.
     */
    private fun renderAvatar(
        graphics: GuiGraphicsExtractor,
        x0: Int,
        y0: Int,
        x1: Int,
        y1: Int,
        size: Int,
        lookX: Float,
        lookY: Float,
        entity: LivingEntity
    ) {
        val yaw = atan(((x0 + x1) / 2.0f - lookX) / LOOK_DIVISOR)
        val pitch = atan(((y0 + y1) / 2.0f - lookY) / LOOK_DIVISOR)

        // Allocated fresh on every submission on purpose: GuiGraphicsExtractor.entity stores
        // these by reference in the deferred GuiRenderState queue, so pooling them into fields
        // would let one submission mutate another's transform (the HUD and the editor screen
        // both submit within a single frame). Do not "optimize" these away.
        val pitchRot = Quaternionf().rotateX(pitch * LOOK_STRENGTH * RAD_PER_DEG)
        val bodyFlip = Quaternionf().rotateZ(BODY_FLIP_ANGLE).mul(pitchRot)

        val state = Minecraft.getInstance().entityRenderDispatcher
            .getRenderer(entity)
            .createRenderState(entity, 1.0f)

        state.shadowPieces.clear()
        state.outlineColor = EntityRenderState.NO_OUTLINE
        // The HUD avatar never burns, even while the real player is on fire.
        state.displayFireAnimation = false

        if (state is LivingEntityRenderState) {
            val cfg = AlpakaConfig.instance
            if (cfg.playerModelDisableMovement) freezeMovementPose(state, entity)
            if (cfg.playerModelHideArmor) hideArmor(state)

            state.bodyRot = 180.0f + yaw * LOOK_STRENGTH
            state.yRot = yaw * LOOK_STRENGTH
            state.xRot = if (state.pose != Pose.FALL_FLYING) -pitch * LOOK_STRENGTH else 0.0f
            state.boundingBoxWidth /= state.scale
            state.boundingBoxHeight /= state.scale
            state.scale = 1.0f
        }

        graphics.entity(
            state,
            size.toFloat(),
            Vector3f(0.0f, state.boundingBoxHeight / 2.0f + Y_OFFSET, 0.0f),
            bodyFlip,
            pitchRot,
            x0,
            y0,
            x1,
            y1
        )
    }

    /**
     * Clears every render-state field the armor layers read, which is equivalent to emptying
     * the player's armor slots before extraction but leaves the live entity untouched.
     *
     * HumanoidArmorLayer, WingsLayer and CapeLayer read only the four *Equipment fields; a
     * skull or block worn on the head is rendered from headItem/wornHead* on the living state
     * instead. EquipmentSlot.BODY is read by no humanoid layer, so it needs no handling.
     */
    private fun hideArmor(state: LivingEntityRenderState) {
        state.headItem.clear()
        state.wornHeadType = null
        state.wornHeadProfile = null

        if (state is HumanoidRenderState) {
            state.headEquipment = ItemStack.EMPTY
            state.chestEquipment = ItemStack.EMPTY
            state.legsEquipment = ItemStack.EMPTY
            state.feetEquipment = ItemStack.EMPTY
        }
    }

    /**
     * Resets every pose/orientation field driven by the player's current movement so the HUD
     * avatar always shows a plain standing pose - not just while swimming, but also while
     * sneaking, riding a minecart/boat/horse, gliding, upside-down, etc.
     */
    private fun freezeMovementPose(state: LivingEntityRenderState, entity: LivingEntity) {
        state.pose = Pose.STANDING
        state.walkAnimationPos = 0.0f
        state.walkAnimationSpeed = 0.0f
        state.isInWater = false
        state.isUpsideDown = false
        state.isAutoSpinAttack = false

        // The avatar's vertical placement is derived from boundingBoxHeight (see renderAvatar),
        // and the live hitbox shrinks with the pose - 1.5 crouching, 0.6 swimming, versus 1.8
        // standing - which would slide the avatar up and down. Pin it to the standing hitbox.
        // getDimensions() already applies the entity's scale, matching how the renderer fills
        // these fields, so the caller's later divide-by-scale still yields the base size.
        val standing = entity.getDimensions(Pose.STANDING)
        state.boundingBoxWidth = standing.width()
        state.boundingBoxHeight = standing.height()
        state.eyeHeight = standing.eyeHeight()

        if (state is HumanoidRenderState) {
            state.swimAmount = 0.0f
            state.isCrouching = false
            state.isFallFlying = false
            state.isVisuallySwimming = false
            state.isPassenger = false
            state.elytraRotX = 0.0f
            state.elytraRotY = 0.0f
            state.elytraRotZ = 0.0f
        }

        if (state is AvatarRenderState) {
            state.fallFlyingTimeInTicks = 0.0f
            state.shouldApplyFlyingYRot = false
            state.flyingYRot = 0.0f
        }
    }
}
