package net.alpaka.addons.mixin;

import net.alpaka.addons.features.dungeon.DungeonSolvers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Teleports the server makes, for the dungeon solvers (the Teleport Maze reads where each pad sent
 * the player). Read at the return: the packet first arrives on the network thread, which hands it
 * to the client thread and bails out, so only the return runs once - on the client thread, with the
 * new position and rotation already applied. Only observed; vanilla answers the teleport as always.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerTeleportMixin {

    @Inject(method = "handleMovePlayer", at = @At("RETURN"))
    private void alpaka$afterServerTeleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        DungeonSolvers.onServerTeleport(player.position(), player.getYRot(), player.getXRot());
    }
}
