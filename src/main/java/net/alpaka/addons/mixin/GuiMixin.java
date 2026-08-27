package net.alpaka.addons.mixin;

import net.alpaka.addons.features.inventoryhud.InventoryHudRenderer;
import net.alpaka.addons.features.playermodel.PlayerModelRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.alpaka.addons.features.slayer.SlayerHudRenderer;
import net.alpaka.addons.features.slayer.SlayerTimerHudRenderer;
import net.alpaka.addons.features.worldage.WorldAgeHudRenderer;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor graphicsExtractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        PlayerModelRenderer.render(graphicsExtractor, deltaTracker);
        WorldAgeHudRenderer.render(graphicsExtractor, deltaTracker);
        SlayerHudRenderer.render(graphicsExtractor, deltaTracker);
        SlayerTimerHudRenderer.render(graphicsExtractor, deltaTracker);
        InventoryHudRenderer.render(graphicsExtractor, deltaTracker);
    }
}
