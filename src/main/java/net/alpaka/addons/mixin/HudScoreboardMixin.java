package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.alpaka.addons.features.scoreboard.ScoreboardBlurFeature;
import net.alpaka.addons.features.scoreboard.ScoreboardFooterFeature;
import net.alpaka.addons.utils.AlpakaPerf;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The scoreboard sidebar's looks: the blurred panel and the centered footer. Optional hooks
 * ({@code alpaka.cosmetic.mixins.json}): a scoreboard mod that takes the sidebar over must not keep
 * the game from starting. Each half alone is harmless - without the wrap the boxes stay vanilla's,
 * without the box hooks the sidebar only moves {@link ScoreboardBlurFeature#SHIFT} pixels left.
 */
@Mixin(Hud.class)
public abstract class HudScoreboardMixin {

    /**
     * The scoreboard sidebar: its two flat boxes become one blurred panel; see ScoreboardBlurFeature.
     * Opened and closed around the whole method in one place, the close in a finally: as a HEAD and a
     * TAIL hook, a sidebar that threw left the panel's shifted pose on the stack for the rest of the HUD.
     */
    @WrapMethod(method = "displayScoreboardSidebar")
    private void alpaka$scoreboard(GuiGraphicsExtractor graphicsExtractor, Objective objective, Operation<Void> original) {
        long t = AlpakaPerf.begin(AlpakaPerf.Section.HUD_SCOREBOARD);
        ScoreboardBlurFeature.begin(graphicsExtractor);
        try {
            original.call(graphicsExtractor, objective);
        } finally {
            ScoreboardBlurFeature.end(graphicsExtractor);
            AlpakaPerf.end(AlpakaPerf.Section.HUD_SCOREBOARD, t);
        }
    }

    @WrapOperation(
            method = "displayScoreboardSidebar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0)
    )
    private void alpaka$scoreboardTitleBox(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
        if (!ScoreboardBlurFeature.titleBox(y0)) original.call(graphics, x0, y0, x1, y1, color);
    }

    @WrapOperation(
            method = "displayScoreboardSidebar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 1)
    )
    private void alpaka$scoreboardBodyBox(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
        if (!ScoreboardBlurFeature.bodyBox(graphics, x0, y0, x1, y1, color)) original.call(graphics, x0, y0, x1, y1, color);
    }

    /** The AlpakaAddons footer is centred like the title instead of starting at the left edge. */
    @WrapOperation(
            method = "displayScoreboardSidebar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", ordinal = 1)
    )
    private void alpaka$centerScoreboardFooter(GuiGraphicsExtractor graphics, Font font, Component line, int x, int y, int color, boolean shadow,
                                               Operation<Void> original, @Local(name = "width") int width) {
        if (ScoreboardFooterFeature.isFooter(line)) x = ScoreboardFooterFeature.centeredX(x, width,
                // The bare footer is one kept component; a font wrapper around it is new every frame.
                line == ScoreboardFooterFeature.FOOTER ? net.alpaka.addons.client.gui.TextWidths.of(font, line) : font.width(line));
        original.call(graphics, font, line, x, y, color, shadow);
    }
}
