package net.alpaka.addons.mixin;

import net.alpaka.addons.features.sky.SkyEffectsFeature;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The fog in open air on SkyBlock's End island while Sky Effects gives it the End's sky: the End's
 * own dark violet rather than the Overworld's sky blue, which would make the far edges of the
 * island glow against the nebula. Only the air's color; water, lava and blindness keep theirs.
 */
@Mixin(AtmosphericFogEnvironment.class)
public class AtmosphericFogEnvironmentMixin {

    @Inject(method = "getBaseColor", at = @At("RETURN"), cancellable = true)
    private void alpaka$endIslandFog(ClientLevel level, Camera camera, int renderDistance, float partialTick,
                                     CallbackInfoReturnable<Integer> cir) {
        if (SkyEffectsFeature.endSkyOnEndIsland(level)) cir.setReturnValue(SkyEffectsFeature.END_FOG_COLOR);
    }
}
