package net.alpaka.addons.mixin;

import net.alpaka.addons.features.clutter.HideVisualClutterFeature;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Falling blocks and experience orbs that Hide Visual Clutter skips are never added; see
 * {@link HideVisualClutterFeature#shouldSkipEntity}. After the thread check, like the other packet
 * hooks, so the packet is only looked at once, on the client thread. Later packets for the skipped
 * entity id find no entity, which vanilla already ignores.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerClutterMixin {

    @Inject(
            method = "handleAddEntity",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                    shift = At.Shift.AFTER),
            cancellable = true
    )
    private void alpaka$skipClutterEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        if (HideVisualClutterFeature.shouldSkipEntity(packet.getType())) {
            ci.cancel();
        }
    }
}
