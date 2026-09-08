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
        // Chroma glows on its own and ignores world light; the plain hat is lit like the rest of the
        // model, so a dark cave darkens it too.
        //
        // The weave texture is itself part-transparent (its alpha averages about 200 of 255), so a
        // translucent type can never make the hat fully opaque. At 100% opacity the solid type is
        // used instead, which ignores alpha altogether and is what "opaque" should mean here.
        //
        // Below 100% the plain entity translucent types are used, knowingly: they vanish where leaves
        // stand behind the hat, because they land in a target the transparency pass composites after
        // the cutout terrain. The item-target type does not have that problem but has the opposite
        // one - the hat then shows through the leaves like an x-ray - which is worse. Anyone who
        // wants the hat to hold up against foliage sets the opacity to 100%.
        val rainbow = AlpakaConfig.instance.chromaHatRainbow
        val opaque = AlpakaConfig.instance.chromaHatOpacity >= 100f
        val renderType = when {
            opaque -> RenderTypes.entitySolid(ChromaHatFeature.TEXTURE)
            rainbow -> RenderTypes.entityTranslucentEmissive(ChromaHatFeature.TEXTURE)
            else -> RenderTypes.entityTranslucent(ChromaHatFeature.TEXTURE)
        }
        poseStack.pushPose()
        parentModel.head.translateAndRotate(poseStack)
        collector.submitCustomGeometry(poseStack, renderType) { pose, consumer ->
            ChromaHatFeature.emit(pose, consumer, wearingHelmet, rainbow, packedLight)
        }
        poseStack.popPose()
    }
}
