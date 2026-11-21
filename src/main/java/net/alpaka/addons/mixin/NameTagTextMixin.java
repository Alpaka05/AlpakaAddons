package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Every entity name tag - players, mobs, armor stands - is built here, as the text content of one
 * submit per part, so the nametag options for everyone sit on this one call. The own animated name
 * tag draws itself and keeps its own settings.
 *
 * <p>The settings go on the content itself rather than on the font call that draws it, because other
 * mods read the submit too: whatever reads the background color from it gets the chosen one.
 *
 * <p>The raised priority applies this mixin after other name tag mods that set the same arguments,
 * so ours runs last and decides.
 */
@Mixin(value = SubmitNodeCollection.class, priority = 1100)
public class NameTagTextMixin {
    private static final String TEXT_INIT = "Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Content$Text;"
            + "<init>(FFLnet/minecraft/util/FormattedCharSequence;ZIII)V";

    /**
     * Name Tag Text Shadow. Vanilla submits a visible tag twice: the normal text, and a faint copy
     * that shows through walls. Only the normal one gets the shadow - a shadow on the faint copy
     * would show a dark ghost of the name through walls. A sneaking player's tag is a single faint
     * normal submit, which is shadowed like any other.
     */
    @ModifyArg(method = "nameTag", at = @At(value = "INVOKE", target = TEXT_INIT), index = 3)
    private static boolean alpaka$textShadow(boolean dropShadow, @Local(argsOnly = true) Font.DisplayMode displayMode) {
        return dropShadow || (AlpakaConfig.instance.nameTagTextShadow && displayMode == Font.DisplayMode.NORMAL);
    }

    /**
     * Nametag Background Color. Only a part that has a box gets the chosen color - the plain text
     * drawn over the box has none - and a zero opacity makes the font skip the box, so the text is
     * drawn as usual without it. The default leaves the box to vanilla, or to another mod that
     * changed it.
     */
    @ModifyArg(method = "nameTag", at = @At(value = "INVOKE", target = TEXT_INIT), index = 5)
    private static int alpaka$backgroundColor(int backgroundColor) {
        int color = AlpakaConfig.instance.nameTagBackgroundColor;
        if (backgroundColor == 0 || color == AlpakaConfig.DEFAULT_NAME_TAG_BACKGROUND) return backgroundColor;
        return (color >>> 24) == 0 ? 0 : color;
    }
}
