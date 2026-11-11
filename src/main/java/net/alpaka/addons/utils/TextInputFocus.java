package net.alpaka.addons.utils;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * Whether the player is typing into a text field on a screen, so a key of ours that is a letter
 * types that letter instead of acting.
 *
 * <p>Looking only at {@code screen.getFocused()} misses the search fields other mods put on a menu:
 * SkyBlock Item List adds its panel as a widget and focuses its search box inside the panel, while
 * the screen's own focus stays elsewhere. So every field on the screen is checked, nested ones too.
 */
public final class TextInputFocus {
    /** Deep enough for any panel; guards against a widget that lists itself as its own child. */
    private static final int MAX_DEPTH = 8;

    private TextInputFocus() {}

    public static boolean isTyping(Screen screen) {
        return screen != null && typingIn(screen, 0);
    }

    private static boolean typingIn(ContainerEventHandler container, int depth) {
        if (depth > MAX_DEPTH) return false;
        // The usual case first: the focused field of the screen itself.
        if (container.getFocused() instanceof EditBox box && box.canConsumeInput()) return true;
        for (GuiEventListener child : container.children()) {
            if (child instanceof AbstractWidget widget && !widget.visible) continue;
            if (child instanceof EditBox box) {
                if (box.canConsumeInput()) return true;
            } else if (child instanceof ContainerEventHandler nested && typingIn(nested, depth + 1)) {
                return true;
            }
        }
        return false;
    }
}
