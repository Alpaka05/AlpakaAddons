package net.alpaka.addons.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.alpaka.addons.features.cosmetics.ChromaHatLayer;
import net.alpaka.addons.features.nametag.CustomNameTagFeature;
import net.alpaka.addons.features.playerscale.PlayerScaleFeature;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks the player renderer for the cosmetics: the chroma hat rides along as an extra render layer,
 * the local player's own name tag is drawn by the mod instead of by vanilla, and the player scale
 * stretches or shrinks the model per axis.
 */
@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

    /**
     * Decides, before vanilla fills the state, whether this player is drawn scaled, and records the
     * answer on the state. HEAD rather than TAIL because vanilla's shadow extraction runs inside this
     * method and asks {@code getShadowRadius}, which reads the answer.
     *
     * The descriptor is spelled out: the class also carries the erased bridges
     * extractRenderState(LivingEntity, ...) and extractRenderState(Entity, ...).
     */
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("HEAD")
    )
    private void alpaka$rememberPlayerScale(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        PlayerScaleFeature.extract(entity, state);
    }

    /** Lifts the name tag attachment with the head, once vanilla has filled it in. */
    @Inject(
        method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
        at = @At("TAIL")
    )
    private void alpaka$scaleNameTagAttachment(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        PlayerScaleFeature.adjustNameTag(state);
    }

    /**
     * Applies the per-axis scale on top of vanilla's uniform 0.9375. This runs after setupRotations
     * and the y-flip, so the axes are the body's own, and before the translation onto the feet, so
     * the model stays standing on the ground. See {@link PlayerScaleFeature}.
     */
    @Inject(
        method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V",
        at = @At("TAIL")
    )
    private void alpaka$applyPlayerScale(AvatarRenderState state, PoseStack poseStack, CallbackInfo ci) {
        PlayerScaleFeature.applyScale(state, poseStack);
    }

    /**
     * Both player renderers - wide and slim - are built through this constructor, so adding the
     * layer here covers every player model the game can draw.
     *
     * The layer list belongs to LivingEntityRenderer, which a mixin on the subclass cannot shadow;
     * hence the accessor. See {@link LivingEntityRendererAccessor}.
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void alpaka$addCosmeticLayers(EntityRendererProvider.Context context, boolean slim, CallbackInfo ci) {
        @SuppressWarnings("unchecked")
        RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) (Object) this;
        ((LivingEntityRendererAccessor) (Object) this).alpaka$addLayer(new ChromaHatLayer(parent));
    }

    /**
     * Lets the local player see their own name.
     *
     * This has to sit on the player renderer's own override: the decision is made in
     * LivingEntityRenderer.shouldShowName, which rejects the camera entity and never calls up to
     * EntityRenderer, so a hook on the base class is never reached for a player. The feature only
     * says yes while the world is extracting the player - a GUI preview keeps vanilla's answer.
     */
    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
    private void alpaka$showOwnName(Avatar entity, double distanceSq, CallbackInfoReturnable<Boolean> cir) {
        if (CustomNameTagFeature.shouldForceShowName(entity)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Replaces vanilla's name tag with the animated one for the local player only.
     *
     * Everyone else's tag falls through to vanilla untouched. The state's entity id, not the state
     * object, identifies the player - one state instance is reused for every player drawn.
     */
    @Inject(
        method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void alpaka$customOwnNameTag(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (CustomNameTagFeature.isEnabled() && CustomNameTagFeature.isLocalPlayerState(state)) {
            CustomNameTagFeature.submit(state, poseStack, collector, camera);
            ci.cancel();
        }
    }
}
