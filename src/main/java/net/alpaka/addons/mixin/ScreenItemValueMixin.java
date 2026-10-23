package net.alpaka.addons.mixin;

import net.alpaka.addons.features.itemvalue.ItemValuePopup;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Item Value popup over a menu: drawn after the menu and before the tooltips, so it sits over
 * the slots and under the tooltip of a slot beside it. Listed after ScreenMixin, so it also comes
 * out above the chat held open there with Chat Peek.
 */
@Mixin(Screen.class)
public abstract class ScreenItemValueMixin {

    @Inject(
        method = "extractRenderStateWithTooltipAndSubtitles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;extractDeferredElements(IIF)V")
    )
    private void alpaka$itemValueOverMenu(GuiGraphicsExtractor graphicsExtractor, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ItemValuePopup.render(graphicsExtractor);
    }

    /**
     * With the mouse on the popup, the menu is told the mouse is nowhere, so the slot under the
     * popup neither lights up nor shows its tooltip through it.
     */
    @ModifyVariable(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int alpaka$mouseXUnderItemValue(int mouseX) {
        return ItemValuePopup.coversMouse((Screen) (Object) this) ? -10000 : mouseX;
    }

    @ModifyVariable(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int alpaka$mouseYUnderItemValue(int mouseY) {
        return ItemValuePopup.coversMouse((Screen) (Object) this) ? -10000 : mouseY;
    }
}
