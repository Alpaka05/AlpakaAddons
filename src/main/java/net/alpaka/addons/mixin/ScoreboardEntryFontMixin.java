package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.features.scoreboard.ScoreboardFooterFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A scoreboard line in the menu font when that is switched on. The line is a record local to
 * {@code Hud.displayScoreboardSidebar}; taking its constructor catches the name and the score in
 * one place, and the score's width is measured again in the new font so the numbers stay aligned.
 */
@Mixin(targets = "net.minecraft.client.gui.Hud$1DisplayEntry")
public abstract class ScoreboardEntryFontMixin {

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static Component alpaka$nameInMenuFont(Component name) {
        // Hypixel's address line becomes the AlpakaAddons footer first, so it gets the font too.
        name = ScoreboardFooterFeature.replace(name);
        return GuiFont.inScoreboard() ? GuiFont.restyle(name) : name;
    }

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private static Component alpaka$scoreInMenuFont(Component score) {
        return GuiFont.inScoreboard() ? GuiFont.restyle(score) : score;
    }

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static int alpaka$scoreWidthInMenuFont(int scoreWidth, @Local(argsOnly = true, ordinal = 1) Component score) {
        return GuiFont.inScoreboard() ? Minecraft.getInstance().font.width(GuiFont.restyle(score)) : scoreWidth;
    }
}
