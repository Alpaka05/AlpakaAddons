package net.alpaka.addons.mixin;

import net.alpaka.addons.features.inventoryhud.InventoryHudRenderer;
import net.alpaka.addons.features.notification.AlpakaNotifications;
import net.alpaka.addons.features.playermodel.PlayerModelRenderer;
import net.alpaka.addons.features.slayer.SlayerHudRenderer;
import net.alpaka.addons.features.slayer.SlayerTimerHudRenderer;
import net.alpaka.addons.features.worldage.WorldAgeHudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the mod's HUD overlays after vanilla's.
 *
 * 26.2 split the old Gui class in two: {@link Hud} is the in-game overlay (hotbar, chat, scoreboard)
 * that used to be Gui, while Gui itself now only manages the current screen. The HUD is extracted
 * whether or not a screen is open, which is what lets the overlays stay visible behind the chat.
 */
@Mixin(Hud.class)
public class HudMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor graphicsExtractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        PlayerModelRenderer.render(graphicsExtractor, deltaTracker);
        WorldAgeHudRenderer.render(graphicsExtractor, deltaTracker);
        SlayerHudRenderer.render(graphicsExtractor, deltaTracker);
        SlayerTimerHudRenderer.render(graphicsExtractor, deltaTracker);
        InventoryHudRenderer.render(graphicsExtractor, deltaTracker);
        // Last, so a notice sits above every other overlay rather than under one.
        AlpakaNotifications.render(graphicsExtractor, deltaTracker);
    }
}
