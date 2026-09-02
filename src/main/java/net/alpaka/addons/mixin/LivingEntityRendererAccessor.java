package net.alpaka.addons.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches LivingEntityRenderer's protected addLayer from outside its hierarchy.
 *
 * Exists because a {@code @Shadow} must name a member declared on the mixin's own target class, and
 * {@code layers} / {@code addLayer} live on LivingEntityRenderer, not on the AvatarRenderer subclass
 * that AvatarRendererMixin hooks. Shadowing them from there failed at apply time and took the whole
 * player renderer down with it - which showed up as a black screen at startup.
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor {

    @SuppressWarnings("rawtypes")
    @Invoker("addLayer")
    boolean alpaka$addLayer(RenderLayer layer);
}
