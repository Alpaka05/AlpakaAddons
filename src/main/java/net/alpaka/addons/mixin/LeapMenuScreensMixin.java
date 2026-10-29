package net.alpaka.addons.mixin;

import net.alpaka.addons.features.leapmenu.LeapMenu;
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
 * Opens Hypixel's Spirit Leap chest in the Leap Menu instead of the vanilla chest screen. Every other
 * menu is left to vanilla. Only the look of that one menu depends on this hook.
 */
@Mixin(MenuScreens.class)
public class LeapMenuScreensMixin {
    @Inject(method = "create", at = @At("HEAD"), cancellable = true)
    private static <T extends AbstractContainerMenu> void alpaka$openLeapMenu(MenuType<T> type, Minecraft minecraft, int containerId,
                                                                            Component title, CallbackInfo ci) {
        if (LeapMenu.tryOpen(type, minecraft, containerId, title)) ci.cancel();
    }
}
