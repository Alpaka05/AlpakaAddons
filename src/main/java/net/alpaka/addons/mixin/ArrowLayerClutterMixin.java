package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.alpaka.addons.features.clutter.HideVisualClutterFeature;
import net.minecraft.client.renderer.entity.layers.ArrowLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The arrows stuck in players, for Hide Visual Clutter's Attached Arrows. */
@Mixin(ArrowLayer.class)
public class ArrowLayerClutterMixin {

    @ModifyReturnValue(method = "numStuck", at = @At("RETURN"))
    private int alpaka$hideStuckArrows(int count) {
        return count > 0 && HideVisualClutterFeature.shouldHideStuckArrows() ? 0 : count;
    }
}
