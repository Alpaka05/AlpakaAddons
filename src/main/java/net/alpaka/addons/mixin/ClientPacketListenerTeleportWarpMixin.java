package net.alpaka.addons.mixin;

import net.alpaka.addons.features.teleportwarp.TeleportWarpFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where the player stood before and after a server teleport, for Teleport Effects. The position is
 * read just before vanilla applies the packet - past its hand-off from the network thread, so on
 * the client thread only - and again at the return. Only observed; vanilla applies and answers the
 * teleport as always.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerTeleportWarpMixin {

    @Unique
    private Vec3 alpaka$warpFrom;

    @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z"))
    private void alpaka$beforeTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        alpaka$warpFrom = player == null ? null : player.position();
    }

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void alpaka$afterTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        Vec3 from = alpaka$warpFrom;
        alpaka$warpFrom = null;
        LocalPlayer player = Minecraft.getInstance().player;
        if (from == null || player == null) return;
        TeleportWarpFeature.onServerTeleport(from, player.position());
    }
}
