package net.alpaka.addons.mixin;

import net.alpaka.addons.features.mobdeath.HideMobDeathsFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Death smoke the server sends as plain particles instead of the entity event; see
 * {@link HideMobDeathsFeature}. After the thread check, like ClientPacketListenerMixin, so the level
 * is read on the client thread.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMobDeathMixin {

    @Inject(
            method = "handleParticleEvent",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                    shift = At.Shift.AFTER),
            cancellable = true
    )
    private void alpaka$hideDeathSmokePacket(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        var level = Minecraft.getInstance().level;
        if (level != null && HideMobDeathsFeature.shouldHideSmokePacket(packet, level)) {
            ci.cancel();
        }
    }
}
