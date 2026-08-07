package net.alpaka.addons.mixin;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.platform.CompareOp;
import net.alpaka.addons.features.blockoverlay.BlockOverlayFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DepthStencilState.class)
public class DepthStencilStateMixin {
    @Inject(
        method = "depthTest",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onDepthTest(CallbackInfoReturnable<CompareOp> cir) {
        if (BlockOverlayFeature.isRenderingBlockOverlay && BlockOverlayFeature.ignoreDepthActive) {
            cir.setReturnValue(CompareOp.ALWAYS_PASS);
        }
    }
}
