package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.alpaka.addons.features.chat.ChatBlurFeature;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.CameraType;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import net.alpaka.addons.config.AlpakaConfig;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    /**
     * Whether the hand is drawn. Wrapped rather than redirected, so a camera mod that hooks the same
     * check still gets its say.
     */
    @WrapOperation(
        method = "renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z",
            ordinal = 0
        )
    )
    private boolean alpaka$handInThirdPerson(CameraType cameraType, Operation<Boolean> original) {
        // Gliding back into first person: the camera is still behind the player for a moment.
        if (net.alpaka.addons.features.perspective.SmoothPerspectiveFeature.hidesHand()) {
            return false;
        }
        // The back view only. In the front view the hand would float in front of your own face.
        if (AlpakaConfig.instance.renderHandInThirdPerson && cameraType == CameraType.THIRD_PERSON_BACK) {
            return true;
        }
        return original.call(cameraType);
    }

    /**
     * The frame's own GUI pass, onto the world - as opposed to a HUD-caching mod running the GUI
     * renderer into a framebuffer of its own. Only this pass takes and samples the blurred copy;
     * see {@link ChatBlurFeature}.
     */
    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V")
    )
    private void alpaka$markFrameGuiPass(GuiRenderer guiRenderer, Operation<Void> original) {
        ChatBlurFeature.setInFramePass(true);
        try {
            original.call(guiRenderer);
        } finally {
            ChatBlurFeature.setInFramePass(false);
        }
    }
}
