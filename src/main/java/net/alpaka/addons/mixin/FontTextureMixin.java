package net.alpaka.addons.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.alpaka.addons.client.gui.GuiFont;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

/**
 * Samples the glyph pages of the mod's TrueType menu fonts linearly.
 *
 * Every font texture is created with a nearest-neighbour sampler, which is right for the pixel
 * font: its glyphs are drawn one texel to one pixel. The TrueType fonts are rasterised four times
 * oversampled and then drawn scaled down, and nearest-neighbour picks one texel out of each block
 * instead of averaging it. That is what made the strokes come out ragged and uneven in width -
 * "pixelated in places". Linear sampling averages the block, which is the anti-aliasing the
 * oversampling was there to provide.
 *
 * Only the mod's own fonts are touched; every other font keeps vanilla's sampler.
 */
@Mixin(FontTexture.class)
public abstract class FontTextureMixin extends AbstractTexture {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void alpaka$sampleSmoothFontsLinearly(Supplier<String> label, GlyphRenderTypes renderTypes, boolean colored, CallbackInfo ci) {
        if (GuiFont.usesSmoothSampling(label.get())) {
            this.sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
        }
    }
}
