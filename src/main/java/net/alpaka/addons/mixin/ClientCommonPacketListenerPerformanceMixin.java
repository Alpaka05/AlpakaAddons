package net.alpaka.addons.mixin;

import net.alpaka.addons.features.performance.PerformanceStats;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts the pings Hypixel sends every server tick, for the Performance HUD's TPS; vanilla still
 * answers each one as before. At the head, so the arrival is timed on the network thread.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientCommonPacketListenerPerformanceMixin {
    @Inject(method = "handlePing", at = @At("HEAD"))
    private void alpaka$countServerTick(ClientboundPingPacket packet, CallbackInfo ci) {
        PerformanceStats.onServerPing(packet);
    }
}
