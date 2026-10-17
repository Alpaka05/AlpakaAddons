package net.alpaka.addons.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.alpaka.addons.features.chat.ScreenshotMessageFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

/**
 * The moment a screenshot is on disk, before anyone posts a chat notice for it. Hooked on the write
 * itself rather than on its call in Screenshot, because other mods replace that code: Screenshot
 * Message Enhancer cancels Screenshot.grab and writes the file from its own mixin, Essential takes
 * over the screenshot key. All of them still end in this method. Which writes are the player's
 * screenshots is decided in ScreenshotMessageFeature.onSaved.
 */
@Mixin(NativeImage.class)
public class NativeImageScreenshotMixin {
    @Inject(method = "writeToFile(Ljava/io/File;)V", at = @At("RETURN"))
    private void alpaka$onScreenshotSaved(File file, CallbackInfo ci) {
        ScreenshotMessageFeature.onSaved(file);
    }
}
