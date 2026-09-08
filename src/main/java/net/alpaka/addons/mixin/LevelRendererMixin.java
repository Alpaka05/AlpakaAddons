package net.alpaka.addons.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.blockoverlay.BlockOverlayFeature;
import net.alpaka.addons.features.etherwarp.EtherwarpDetector;
import net.alpaka.addons.features.etherwarp.EtherwarpOverlayFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.gizmos.Gizmos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    /**
     * Adds the Etherwarp target to the frame's gizmos. RETURN of collectPerFrameRenderThreadGizmos
     * (26.2's name for the per-frame collection; 26.1 calls it collectPerFrameGizmos): the method has
     * just installed the level renderer's collector as the one {@code Gizmos} adds to, and the caller
     * keeps it open while vanilla's own debug renderers contribute theirs, so anything added here is
     * drawn with them. World coordinates, no pose stack needed.
     */
    @Inject(method = "collectPerFrameRenderThreadGizmos", at = @At("RETURN"))
    private void alpaka$collectEtherwarpGizmos(CallbackInfoReturnable<Gizmos.TemporaryCollection> cir) {
        if (!EtherwarpOverlayFeature.isEnabled()) return;
        EtherwarpOverlayFeature.collectGizmos();
    }

    /**
     * Replaces the vanilla block outline with the mod's overlay.
     *
     * Since 26.2 the outline is no longer drawn straight into a buffer: submitBlockOutline hands the
     * targeted block's shape to the SubmitNodeCollector and a feature renderer draws it later in the
     * frame. The hook therefore sits on the submission, and the overlay is submitted the same way,
     * which puts it in the same pass the vanilla outline would have been drawn in.
     */
    @Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true)
    private void onSubmitBlockOutline(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState levelState, CallbackInfo ci) {
        if (!AlpakaConfig.instance.blockOverlayEnabled) return;

        BlockOutlineRenderState state = levelState.blockOutlineRenderState;
        if (state == null) return; // nothing targeted; vanilla returns as well

        // Aiming an Etherwarp teleport: draw nothing at all, so the teleport-target
        // indicators other Skyblock mods render are not overlapped.
        if (AlpakaConfig.instance.blockHideOnEtherwarp && EtherwarpDetector.isAimingEtherwarp()) {
            ci.cancel();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (AlpakaConfig.instance.blockIgnorePlants && mc.level != null
                && BlockOverlayFeature.isPlantBlock(mc.level.getBlockState(state.pos()))) {
            ci.cancel();
            return;
        }

        ci.cancel();
        BlockOverlayFeature.submit(poseStack, collector, levelState.cameraRenderState, state);
    }
}
