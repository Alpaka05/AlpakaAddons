package net.alpaka.addons.mixin;

import net.alpaka.addons.features.garden.FarmingTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Counts the blocks the client breaks, for the Farming HUD's blocks per second. Only reads that a
 * break happened; the call and its result are left alone and nothing is sent.
 */
@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeFarmingMixin {

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void alpaka$afterDestroyBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) return;
        var player = Minecraft.getInstance().player;
        if (player != null) FarmingTracker.onBlockBroken(player.getMainHandItem());
    }
}
