package net.alpaka.addons.mixin;

import net.alpaka.addons.features.nametag.CustomNameTagFeature;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks the span in which the world - as opposed to a GUI preview - extracts an entity's render
 * state. See {@link CustomNameTagFeature#getExtractingWorldEntity()} for why that distinction matters.
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {

    @Inject(method = "extractEntity", at = @At("HEAD"))
    private void alpaka$beginWorldExtraction(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        CustomNameTagFeature.setExtractingWorldEntity(true);
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void alpaka$endWorldExtraction(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
        CustomNameTagFeature.setExtractingWorldEntity(false);
    }
}
