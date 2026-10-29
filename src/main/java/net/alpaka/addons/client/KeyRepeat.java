package net.alpaka.addons.client;

/**
 * Whether the key event the keyboard handler is passing on right now is a held key repeating.
 *
 * A screen's keyPressed gets presses and repeats alike and cannot tell them apart; the keyboard
 * handler knows, and notes it here before it hands the event on (KeyboardHandlerMixin). Only read
 * it from within the handling of that event.
 */
public final class KeyRepeat {
    private static boolean repeating;

    private KeyRepeat() {}

    /** Called by KeyboardHandlerMixin for every key event, before the game handles it. */
    public static void note(boolean repeat) {
        repeating = repeat;
    }

    public static boolean isRepeat() {
        return repeating;
    }
}
