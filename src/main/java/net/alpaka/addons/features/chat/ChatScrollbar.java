package net.alpaka.addons.features.chat;

import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

/**
 * Better Chat's scrollbar for the open chat, in place of vanilla's two-pixel line, which can only
 * be looked at: a wider, rounded bar beside the chat box that can be grabbed and dragged, and a
 * click on its track jumps there.
 *
 * The chat mixin draws it at the end of each chat layout, in the chat's own coordinates, and this
 * keeps where it landed on screen; the chat screen's mixin hands the mouse to it from there. It
 * only exists while the chat is drawn in the foreground - the chat screen, or Chat Peek - and only
 * when there is more chat than fits.
 */
public final class ChatScrollbar {

    /** The bar's width, and the gap to the chat box, in GUI pixels whatever the chat's scale. */
    private static final float BAR_W = 5.0f;
    private static final float GAP = 3.0f;
    /** How far past the bar a press still grabs it, on either side, in GUI pixels. */
    private static final float GRAB_SLACK = 3.0f;
    /** The thumb never gets shorter than this, in GUI pixels, so it stays easy to grab. */
    private static final float MIN_THUMB = 12.0f;

    // A dark track, so the light thumb stands out over a bright sky as much as over the chat.
    private static final int TRACK = 0x55000000;
    private static final int THUMB = 0xBBFFFFFF;
    private static final int THUMB_HOVER = 0xFFFFFFFF;

    /** Where the bar was drawn last, in GUI coordinates on screen; {@code shown} false when it was not. */
    private static boolean shown;
    private static float x0, x1, trackTop, trackBottom, thumbH;
    private static double maxScroll;

    private static boolean dragging;
    /** Where on the thumb it was grabbed, from the thumb's top, in GUI pixels. */
    private static float grab;

    private ChatScrollbar() {}

    public static boolean isEnabled() {
        return AlpakaConfig.instance.betterChatOn(AlpakaConfig.instance.chatScrollbarEnabled);
    }

    /** A chat layout starts; until it draws the bar there is none to grab. */
    public static void beginLayout() {
        shown = false;
    }

    /**
     * Draws the bar for one chat layout, under the chat's pose: its scale and its indent are
     * applied, so chat pixels are the unit here, and {@code chatScale} turns GUI pixels into them.
     *
     * @param scroll    lines scrolled up from the newest, fractional while the smooth scroll glides
     * @param newSince  a message came in while scrolled up; the thumb takes the accent then
     */
    public static void draw(GuiGraphicsExtractor graphics, float chatScale, int boxRight, int chatBottom, int lineHeight,
                            int total, int perPage, double scroll, boolean newSince, int mouseX, int mouseY) {
        if (total <= perPage || perPage <= 0) return;
        float unit = 1.0f / chatScale;
        int pad = ChatBlurFeature.isEnabled() ? ChatBlurFeature.PADDING : 0;
        float left = boxRight + pad + GAP * unit;
        float width = BAR_W * unit;
        float trackH = perPage * (float) lineHeight;
        float top = chatBottom - trackH;
        float thumb = Math.max(MIN_THUMB * unit, trackH * perPage / total);
        double max = total - perPage;
        float frac = (float) Math.max(0.0, Math.min(1.0, scroll / max));
        float thumbBottom = chatBottom - frac * (trackH - thumb);

        // Kept in screen GUI coordinates, which is what the mouse reports.
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        Vector2f a = pose.transformPosition(new Vector2f(left, top));
        Vector2f b = pose.transformPosition(new Vector2f(left + width, chatBottom));
        x0 = a.x;
        x1 = b.x;
        trackTop = a.y;
        trackBottom = b.y;
        thumbH = thumb * chatScale;
        maxScroll = max;
        shown = true;

        float thumbTopScreen = pose.transformPosition(new Vector2f(left, thumbBottom - thumb)).y;
        boolean hover = dragging || (mouseX >= x0 - GRAB_SLACK && mouseX <= x1 + GRAB_SLACK
                && mouseY >= thumbTopScreen && mouseY <= thumbTopScreen + thumbH);

        // Whole chat pixels for the rounded shapes; the thumb's fraction of a pixel goes into the
        // pose, so it glides with the smooth scroll instead of stepping.
        int ix = (int) Math.floor(left);
        int iw = Math.max(1, Math.round(width));
        int radius = (iw + 1) / 2 + 1;
        ModernGuiUtils.drawRoundedRect(graphics, ix, Math.round(top), iw, Math.round(trackH), radius, TRACK);
        float thumbTop = thumbBottom - thumb;
        int it = (int) Math.floor(thumbTop);
        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0f, thumbTop - it);
        int color = newSince ? (ModernGuiUtils.getAccentColor() & 0x00FFFFFF) | (hover ? 0xFF000000 : 0xCC000000)
                : hover ? THUMB_HOVER : THUMB;
        ModernGuiUtils.drawRoundedRect(graphics, ix, it, iw, Math.round(thumb), radius, color);
        graphics.pose().popMatrix();
    }

    /**
     * A press in the open chat. On the thumb it grabs it where it was pressed; on the track the
     * thumb jumps to centre on the mouse and is grabbed there. Returns whether the press was ours.
     */
    public static boolean press(double mouseX, double mouseY) {
        if (!shown || !isEnabled()) return false;
        if (mouseX < x0 - GRAB_SLACK || mouseX > x1 + GRAB_SLACK || mouseY < trackTop || mouseY > trackBottom) return false;
        float thumbTop = thumbTop();
        grab = mouseY >= thumbTop && mouseY <= thumbTop + thumbH ? (float) (mouseY - thumbTop) : thumbH / 2.0f;
        dragging = true;
        drag(mouseY);
        return true;
    }

    /** The mouse moved with the button down; true while the bar is being dragged. */
    public static boolean drag(double mouseY) {
        if (!dragging) return false;
        float span = trackBottom - trackTop - thumbH;
        if (span <= 0.0f || maxScroll <= 0.0) return true;
        double frac = Math.max(0.0, Math.min(1.0, (trackBottom - thumbH - (mouseY - grab)) / span));
        if (Minecraft.getInstance().gui.hud.getChat() instanceof AlpakaChatScroll chat) {
            chat.alpaka$scrollTo(frac * maxScroll);
        }
        return true;
    }

    /** The button came up; true if it ended a drag. */
    public static boolean release() {
        boolean was = dragging;
        dragging = false;
        return was;
    }

    private static float thumbTop() {
        double scroll = Minecraft.getInstance().gui.hud.getChat() instanceof AlpakaChatScroll chat ? chat.alpaka$scrollPosition() : 0.0;
        float frac = (float) Math.max(0.0, Math.min(1.0, maxScroll <= 0.0 ? 0.0 : scroll / maxScroll));
        return trackBottom - thumbH - frac * (trackBottom - trackTop - thumbH);
    }
}
