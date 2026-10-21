package net.alpaka.addons.mixin;

import net.alpaka.addons.features.resourcepack.ServerPackCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Joining a singleplayer world. Vanilla only touches the server packs there when the world has a
 * pack of its own, so without this Hypixel's pack kept by Cache Resource Pack would show in it.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerPackCacheMixin {
    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void alpaka$unloadKeptPacksInSingleplayer(ClientboundLoginPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isLocalServer() && ServerPackCache.onLocalWorld()) {
            Minecraft.getInstance().getDownloadedPackSource().popAll();
        }
    }
}
