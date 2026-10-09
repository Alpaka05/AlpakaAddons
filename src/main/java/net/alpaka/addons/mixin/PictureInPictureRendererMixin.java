package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.alpaka.addons.client.gui.GuiSkinStyle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix3x2fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * A styled skin preview is blitted in its tint and at its sub-pixel place; see {@link GuiSkinStyle}.
 * Every other picture-in-picture is left as vanilla draws it.
 */
@Mixin(PictureInPictureRenderer.class)
public class PictureInPictureRendererMixin {

    private static final String BLIT_STATE =
            "Lnet/minecraft/client/renderer/state/gui/BlitRenderState;<init>(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/client/gui/render/TextureSetup;Lorg/joml/Matrix3x2fc;IIIIFFFFILnet/minecraft/client/gui/navigation/ScreenRectangle;Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V";

    @ModifyArg(method = "blitTexture", at = @At(value = "INVOKE", target = BLIT_STATE), index = 2)
    private Matrix3x2fc alpaka$placeStyledPreview(Matrix3x2fc pose, @Local(argsOnly = true) PictureInPictureRenderState state) {
        return GuiSkinStyle.pose(state, pose);
    }

    @ModifyArg(method = "blitTexture", at = @At(value = "INVOKE", target = BLIT_STATE), index = 11)
    private int alpaka$tintStyledPreview(int color, @Local(argsOnly = true) PictureInPictureRenderState state) {
        return GuiSkinStyle.color(state, color);
    }
}
