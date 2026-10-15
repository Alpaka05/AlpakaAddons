package net.alpaka.addons.features.chat;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.logging.LogUtils;
import net.alpaka.addons.client.gui.AlpakaGuiElementSink;
import net.alpaka.addons.client.gui.BlurRectRenderState;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix3x2f;
import org.slf4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;

/**
 * The screenshot, shown for a moment the way a phone does it: a white flash, the picture filling
 * the screen and shrinking into the bottom-right corner with a small overshoot, a few seconds
 * there in a white frame, then it slides out to the right.
 *
 * The file is read and scaled down off the render thread, then uploaded as a texture of its own,
 * which is released as soon as the preview is gone. It is drawn after everything else, toasts
 * aside, so it shows over menus as well.
 *
 * While the chat is open the thumbnail rests higher, clear of the input bar along the bottom, and
 * glides between the two places when the chat opens or closes.
 *
 * A screenshot taken while the preview is on screen would capture the preview itself, since the
 * game grabs the last finished frame. So that press is held back: the next frame is drawn without
 * the preview, and the screenshot is taken from that one.
 */
public final class ScreenshotPreview {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("alpaka", "screenshot_preview");

    /** The picture is scaled to at most this width before upload; plenty for a thumbnail. */
    private static final int MAX_TEXTURE_WIDTH = 960;

    private static final long FLASH_MS = 260L;
    private static final long SHRINK_MS = 650L;
    private static final long EXIT_MS = 380L;
    /** Thumbnail margin from the screen's edge, frame width and corner radius, in GUI pixels. */
    private static final float MARGIN = 10.0f;
    private static final float FRAME = 2.0f;
    private static final float RADIUS = 5.0f;
    private static final float SHADOW = 8.0f;
    /**
     * How far the thumbnail rises while the chat is open: the height of vanilla's input bar, whose
     * box starts 14 pixels above the screen's bottom edge, so the margin is kept above the bar.
     */
    private static final float CHAT_INPUT_HEIGHT = 14.0f;
    /** How quickly the thumbnail follows the chat opening or closing, as a time constant in ms. */
    private static final float LIFT_TAU = 60.0f;

    private static DynamicTexture texture;
    private static long shownAtMs;
    /** How far the thumbnail is lifted right now, easing towards {@link #liftTarget}. */
    private static float lift;
    private static long lastFrameMs;
    /** A screenshot press waiting for a frame without the preview; see the class comment. */
    private static Runnable pendingCapture;

    /** Dev tour only: a fixed point in the animation, in ms, or -1 to run it normally. */
    private static float frozenAtMs = -1.0f;

    private ScreenshotPreview() {}

    public static void devFreezeAt(float ms) {
        frozenAtMs = ms;
    }

