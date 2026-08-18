package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fullbright, implemented by pinning the lightmap's night-vision inputs to full strength.
 */
@Mixin(Lightmap.class)
public class LightmapMixin {

    /**
     * How long after the setting is toggled we keep forcing lightmap rebuilds.
     *
     * Long enough to span a client tick, which is when the vanilla extractor next refreshes the
     * render state with the game's real values - so the last rebuild in this window is guaranteed
     * to be built from correct data rather than from the values this mixin had been writing.
     */
    @Unique
    private static final long ALPAKA_FORCE_WINDOW_MS = 250L;

    /**
     * Static rather than per-instance: the previous state has to survive the Lightmap being
     * recreated, otherwise a toggle made across that boundary is never noticed.
     */
    @Unique
    private static boolean alpaka$wasEnabled = false;

    @Unique
    private static long alpaka$forceUpdateUntilMs = 0L;

    @Inject(method = "render", at = @At("HEAD"))
    private void onRender(LightmapRenderState state, CallbackInfo ci) {
        boolean enabled = AlpakaConfig.instance.fullbrightEnabled;

        if (enabled != alpaka$wasEnabled) {
            alpaka$wasEnabled = enabled;
            alpaka$forceUpdateUntilMs = System.currentTimeMillis() + ALPAKA_FORCE_WINDOW_MS;
        }

        if (enabled) {
            state.nightVisionEffectIntensity = 1.0f;
            // Vanilla's own constant, so there is nothing to allocate per frame and no mixin
            // static initialiser to depend on.
            state.nightVisionColor = LightmapRenderStateExtractor.WHITE;
        }

        // render() returns immediately unless needsUpdate is set, and vanilla only sets it once per
        // tick. Forcing it while fullbright is on - and for a short window after it is switched off,
        // so the restored values actually get uploaded - is what makes both directions take effect
        // straight away instead of lingering until something else invalidates the lightmap.
        if (enabled || System.currentTimeMillis() < alpaka$forceUpdateUntilMs) {
            state.needsUpdate = true;
        }
    }
}
