package net.alpaka.addons.mixin;

import net.alpaka.addons.features.blaze.CleanBlazeFeature;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Drops the sky flash a lightning bolt causes while Clean Blaze hides lightning. The bolt itself is
 * hidden from {@link EntityRendererMixin}; the thunder still plays.
 */
@Mixin(LightningBolt.class)
public class LightningBoltMixin {

    @Redirect(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setSkyFlashTime(I)V"
        )
    )
    private void alpaka$skipSkyFlash(Level level, int ticks) {
        if (!CleanBlazeFeature.hideLightning()) {
            level.setSkyFlashTime(ticks);
        }
    }
}
