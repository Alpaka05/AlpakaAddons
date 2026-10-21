package net.alpaka.addons.mixin;

import net.alpaka.addons.features.scoreboard.ScoreboardFooterFeature;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Swaps Hypixel's address line for the AlpakaAddons footer. The line is a record local to
 * {@code Hud.displayScoreboardSidebar}; changing its name in the constructor happens before vanilla
 * measures the sidebar's width from it.
 */
@Mixin(targets = "net.minecraft.client.gui.Hud$1DisplayEntry")
public abstract class ScoreboardFooterMixin {

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Component alpaka$replaceFooter(Component name) {
        return ScoreboardFooterFeature.replace(name);
    }
}
