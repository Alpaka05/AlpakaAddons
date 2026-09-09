package net.alpaka.addons.mixin;

import net.alpaka.addons.features.blaze.CleanBlazeFeature;
import net.alpaka.addons.features.critters.PangolinHighlightFeature;
import net.alpaka.addons.features.damagetags.DamageTagFeature;
import net.alpaka.addons.features.mobdeath.HideMobDeathsFeature;
import net.alpaka.addons.features.playerscale.PlayerScaleFeature;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    /**
     * Widens or narrows a scaled player's shadow with the body. RETURN on the base method: the
     * living-entity override calls up to here and then applies the entity's own attribute scale on
     * top, which is the right order for a cosmetic factor on the vanilla footprint.
     */
    @Inject(method = "getShadowRadius", at = @At("RETURN"), cancellable = true)
    private void alpaka$scalePlayerShadow(S state, CallbackInfoReturnable<Float> cir) {
        float factor = PlayerScaleFeature.shadowFactor(state);
        if (factor != 1.0f) {
            cir.setReturnValue(cir.getReturnValueF() * factor);
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(T entity, S state, float partialTick, CallbackInfo ci) {
        CleanBlazeFeature.shouldHideEntityFire(state);
        // TAIL, so this lands after vanilla has decided outlineColor from the glowing effect, and
        // still before LevelRenderer reads appearsGlowing() to build the outline pass.
        PangolinHighlightFeature.applyOutline(entity, state);
    }

    @Inject(method = "shouldShowName", at = @At("HEAD"), cancellable = true)
    private void hideBlazeNameTags(T entity, double distance, CallbackInfoReturnable<Boolean> info) {
        if (CleanBlazeFeature.shouldHideNameTag(entity) || DamageTagFeature.shouldHideNameTag(entity)) {
            info.setReturnValue(false);
        }
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void filterBlazeEntities(T entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> info) {
        if (CleanBlazeFeature.shouldHideEntity(entity) || DamageTagFeature.shouldHideEntity(entity)
                || HideMobDeathsFeature.shouldHideEntity(entity)) {
            info.setReturnValue(false);
        }
    }
}
