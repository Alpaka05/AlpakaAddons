package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.chat.ChatOverHud;
import net.alpaka.addons.features.chat.ChatPeekFeature;
import net.alpaka.addons.features.chat.ChatTabsFeature;
import net.alpaka.addons.features.deployable.DeployableTimerHudRenderer;
import net.alpaka.addons.features.inventoryhud.InventoryHudRenderer;
import net.alpaka.addons.features.notification.AlpakaNotifications;
import net.alpaka.addons.features.playermodel.PlayerModelRenderer;
import net.alpaka.addons.features.ragnarock.RagnarockFeature;
import net.alpaka.addons.features.scoreboard.ScoreboardBlurFeature;
import net.alpaka.addons.features.scoreboard.ScoreboardFooterFeature;
import net.alpaka.addons.features.slayer.SlayerHudRenderer;
import net.alpaka.addons.features.tooltip.OldMasterStarsFeature;
import net.alpaka.addons.features.slayer.SlayerTimerHudRenderer;
import net.alpaka.addons.features.worldage.WorldAgeHudRenderer;
import net.alpaka.addons.utils.AlpakaPerf;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.Objective;
import net.minecraft.network.chat.Component;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.gui.Font;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Final;
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
public class HudMixin implements ChatOverHud.Late {
    @Shadow @Final private Minecraft minecraft;
    @Shadow private boolean isHidden;
    @Shadow private int toolHighlightTimer;
    @Shadow private ItemStack lastToolHighlight;

    @Shadow private void extractChat(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) { throw new AssertionError("replaced by mixin"); }
    @Shadow private void extractTabList(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) { throw new AssertionError("replaced by mixin"); }

    /** Better Chat: the chat and the player list after the whole HUD, under the same conditions as in it. */
    @Override
    public void alpaka$extractChatLate(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (this.isHidden || this.minecraft.gui.screen() instanceof LevelLoadingScreen) return;
        this.extractChat(graphics, deltaTracker);
        this.extractTabList(graphics, deltaTracker);
    }

    /** Better Chat draws the chat and the player list after the HUD instead; see ChatOverHud. */
    @Inject(method = {"extractChat", "extractTabList"}, at = @At("HEAD"), cancellable = true)
    private void alpaka$chatAfterHud(GuiGraphicsExtractor graphicsExtractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ChatOverHud.defersHudCall()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor graphicsExtractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        long t = AlpakaPerf.begin(AlpakaPerf.Section.HUD_PLAYER_MODEL);
        PlayerModelRenderer.render(graphicsExtractor, deltaTracker);
        AlpakaPerf.end(AlpakaPerf.Section.HUD_PLAYER_MODEL, t);
        t = AlpakaPerf.begin(AlpakaPerf.Section.HUD_WORLD_AGE);
        WorldAgeHudRenderer.render(graphicsExtractor, deltaTracker);
        AlpakaPerf.end(AlpakaPerf.Section.HUD_WORLD_AGE, t);
        t = AlpakaPerf.begin(AlpakaPerf.Section.HUD_SLAYER);
        SlayerHudRenderer.render(graphicsExtractor, deltaTracker);
        AlpakaPerf.end(AlpakaPerf.Section.HUD_SLAYER, t);
        t = AlpakaPerf.begin(AlpakaPerf.Section.HUD_SLAYER_TIMER);
        SlayerTimerHudRenderer.render(graphicsExtractor, deltaTracker);
        AlpakaPerf.end(AlpakaPerf.Section.HUD_SLAYER_TIMER, t);
        t = AlpakaPerf.begin(AlpakaPerf.Section.HUD_DEPLOYABLE_TIMER);
        DeployableTimerHudRenderer.render(graphicsExtractor, deltaTracker);
        AlpakaPerf.end(AlpakaPerf.Section.HUD_DEPLOYABLE_TIMER, t);
        RagnarockFeature.render(graphicsExtractor);
        t = AlpakaPerf.begin(AlpakaPerf.Section.HUD_INVENTORY);
        InventoryHudRenderer.render(graphicsExtractor, deltaTracker);
        AlpakaPerf.end(AlpakaPerf.Section.HUD_INVENTORY, t);
        // Last, so a notice sits above every other overlay rather than under one.
        t = AlpakaPerf.begin(AlpakaPerf.Section.HUD_NOTIFICATIONS);
        AlpakaNotifications.render(graphicsExtractor, deltaTracker);
        AlpakaPerf.end(AlpakaPerf.Section.HUD_NOTIFICATIONS, t);
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
        ChatTabsFeature.renderFilterChip(graphicsExtractor);
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

    /** Old Master Stars for the held item's name above the hotbar; the tooltip is done by its feature. */
    @WrapOperation(
            method = "extractSelectedItemName",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getHoverName()Lnet/minecraft/network/chat/Component;")
    )
    private Component alpaka$oldMasterStars(ItemStack stack, Operation<Component> original) {
        return OldMasterStarsFeature.apply(original.call(stack));
    }

    /** The scoreboard sidebar: its two flat boxes become one blurred panel; see ScoreboardBlurFeature. */
    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"))
    private void alpaka$beginScoreboard(GuiGraphicsExtractor graphicsExtractor, Objective objective, CallbackInfo ci) {
        ScoreboardBlurFeature.begin(graphicsExtractor);
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("TAIL"))
    private void alpaka$endScoreboard(GuiGraphicsExtractor graphicsExtractor, Objective objective, CallbackInfo ci) {
        ScoreboardBlurFeature.end(graphicsExtractor);
    }

    @WrapOperation(
            method = "displayScoreboardSidebar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0)
    )
    private void alpaka$scoreboardTitleBox(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
        if (!ScoreboardBlurFeature.titleBox(y0)) original.call(graphics, x0, y0, x1, y1, color);
    }

    @WrapOperation(
            method = "displayScoreboardSidebar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 1)
    )
    private void alpaka$scoreboardBodyBox(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
        if (!ScoreboardBlurFeature.bodyBox(graphics, x0, y0, x1, y1, color)) original.call(graphics, x0, y0, x1, y1, color);
    }

    /** The AlpakaAddons footer is centred like the title instead of starting at the left edge. */
    @WrapOperation(
            method = "displayScoreboardSidebar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", ordinal = 1)
    )
    private void alpaka$centerScoreboardFooter(GuiGraphicsExtractor graphics, Font font, Component line, int x, int y, int color, boolean shadow,
                                               Operation<Void> original, @Local(name = "width") int width) {
        if (ScoreboardFooterFeature.isFooter(line)) x = ScoreboardFooterFeature.centeredX(x, width, font.width(line));
        original.call(graphics, font, line, x, y, color, shadow);
    }
}
