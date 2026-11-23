package net.alpaka.addons.features.chat;

/**
 * What the chat gains through its mixin for the draggable scrollbar: its scroll position, read and
 * set, and the fade the scrollbar follows while the chat opens.
 */
public interface AlpakaChatScroll {

    /** Lines scrolled up from the newest, fractional while the smooth scroll glides. */
    double alpaka$scrollPosition();

    /** Scrolls straight to the position, without a glide, clamped to the chat's range. */
    void alpaka$scrollTo(double lines);

    /** The color with its alpha scaled by how far the opening chat has unrolled; unchanged once it is open. */
    int alpaka$fadeWhileOpening(int color);
}
