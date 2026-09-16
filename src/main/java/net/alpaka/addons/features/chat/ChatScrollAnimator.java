package net.alpaka.addons.features.chat;

import net.alpaka.addons.config.AlpakaConfig;

/**
 * Smooth chat scrolling: the settings behind it. The movement itself lives in the chat mixin,
 * because it has to own the chat's scroll position: the smooth position is a fraction of a line,
 * the whole part decides which lines are laid out and the rest shifts them, so every notch of the
 * wheel is one continuous glide in which lines enter and leave the box one at a time.
 */
public final class ChatScrollAnimator {

    /**
     * How fast the smooth position closes in on the target, per second. A wheel notch is seven
     * lines, and 7 per second settles that in about 430 ms. Overridable with -Dalpaka.scrollRate.
     */
    public static final float RATE = Float.parseFloat(System.getProperty("alpaka.scrollRate", "7"));

    private ChatScrollAnimator() {}

    public static boolean isEnabled() {
        return AlpakaConfig.instance.chatSmoothScrollEnabled;
    }
}
