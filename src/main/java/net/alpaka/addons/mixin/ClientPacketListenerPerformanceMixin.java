package net.alpaka.addons.mixin;

import net.alpaka.addons.features.performance.PerformanceStats;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reads the two packets the Performance HUD measures with, as they arrive; nothing is changed.
 *
 * Both hooks sit at the head, so they see the packet on the network thread, before it waits for the
 * next client tick: the arrival time is then not shifted by however long the current frame takes.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerPerformanceMixin {
    @Inject(method = "handleSetTime", at = @At("HEAD"))
    private void alpaka$countServerTicks(ClientboundSetTimePacket packet, CallbackInfo ci) {
        PerformanceStats.onTimeUpdate(packet, packet.gameTime());
    }

    @Inject(method = "handlePongResponse", at = @At("HEAD"))
    private void alpaka$measurePing(ClientboundPongResponsePacket packet, CallbackInfo ci) {
        PerformanceStats.onPong(packet.time());
    }
}
