package net.alpaka.addons.features.chat;

import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
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
 * the chat screen was closed. With Better Chat the chat is drawn once the whole HUD is done, cache
 * image included ({@link #extractAfterHud}). The player list goes with it, so it still covers the
 * chat as in vanilla, and the notices come last so the chat does not cover one in its corner.
 *
 * What is drawn there is the chat and the player list as Fabric's HUD registry has them: vanilla's,
 * or what another mod replaced them with, or nothing where one removed them. The registry hands
 * them over in their place in the HUD each frame ({@link #register}), and they are drawn there
 * unless Better Chat draws them after the HUD. Elements another mod attached after the chat stay
 * in the HUD, below the chat.
 */
public final class ChatOverHud {

    /** Implemented by the HUD's mixin: its chat and player list, drawn now. */
    public interface Late {
        void alpaka$extractChatLate(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker);
    }

    /** The chat as the HUD registry handed it over this frame, or null while it has handed none. */
    private static HudElement chat;
    /** The player list, likewise. */
    private static HudElement playerList;

    /** In the chat's place in the HUD: the chat, unless Better Chat draws it after the HUD. */
    private static final HudElement CHAT_IN_PLACE = (graphics, deltaTracker) -> {
        if (!isEnabled() && chat != null) chat.extractRenderState(graphics, deltaTracker);
    };
    private static final HudElement PLAYER_LIST_IN_PLACE = (graphics, deltaTracker) -> {
        if (!isEnabled() && playerList != null) playerList.extractRenderState(graphics, deltaTracker);
    };

    private ChatOverHud() {}

    /**
     * Takes the chat's and the player list's places in the HUD registry. Fabric runs the function
     * every frame, with the element as it stands then - vanilla's, or another mod's replacement.
     */
    public static void register() {
        HudElementRegistry.replaceElement(VanillaHudElements.CHAT, previous -> {
            chat = previous;
            return CHAT_IN_PLACE;
        });
        HudElementRegistry.replaceElement(VanillaHudElements.PLAYER_LIST, previous -> {
            playerList = previous;
            return PLAYER_LIST_IN_PLACE;
        });
    }

    public static boolean isEnabled() {
        return AlpakaConfig.instance.betterChatOn(true);
    }

    /** Right after the HUD: the chat and the player list, on top of all of it. */
    public static void extractAfterHud(Hud hud, GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!isEnabled()) return;
        ((Late) hud).alpaka$extractChatLate(graphics, deltaTracker);
    }

    /** The chat and the player list the registry handed over, for {@link Late}. */
    public static void extractElements(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (chat != null) chat.extractRenderState(graphics, deltaTracker);
        if (playerList != null) playerList.extractRenderState(graphics, deltaTracker);
    }
}
