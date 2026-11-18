package net.alpaka.addons.mixin;

import net.alpaka.addons.features.clutter.HideVisualClutterFeature;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Block break particles and firework bursts for Hide Visual Clutter; see
 * {@link HideVisualClutterFeature}. Skipped before vanilla looks up the block's shape or builds the
 * burst, so a hidden one costs nothing but the check.
 */
@Mixin(ClientLevel.class)
public class ClientLevelClutterMixin {

    @Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
    private void alpaka$hideDestroyParticles(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (HideVisualClutterFeature.shouldHideBlockParticles()) ci.cancel();
    }

    @Inject(method = "addBreakingBlockEffect", at = @At("HEAD"), cancellable = true)
    private void alpaka$hideBreakingParticles(BlockPos pos, Direction direction, CallbackInfo ci) {
        if (HideVisualClutterFeature.shouldHideBlockParticles()) ci.cancel();
    }

    @Inject(method = "createFireworks", at = @At("HEAD"), cancellable = true)
    private void alpaka$hideFireworkBurst(double x, double y, double z, double xd, double yd, double zd,
                                          List<FireworkExplosion> explosions, CallbackInfo ci) {
        if (HideVisualClutterFeature.shouldHideFireworkBurst()) ci.cancel();
    }
}
