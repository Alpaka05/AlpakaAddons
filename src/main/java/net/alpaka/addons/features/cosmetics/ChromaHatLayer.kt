package net.alpaka.addons.features.cosmetics

import com.mojang.blaze3d.vertex.PoseStack
import net.alpaka.addons.config.AlpakaConfig
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
        // The item-target translucent type: what vanilla draws translucent items in the world with.
        // The plain entity translucent types vanished wherever leaves stood behind the hat - they
        // land in a target that the transparency pass composites after the cutout terrain has
        // already claimed those pixels - while the item target is sorted against terrain correctly.
        // Chroma glow comes from full-bright lightmap coordinates on the vertices rather than from an
        // emissive type, which gives the same result and keeps the one render type for both looks.
        val rainbow = AlpakaConfig.instance.chromaHatRainbow
        // The weave texture is itself part-transparent (its alpha averages about 200 of 255), so a
        // translucent type can never make the hat fully opaque. At 100% opacity the solid type is
        // used instead, which ignores alpha altogether and is what "opaque" should mean here.
        val opaque = AlpakaConfig.instance.chromaHatOpacity >= 100f
        val renderType = if (opaque) {
            RenderTypes.entitySolid(ChromaHatFeature.TEXTURE)
        } else {
            RenderTypes.entityTranslucentCullItemTarget(ChromaHatFeature.TEXTURE)
        }
        poseStack.pushPose()
        parentModel.head.translateAndRotate(poseStack)
        collector.submitCustomGeometry(poseStack, renderType) { pose, consumer ->
            ChromaHatFeature.emit(pose, consumer, wearingHelmet, rainbow, packedLight)
        }
        poseStack.popPose()
    }
}
