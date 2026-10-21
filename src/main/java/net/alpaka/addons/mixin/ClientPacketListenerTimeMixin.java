package net.alpaka.addons.mixin;

import net.alpaka.addons.features.worldage.WorldClockTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Time packets, after they are applied on the client thread, for the world age HUD. Only observed;
 * nothing is altered.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerTimeMixin {

    @Inject(method = "handleSetTime", at = @At("TAIL"))
    private void alpaka$observeTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            WorldClockTracker.onTimePacket(level, packet);
        }
    }
}
