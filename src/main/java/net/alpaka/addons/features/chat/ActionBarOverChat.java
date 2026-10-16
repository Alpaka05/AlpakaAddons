package net.alpaka.addons.features.chat;

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
 */
public final class ActionBarOverChat {
    private static final Identifier AFTER_CHAT = Identifier.fromNamespaceAndPath("alpaka", "action_bar_over_chat");

    // The action bar element held back this frame, with what it was called with.
    private static HudElement held;
    private static DeltaTracker heldDelta;

    private ActionBarOverChat() {}

    public static void register() {
        HudElementRegistry.replaceElement(VanillaHudElements.OVERLAY_MESSAGE, actionBar -> (graphics, delta) -> {
            if (movesAboveChat()) {
                held = actionBar;
                heldDelta = delta;
            } else {
                actionBar.extractRenderState(graphics, delta);
            }
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, AFTER_CHAT, (graphics, delta) -> drawHeld(graphics));
    }

    private static boolean movesAboveChat() {
        if (!ChatBlurFeature.isEnabled()) return false;
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
