package net.alpaka.addons.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.alpaka.addons.features.blockoverlay.BlockOverlayFeature;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(
        method = "renderHitOutline",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRenderHitOutline(PoseStack poseStack, VertexConsumer vertexConsumer, double camX, double camY, double camZ, BlockOutlineRenderState state, int light, float alpha, CallbackInfo ci) {
        if (net.alpaka.addons.config.AlpakaConfig.instance.blockOverlayEnabled) {
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
