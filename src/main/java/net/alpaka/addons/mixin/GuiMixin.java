package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.escapemenu.CustomPauseScreen;
import net.alpaka.addons.features.guifade.GuiFadeTracker;
import net.alpaka.addons.features.mainmenu.CustomMainMenuScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Swaps the vanilla pause and title screens for the mod's own, and watches screens open and close.
 *
 * Since 26.2 the current screen lives on {@link Gui} rather than on {@link Minecraft}: Gui.setScreen
 * is the single place every screen change passes through, including the pause screen opened by
 * Gui.setPauseScreen and the title screen substituted for {@code null} while no level is loaded.
 */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Shadow private Screen screen;
    @Shadow public abstract void setScreen(Screen screen);

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void replacePauseScreen(Screen screen, CallbackInfo ci) {
        if (screen != null && screen.getClass() == PauseScreen.class && AlpakaConfig.instance.customEscapeMenuEnabled) {
            ci.cancel();
            this.setScreen(new CustomPauseScreen());
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void replaceTitleScreen(Screen screen, CallbackInfo ci) {
        if (!AlpakaConfig.instance.customMainMenuEnabled) return;
        if (Minecraft.getInstance().level == null) {
            if (screen == null || screen instanceof TitleScreen) {
                if (!(this.screen instanceof CustomMainMenuScreen) || screen instanceof TitleScreen) {
                    ci.cancel();
                    this.setScreen(new CustomMainMenuScreen());
                }
            }
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        Screen oldScreen = this.screen;

        if (oldScreen == null && screen != null) {
            GuiFadeTracker.onGuiOpened();
        } else if (screen == null) {
            GuiFadeTracker.onGuiClosed();
        }

        boolean isOldInventory = oldScreen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
            || oldScreen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
        boolean isNewInventory = screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
            || screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;

        if (isOldInventory && !isNewInventory) {
            net.alpaka.addons.features.sound.CustomSoundFeature.playInventoryCloseSound();
        } else if (!isOldInventory && isNewInventory) {
            net.alpaka.addons.features.sound.CustomSoundFeature.playInventoryOpenSound();
        }
    }
}
