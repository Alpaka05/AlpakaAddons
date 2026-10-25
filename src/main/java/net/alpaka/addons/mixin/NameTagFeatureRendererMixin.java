package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Every entity name tag - players, mobs, armor stands - is laid out here, so the nametag
 * options for everyone sit on this one call. The own animated name tag draws itself and keeps its
 * own settings. The background color sits on the submit instead ({@link NameTagSubmitMixin}).
 *
 * <p>The raised priority applies this mixin after Name Tag Tweaks, which sets the same shadow
 * argument to its own setting and ignores what it gets. Applied later, ours runs last and decides.
 */
@Mixin(value = NameTagFeatureRenderer.class, priority = 1100)
public class NameTagFeatureRendererMixin {

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
