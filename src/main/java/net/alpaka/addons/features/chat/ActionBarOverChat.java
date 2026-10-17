package net.alpaka.addons.features.chat;

import net.alpaka.addons.compat.GnetumCompat;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * The action bar and the held item's name go above the blurred chat panel instead of under it.
 *
 * The panel shows the world as it was before any HUD element ({@link ChatBlurFeature}), so whatever
 * lies beneath it - the action bar's health and mana, the item name above it - is not dimmed as by
 * vanilla's flat boxes but gone. Vanilla draws both before the chat; while the chat blur is on, they
 * are held back and drawn right after the chat instead, in their own order, above the panel. That
 * holds with the chat open or peeked too: the open chat is wider and taller and would cover more.
 *
 * Done through Fabric's HUD registry rather than a mixin on the elements' methods, so each element
 * moves as one: mods that hook into the action bar's method (SkyHanni posts an event at its start
 * and end) see both ends in the new place, and a mod that hides or replaces an element still does.
 *
 * Under Gnetum, which caches HUD elements into an image of its own, the moved elements and the
 * element after the chat leave that cache while the chat blur is on. The blurred chat is drawn
 * uncached, over Gnetum's image, so an action bar left in the image ended up under the panel again.
 * Uncached, they are all drawn in the frame's own GUI pass, in this order. An element the player
 * forced into Gnetum's cache stays there and is drawn where vanilla puts it.
 */
public final class ActionBarOverChat {
    private static final String GNETUM_REASON = "AlpakaAddons draws the action bar above the blurred chat";
    private static final Identifier AFTER_CHAT = Identifier.fromNamespaceAndPath("alpaka", "action_bar_over_chat");

    /** The elements moved above the chat, in vanilla's order: the held item's name, then the action bar. */
    private static final Held[] HELD = {new Held(), new Held()};

    private ActionBarOverChat() {}

    public static void register() {
        hold(VanillaHudElements.HELD_ITEM_TOOLTIP, HELD[0]);
        hold(VanillaHudElements.OVERLAY_MESSAGE, HELD[1]);
        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, AFTER_CHAT, (graphics, delta) -> {
            if (ChatBlurFeature.isEnabled()) GnetumCompat.keepOutOfCache(GNETUM_REASON);
            for (Held held : HELD) held.draw(graphics);
        });
    }

    private static void hold(Identifier id, Held slot) {
        HudElementRegistry.replaceElement(id, element -> (graphics, delta) -> {
            if (ChatBlurFeature.isEnabled()) GnetumCompat.keepOutOfCache(GNETUM_REASON);
            // Still held from the last frame: the element after the chat didn't run in this pass
            // (Gnetum keeps it cached), so this one is drawn in place rather than lost.
            boolean unclaimed = slot.element != null;
            if (ChatBlurFeature.isEnabled() && !GnetumCompat.isCaching() && !unclaimed) {
                slot.element = element;
                slot.delta = delta;
            } else {
                slot.element = null;
                slot.delta = null;
                element.extractRenderState(graphics, delta);
            }
        });
    }

    /** One element held back this frame, with what it was called with. */
    private static final class Held {
        HudElement element;
        DeltaTracker delta;

        void draw(GuiGraphicsExtractor graphics) {
            HudElement e = element;
            DeltaTracker d = delta;
            element = null;
            delta = null;
            if (e != null) e.extractRenderState(graphics, d);
        }
    }
}
