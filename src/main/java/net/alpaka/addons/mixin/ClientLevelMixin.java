package net.alpaka.addons.mixin;

import net.alpaka.addons.features.blaze.FirePitFeature;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Block changes the server sends, single and per section alike, as they arrive and before they are
 * applied, so the block they replace can still be read. Only observed; nothing is altered.
 */
@Mixin(ClientLevel.class)
public class ClientLevelMixin {

    @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"))
    private void alpaka$observeServerBlockChange(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        FirePitFeature.onServerBlockChange((ClientLevel) (Object) this, pos, state);
    }
}
