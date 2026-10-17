package net.alpaka.addons.features.chat;

import net.alpaka.addons.compat.GnetumCompat;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * The action bar goes above the blurred chat panel instead of under it.
 *
 * The panel shows the world as it was before any HUD element ({@link ChatBlurFeature}), so whatever
 * lies beneath it - the action bar's health and mana - is not dimmed as by vanilla's flat boxes but
 * gone. Vanilla draws the action bar before the chat; while the chat blur is on, it is held back and
 * drawn right after the chat instead, in a stratum of its own above the panel.
 *
 * Done through Fabric's HUD registry rather than a mixin on the action bar's method, so the whole
 * element moves as one: mods that hook into that method (SkyHanni posts an event at its start and
 * end) see both ends in the new place, and a mod that hides or replaces the element still does.
 * While the chat is open or peeked the chat stays on top, as the thing being read.
 *
 * Under Gnetum, which caches HUD elements into an image of its own, the action bar and the element
 * after the chat leave that cache while the chat blur is on. The blurred chat is drawn uncached,
 * over Gnetum's image, so an action bar left in the image ended up under the panel again. Uncached,
 * all three are drawn in the frame's own GUI pass, in this order. An element the player forced into
 * Gnetum's cache stays there, and the action bar is then drawn where vanilla puts it.
 */
public final class ActionBarOverChat {
    private static final String GNETUM_REASON = "AlpakaAddons draws the action bar above the blurred chat";
    private static final Identifier AFTER_CHAT = Identifier.fromNamespaceAndPath("alpaka", "action_bar_over_chat");

    // The action bar element held back this frame, with what it was called with.
    private static HudElement held;
    private static DeltaTracker heldDelta;

    private ActionBarOverChat() {}

    public static void register() {
        HudElementRegistry.replaceElement(VanillaHudElements.OVERLAY_MESSAGE, actionBar -> (graphics, delta) -> {
            if (ChatBlurFeature.isEnabled()) GnetumCompat.keepOutOfCache(GNETUM_REASON);
            // Still held from the last frame: the element after the chat didn't run in this pass
            // (Gnetum keeps it cached), so the action bar is drawn in place rather than lost.
            boolean unclaimed = held != null;
            if (movesAboveChat() && !unclaimed) {
                held = actionBar;
                heldDelta = delta;
            } else {
                held = null;
                heldDelta = null;
                actionBar.extractRenderState(graphics, delta);
            }
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, AFTER_CHAT, (graphics, delta) -> {
            if (ChatBlurFeature.isEnabled()) GnetumCompat.keepOutOfCache(GNETUM_REASON);
            drawHeld(graphics);
        });
    }

    private static boolean movesAboveChat() {
        if (!ChatBlurFeature.isEnabled() || GnetumCompat.isCaching()) return false;
        Minecraft mc = Minecraft.getInstance();
        return !mc.gui.hud.getChat().isChatFocused() && !ChatPeekFeature.isPeeking();
    }

    private static void drawHeld(GuiGraphicsExtractor graphics) {
        HudElement actionBar = held;
        DeltaTracker delta = heldDelta;
        held = null;
        heldDelta = null;
        if (actionBar != null) actionBar.extractRenderState(graphics, delta);
    }
}
