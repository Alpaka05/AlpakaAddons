package net.alpaka.addons.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.blockoverlay.BlockOverlayFeature;
import net.alpaka.addons.features.etherwarp.EtherwarpDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

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
