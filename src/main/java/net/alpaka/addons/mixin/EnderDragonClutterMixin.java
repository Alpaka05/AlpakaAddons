package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.alpaka.addons.features.clutter.HideVisualClutterFeature;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The explosions around a dying Ender Dragon, which the client spawns itself in the dragon's last
 * second; the dragon and its rays are skipped in EntityRendererMixin. See Hide Visual Clutter.
 */
@Mixin(EnderDragon.class)
public class EnderDragonClutterMixin {

    @WrapWithCondition(
            method = "tickDeath",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V")
    )
    private boolean alpaka$hideDeathExplosions(Level level, ParticleOptions options, double x, double y, double z, double xd, double yd, double zd) {
        return !(level.isClientSide() && HideVisualClutterFeature.shouldHideDragonDeathParticles());
    }
}
