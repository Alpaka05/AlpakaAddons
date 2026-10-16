package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.NativeImage;
import net.alpaka.addons.features.chat.ScreenshotMessageFeature;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.io.File;

/**
 * The moment a screenshot is on disk, before the game posts its chat notice - which other mods,
 * Essential among them, may keep out of the chat; Essential also takes over the screenshot key, but
 * its capture ends in this same write. The write happens in a lambda inside grab, hence the
 * wildcard: whatever that lambda is called, it is the only place Screenshot writes a file.
 */
@Mixin(Screenshot.class)
public class ScreenshotMixin {
    @WrapOperation(
            method = "*",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;writeToFile(Ljava/io/File;)V")
    )
    private static void alpaka$onScreenshotSaved(NativeImage image, File file, Operation<Void> original) {
        original.call(image, file);
        ScreenshotMessageFeature.onSaved(file);
    }
}
