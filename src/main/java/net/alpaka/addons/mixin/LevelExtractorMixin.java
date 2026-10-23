package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.alpaka.addons.utils.AlpakaPerf;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Times the frame's entity phase as a whole for {@code /alpakadebug perf}: vanilla's entity
 * extraction with every per-entity rule hook of the mod in it, once per frame instead of once per
 * entity and hook. Only a measurement, so the game starts without it should the method change.
 */
@Mixin(LevelExtractor.class)
public class LevelExtractorMixin {

    @WrapMethod(method = "extractVisibleEntities", require = 0)
    private void alpaka$timeEntityPhase(Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState state,
                                        Operation<Void> original) {
        long t = AlpakaPerf.begin(AlpakaPerf.Section.ENTITY_PHASE);
        try {
            original.call(camera, frustum, deltaTracker, state);
        } finally {
            AlpakaPerf.end(AlpakaPerf.Section.ENTITY_PHASE, t);
        }
    }
}
