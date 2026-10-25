package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.alpaka.addons.features.explosion.HideExplosionsFeature;
import net.alpaka.addons.features.ragnarock.RagnarockFeature;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The sounds the server plays, as they arrive. Read after the thread check: the packet first comes
 * in on the network thread, which hands it to the client thread and bails out there, so the head would see
 * every sound twice.
 *
 * Also where Hide Explosions drops explosion particles and explode packets, see
 * {@link HideExplosionsFeature}.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(
            method = "handleSoundEvent",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                    shift = At.Shift.AFTER)
    )
    private void alpaka$onSound(ClientboundSoundPacket packet, CallbackInfo ci) {
        RagnarockFeature.onSound(packet);
    }

    // After the thread check for the same reason as above; dropping the packet here skips the whole
    // spawn loop, not just the particles at its end.
    @Inject(
            method = "handleParticleEvent",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                    shift = At.Shift.AFTER),
            cancellable = true
    )
    private void alpaka$hideExplosionParticles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (HideExplosionsFeature.shouldHideParticlePacket(packet.getParticle())) {
            ci.cancel();
        }
    }

    @WrapWithCondition(
            method = "handleExplosion",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V")
    )
    private boolean alpaka$hideExplosionFlash(ClientLevel level, ParticleOptions options, double x, double y, double z, double xd, double yd, double zd) {
        return !HideExplosionsFeature.shouldHideExplodePacket();
    }

    @WrapWithCondition(
            method = "handleExplosion",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;trackExplosionEffects(Lnet/minecraft/world/phys/Vec3;FILnet/minecraft/util/random/WeightedList;)V")
    )
    private boolean alpaka$hideExplosionDebris(ClientLevel level, Vec3 center, float radius, int blockCount, WeightedList<ExplosionParticleInfo> blockParticles) {
        return !HideExplosionsFeature.shouldHideExplodePacket();
    }
}
