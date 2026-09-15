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
 * one of this mod's own post-effect definitions instead, picked by GUI scale so the blur is the
 * same width in GUI pixels whatever the window scale, and independent of the player's menu-blur
 * setting. The panel's fragment shader then samples the blurred copy at its own screen position,
 * tints it with the chat's background colour and fades the rounded edge over one pixel.
 *
 * The copy is only made in frames where a panel asked for it ({@link #request()}), so a hidden
 * chat costs nothing, and the sampled view is looked up at draw time ({@link #textureSetup()}),
 * after the copy of that frame exists, so a window resize between extraction and drawing can never
 * hand the GUI a view of a texture that has just been replaced.
 */
public final class ChatBlurFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger("AlpakaAddons/ChatBlur");

    /** How wide the panel grows past vanilla's own box, in chat pixels. */
    public static final int PADDING = 3;
    /** The panel's corner radius in chat pixels. */
    public static final int RADIUS = 5;

    private static TextureTarget blurred;
    private static CrossFrameResourcePool pool;
    private static boolean requested;
    private static boolean ready;
    private static boolean warned;

    // The extraction in progress: the graphics to submit the panel to, and what the chat's own
    // background pass measured. Vanilla draws one box per line in that pass; with the panel on,
    // those boxes are only measured, and the panel is submitted in their place before the text.
    private static GuiGraphicsExtractor currentGraphics;
    private static float panelHeight;
    private static float panelAlpha;
    private static int panelBottom;
    private static int panelWidth;
    private static float panelOpacity;

    private ChatBlurFeature() {}

    public static boolean isEnabled() {
        return AlpakaConfig.instance.chatBlurEnabled;
    }

    /** The chat is about to be extracted into this graphics. */
    public static void beginExtraction(GuiGraphicsExtractor graphics) {
        currentGraphics = graphics;
        resetPanel();
    }

    public static void endExtraction() {
        currentGraphics = null;
    }

    public static void resetPanel() {
        panelHeight = 0.0f;
        panelAlpha = 0.0f;
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
        panelOpacity = backgroundOpacity;
        panelHeight += alpha * lineHeight;
        panelAlpha = Math.max(panelAlpha, alpha);
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
        int y0 = Math.round(panelBottom - panelHeight) - PADDING;
        int y1 = panelBottom + PADDING;
        int alpha = Math.round(Mth.clamp(panelAlpha * panelOpacity, 0.0f, 1.0f) * 255.0f);
        if (alpha <= 0) return;

        sink.alpaka$submitElement(new BlurRectRenderState(
                new Matrix3x2f(graphics.pose()), x0, y0, x1, y1,
                Math.round(RADIUS * toScreen), alpha << 24, toScreen, sink.alpaka$currentScissor()));
        request();
    }

    /** A panel was extracted this frame; the frame has to be captured and blurred before drawing. */
    public static void request() {
        requested = true;
    }

    /** Whether a blurred copy exists for the panel to sample; false falls back to a plain panel. */
    public static boolean isReady() {
        return ready && blurred != null;
    }

    /** Called at the start of the GUI renderer's frame, before any GUI element is drawn. */
    public static void captureFrame() {
        if (!requested) return;
        requested = false;
        ready = false;

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget main = minecraft.gameRenderer.mainRenderTarget();
        GpuTexture source = main.getColorTexture();
        if (source == null) return;

        int width = main.width;
        int height = main.height;
        if (width <= 0 || height <= 0) return;

        try {
            if (blurred == null || blurred.width != width || blurred.height != height) {
                if (blurred != null) blurred.destroyBuffers();
                blurred = new TextureTarget("Alpaka chat blur", width, height, false, source.getFormat());
            }
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.copyTextureToTexture(source, blurred.getColorTexture(), 0, 0, 0, 0, 0, width, height);

            int scale = Math.max(1, Math.min(4, (int) minecraft.getWindow().getGuiScale()));
            PostChain chain = minecraft.getShaderManager().getPostChain(
                    Identifier.fromNamespaceAndPath("alpaka", "chat_blur_s" + scale), LevelTargetBundle.MAIN_TARGETS);
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
