package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.NativeImage;
import net.alpaka.addons.client.gui.SmoothFontFaces;
import org.lwjgl.util.freetype.FT_Face;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Renders the mod's glyph bitmaps unhinted too. The bitmap has to come from the same outline the
 * glyph's size was measured on in {@link TrueTypeGlyphProviderMixin}, or the copy is refused.
 */
@Mixin(NativeImage.class)
public abstract class NativeImageFontMixin {

    @WrapOperation(method = "copyFromFont", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/freetype/FreeType;FT_Load_Glyph(Lorg/lwjgl/util/freetype/FT_Face;II)I", remap = false))
    private int alpaka$renderUnhinted(FT_Face face, int index, int flags, Operation<Integer> original) {
        return original.call(face, index, SmoothFontFaces.loadFlags(face, flags));
    }
}
