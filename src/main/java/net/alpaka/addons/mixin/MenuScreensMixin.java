package net.alpaka.addons.mixin;

import net.alpaka.addons.features.market.MarketScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Opens the Auction House's and the Bazaar's chests in the market screen instead of the vanilla
 * chest screen. The chest itself is made exactly as vanilla makes it; only the screen showing it
 * differs. Every other menu is left to vanilla.
 */
@Mixin(MenuScreens.class)
public class MenuScreensMixin {
    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private static <T extends AbstractContainerMenu> void alpaka$openMarket(MenuType<T> type, Minecraft minecraft, int containerId,
                                                                          Component title, CallbackInfo ci) {
        if (MarketScreen.tryOpen(type, minecraft, containerId, title)) ci.cancel();
    }
}