    public static boolean isEnabled() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        return cfg.betterScreenshotMessageEnabled && cfg.screenshotPreviewEnabled;
    }

    /** Starts the preview for a freshly saved screenshot. Any thread. */
    public static void show(File file) {
        if (!isEnabled()) return;
        Util.ioPool().execute(() -> {
            NativeImage small;
            try (InputStream in = Files.newInputStream(file.toPath()); NativeImage full = NativeImage.read(in)) {
                int w = full.getWidth();
                int h = full.getHeight();
                float k = Math.min(1.0f, MAX_TEXTURE_WIDTH / (float) w);
                int tw = Math.max(1, Math.round(w * k));
                int th = Math.max(1, Math.round(h * k));
                small = new NativeImage(tw, th, false);
                full.resizeSubRectTo(0, 0, w, h, small);
            } catch (Throwable t) {
                LOGGER.warn("Couldn't load screenshot {} for the preview", file, t);
                return;
            }
            Minecraft.getInstance().execute(() -> install(small));
        });
    }

    private static void install(NativeImage image) {
        Minecraft mc = Minecraft.getInstance();
        release();
        texture = new DynamicTexture(() -> "Alpaka screenshot preview", image);
        mc.getTextureManager().register(TEXTURE, texture);
        shownAtMs = Util.getMillis();
        // Starts where it belongs: the shrink already lands it above an open chat's input bar.
        lift = liftTarget();
        lastFrameMs = shownAtMs;
    }

    /** Where the thumbnail should rest: above the input bar while the chat is open. */
    private static float liftTarget() {
        return Minecraft.getInstance().gui.screen() instanceof ChatScreen ? CHAT_INPUT_HEIGHT : 0.0f;
    }

    /** Moves the lift towards its target, at the same speed whatever the frame rate. */
    private static void updateLift() {
        long now = Util.getMillis();
        long deltaMs = Math.clamp(now - lastFrameMs, 0L, 250L);
        lastFrameMs = now;
        float target = liftTarget();
        lift += (target - lift) * (1.0f - (float) Math.exp(-deltaMs / LIFT_TAU));
        if (Math.abs(target - lift) < 0.01f) lift = target;
    }

    private static void release() {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE);
            texture = null;
        }
    }

    /** Whether a frame drawn now would contain the preview. */
    public static boolean isOnScreen() {
        return texture != null;
    }

    /**
     * A screenshot press while the preview is up: the preview goes, and the screenshot is taken once
     * a frame without it has been drawn.
     */
    public static void captureWithoutPreview(Runnable capture) {
        release();
        pendingCapture = capture;
    }

    private static float duration() {
        return SHRINK_MS + AlpakaConfig.instance.screenshotPreviewSeconds * 1000.0f + EXIT_MS;
    }

    /** Draws the preview over everything but the toasts. Called once per frame. */
    public static void render(GuiGraphicsExtractor graphics) {
        if (pendingCapture != null) {
            // This frame goes out without the preview; the capture runs before the next one.
            Runnable capture = pendingCapture;
            pendingCapture = null;
            Minecraft.getInstance().execute(capture);
            return;
        }
        if (texture == null) return;
        if (!isEnabled()) {
            release();
            return;
        }

        float elapsed = frozenAtMs >= 0.0f ? frozenAtMs : Util.getMillis() - shownAtMs;
        if (elapsed >= duration()) {
            release();
            return;
        }

        float sw = graphics.guiWidth();
        float sh = graphics.guiHeight();
        float guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();

        // Shrink: from the whole screen to the thumbnail, with a slight overshoot at the end.
        float e = easeOutBack(Math.min(1.0f, elapsed / SHRINK_MS));
        float thumbScale = Math.clamp(AlpakaConfig.instance.screenshotPreviewSize, 5.0f, 60.0f) / 100.0f;
        float thumbX = sw - MARGIN - sw * thumbScale;
        updateLift();
        float thumbY = sh - MARGIN - lift - sh * thumbScale;
        float s = lerp(1.0f, thumbScale, e);
        float x = lerp(0.0f, thumbX, e);
        float y = lerp(0.0f, thumbY, e);

        // Exit: out to the right, picking up speed.
        float exitStart = duration() - EXIT_MS;
        if (elapsed > exitStart) {
            float t = (elapsed - exitStart) / EXIT_MS;
            x += (sw - thumbX + SHADOW) * t * t * t;
        }

        // Above whatever the screen drew, its own blurred layers included.
        graphics.nextStratum();

        // The frame: white, rounded, with a drop shadow; it grows in as the picture shrinks.
        float frameIn = Math.clamp(e, 0.0f, 1.0f);
        if (graphics instanceof AlpakaGuiElementSink sink) {
            // In the picture's own coordinates, where one unit is s GUI pixels.
            int frame = Math.round(FRAME * frameIn / s);
            Matrix3x2f pose = new Matrix3x2f(graphics.pose()).translate(x, y).scale(s, s);
            sink.alpaka$submitElement(new BlurRectRenderState(pose,
                    -frame, -frame, Math.round(sw) + frame, Math.round(sh) + frame,
                    Math.round(RADIUS * frameIn * guiScale), 0xFFFFFFFF, guiScale * s, 1.0f,
                    Math.round(SHADOW * frameIn * guiScale), sink.alpaka$currentScissor()));
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(s, s);
        graphics.blit(texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                0, 0, Math.round(sw), Math.round(sh), 0.0f, 1.0f, 0.0f, 1.0f);
        graphics.pose().popMatrix();

        // The flash, over the whole screen and the picture alike.
        if (elapsed < FLASH_MS) {
            graphics.nextStratum();
            float f = 1.0f - elapsed / FLASH_MS;
            int alpha = Math.round(150.0f * f * f);
            graphics.fill(0, 0, Math.round(sw) + 1, Math.round(sh) + 1, (alpha << 24) | 0xFFFFFF);
        }
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /** Overshoots its end a little and settles back, like a spring with some damping. */
    private static float easeOutBack(float t) {
        float c1 = 1.15f;
        float c3 = c1 + 1.0f;
        float u = t - 1.0f;
        return 1.0f + c3 * u * u * u + c1 * u * u;
    }
}
