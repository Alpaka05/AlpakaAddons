package net.alpaka.addons.mixin;

import net.alpaka.addons.features.garden.AlignmentHelper;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Alignment Helper: the mouse turns slower while lining up, and not at all while farming locked. */
@Mixin(MouseHandler.class)
public class AlignmentMouseHandlerMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "turnPlayer", at = @At("HEAD"))
    private void alpaka$alignmentHelper(double movementTime, CallbackInfo ci) {
        double factor = AlignmentHelper.mouseFactor();
        if (factor == 1.0) return;
        this.accumulatedDX *= factor;
        this.accumulatedDY *= factor;
    }
}
