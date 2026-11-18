package net.alpaka.addons.mixin;

import net.alpaka.addons.features.blaze.CleanBlazeFeature;
import net.alpaka.addons.features.damagetags.DamageTagFeature;
import net.alpaka.addons.features.dungeonmobs.StarredMobsHighlight;
import net.alpaka.addons.features.fire.FireOverlayFeature;
import net.alpaka.addons.features.garden.PestFinder;
import net.alpaka.addons.features.clutter.HideVisualClutterFeature;
import net.alpaka.addons.features.mobdeath.HideMobDeathsFeature;
import net.alpaka.addons.features.playerscale.PlayerScaleFeature;
import net.alpaka.addons.features.slayer.SlayerBossHighlight;
import net.alpaka.addons.features.teammateglow.TeammateClassGlow;
import net.alpaka.addons.utils.AlpakaPerf;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    /**
     * Widens or narrows a scaled player's shadow with the body. RETURN on the base method: the
     * living-entity override calls up to here and then applies the entity's own attribute scale on
     * top, which is the right order for a cosmetic factor on the vanilla footprint.
     */
    @ModifyReturnValue(method = "getShadowRadius", at = @At("RETURN"))
    private float alpaka$scalePlayerShadow(float radius, S state) {
        // A return-value hook makes no callback object per entity and frame, nor a boxed Float.
        float factor = PlayerScaleFeature.shadowFactor(state);
        return factor != 1.0f ? radius * factor : radius;
    }

    // The rule hooks below run per entity and frame, so they are only counted; timing each would
    // cost more than most of what it timed. The entity phase is timed as a whole in
    // LevelExtractorMixin.

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(T entity, S state, float partialTick, CallbackInfo ci) {
        AlpakaPerf.count(AlpakaPerf.Section.ENTITY_RULES);
        CleanBlazeFeature.shouldHideEntityFire(entity, state);
        FireOverlayFeature.applyEntity(state);
        TeammateClassGlow.applyOutline(entity, state);
        SlayerBossHighlight.applyOutline(entity, state);
        StarredMobsHighlight.applyOutline(entity, state);
        PestFinder.applyOutline(entity, state);
    }

    /**
     * On vanilla's answer rather than at HEAD: an entity that is out of sight or range anyway (most
     * of them) skips the rules, and no callback object is made per entity and frame. The rules only
     * read the entity and fill their own caches, so asking them after culling hides the same ones.
     */
    @ModifyReturnValue(method = "shouldRender", at = @At("RETURN"))
    private boolean filterBlazeEntities(boolean visible, T entity, Frustum frustum, double x, double y, double z) {
        if (!visible) return false;
        AlpakaPerf.count(AlpakaPerf.Section.ENTITY_RULES);
        return !(CleanBlazeFeature.shouldHideEntity(entity) || DamageTagFeature.shouldHideEntity(entity)
                || HideMobDeathsFeature.shouldHideEntity(entity) || StarredMobsHighlight.shouldHideEntity(entity)
                || HideVisualClutterFeature.shouldHideEntity(entity));
    }
}
