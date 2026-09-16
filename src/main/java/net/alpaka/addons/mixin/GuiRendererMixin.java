package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderPass;
import net.alpaka.addons.features.chat.ChatBlurFeature;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two things at the GUI renderer.
 *
 * The blurred chat background needs the one moment when the frame holds the finished world and
 * not yet a single GUI element, with every element of the frame already extracted; the copy of
 * the frame the chat panel samples is taken there. See {@link ChatBlurFeature}.
 *
 * And scissor rectangles are clamped to the window. Vanilla only trims a scissor's right and bottom
 * edges to the window; a rectangle whose left or top edge lies off screen goes to the render pass
 * as is, and the render pass throws on it. A screen laid out wider than a small window - the
 * config menu in a windowed game - therefore crashed the game as soon as it clipped anything.
 */
@Mixin(GuiRenderer.class)
public class GuiRendererMixin {

    /**
     * Right before the elements are prepared: the panorama, which the renderer draws first of all
     * when a screen shows one, is in the frame by now, so a menu's glass blurs it too.
     */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;prepare()V"))
    private void alpaka$captureFrameForBlur(CallbackInfo ci) {
        ChatBlurFeature.captureFrame();
    }

    @WrapOperation(
        method = "enableScissor(Lnet/minecraft/client/gui/navigation/ScreenRectangle;Lcom/mojang/blaze3d/systems/RenderPass;)V",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;enableScissor(IIII)V")
    )
    private void alpaka$clampScissorToWindow(RenderPass pass, int x, int y, int width, int height, Operation<Void> original) {
        if (x < 0) {
            width += x;
            x = 0;
        }
        if (y < 0) {
            height += y;
            y = 0;
        }
        if (width <= 0 || height <= 0) {
            // Entirely off screen: the render pass refuses an empty scissor, so clip to a single
            // corner pixel instead, which is as close to drawing nothing as it allows.
            x = 0;
            y = 0;
            width = 1;
            height = 1;
        }
        original.call(pass, x, y, width, height);
    }
}
