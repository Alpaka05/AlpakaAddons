package net.alpaka.addons.features.chat;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.alpaka.addons.client.AlpakaKeyCategory;
import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

/**
 * Holds the chat open for reading while the player keeps moving - or keeps a menu open.
 *
 * While the Peek Chat key is down the chat is drawn exactly as the chat screen would draw it: every
 * line at full opacity, the focused chat height, the scrollbar - only without the input line. In the
 * world that happens in the HUD, without releasing the mouse. With an inventory or another menu open
 * (Chat Peek In Menus) the chat is drawn on top of the menu instead, above its blur and dimming, so
 * it reads as if it belonged to the menu. The scroll wheel moves through the history for as long as
 * the key is held, in the world and in a menu alike, and letting go puts the chat back to its newest
 * line.
 *
 * The key is unbound until the player picks one under Options > Controls, in the mod's own section.
 *
 * Everything hangs off {@link #isPeeking()}: the two hooks in ChatComponent (the chat height and the
 * display mode of the HUD draw), the HUD chat hook in Hud, the overlay hook in Screen and the scroll
 * hook in MouseHandler all ask it, so there is no state to keep in step beyond noticing the release.
 *
 * ### Why the key is read from GLFW and not from the key mapping
 *
 * A key mapping only ever reports down while no screen is open: opening a screen releases every
 * mapping, and presses that arrive while a screen is up are handed to the screen and never reach
 * the mapping. So the physical state of the bound key is read straight from GLFW. That read is used
 * in the world too, which is what lets the chat stay open across opening or closing a menu with the
 * key still held - closing a screen does not read the keyboard back into the mappings, so the
 * mapping would report the key up until it was pressed again.
 */
public final class ChatPeekFeature {

    private ChatPeekFeature() {
    }

    public static PeekKeyMapping PEEK_KEY;

    /** Whether the last call saw the key down, so the release can be noticed once. */
    private static boolean wasPeeking = false;

    public static void register() {
        PEEK_KEY = new PeekKeyMapping();
        KeyMappingHelper.registerKeyMapping(PEEK_KEY);
    }

    /**
     * Whether the chat is being held open right now, in the world or on top of a menu.
     */
    public static boolean isPeeking() {
        Minecraft mc = Minecraft.getInstance();
        boolean peeking = AlpakaConfig.instance.chatPeekEnabled
                && PEEK_KEY != null && mc.player != null
                && isKeyHeld(mc);
        if (wasPeeking && !peeking && mc.gui != null) {
            // Back to the newest line, as closing the chat screen does. Otherwise the HUD would keep
            // showing wherever the player had scrolled to, with the new-message marker on top.
            mc.gui.hud.getChat().resetChatScroll();
        }
        wasPeeking = peeking;
        return peeking;
    }

    /** Whether the chat is being held open on top of a menu right now. */
    public static boolean isPeekingInMenu() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gui.screen() != null && isPeeking();
    }

    private static boolean isKeyHeld(Minecraft mc) {
        Screen screen = mc.gui.screen();
        if (screen != null) {
            if (!AlpakaConfig.instance.chatPeekInMenus) return false;
            // The chat screen shows the real thing.
            if (screen instanceof ChatScreen) return false;
            // A letter typed into a search box or an anvil should not also pop the chat open.
            if (screen.getFocused() instanceof EditBox box && box.canConsumeInput()) return false;
        }
        return PEEK_KEY.isHeld(mc.getWindow());
    }

    /**
     * Draws the chat on top of an open menu. Called from the Screen hook after the menu's own content
     * and before its tooltips, in a stratum of its own so it lands above the menu's blur and dimming.
     * The HUD's copy underneath is skipped meanwhile ({@link #hidesHudChat()}), otherwise the blurred
     * lines would show through the translucent line backgrounds.
     */
    public static void renderOverMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!isPeekingInMenu()) return;
        Minecraft mc = Minecraft.getInstance();
        graphics.nextStratum();
        mc.gui.hud.getChat().extractRenderState(graphics, mc.font, mc.gui.hud.getGuiTicks(), mouseX, mouseY,
                ChatComponent.DisplayMode.FOREGROUND, false);
    }

    /** Whether the HUD should skip its own chat draw because {@link #renderOverMenu} draws it instead. */
    public static boolean hidesHudChat() {
        return isPeekingInMenu();
    }

    /**
     * The scroll wheel while peeking. Returns true when the event was taken, which also keeps it
     * away from the hotbar - or from the menu underneath.
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

    /**
     * The Peek Chat key mapping. A subclass only so the bound key, which KeyMapping keeps protected,
     * can be read for the physical check.
     */
    public static final class PeekKeyMapping extends KeyMapping {
        PeekKeyMapping() {
            super("key.alpaka.chat_peek", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, AlpakaKeyCategory.CATEGORY);
        }

        /** Whether the bound key is physically down right now, whatever screen is open. */
        boolean isHeld(Window window) {
            InputConstants.Key bound = this.key;
            return switch (bound.getType()) {
                case KEYSYM -> bound.getValue() != GLFW.GLFW_KEY_UNKNOWN
                        && InputConstants.isKeyDown(window, bound.getValue());
                case MOUSE -> GLFW.glfwGetMouseButton(window.handle(), bound.getValue()) == GLFW.GLFW_PRESS;
                // GLFW cannot be asked about a raw scan code; the mapping's own state is all there is.
                case SCANCODE -> this.isDown();
            };
        }
    }
}
