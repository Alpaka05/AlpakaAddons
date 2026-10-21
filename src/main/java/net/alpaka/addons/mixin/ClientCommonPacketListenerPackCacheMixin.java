package net.alpaka.addons.mixin;

import net.alpaka.addons.features.resourcepack.ServerPackCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URI;
import java.net.URL;

/**
 * Skips the "this server requires a resource pack" prompt for a pack Cache Resource Pack already
 * has loaded: it was accepted when it was first loaded. The prompt otherwise comes back on every
 * join through the main menu's Join Hypixel button, whose server entry is new each time and so is
 * always set to prompt.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientCommonPacketListenerPackCacheMixin {
    @Inject(method = "handleResourcePackPush", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V"))
    private void alpaka$skipPromptForLoadedPack(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        if (!ServerPackCache.isAlreadyLoaded(packet.id(), packet.hash())) return;
        URL url;
        try {
            url = URI.create(packet.url()).toURL();
        } catch (Exception e) {
            return;
        }
        // Goes the usual way from here: DownloadedPackSourceMixin answers it as applied.
        Minecraft.getInstance().getDownloadedPackSource().pushPack(packet.id(), url, packet.hash());
        ci.cancel();
    }
}
