package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.alpaka.addons.features.fire.FireOverlayFeature;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The first-person fire overlay: see {@link FireOverlayFeature}. */
@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {

    @WrapWithCondition(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private boolean alpaka$hideFirstPersonFire(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite) {
        return FireOverlayFeature.showFirstPersonFire();
    }

    /** Both fire sprites go through buildFireQuad, whose last argument is the ARGB tint. */
    @ModifyArg(method = "buildFireQuad", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;buildSpriteQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;FFFFFI)V"),
            index = 8)
    private static int alpaka$fadeFirstPersonFire(int argb) {
        return FireOverlayFeature.firstPersonFireColor(argb);
    }
}
