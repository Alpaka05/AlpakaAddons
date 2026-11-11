package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.alpaka.addons.features.clutter.HideVisualClutterFeature;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The crit trail of fully drawn arrows, for Hide Visual Clutter's Hit Particles. */
@Mixin(AbstractArrow.class)
public class AbstractArrowClutterMixin {

    @WrapWithCondition(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V")
    )
    private boolean alpaka$hideCritTrail(Level level, ParticleOptions options, double x, double y, double z, double xd, double yd, double zd) {
        return !(level.isClientSide() && HideVisualClutterFeature.shouldHideArrowTrail());
    }
}
