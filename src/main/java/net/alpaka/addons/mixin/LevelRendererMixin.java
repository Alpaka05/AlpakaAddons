package net.alpaka.addons.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.alpaka.addons.features.blockoverlay.BlockOverlayFeature;
import net.alpaka.addons.features.etherwarp.EtherwarpDetector;
import net.alpaka.addons.features.etherwarp.EtherwarpOverlayFeature;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.gizmos.Gizmos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    /**
     * Adds the Etherwarp target to the frame's gizmos. RETURN of collectPerFrameGizmos: the method
     * has just installed the level renderer's collector as the one {@code Gizmos} adds to, and the
     * caller keeps it open while vanilla's own debug renderers contribute theirs, so anything added
     * here is drawn with them. World coordinates, no pose stack needed.
     */
    @Inject(method = "collectPerFrameGizmos", at = @At("RETURN"))
    private void alpaka$collectEtherwarpGizmos(CallbackInfoReturnable<Gizmos.TemporaryCollection> cir) {
        if (!EtherwarpOverlayFeature.isEnabled()) return;
        EtherwarpOverlayFeature.collectGizmos();
    }

    @Inject(
        method = "renderHitOutline",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRenderHitOutline(PoseStack poseStack, VertexConsumer vertexConsumer, double camX, double camY, double camZ, BlockOutlineRenderState state, int light, float alpha, CallbackInfo ci) {
        if (net.alpaka.addons.config.AlpakaConfig.instance.blockOverlayEnabled) {
            // Aiming an Etherwarp teleport: draw nothing at all, so the teleport-target
            // indicators other Skyblock mods render are not overlapped.
            if (net.alpaka.addons.config.AlpakaConfig.instance.blockHideOnEtherwarp
                    && EtherwarpDetector.isAimingEtherwarp()) {
                ci.cancel();
                return;
            }

            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (net.alpaka.addons.config.AlpakaConfig.instance.blockIgnorePlants && mc.level != null && state != null) {
                if (BlockOverlayFeature.isPlantBlock(mc.level.getBlockState(state.pos()))) {
                    ci.cancel();
                    return;
                }
            }
            ci.cancel();
            BlockOverlayFeature.render(poseStack, camX, camY, camZ, state);
        }
    }
}
