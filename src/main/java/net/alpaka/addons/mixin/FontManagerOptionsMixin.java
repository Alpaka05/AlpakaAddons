package net.alpaka.addons.mixin;

import net.alpaka.addons.client.gui.AlpakaStyle;
import net.minecraft.client.Options;
import net.minecraft.client.gui.font.FontManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Force Unicode Font and Japanese Glyph Variants swap glyphs in place, without a resource reload:
 * the kept text widths (see {@link AlpakaStyle#fontGeneration}) are dropped here too.
 */
@Mixin(FontManager.class)
public class FontManagerOptionsMixin {
    @Inject(method = "updateOptions", at = @At("TAIL"))
    private void alpaka$fontOptionsChanged(Options options, CallbackInfo ci) {
        AlpakaStyle.onResourcesReloaded();
    }
}
