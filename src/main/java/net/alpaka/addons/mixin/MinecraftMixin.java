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
}
