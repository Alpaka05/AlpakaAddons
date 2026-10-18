package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.alpaka.addons.features.fire.FireOverlayFeature;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The first-person fire overlay: see {@link FireOverlayFeature}. */
@Mixin(ScreenEffectRenderer.class)
public class ScreenEffectRendererMixin {

    @Shadow
    private static void buildQuad(VertexConsumer builder, Matrix4f pose, float x0, float y0, float x1, float y1, float z,
                                  float u0, float v0, float u1, float v1, int color) {
    }

    @WrapWithCondition(method = "submit", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submitFire(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
    private boolean alpaka$hideFirstPersonFire(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite) {
        return FireOverlayFeature.showFirstPersonFire();
    }

    /** The still fire reads the texture file instead of the animated block atlas. */
    @WrapOperation(method = "submitFire", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/rendertype/RenderTypes;fireScreenEffect(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/rendertype/RenderType;"))
    private static RenderType alpaka$stillFireTexture(Identifier atlas, Operation<RenderType> original,
                                                     PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite) {
        return original.call(FireOverlayFeature.staticFirstPersonFire() ? FireOverlayFeature.spriteTexture(sprite) : atlas);
    }

    /** Both fire quads are moved by the same translate before they are built; this lowers them. */
    @ModifyArg(method = "lambda$submitFire$0", at = @At(value = "INVOKE",
            target = "Lorg/joml/Matrix4f;translate(FFF)Lorg/joml/Matrix4f;"), index = 1)
    private static float alpaka$lowerFirstPersonFire(float y) {
        return FireOverlayFeature.firstPersonFireY(y);
    }

    /**
     * Builds each fire quad with the fade applied and, for the still fire, the texture file's first
     * frame in place of the atlas sprite's coordinates. The atlas sprite is drawn mirrored (U1 to
     * U0, V1 to V0), and so is the frame.
     */
    @WrapOperation(method = "buildFireQuad", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;buildSpriteQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;FFFFFI)V"))
    private static void alpaka$buildFireQuad(VertexConsumer builder, Matrix4f pose, TextureAtlasSprite sprite,
                                             float x0, float y0, float x1, float y1, float z, int color,
                                             Operation<Void> original) {
        int faded = FireOverlayFeature.firstPersonFireColor(color);
        if (FireOverlayFeature.staticFirstPersonFire()) {
            buildQuad(builder, pose, x0, y0, x1, y1, z, 1.0f, FireOverlayFeature.firstFrameV(sprite), 0.0f, 0.0f, faded);
        } else {
            original.call(builder, pose, sprite, x0, y0, x1, y1, z, faded);
        }
    }
}
