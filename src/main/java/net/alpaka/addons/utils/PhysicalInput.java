package net.alpaka.addons.utils;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.sdl.SDLMouse;

/**
 * Whether a key or mouse button is physically held right now, asked of SDL directly.
 *
 * A key mapping only reports down while no screen is open: opening a screen releases every mapping,
 * and presses that arrive while a screen is up go to the screen. Features that act on a held key
 * inside menus (Peek Chat, Price Tooltip, the player list) read the device state here instead.
 */
public final class PhysicalInput {
    private PhysicalInput() {
    }

    public static boolean isDown(InputConstants.Key key) {
        return switch (key.getType()) {
            case KEYBOARD -> key.getValue() != InputConstants.UNKNOWN.getValue()
                    && InputConstants.isKeyDown(key.getValue());
            case MOUSE -> isMouseButtonDown(key.getValue());
        };
    }

    /** SDL numbers mouse buttons from 1 (left); bit n-1 of the button mask is button n. */
    private static boolean isMouseButtonDown(int button) {
        if (button < 1 || button > 32) return false;
        return (SDLMouse.SDL_GetMouseState(null, null) & (1 << (button - 1))) != 0;
    }
}
