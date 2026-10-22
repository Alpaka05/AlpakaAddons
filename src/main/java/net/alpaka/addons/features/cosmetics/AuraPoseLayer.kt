package net.alpaka.addons.features.cosmetics

import com.mojang.blaze3d.vertex.PoseStack
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * Where the eyes, hands and chest of the local player's model are this frame, for the storm aura's
 * burning eyes and charged fists.
 *
 * The aura itself is drawn from the player's position and knows nothing of the pose; this render layer
 * runs once the model has been posed, the way the sensei hat's does, and notes where a few points of
 * the head, arms and body ended up. It draws nothing. Layers are submitted before the aura's geometry
 * is built, so the points are always from the same frame.
 */
class AuraPoseLayer(parent: RenderLayerParent<AvatarRenderState, PlayerModel>) :
    RenderLayer<AvatarRenderState, PlayerModel>(parent) {

    private val scratch = Vector3f()

    override fun submit(
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        packedLight: Int,
        state: AvatarRenderState,
        yRot: Float,
        xRot: Float,
    ) {
        val cfg = AlpakaConfig.instance
        if (!cfg.auraEnabled || cfg.auraEffect != AuraFeature.EFFECT_STORM || state.isInvisible) return
        val player = Minecraft.getInstance().player ?: return
        if (state.id != player.id) return
        val model = parentModel
        capture(poseStack, model.head, AuraAnchors.EYE_A, -2f, -3.5f, -4.8f)
        capture(poseStack, model.head, AuraAnchors.EYE_B, 2f, -3.5f, -4.8f)
        capture(poseStack, model.head, AuraAnchors.HEAD_TOP, 0f, -8.6f, 0f)
        capture(poseStack, model.head, AuraAnchors.HEAD_CENTER, 0f, -4f, 0f)
        capture(poseStack, model.head, AuraAnchors.HEAD_FRONT, 0f, -4f, -6f)
        capture(poseStack, model.rightArm, AuraAnchors.HAND_R, -1f, 10.5f, 0f)
        capture(poseStack, model.leftArm, AuraAnchors.HAND_L, 1f, 10.5f, 0f)
        capture(poseStack, model.body, AuraAnchors.CHEST, 0f, 5f, 0f)
        AuraAnchors.capturedNanos = System.nanoTime()
    }

    /** One point of a model part, given in the part's own pixels (y down, -z forward). */
    private fun capture(poseStack: PoseStack, part: ModelPart, index: Int, px: Float, py: Float, pz: Float) {
        poseStack.pushPose()
        part.translateAndRotate(poseStack)
        poseStack.last().pose().transformPosition(px / 16f, py / 16f, pz / 16f, scratch)
        poseStack.popPose()
        AuraAnchors.raw[index * 3] = scratch.x
        AuraAnchors.raw[index * 3 + 1] = scratch.y
        AuraAnchors.raw[index * 3 + 2] = scratch.z
    }
}

/** The points [AuraPoseLayer] took, and turning them into the aura's own frame. */
internal object AuraAnchors {
    const val EYE_A = 0
    const val EYE_B = 1
    const val HEAD_TOP = 2
    const val HEAD_CENTER = 3
    const val HEAD_FRONT = 4
    const val HAND_R = 5
    const val HAND_L = 6
    const val CHEST = 7
    const val COUNT = 8

    /** As the model's pose put them: through the level's pose, relative to the camera. */
    val raw = FloatArray(COUNT * 3)
    var capturedNanos = 0L

    private val local = FloatArray(COUNT * 3)
    private val inverse = Matrix4f()
    private val scratch = Vector3f()

    /**
     * The points in the aura's frame (feet at the origin), or null when there is no fresh capture.
     *
     * [levelPose] is the pose the aura's geometry is drawn with: whatever the level applies on top
     * of camera-relative coordinates, undone here, since the model's points went through it too. A
     * capture is also rejected when its chest is nowhere near the player - the inventory screen's
     * preview runs the same layer with a pose of its own.
     */
    fun resolve(levelPose: Matrix4f, feetX: Float, feetY: Float, feetZ: Float, height: Float): FloatArray? {
        if (System.nanoTime() - capturedNanos > 60_000_000L) return null
        levelPose.invert(inverse)
        for (i in 0 until COUNT) {
            inverse.transformPosition(raw[i * 3], raw[i * 3 + 1], raw[i * 3 + 2], scratch)
            local[i * 3] = scratch.x - feetX
            local[i * 3 + 1] = scratch.y - feetY
            local[i * 3 + 2] = scratch.z - feetZ
        }
        val cx = local[CHEST * 3]
        val cy = local[CHEST * 3 + 1] - 0.65f * height
        val cz = local[CHEST * 3 + 2]
        if (cx * cx + cy * cy + cz * cz > 4f) return null
        return local
    }
}
