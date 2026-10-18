package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Hide Name Tag Background: every entity name tag - players, mobs, armor stands - is laid out here,
 * and its dark box is nothing but the font's background color. A zero color makes the font skip the
 * box, so the text is drawn as usual without it. The own animated name tag draws its backdrop itself
 * and keeps its own setting.
 */
@Mixin(NameTagFeatureRenderer.class)
public class NameTagFeatureRendererMixin {

    @ModifyArg(
            method = "prepareText",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"),
            index = 6
    )
    private static int alpaka$hideBackground(int backgroundColor) {
        return AlpakaConfig.instance.hideNameTagBackground ? 0 : backgroundColor;
    }
}
