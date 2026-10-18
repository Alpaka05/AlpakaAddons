package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Every entity name tag - players, mobs, armor stands - is laid out here, so the two name tag
 * options for everyone sit on this one call. The own animated name tag draws itself and keeps its
 * own settings.
 */
@Mixin(NameTagFeatureRenderer.class)
public class NameTagFeatureRendererMixin {

    /**
     * Hide Name Tag Background: the dark box is nothing but the font's background color. A zero
     * color makes the font skip the box, so the text is drawn as usual without it.
     */
    @ModifyArg(
            method = "prepareText",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"),
            index = 6
    )
    private static int alpaka$hideBackground(int backgroundColor) {
        return AlpakaConfig.instance.hideNameTagBackground ? 0 : backgroundColor;
    }

    /**
     * Name Tag Text Shadow. Vanilla submits a visible tag twice: the normal text, and a faint copy
     * that shows through walls. Only the normal one gets the shadow - a shadow on the faint copy
     * would show a dark ghost of the name through walls. A sneaking player's tag is a single faint
     * normal submit, which is shadowed like any other.
     */
    @ModifyArg(
            method = "prepareText",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"),
            index = 4
    )
    private static boolean alpaka$textShadow(boolean dropShadow, @Local(argsOnly = true) NameTagFeatureRenderer.Submit submit) {
        return dropShadow || (AlpakaConfig.instance.nameTagTextShadow && submit.displayMode() == Font.DisplayMode.NORMAL);
    }
}
