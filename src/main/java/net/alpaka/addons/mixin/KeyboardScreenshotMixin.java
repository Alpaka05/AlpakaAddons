package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.alpaka.addons.features.chat.ScreenshotMessageFeature;
import net.alpaka.addons.features.chat.ScreenshotPreview;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.File;
import java.util.function.Consumer;

/**
 * The screenshot key. It is noted, so the file it leads to is known as the player's own (see
 * ScreenshotMessageFeature.onSaved). While the screenshot preview is on screen the game would capture
 * the last frame, preview and all, so the capture waits for a frame drawn without it.
 *
 * On 26.1.2 the key is handled in KeyboardHandler.keyPress, not in Minecraft.handleGlobalKeyPress.
 */
@Mixin(KeyboardHandler.class)
public class KeyboardScreenshotMixin {
    @WrapOperation(
            method = "keyPress",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Screenshot;grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V")
    )
    private void alpaka$captureWithoutPreview(File gameDirectory, RenderTarget target, Consumer<Component> callback,
                                              Operation<Void> original) {
        ScreenshotMessageFeature.onKeyPressed();
        if (ScreenshotPreview.isOnScreen()) {
            ScreenshotPreview.captureWithoutPreview(() -> original.call(gameDirectory, target, callback));
        } else {
            original.call(gameDirectory, target, callback);
        }
    }
}
