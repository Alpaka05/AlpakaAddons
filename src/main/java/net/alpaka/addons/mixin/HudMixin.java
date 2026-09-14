package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.chat.ChatPeekFeature;
import net.alpaka.addons.features.chat.ChatTabsFeature;
import net.alpaka.addons.features.inventoryhud.InventoryHudRenderer;
import net.alpaka.addons.features.notification.AlpakaNotifications;
import net.alpaka.addons.features.playermodel.PlayerModelRenderer;
import net.alpaka.addons.features.slayer.SlayerHudRenderer;
import net.alpaka.addons.features.slayer.SlayerTimerHudRenderer;
import net.alpaka.addons.features.worldage.WorldAgeHudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
    @Shadow private int toolHighlightTimer;
    @Shadow private ItemStack lastToolHighlight;

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

    /**
     * Chat peek in a menu: the HUD's own chat stays away while the chat is drawn on top of the menu
     * instead - otherwise the blurred copy would show through the translucent line backgrounds.
     */
    @Inject(method = "extractChat", at = @At("HEAD"), cancellable = true)
    private void alpaka$hideChatBehindPeekedMenu(GuiGraphicsExtractor graphicsExtractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ChatPeekFeature.hidesHudChat()) {
            ci.cancel();
        }
    }

    /** Chat peek in the world: the tab row goes under the held-open chat, as in the chat screen. */
    @Inject(method = "extractChat", at = @At("TAIL"))
    private void alpaka$chatTabsWhilePeeking(GuiGraphicsExtractor graphicsExtractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        ChatTabsFeature.renderWhilePeeking(graphicsExtractor);
    }

    /**
     * While Tab cycles the chat tabs, the player list it is bound to stays away - in the chat screen
     * and while peeking - so the key does one thing there and not two.
     */
    @Inject(method = "extractTabList", at = @At("HEAD"), cancellable = true)
    private void alpaka$noTabListWhileTabCyclesChatTabs(GuiGraphicsExtractor graphicsExtractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ChatTabsFeature.suppressesTabList()) {
            ChatTabsFeature.releasePlayerListKey();
            ci.cancel();
        }
    }

    /**
     * Keeps the held item's name up. Vanilla counts the highlight timer down every tick and fades
     * the name once it drops below ten; topping it up after that count keeps the fade from ever
     * starting, while an empty hand still clears the name the vanilla way.
     */
    @Inject(method = "tick()V", at = @At("TAIL"))
    private void alpaka$keepItemNameVisible(CallbackInfo ci) {
        if (AlpakaConfig.instance.persistentItemNameEnabled && !this.lastToolHighlight.isEmpty()) {
            this.toolHighlightTimer = Math.max(this.toolHighlightTimer, 20);
        }
    }
}
