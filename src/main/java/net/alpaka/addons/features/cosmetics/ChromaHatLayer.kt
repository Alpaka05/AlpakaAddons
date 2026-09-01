package net.alpaka.addons.features.cosmetics

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.model.player.PlayerModel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.rendertype.RenderTypes

/**
 * The render layer that puts [ChromaHatFeature]'s hat on the player model.
 *
 * Added to both player renderers (wide and slim arms) from `AvatarRendererMixin`, the same way
 * vanilla attaches capes, elytra and worn heads. Being a layer is what makes the hat follow the
 * head: layers run after the model has been posed for the frame, so translating into the head part
 * picks up its current pitch and yaw, the crouch offset, the swimming pose, everything.
 *
 * The layer itself renders for whichever player state it is handed; [ChromaHatFeature.shouldRender]
 * is what narrows that to the local player.
 */
class ChromaHatLayer(parent: RenderLayerParent<AvatarRenderState, PlayerModel>) :
    RenderLayer<AvatarRenderState, PlayerModel>(parent) {

    override fun submit(
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        packedLight: Int,
        state: AvatarRenderState,
        yRot: Float,
        xRot: Float,
    ) {
        if (!ChromaHatFeature.shouldRender(state)) return

        val wearingHelmet = !state.headEquipment.isEmpty
        poseStack.pushPose()
        parentModel.head.translateAndRotate(poseStack)
        collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucentEmissive(ChromaHatFeature.TEXTURE)) { pose, consumer ->
            ChromaHatFeature.emit(pose, consumer, wearingHelmet)
        }
        poseStack.popPose()
    }
}
