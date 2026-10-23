package net.alpaka.addons.mixin;

import net.alpaka.addons.features.blaze.CleanBlazeFeature;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Drops the sky flash a lightning bolt causes while Clean Blaze hides lightning. The bolt itself is
 * hidden from {@link EntityRendererMixin}; the thunder still plays.
 */
@Mixin(LightningBolt.class)
public class LightningBoltMixin {

    // A condition rather than a redirect, so another mod's hook on the same call still runs.
    @WrapWithCondition(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setSkyFlashTime(I)V"
        )
    )
    private boolean alpaka$skipSkyFlash(Level level, int ticks) {
        return !CleanBlazeFeature.hideLightning();
    }
}
