package net.alpaka.addons.mixin;

import net.alpaka.addons.features.blaze.CleanBlazeFeature;
import net.alpaka.addons.features.clutter.HideVisualClutterFeature;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void filterBlazeParticles(ParticleOptions options, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, CallbackInfoReturnable<Particle> info) {
        if (CleanBlazeFeature.shouldCancelParticle(options)) {
            info.setReturnValue(null);
        }
    }

    // Hide Visual Clutter's hit sparks: the attack animation packet and the player's own crits both
    // come through here, and nothing else makes crit or enchanted hit emitters.
    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V",
            at = @At("HEAD"), cancellable = true)
    private void alpaka$hideHitSparks(Entity entity, ParticleOptions options, CallbackInfo ci) {
        if (HideVisualClutterFeature.shouldHideTrackingEmitter(options)) ci.cancel();
    }

    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V",
            at = @At("HEAD"), cancellable = true)
    private void alpaka$hideHitSparksTimed(Entity entity, ParticleOptions options, int lifetime, CallbackInfo ci) {
        if (HideVisualClutterFeature.shouldHideTrackingEmitter(options)) ci.cancel();
    }
}
