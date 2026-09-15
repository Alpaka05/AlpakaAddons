package net.alpaka.addons.mixin;

import net.alpaka.addons.features.chat.ChatBlurFeature;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The one moment the blurred chat background needs: the frame holds the finished world and not
 * yet a single GUI element, and every element of the frame has already been extracted. The copy of
 * the frame the chat panel samples is taken here. See {@link ChatBlurFeature}.
 */
@Mixin(GuiRenderer.class)
public class GuiRendererMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void alpaka$captureFrameForChatBlur(CallbackInfo ci) {
        ChatBlurFeature.captureFrame();
    }
}
