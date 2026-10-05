package net.alpaka.addons.features.chat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import net.alpaka.addons.client.gui.AlpakaGuiElementSink;
import net.alpaka.addons.client.gui.BlurRectRenderState;
import net.alpaka.addons.client.gui.RoundedRectRenderState;
import org.joml.Vector2f;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The blurred chat background: one rounded, slightly padded panel behind the visible chat lines
 * that shows the world behind it blurred, in place of vanilla's stack of flat per-line boxes.
 *
 * ### How the blur is made
 *
 * The GUI is drawn straight onto the frame after the world, and a shader cannot read the very
 * target it is writing to. So, at the start of the GUI's render pass - after every element has
 * been extracted, before any is drawn - the frame so far (the world, nothing of the HUD yet) is
 * copied into a render target of this class's own, and vanilla's separable box blur is run over
 * that copy three times. That is the same post effect the pause menu uses; the radius comes from
 * one of this mod's own post-effect definitions instead, one per radius, picked from the GUI scale
 * and the Blur Strength setting ({@link #blurRadius}) so the blur is the same width in GUI pixels
 * whatever the window scale, and independent of the player's menu-blur setting. The panel's
 * fragment shader then samples the blurred copy at its own screen position, tints it with the
 * chat's background colour and fades the rounded edge over one pixel.
 *
 * The copy is only made in frames where a panel asked for it ({@link #request()}), so a hidden
 * chat costs nothing.
 *
 * ### Resizes
 *
 * The GUI renderer asks each element for its texture while preparing the frame, before anything is
 * drawn. With the copy deferred to the blur point ({@link #captureAfterBackground()}), that is before
 * the copy of this frame exists. So the target is sized in {@link #captureFrameBeforeGui()}, before
 * the elements are prepared, and never replaced later in the frame. A target that no longer fits is
 * set aside and closed a frame later, and the frame that replaced it draws its panels without blur.
 * Destroying it at the blur point instead, as this once did, left the main menu's glass tabs holding
 * a closed texture, and resizing the window crashed the game.
 */
public final class ChatBlurFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger("AlpakaAddons/ChatBlur");

    /** How wide the panel grows past vanilla's own box, in chat pixels. */
    public static final int PADDING = 3;
    /** The panel's corner radius in chat pixels. */
    public static final int RADIUS = 5;

    /** Blur radius per GUI scale step at full strength: 3 screen pixels for each GUI pixel. */
    private static final int RADIUS_PER_SCALE = 3;
    /** The largest radius a post effect is shipped for: full strength at GUI scale 4. */
    static final int MAX_BLUR_RADIUS = 4 * RADIUS_PER_SCALE;

    private static TextureTarget blurred;
    /** Targets replaced after a resize, closed at the start of the next frame. */
    private static final java.util.List<TextureTarget> PENDING_CLOSE = new java.util.ArrayList<>();
    private static CrossFrameResourcePool pool;
    private static boolean requested;
    private static boolean ready;
    private static boolean warned;

    // A screen can ask for the copy to be taken after its background strata rather than before
    // any GUI element: asked during extraction, decided when the frame is drawn.
    private static boolean captureAfterBackground;
    private static boolean deferred;

    // The extraction in progress: the graphics to submit the panel to, and what the chat's own
    // background pass measured. Vanilla draws one box per line in that pass; with the panel on,
    // those boxes are only measured, and the panel is submitted in their place before the text.
    private static GuiGraphicsExtractor currentGraphics;
    private static int mouseX;
    private static int mouseY;
    private static boolean foreground;
    private static float scrollOffset;
    private static float panelHeight;
    private static float panelAlpha;
    private static int panelLines;
    private static int panelBottom;
    private static int panelWidth;
    private static int panelLineHeight;
    private static float panelOpacity;
    /** The panel never grows taller than this many lines: the chat's box, while lines slide inside it. */
    private static int panelMaxLines = Integer.MAX_VALUE;

    /** The lift of the line under the mouse, over the panel's own tint. */
    private static final int HOVER_HIGHLIGHT = 0x22FFFFFF;

    private ChatBlurFeature() {}

    public static boolean isEnabled() {
        return AlpakaConfig.instance.chatBlurEnabled;
    }

    /** The chat is about to be extracted into this graphics, with the mouse here. */
    public static void beginExtraction(GuiGraphicsExtractor graphics, int mx, int my) {
        currentGraphics = graphics;
        mouseX = mx;
        mouseY = my;
        resetPanel(false);
    }

    public static void endExtraction() {
        currentGraphics = null;
    }

    /** A new layout pass starts; whether it draws the chat as open (mouse visible) or as the HUD. */
    public static void resetPanel(boolean foregroundMode) {
        panelMaxLines = Integer.MAX_VALUE;
        panelHeight = 0.0f;
        panelAlpha = 0.0f;
        panelLines = 0;
        scrollOffset = 0.0f;
        foreground = foregroundMode;
    }

    /**
     * How far the lines are currently shifted by the smooth scroll, in chat pixels. The panel is
     * the box the lines scroll inside, so it stays put while they move and the lines are clipped to
     * it; the arrival slide, by contrast, moves box and lines together.
     */
    public static void setScrollOffset(float offset) {
        scrollOffset = offset;
    }

    /**
     * The box holds this many lines. While lines slide or scroll, one or two more are laid out than
     * fit, and the panel must stay the box's size rather than grow past its top edge.
     */
    public static void limitPanel(int lines) {
        panelMaxLines = Math.max(0, lines);
    }

    /**
     * One of vanilla's per-line background boxes. Returns true when the panel takes it over, in
     * which case the box itself must not be drawn.
     *
     * The panel's height is the alpha-weighted sum of the line heights rather than a count: a line
     * fading out at the top, or fading in at the bottom, shrinks or grows the panel by just as much
     * as it is visible, so the panel's edge glides instead of jumping a whole line at a time.
     */
    public static boolean collectLine(int chatBottom, int lineHeight, int width, float backgroundOpacity, float alpha) {
        if (currentGraphics == null || !isEnabled()) return false;
        panelBottom = chatBottom;
        panelWidth = width;
        panelLineHeight = lineHeight;
        panelOpacity = backgroundOpacity;
        panelHeight += alpha * lineHeight;
        panelAlpha = Math.max(panelAlpha, alpha);
        panelLines++;
        return true;
    }


    /**
     * Submits the panel for the lines measured so far, under the chat's current pose. Called right
     * before the chat draws its text, so the text lands on top.
     *
     * @param chatScale the chat's own scale setting, which the pose already applies; with the GUI
     *                  scale it says how many screen pixels one chat pixel covers
     */
    public static void submitPanel(float chatScale) {
        GuiGraphicsExtractor graphics = currentGraphics;
        if (graphics == null || !isEnabled()) return;
        if (panelHeight <= 0.01f || panelAlpha <= 0.002f) return;
        if (!(graphics instanceof AlpakaGuiElementSink sink)) return;

        float toScreen = (float) Minecraft.getInstance().getWindow().getGuiScale() * chatScale;
        int x0 = -4 - PADDING;
        int x1 = panelWidth + 8 + PADDING;
        float height = Math.min(panelHeight, panelMaxLines * (float) panelLineHeight);
        int y0 = Math.round(panelBottom - height) - PADDING;
        int y1 = panelBottom + PADDING;
        int alpha = Math.round(Mth.clamp(panelAlpha * panelOpacity, 0.0f, 1.0f) * 255.0f);
        if (alpha <= 0) return;

        // Submitted before the lines are shifted by the scroll, so the current pose is the box's;
        // the lines will be drawn shifted down by the scroll offset, and the hover lift follows them.
        Matrix3x2f boxPose = new Matrix3x2f(graphics.pose());
        Matrix3x2f linePose = new Matrix3x2f(boxPose).translate(0.0f, scrollOffset);

        sink.alpaka$submitElement(new BlurRectRenderState(
                boxPose, x0, y0, x1, y1,
                Math.round(RADIUS * toScreen), alpha << 24, toScreen, sink.alpaka$currentScissor()));
        request();

        // With the chat open, the line under the mouse is lifted a little. The mouse is taken into
        // the lines' own coordinates, so the lift follows the lines while they scroll.
        if (foreground && panelLines > 0 && panelLineHeight > 0) {
            Matrix3x2f inverse = new Matrix3x2f(linePose).invert();
            Vector2f local = inverse.transformPosition(new Vector2f(mouseX, mouseY));
            if (local.x >= x0 && local.x <= x1 && local.y <= panelBottom) {
                int line = Mth.floor((panelBottom - local.y) / panelLineHeight);
                if (line >= 0 && line < panelLines) {
                    int top = panelBottom - (line + 1) * panelLineHeight;
                    sink.alpaka$submitElement(new RoundedRectRenderState(
                            linePose, x0 + 1, top, x1 - 1, top + panelLineHeight,
                            Math.round(3 * toScreen), 0, HOVER_HIGHLIGHT,
                            Math.max(1, Math.round(toScreen)), sink.alpaka$currentScissor()));
                }
            }
        }
    }

    /** A panel was extracted this frame; the frame has to be captured and blurred before drawing. */
    public static void request() {
        requested = true;
    }

    /** Whether a blurred copy exists for the panel to sample; false falls back to a plain panel. */
    public static boolean isReady() {
        return ready && blurred != null;
    }

    /**
     * A screen's background is drawn as GUI elements too - the main menu's panorama overlay, which
     * resource packs replace with whole backgrounds - and a copy taken before the GUI misses it.
     * Calling this while extracting, right after {@code graphics.nextStratum()} and
     * {@code graphics.blurBeforeThisStratum()}, moves this frame's copy to the GUI renderer's blur
     * point, where the strata before it are already on the frame; vanilla's own blur, which that
     * marker would otherwise run there, is skipped.
     */
    public static void captureAfterBackground() {
        captureAfterBackground = true;
    }

    /** Called before the GUI renderer prepares the frame's elements: the world is on the frame, nothing of the GUI yet. */
    public static void captureFrameBeforeGui() {
        // Last frame's draws are done, so nothing can still sample the targets set aside then.
        for (TextureTarget old : PENDING_CLOSE) old.destroyBuffers();
        PENDING_CLOSE.clear();

        // A deferred copy that never reached the blur point leaves the last copy stale; draw this
        // frame's panels without blur rather than sample it.
        if (deferred) ready = false;

        boolean wanted = requested || captureAfterBackground;
        deferred = captureAfterBackground;
        captureAfterBackground = false;
        if (wanted) ensureTarget();
        if (!deferred) captureFrame();
    }

    /**
     * Creates the target, or replaces it when the window size changed. Only called before the GUI
     * prepares its elements, so no element of this frame can hold a view of a replaced texture.
     */
    private static void ensureTarget() {
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuTexture source = main.getColorTexture();
        if (source == null || main.width <= 0 || main.height <= 0) return;
        if (blurred != null && blurred.width == main.width && blurred.height == main.height) return;

        if (blurred != null) {
            PENDING_CLOSE.add(blurred);
            blurred = null;
        }
        // The new target holds nothing yet: this frame's panels are prepared without blur.
        ready = false;
        try {
            blurred = new TextureTarget("Alpaka chat blur", main.width, main.height, false, source.getFormat());
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Could not create the chat blur target; drawing the chat background without blur", e);
            }
        }
    }

    /**
     * Called at the GUI renderer's blur point, between the strata before it and those after.
     * Returns true when the copy was taken here, in which case vanilla's blur is to be skipped.
     */
    public static boolean captureAtBlurPoint() {
        if (!deferred) return false;
        deferred = false;
        // Only a screen's background defers the copy - the main menu's glass - and a menu keeps
        // the full blur; the strength setting is for the panels over the world.
        captureFrame(100.0f);
        return true;
    }

    /** Copies and blurs the frame as it is now, if a panel asked for it. */
    public static void captureFrame() {
        captureFrame(AlpakaConfig.instance.chatBlurStrength);
    }

    private static void captureFrame(float strengthPercent) {
        if (!requested) return;
        long t = net.alpaka.addons.utils.AlpakaPerf.begin(net.alpaka.addons.utils.AlpakaPerf.Section.BLUR_CAPTURE);
        try {
            captureFrameNow(strengthPercent);
        } finally {
            net.alpaka.addons.utils.AlpakaPerf.end(net.alpaka.addons.utils.AlpakaPerf.Section.BLUR_CAPTURE, t);
        }
    }

    /**
     * The blur radius, in screen pixels, for this GUI scale and strength: 3 per GUI scale step at
     * full strength, scaled down by the strength. 0 means no blur at all.
     */
    static int blurRadius(int guiScale, float strengthPercent) {
        int scale = Math.max(1, Math.min(4, guiScale));
        float strength = Mth.clamp(strengthPercent / 100.0f, 0.0f, 1.0f);
        return Math.min(MAX_BLUR_RADIUS, Math.round(RADIUS_PER_SCALE * scale * strength));
    }

    private static void captureFrameNow(float strengthPercent) {
        requested = false;
        ready = false;

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
        GpuTexture source = main.getColorTexture();
        if (source == null) return;

        int width = main.width;
        int height = main.height;
        if (width <= 0 || height <= 0) return;
        // Sized before the GUI was prepared (see ensureTarget); never replaced here, where this
        // frame's elements may already hold its view. A mismatch only skips the copy.
        if (blurred == null || blurred.width != width || blurred.height != height) return;

        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(source, blurred.getColorTexture(), 0, 0, 0, 0, 0, width, height);

            int radius = blurRadius((int) minecraft.getWindow().getGuiScale(), strengthPercent);
            if (radius <= 0) {
                // Strength 0: the plain copy is the panel's backdrop, the world sharp behind the tint.
                // No radius-0 effect exists, and box_blur would read 0 as the menu-blur setting.
                ready = true;
                return;
            }
            PostChain chain = minecraft.getShaderManager().getPostChain(
                    Identifier.fromNamespaceAndPath("alpaka", "chat_blur_r" + radius), LevelTargetBundle.MAIN_TARGETS);
            if (chain == null) {
                if (!warned) {
                    warned = true;
                    LOGGER.warn("Chat blur post effect is missing; drawing the chat background without blur");
                }
                return;
            }
            if (pool == null) pool = new CrossFrameResourcePool(3);
            chain.process(blurred, pool);
            pool.endFrame();
            ready = true;
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Chat blur failed; drawing the chat background without blur", e);
            }
            ready = false;
        }
    }

    /** The blurred frame for the panel's shader to sample, valid only while {@link #isReady()}. */
    public static TextureSetup textureSetup() {
        if (!isReady()) return TextureSetup.noTexture();
        return TextureSetup.singleTexture(blurred.getColorTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    }
}
