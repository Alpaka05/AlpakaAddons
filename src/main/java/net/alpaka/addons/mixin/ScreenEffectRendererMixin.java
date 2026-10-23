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

    /**
     * Builds each fire quad lowered, with the fade applied and, for the still fire, the texture
     * file's first frame in place of the atlas sprite's coordinates. The atlas sprite is drawn
     * mirrored (U1 to U0, V1 to V0), and so is the frame.
     *
     * The drop goes onto the quad's pose here rather than into the translate vanilla places each
     * quad with: that sits in a lambda, whose generated name changes between Minecraft versions.
     * The pose is only turned about its vertical axis after that translate, and a move along the
     * vertical axis gives the same result before or after such a turn.
     */
    @WrapOperation(method = "buildFireQuad", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;buildSpriteQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;FFFFFI)V"))
    private static void alpaka$buildFireQuad(VertexConsumer builder, Matrix4f pose, TextureAtlasSprite sprite,
                                             float x0, float y0, float x1, float y1, float z, int color,
                                             Operation<Void> original) {
        float drop = FireOverlayFeature.firstPersonFireDrop();
        Matrix4f placed = drop != 0.0f ? new Matrix4f(pose).translate(0.0f, drop, 0.0f) : pose;
        int faded = FireOverlayFeature.firstPersonFireColor(color);
        if (FireOverlayFeature.staticFirstPersonFire()) {
            buildQuad(builder, placed, x0, y0, x1, y1, z, 1.0f, FireOverlayFeature.firstFrameV(sprite), 0.0f, 0.0f, faded);
        } else {
            original.call(builder, placed, sprite, x0, y0, x1, y1, z, faded);
        }
    }
}
