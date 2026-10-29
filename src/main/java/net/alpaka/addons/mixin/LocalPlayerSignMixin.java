package net.alpaka.addons.mixin;

import net.alpaka.addons.features.market.MarketInputScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opens Hypixel's sign prompts - a search, a price, an amount - in the market input bar instead of
 * the sign editor. The bar is the sign editor underneath and sends what it does; signs that are not
 * such a prompt open as usual.
 */
@Mixin(LocalPlayer.class)
public class LocalPlayerSignMixin {
    @Inject(method = "openTextEdit", at = @At("HEAD"), cancellable = true)
    private void alpaka$openMarketInput(SignBlockEntity sign, boolean isFrontText, CallbackInfo ci) {
        if (MarketInputScreen.tryOpen(sign, isFrontText)) ci.cancel();
    }
}
