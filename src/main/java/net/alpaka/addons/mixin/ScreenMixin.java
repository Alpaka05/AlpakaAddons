package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.snow.SnowOverlayRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.alpaka.addons.features.guifade.GuiFadeTracker;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Shadow public int width;
    @Shadow public int height;

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void onExtractBackground(GuiGraphicsExtractor graphicsExtractor, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
        if (AlpakaConfig.instance.inventorySnowEnabled) {
            SnowOverlayRenderer.render(graphicsExtractor, this.width, this.height);
        }
    }

    @Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
    private void onExtractTransparentBackground(GuiGraphicsExtractor graphicsExtractor, CallbackInfo ci) {
        long now = System.currentTimeMillis();
        long openTime = GuiFadeTracker.getGuiOpenTime();

        float opacity = AlpakaConfig.instance.containerBgOpacity;

        if (AlpakaConfig.instance.containerBgFadeInEnabled) {
            long elapsed = now - openTime;
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
}
