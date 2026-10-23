package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Nametag Background Color. The color goes on the submit itself rather than on the font call that
 * draws it, because other mods read it from there too: Essential draws the padding around its
 * nametag icon with this color, and Name Tag Tweaks sets its own color before the submit is made.
 * Reading it here gives all of them the chosen color.
 */
@Mixin(NameTagFeatureRenderer.Submit.class)
public class NameTagSubmitMixin {

    /**
     * Only a submit that has a box gets the chosen color - the plain text drawn over the box has
     * none - and a zero opacity makes the font skip the box, so the text is drawn as usual without
     * it. The default leaves the box to vanilla, or to another mod that changed it.
     */
    @ModifyReturnValue(method = "backgroundColor", at = @At("RETURN"))
    private int alpaka$backgroundColor(int backgroundColor) {
        int color = AlpakaConfig.instance.nameTagBackgroundColor;
        if (backgroundColor == 0 || color == AlpakaConfig.DEFAULT_NAME_TAG_BACKGROUND) return backgroundColor;
        return (color >>> 24) == 0 ? 0 : color;
    }
}
