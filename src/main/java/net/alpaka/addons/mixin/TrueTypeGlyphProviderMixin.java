package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import net.alpaka.addons.client.gui.SmoothFontFaces;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FreeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

/** Loads the glyphs of the mod's own fonts unhinted; see {@link SmoothFontFaces}. */
@Mixin(TrueTypeGlyphProvider.class)
public abstract class TrueTypeGlyphProviderMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void alpaka$rememberFace(ByteBuffer fontMemory, FT_Face face, float size, float oversample,
                                     float shiftX, float shiftY, String skip, CallbackInfo ci) {
        SmoothFontFaces.onProviderCreated(face);
    }

    @WrapOperation(method = "loadGlyph", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/freetype/FreeType;FT_Load_Glyph(Lorg/lwjgl/util/freetype/FT_Face;II)I", remap = false))
    private int alpaka$loadUnhinted(FT_Face face, int index, int flags, Operation<Integer> original) {
        return original.call(face, index, SmoothFontFaces.loadFlags(face, flags));
    }

    @WrapOperation(method = "close", at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/freetype/FreeType;FT_Done_Face(Lorg/lwjgl/util/freetype/FT_Face;)I", remap = false))
    private int alpaka$forgetFace(FT_Face face, Operation<Integer> original) {
        SmoothFontFaces.onFaceClosed(face);
        return original.call(face);
    }
}
