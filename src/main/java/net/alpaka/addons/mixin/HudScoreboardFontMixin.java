package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.alpaka.addons.client.gui.GuiFont;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The scoreboard's title in the menu font when that is switched on; the lines below are done in
 * {@link ScoreboardEntryFontMixin}. The sidebar is measured from the same components it draws, so
 * it sizes itself to the new font.
 */
@Mixin(Hud.class)
public abstract class HudScoreboardFontMixin {

    @ModifyExpressionValue(method = "displayScoreboardSidebar", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/Objective;getDisplayName()Lnet/minecraft/network/chat/Component;"))
    private Component alpaka$titleInMenuFont(Component title) {
        return GuiFont.inScoreboard() ? GuiFont.restyle(title) : title;
    }
}
