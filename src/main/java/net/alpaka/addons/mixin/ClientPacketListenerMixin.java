package net.alpaka.addons.mixin;

import net.alpaka.addons.features.ragaxe.RagAxeCountdownFeature;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The sounds the server plays, as they arrive. Read after the thread check: the packet first comes
 * in on the network thread, which hands it to the client thread and bails out there, so the head would see
 * every sound twice.
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
        RagAxeCountdownFeature.onSound(packet);
    }
}
