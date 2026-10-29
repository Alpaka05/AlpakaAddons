package net.alpaka.addons.mixin;

import net.alpaka.addons.features.garden.PestFinder;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pest Finder reads the vacuum's particle trail as the packets arrive, and drops them when Hide
 * Trail Particles is on. After the thread check, like ClientPacketListenerMixin: the packet first
 * comes in on the network thread and is handed to the client thread from there.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerPestFinderMixin {

    @Inject(
            method = "handleParticleEvent",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                    shift = At.Shift.AFTER),
            cancellable = true
    )
    private void alpaka$pestFinderParticles(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (PestFinder.onParticles(packet)) {
            ci.cancel();
        }
    }
}
