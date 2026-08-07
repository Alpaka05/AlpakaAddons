package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.escapemenu.CustomPauseScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow public abstract void setScreen(Screen guiScreen);

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void replacePauseScreen(Screen screen, CallbackInfo ci) {
        if (screen != null && screen.getClass() == PauseScreen.class && AlpakaConfig.instance.customEscapeMenuEnabled) {
            ci.cancel();
            this.setScreen(new CustomPauseScreen());
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        Minecraft mc = (Minecraft)(Object)this;
        Screen oldScreen = mc.screen;
        
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
