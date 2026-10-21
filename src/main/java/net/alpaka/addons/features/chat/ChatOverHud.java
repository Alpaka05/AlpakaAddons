package net.alpaka.addons.features.chat;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;

/**
 * Better Chat keeps the chat above the rest of the HUD - the action bar, the held item's name, the
 * mod's overlays - the way the chat screen has it, whether the chat is just showing new messages,
 * peeked or open.
 *
 * Vanilla draws the HUD chat near the end of the HUD, after the action bar, and the chat screen
 * draws it after the whole HUD. With a HUD cache such as Gnetum, though, the HUD elements it caches
 * are drawn as one image after the elements it does not - and the chat, whose glass needs the
 * frame's own pass, is one it does not cache. So the action bar landed on top of the chat whenever
 * the chat screen was closed. With Better Chat the HUD's own chat call is skipped, and the chat is
 * drawn once the whole HUD is done, cache image included ({@link #extractAfterHud}). The player
 * list goes with it, so it still covers the chat as in vanilla.
 */
public final class ChatOverHud {

    /** Implemented by the HUD's mixin: its chat and player list, drawn now. */
    public interface Late {
        void alpaka$extractChatLate(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker);
    }

    /** Set while the chat is drawn after the HUD, so the HUD hooks let that one call through. */
    private static boolean late;

    private ChatOverHud() {}

    public static boolean isEnabled() {
        return AlpakaConfig.instance.betterChatOn(true);
    }

    /** Whether the HUD skips its own chat and player list call, both being drawn after it instead. */
    public static boolean defersHudCall() {
        return !late && isEnabled();
    }

    /** Right after the HUD: the chat and the player list, on top of all of it. */
    public static void extractAfterHud(Hud hud, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!isEnabled()) return;
        late = true;
        try {
            ((Late) hud).alpaka$extractChatLate(graphics, deltaTracker);
        } finally {
            late = false;
        }
    }
}
