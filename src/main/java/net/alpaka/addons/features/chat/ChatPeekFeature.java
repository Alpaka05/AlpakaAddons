package net.alpaka.addons.features.chat;

import com.mojang.blaze3d.platform.InputConstants;
import net.alpaka.addons.client.AlpakaKeyCategory;
import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Holds the chat open for reading while the player keeps moving.
 *
 * While the Peek Chat key is down the HUD draws the chat exactly as the chat screen would: every
 * line at full opacity, the focused chat height, the scrollbar - only without the input line, and
 * without releasing the mouse. The scroll wheel moves through the history for as long as the key is
 * held, and letting go puts the chat back to its newest line.
 *
 * The key is unbound until the player picks one under Options > Controls, in the mod's own section.
 *
 * Everything hangs off {@link #isPeeking()}: the two hooks in ChatComponent (the chat height and the
 * display mode of the HUD draw) and the scroll hook in MouseHandler all ask it, so there is no state
 * to keep in step beyond noticing the release.
 */
public final class ChatPeekFeature {

    private ChatPeekFeature() {
    }

    public static KeyMapping PEEK_KEY;

    /** Whether the last call saw the key down, so the release can be noticed once. */
    private static boolean wasPeeking = false;

    public static void register() {
        PEEK_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.alpaka.chat_peek",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN,
                AlpakaKeyCategory.CATEGORY
        ));
    }

    /**
     * Whether the chat is being held open right now.
     *
     * Only in the world with no screen up: a screen releases every key mapping anyway, and the chat
     * screen has the real thing.
     */
    public static boolean isPeeking() {
        Minecraft mc = Minecraft.getInstance();
        boolean peeking = AlpakaConfig.instance.chatPeekEnabled
                && PEEK_KEY != null && PEEK_KEY.isDown()
                && mc.player != null && mc.gui.screen() == null;
        if (wasPeeking && !peeking && mc.gui != null) {
            // Back to the newest line, as closing the chat screen does. Otherwise the HUD would keep
            // showing wherever the player had scrolled to, with the new-message marker on top.
            mc.gui.hud.getChat().resetChatScroll();
        }
        wasPeeking = peeking;
        return peeking;
    }

    /**
     * The scroll wheel while peeking. Returns true when the event was taken, which also keeps it
     * away from the hotbar.
     *
     * Same feel as the chat screen: seven lines a notch, one with shift held.
     */
    public static boolean onScroll(double yOffset) {
        if (!isPeeking()) return false;
        if (yOffset == 0) return true;
        double amount = Mth.clamp(yOffset, -1.0, 1.0);
        Minecraft mc = Minecraft.getInstance();
        if (!mc.hasShiftDown()) {
            amount *= ChatScreen.MOUSE_SCROLL_SPEED;
        }
        mc.gui.hud.getChat().scrollChat((int) amount);
        return true;
    }
}
