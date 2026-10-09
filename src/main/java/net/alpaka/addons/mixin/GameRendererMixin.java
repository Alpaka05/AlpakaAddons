package net.alpaka.addons.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.CameraType;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.alpaka.addons.config.AlpakaConfig;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Redirect(
        method = "renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/CameraType;isFirstPerson()Z",
            ordinal = 0
        )
    )
    private boolean redirectIsFirstPerson(CameraType cameraType) {
        // Gliding back into first person: the camera is still behind the player for a moment.
        if (net.alpaka.addons.features.perspective.SmoothPerspectiveFeature.hidesHand()) {
            return false;
        }
        // The back view only. In the front view the hand would float in front of your own face.
        if (AlpakaConfig.instance.renderHandInThirdPerson && cameraType == CameraType.THIRD_PERSON_BACK) {
            return true;
        }
        return cameraType.isFirstPerson();
    }
}

