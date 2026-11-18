package net.alpaka.addons.utils;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Mouse button numbers. Since 26.3 they are SDL's, counted from 1 (left 1, middle 2, right 3), while
 * a container click still names its button the way the protocol does (left 0, right 1). Code that
 * turns a mouse click into a container click goes through {@link #containerButton}.
 */
public final class MouseButtons {
    private MouseButtons() {
    }

    public static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    public static final int RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;

    /** Left or right, the two buttons menus are used with. */
    public static boolean isLeftOrRight(int button) {
        return button == LEFT || button == RIGHT;
    }

    /** The container click button for a mouse button, as vanilla's container screen works it out. */
    public static int containerButton(int mouseButton) {
        return switch (mouseButton) {
            case LEFT -> 0;
            case RIGHT -> 1;
            default -> mouseButton;
        };
    }
}
