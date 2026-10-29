package net.alpaka.addons.mixin;

import net.alpaka.addons.features.chestvalue.ChestValuePanel;
import net.alpaka.addons.features.itemvalue.ItemValuePopup;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Item Value popup over a menu, and the Chest Value panel beside it: drawn after the menu and
 * before the tooltips, so they sit over the slots and under the tooltip of a slot beside them. The
 * popup comes last, above the panel it may be opened from. Listed after ScreenMixin, so both also
 * come out above the chat held open there with Chat Peek.
 */
@Mixin(Screen.class)
public abstract class ScreenItemValueMixin {

    @Inject(
        method = "extractRenderStateWithTooltipAndSubtitles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;extractDeferredElements(IIF)V")
    )
    private void alpaka$itemValueOverMenu(GuiGraphicsExtractor graphicsExtractor, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ChestValuePanel.render(graphicsExtractor, (Screen) (Object) this);
        ItemValuePopup.render(graphicsExtractor);
    }

    /**
     * With the mouse on the popup or the panel, the menu is told the mouse is nowhere, so the slot
     * under it neither lights up nor shows its tooltip through it.
     */
    @ModifyVariable(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int alpaka$mouseXUnderItemValue(int mouseX) {
        return alpaka$covered() ? -10000 : mouseX;
    }

    @ModifyVariable(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int alpaka$mouseYUnderItemValue(int mouseY) {
        return alpaka$covered() ? -10000 : mouseY;
    }

    private boolean alpaka$covered() {
        Screen self = (Screen) (Object) this;
        return ItemValuePopup.coversMouse(self) || ChestValuePanel.coversMouse(self);
    }
}
