package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.chat.ChatPeekFeature;
import net.alpaka.addons.features.itemvalue.ItemValuePopup;
import net.alpaka.addons.features.snow.SnowOverlayRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.alpaka.addons.features.guifade.GuiFadeTracker;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Shadow public int width;
    @Shadow public int height;

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void onExtractBackground(GuiGraphicsExtractor graphicsExtractor, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
        // Container screens only, as the option says; the main menu draws its own. It used to fall
        // behind every screen, the command wheel and the config included.
        if (AlpakaConfig.instance.inventorySnowEnabled
                && (Object) this instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
            SnowOverlayRenderer.render(graphicsExtractor, this.width, this.height);
        }
    }

    @Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
    private void onExtractTransparentBackground(GuiGraphicsExtractor graphicsExtractor, CallbackInfo ci) {
        float opacity = AlpakaConfig.instance.containerBgOpacity;

        // The fade belongs to container GUIs only. Applying it to every screen also animated the
        // backdrop behind the mod's own menus and the pause screen, which draw their own panel
        // animation on top - the two together made the whole background look like it moved.
        boolean isContainer = (Object) this instanceof AbstractContainerScreen;

        if (AlpakaConfig.instance.containerBgFadeInEnabled && isContainer) {
            long elapsed = System.currentTimeMillis() - GuiFadeTracker.getGuiOpenTime();
            int duration = Math.max(10, AlpakaConfig.instance.containerBgFadeInDurationMs);
            float progress = Math.min(1.0f, (float) elapsed / (float) duration);
            float fadeFactor = progress * progress;
            opacity *= fadeFactor;
        }

        if (opacity <= 0.001f) {
            ci.cancel();
            return;
        }

        ci.cancel();
        int alpha = Math.round(255.0f * opacity);
        int color = (alpha << 24);
        graphicsExtractor.fillGradient(0, 0, this.width, this.height, color, color);
    }

    /**
     * Chat peek in a menu: the chat goes in after the menu's content and before its tooltips, so it
     * sits above the blur and dimming while a hovered item's tooltip still comes out on top of it.
     */
    @Inject(
        method = "extractRenderStateWithTooltipAndSubtitles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;extractDeferredElements(IIF)V")
    )
    private void alpaka$peekChatOverMenu(GuiGraphicsExtractor graphicsExtractor, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ChatPeekFeature.renderOverMenu(graphicsExtractor, mouseX, mouseY);
    }

    /**
     * The Item Value popup goes in at the same point, after the peeked chat: over the menu, under the
     * tooltip of a slot beside it.
     */
    @Inject(
        method = "extractRenderStateWithTooltipAndSubtitles",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;extractDeferredElements(IIF)V")
    )
    private void alpaka$itemValueOverMenu(GuiGraphicsExtractor graphicsExtractor, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ItemValuePopup.render(graphicsExtractor);
    }

    /**
     * With the mouse on the Item Value popup, the menu is told the mouse is nowhere, so the slot under
     * the popup neither lights up nor shows its tooltip through it.
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
