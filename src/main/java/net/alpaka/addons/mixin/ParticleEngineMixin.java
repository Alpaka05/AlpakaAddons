package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void filterBlazeParticles(ParticleOptions options, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfoReturnable<Particle> info) {
        if (!AlpakaConfig.instance.cleanBlazeEnabled) return;

        ParticleType<?> type = options.getType();
        if (type == ParticleTypes.FLAME || type == ParticleTypes.SMOKE || type == ParticleTypes.LARGE_SMOKE || type == ParticleTypes.SMALL_FLAME) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                AABB box = new AABB(x - 5.0, y - 5.0, z - 5.0, x + 5.0, y + 5.0, z + 5.0);
                if (!level.getEntitiesOfClass(Blaze.class, box).isEmpty() || !level.getEntitiesOfClass(SmallFireball.class, box).isEmpty()) {
                    info.setReturnValue(null);
                }
            }
        }
    }
}
