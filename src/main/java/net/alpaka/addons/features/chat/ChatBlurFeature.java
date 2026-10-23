package net.alpaka.addons.features.chat;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.alpaka.addons.client.gui.AlpakaGuiElementSink;
import net.alpaka.addons.client.gui.BlurRectRenderState;
import net.alpaka.addons.client.gui.RoundedRectRenderState;
import net.alpaka.addons.compat.GnetumCompat;
import org.joml.Vector2f;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;
import java.util.function.IntPredicate;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
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
 * blurred into targets of this class's own by a dual Kawase blur ({@link DualKawaseBlur}): a few
 * halvings and doublings at a fraction of the screen's size, which costs far less than a blur at
 * full size. The width comes from the GUI scale and the Blur Strength setting ({@link #blurRadius}),
 * so the blur is the same width in GUI pixels whatever the window scale, and independent of the
 * player's menu-blur setting. The panel's fragment shader then samples the blurred frame at its
 * own screen position, tints it with the chat's background color and fades the rounded edge over
 * one pixel.
 *
 * The blur only runs in frames where a panel asked for it ({@link #request()}), so a hidden chat
 * costs nothing.
 *
 * ### The HUD under the chat
 *
 * The chat is drawn after the action bar, the hotbar and the held item's name, and a panel drawn
 * in its place would cover them with the world as it was before the HUD. So, outside menus, the
 * glass goes under the whole HUD instead ({@link net.alpaka.addons.client.gui.AlpakaGuiUnderlay}),
 * and in the chat's own place only a translucent veil of the chat's color is drawn: whatever of
 * the HUD lies under the chat stays readable, dimmed as if behind the glass, with the chat's text
 * on top. Glass and veil together are as dark over the world as the panel alone would be.
 *
 * ### Resizes
 *
 * The GUI renderer asks each element for its texture while preparing the frame, before anything is
 * drawn. With the blur deferred to the blur point ({@link #captureAfterBackground()}), that is
 * before the blur of this frame exists. So the targets are sized in {@link #captureFrameBeforeGui()},
 * before the elements are prepared, and never replaced later in the frame. Targets that no longer
 * fit are set aside and closed a frame later, and the frame that replaced them draws its panels
 * without blur. Destroying them at the blur point instead, as this once did, left the main menu's
 * glass tabs holding a closed texture, and resizing the window crashed the game.
 *
 * ### Other passes of the GUI renderer
 *
 * Only the frame's own GUI pass draws onto the world. A HUD-caching mod such as Gnetum runs the GUI
 * renderer a second time, during extraction, into a cleared framebuffer of its own; a blur taken
 * there is of that empty buffer, and the panels came out black. Such a pass neither runs the blur
 * nor samples it ({@link #isReady()}): its panels fall back to the plain tint, and the panels ask
 * Gnetum to leave their HUD out of its cache ({@link GnetumCompat}), so they are drawn in the
 * frame's pass and blur there. The glass under the HUD is put into its layer outside Gnetum's pass,
 * once Gnetum hands the chat's elements to the frame ({@link GnetumCompat#deferUnderlay}).
 */
public final class ChatBlurFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger("AlpakaAddons/ChatBlur");

    /** How wide the panel grows past vanilla's own box, in chat pixels. */
    public static final int PADDING = 3;
    /** The panel's corner radius in chat pixels. */
    public static final int RADIUS = 5;
    /** How far the panel's drop shadow reaches, in chat pixels. */
    public static final int SHADOW = 4;

    /** Blur radius per GUI scale step at full strength: 5 screen pixels for each GUI pixel. */
    private static final int RADIUS_PER_SCALE = 5;
    /** The widest blur: full strength at GUI scale 4. */
    static final int MAX_BLUR_RADIUS = 4 * RADIUS_PER_SCALE;

    /**
     * How much of the chat's tint is the veil over the HUD rather than the glass under it: the HUD
     * under the chat shows at 1 - this share of the tint's strength.
     */
    static final float VEIL_SHARE = 0.6f;

    private static final DualKawaseBlur BLUR = new DualKawaseBlur();
    /** At strength 0: a plain copy of the frame, the world sharp behind the tint. */
    private static TextureTarget sharp;
    /** Targets replaced after a resize, closed at the start of the next frame. */
    private static final java.util.List<TextureTarget> PENDING_CLOSE = new java.util.ArrayList<>();
    /** This frame's blur, decided before the GUI is prepared; NONE samples the sharp copy. */
    private static DualKawaseBlur.Plan framePlan = DualKawaseBlur.Plan.NONE;
    private static boolean requested;
    private static boolean ready;
    private static boolean warned;
    /** Whether the GUI renderer is drawing the frame's own GUI, onto the world, rather than another pass. */
    private static boolean inFramePass;

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

    /** The lift of the message under the mouse, over the panel's own tint. */
    private static final int HOVER_HIGHLIGHT = 0x22FFFFFF;

    private ChatBlurFeature() {}

    public static boolean isEnabled() {
        return AlpakaConfig.instance.betterChatOn(AlpakaConfig.instance.chatBlurEnabled);
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
     * before the chat draws its text, so the text lands on top, and before the chat box's scissor
     * is set, so the panel's drop shadow is not cut off at the box.
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

        // Before the submission, which Gnetum then hands to the frame's own pass; see request().
        request();
        // Submitted before the lines are shifted by the scroll, so the current pose is the box's.
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        int radius = Math.round(RADIUS * toScreen);
        int shadow = Math.round(SHADOW * toScreen * panelAlpha);
        ScreenRectangle scissor = sink.alpaka$currentScissor();
        if (!underHud()) {
            sink.alpaka$submitElement(new BlurRectRenderState(
                    pose, x0, y0, x1, y1, radius, alpha << 24, toScreen, 1.0f, shadow, scissor));
            return;
        }
        float tint = alpha / 255.0f;
        float veil = tint * VEIL_SHARE;
        int glassAlpha = Math.round(glassTint(tint, veil) * 255.0f);
        int veilAlpha = Math.round(veil * 255.0f);
        sink.alpaka$submitUnderlay(new BlurRectRenderState(
                pose, x0, y0, x1, y1, radius, glassAlpha << 24, toScreen, 1.0f, shadow, scissor));
        if (veilAlpha > 0) {
            sink.alpaka$submitElement(new RoundedRectRenderState(
                    pose, x0, y0, x1, y1, radius, 0, veilAlpha << 24, Math.max(1, Math.round(toScreen)), scissor));
        }
    }

    /**
     * The glass's tint under a veil of the given strength, such that both together cover the world
     * as much as the tint alone: 1 - (1 - glass)(1 - veil) = tint.
     */
    static float glassTint(float tint, float veil) {
        if (veil >= 1.0f) return 0.0f;
        return Mth.clamp(1.0f - (1.0f - tint) / (1.0f - veil), 0.0f, 1.0f);
    }

    /**
     * Whether the glass goes under the whole HUD. Not with a menu open: its background covers the
     * HUD, and a chat drawn over the menu - Chat Peek - would lose its glass under it. Not into
     * Gnetum's cache either, whose image is drawn over the frame's lowest layer.
     */
    private static boolean underHud() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return (screen == null || screen instanceof ChatScreen) && !GnetumCompat.isCaching();
    }

    /**
     * With the chat open, lifts the message under the mouse a little - all of its lines, when it
     * wraps. Called after the panel, once the chat box's scissor is set, and still before the lines
     * are shifted by the scroll: the mouse is taken into the lines' own coordinates, so the lift
     * follows the lines while they scroll. A message reaching past the box is cut off by the
     * scissor. Never while peeking: the chat is only held open there, and in the world the mouse
     * position is the hidden cursor that moves with the camera.
     *
     * @param continuesBelow whether the line at a row, counted up from the box's bottom, is not
     *                       the last of its message, so the message goes on in the row below
     */
    public static void submitHoverHighlight(float chatScale, IntPredicate continuesBelow) {
        GuiGraphicsExtractor graphics = currentGraphics;
        if (graphics == null || !isEnabled() || !foreground || ChatPeekFeature.isPeeking()) return;
        if (panelHeight <= 0.01f || panelAlpha <= 0.002f) return;
        if (panelLines <= 0 || panelLineHeight <= 0) return;
        if (!(graphics instanceof AlpakaGuiElementSink sink)) return;

        float toScreen = (float) Minecraft.getInstance().getWindow().getGuiScale() * chatScale;
        int x0 = -4 - PADDING;
        int x1 = panelWidth + 8 + PADDING;
        Matrix3x2f linePose = new Matrix3x2f(graphics.pose()).translate(0.0f, scrollOffset);
        Matrix3x2f inverse = new Matrix3x2f(linePose).invert();
        Vector2f local = inverse.transformPosition(new Vector2f(mouseX, mouseY));
        if (local.x < x0 || local.x > x1 || local.y > panelBottom) return;
        int line = Mth.floor((panelBottom - local.y) / panelLineHeight);
        if (line < 0 || line >= panelLines) return;
        int top = panelBottom - (messageTop(line, continuesBelow) + 1) * panelLineHeight;
        int bottom = panelBottom - messageBottom(line, continuesBelow) * panelLineHeight;
        sink.alpaka$submitElement(new RoundedRectRenderState(
                linePose, x0 + 1, top, x1 - 1, bottom,
                Math.round(3 * toScreen), 0, HOVER_HIGHLIGHT,
                Math.max(1, Math.round(toScreen)), sink.alpaka$currentScissor()));
    }

    /** The bottom row of the message the given row belongs to; rows count up from the box's bottom. */
    static int messageBottom(int row, IntPredicate continuesBelow) {
        while (continuesBelow.test(row)) row--;
        return row;
    }

    /** The top row of the message the given row belongs to; rows count up from the box's bottom. */
    static int messageTop(int row, IntPredicate continuesBelow) {
        while (continuesBelow.test(row + 1)) row++;
        return row;
    }

    /**
     * A panel is extracted this frame; the frame has to be captured and blurred before drawing.
     * Called right before the panel is submitted: a HUD Gnetum is caching is taken out of its cache
     * then, and Gnetum passes that very submission on to the frame's own GUI pass.
     */
    public static void request() {
        requested = true;
        GnetumCompat.keepOutOfCache();
    }

    /**
     * Whether a blurred copy exists for the panel to sample; false falls back to a plain panel.
     * Never in a GUI pass other than the frame's own, whose target is not the frame the copy shows.
     */
    public static boolean isReady() {
        return ready && inFramePass && (framePlan.levels() > 0 ? BLUR.exists() : sharp != null);
    }

    /** The GameRenderer starts or ends drawing the frame's own GUI, onto the world. */
    public static void setInFramePass(boolean value) {
        inFramePass = value;
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
        // Another pass of the GUI renderer, into a target of its own: nothing of the world to copy,
        // and this frame's request stays for the frame's own pass.
        if (!inFramePass) return;

        // Last frame's draws are done, so nothing can still sample the targets set aside then.
        for (TextureTarget old : PENDING_CLOSE) old.destroyBuffers();
        PENDING_CLOSE.clear();

        // A deferred blur that never reached the blur point leaves the last one stale; draw this
        // frame's panels without blur rather than sample it.
        if (deferred) ready = false;

        boolean wanted = requested || captureAfterBackground;
        deferred = captureAfterBackground;
        captureAfterBackground = false;
        // Only a screen's background defers the blur - the main menu's glass - and a menu keeps
        // the full blur; the strength setting is for the panels over the world.
        DualKawaseBlur.Plan plan = DualKawaseBlur.plan(blurRadius(
                (int) Minecraft.getInstance().getWindow().getGuiScale(),
                deferred ? 100.0f : AlpakaConfig.instance.chatBlurStrength));
        if (!plan.equals(framePlan)) {
            // The panels sample another target than last frame's, which holds nothing yet.
            if ((plan.levels() == 0) != (framePlan.levels() == 0)) ready = false;
            framePlan = plan;
        }
        if (wanted) ensureTargets();
        if (!deferred) captureFrame();
    }

    /**
     * Creates the targets this frame's plan samples, or replaces them when the window size
     * changed. Only called before the GUI prepares its elements, so no element of this frame can
     * hold a view of a replaced texture.
     */
    private static void ensureTargets() {
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuTexture source = main.getColorTexture();
        if (source == null || main.width <= 0 || main.height <= 0) return;
        try {
            if (framePlan.levels() > 0) {
                if (BLUR.fits(main.width, main.height)) return;
                BLUR.resize(main.width, main.height, source.getFormat(), PENDING_CLOSE);
            } else {
                if (sharp != null && sharp.width == main.width && sharp.height == main.height) return;
                if (sharp != null) PENDING_CLOSE.add(sharp);
                sharp = null;
                sharp = new TextureTarget("Alpaka chat backdrop", main.width, main.height, false, source.getFormat());
            }
            // The new targets hold nothing yet: this frame's panels are prepared without blur.
            ready = false;
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Could not create the chat blur targets; drawing the chat background without blur", e);
            }
        }
    }

    /**
     * Called at the GUI renderer's blur point, between the strata before it and those after.
     * Returns true when the blur ran here, in which case vanilla's blur is to be skipped.
     */
    public static boolean captureAtBlurPoint() {
        if (!deferred || !inFramePass) return false;
        deferred = false;
        captureFrame();
        return true;
    }

    /** Blurs the frame as it is now, if a panel asked for it. */
    public static void captureFrame() {
        if (!requested) return;
        long t = net.alpaka.addons.utils.AlpakaPerf.begin(net.alpaka.addons.utils.AlpakaPerf.Section.BLUR_CAPTURE);
        try {
            captureFrameNow();
        } finally {
            net.alpaka.addons.utils.AlpakaPerf.end(net.alpaka.addons.utils.AlpakaPerf.Section.BLUR_CAPTURE, t);
        }
    }

    /**
     * The blur radius, in screen pixels, for this GUI scale and strength: 5 per GUI scale step at
     * full strength, scaled down by the strength. 0 means no blur at all.
     */
    static int blurRadius(int guiScale, float strengthPercent) {
        int scale = Math.max(1, Math.min(4, guiScale));
        float strength = Mth.clamp(strengthPercent / 100.0f, 0.0f, 1.0f);
        return Math.min(MAX_BLUR_RADIUS, Math.round(RADIUS_PER_SCALE * scale * strength));
    }

    private static void captureFrameNow() {
        requested = false;
        ready = false;

        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        GpuTexture source = main.getColorTexture();
        if (source == null || main.width <= 0 || main.height <= 0) return;

        try {
            if (framePlan.levels() == 0) {
                // Strength 0: the plain copy is the panel's backdrop, the world sharp behind the tint.
                // Sized before the GUI was prepared (see ensureTargets); never replaced here, where
                // this frame's elements may already hold its view. A mismatch only skips the copy.
                if (sharp == null || sharp.width != main.width || sharp.height != main.height) return;
                RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                        source, sharp.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
            } else {
                if (!BLUR.fits(main.width, main.height)) return;
                BLUR.run(main.getColorTextureView(), framePlan);
            }
            ready = true;
        } catch (RuntimeException e) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Chat blur failed; drawing the chat background without blur", e);
            }
        }
    }

    /** The pipelines of the blur, registered with vanilla's at client start. */
    public static void init() {
        DualKawaseBlur.init();
    }

    /** The blurred frame for the panel's shader to sample, valid only while {@link #isReady()}. */
    public static TextureSetup textureSetup() {
        if (!isReady()) return TextureSetup.noTexture();
        GpuTextureView view = framePlan.levels() > 0 ? BLUR.output() : sharp.getColorTextureView();
        return TextureSetup.singleTexture(view,
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    }
}
