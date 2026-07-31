package net.alpaka.addons.mixin;

import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.option.Perspective;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.alpaka.addons.config.AlpakaConfig;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Redirect(
        method = "renderHand(Lnet/minecraft/client/render/Camera;FLorg/joml/Matrix4f;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/option/Perspective;isFirstPerson()Z",
            ordinal = 0
        )
    )
    private boolean redirectIsFirstPerson(Perspective perspective) {
        if (AlpakaConfig.instance.renderHandInThirdPerson) {
            return true;
        }
        return perspective.isFirstPerson();
    }
}
