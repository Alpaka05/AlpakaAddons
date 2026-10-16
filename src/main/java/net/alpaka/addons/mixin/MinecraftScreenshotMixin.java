package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.alpaka.addons.features.chat.ScreenshotMessageFeature;
import net.alpaka.addons.features.chat.ScreenshotPreview;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The screenshot key. It is noted, so the file it leads to is known as the player's own (see
 * ScreenshotMessageFeature.onSaved). While the screenshot preview is on screen the game would capture
 * the last frame, preview and all, so the capture waits for a frame drawn without it.
 */
@Mixin(Minecraft.class)
public class MinecraftScreenshotMixin {
    @WrapOperation(
            method = "handleGlobalKeyPress",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;grab(Lnet/minecraft/client/Minecraft;Z)V")
    )
    private void alpaka$captureWithoutPreview(Minecraft minecraft, boolean flag, Operation<Void> original) {
        ScreenshotMessageFeature.onKeyPressed();
        if (ScreenshotPreview.isOnScreen()) {
            ScreenshotPreview.captureWithoutPreview(() -> original.call(minecraft, flag));
        } else {
            original.call(minecraft, flag);
        }
    }
}
