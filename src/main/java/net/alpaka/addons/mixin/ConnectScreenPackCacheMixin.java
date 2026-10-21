package net.alpaka.addons.mixin;

import net.alpaka.addons.features.resourcepack.ServerPackCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells Cache Resource Pack which server a connection is for; see ServerPackCache.onConnecting. */
@Mixin(ConnectScreen.class)
public class ConnectScreenPackCacheMixin {
    @Inject(method = "connect", at = @At("HEAD"))
    private void alpaka$noteServer(Minecraft minecraft, ServerAddress address, ServerData server,
                                   @Nullable TransferState transferState, CallbackInfo ci) {
        ServerPackCache.onConnecting(server);
    }
}
