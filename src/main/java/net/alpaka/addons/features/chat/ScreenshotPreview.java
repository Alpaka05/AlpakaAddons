package net.alpaka.addons.features.chat;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.logging.LogUtils;
import net.alpaka.addons.client.gui.AlpakaGuiElementSink;
import net.alpaka.addons.client.gui.RoundedImageRenderState;
import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.joml.Matrix3x2f;
import org.slf4j.Logger;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The screenshot, shown for a moment the way a phone does it: a white flash, the picture filling
 * the screen and shrinking into the bottom-right corner with a small overshoot, a few seconds
 * there with rounded corners and a soft shadow, then it slides out to the right.
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
 * the preview, and the screenshot is taken from that one, in the next round of the game loop's
 * tasks - not from inside the frame, where the target still holds the one with the preview.
 *
 * With a screen open, so the cursor is free, the thumbnail can be clicked: what that does is the
 * Preview Click setting. While the cursor rests on it, it grows a little and its time stands still.
 */
public final class ScreenshotPreview {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("alpaka", "screenshot_preview");

    /** The picture is scaled to at most this width before upload; plenty for a thumbnail. */
    private static final int MAX_TEXTURE_WIDTH = 960;

    private static final long FLASH_MS = 260L;
    private static final long SHRINK_MS = 650L;
    private static final long EXIT_MS = 380L;
    /** Thumbnail margin from the screen's edge, corner radius and shadow reach, in GUI pixels. */
    private static final float MARGIN = 10.0f;
    private static final float RADIUS = 5.0f;
    private static final float SHADOW = 8.0f;
    /**
     * How far the thumbnail rises while the chat is open: the height of vanilla's input bar, whose
     * box starts 14 pixels above the screen's bottom edge, so the margin is kept above the bar.
     */
    private static final float CHAT_INPUT_HEIGHT = 14.0f;
    /** How quickly the thumbnail follows the chat opening or closing, as a time constant in ms. */
    private static final float LIFT_TAU = 60.0f;

    /** How much the thumbnail grows while the cursor is on it, and how fast it gets there. */
    private static final float HOVER_GROW = 0.04f;
    private static final float HOVER_MS = 120.0f;

    private static DynamicTexture texture;
    /** The screenshot on screen; what a click on the thumbnail acts on. */
    private static File file;
    private static long lastHoverMs;
    private static float hover;
    /** Where the thumbnail was last drawn, in GUI pixels; clickable only while it rests in the corner. */
    private static boolean clickable;
    private static float hitX, hitY, hitW, hitH;
    private static long shownAtMs;
    /** How far the thumbnail is lifted right now, easing towards {@link #liftTarget}. */
    private static float lift;
    private static long lastFrameMs;
    /** Screenshot presses waiting for a frame without the preview; see the class comment. */
    private static final List<Runnable> pendingCaptures = new ArrayList<>();

    /** Dev tour only: a fixed point in the animation, in ms, or -1 to run it normally. */
    private static float frozenAtMs = -1.0f;

    private ScreenshotPreview() {}

    public static void devFreezeAt(float ms) {
        frozenAtMs = ms;
    }

    /** Clicks on the thumbnail, in every screen. */
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !mouseClicked(event)));
    }

    public static boolean isEnabled() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        return cfg.betterScreenshotMessageEnabled && cfg.screenshotPreviewEnabled;
    }

    /** Starts the preview for a freshly saved screenshot. Any thread. */
    public static void show(File file) {
        if (!isEnabled()) return;
        Util.ioPool().execute(() -> {
            NativeImage small = null;
            try (InputStream in = Files.newInputStream(file.toPath()); NativeImage full = NativeImage.read(in)) {
                int w = full.getWidth();
                int h = full.getHeight();
                float k = Math.min(1.0f, MAX_TEXTURE_WIDTH / (float) w);
                int tw = Math.max(1, Math.round(w * k));
                int th = Math.max(1, Math.round(h * k));
                small = new NativeImage(tw, th, false);
                full.resizeSubRectTo(0, 0, w, h, small);
            } catch (Throwable t) {
                if (small != null) small.close();
                LOGGER.warn("Couldn't load screenshot {} for the preview", file, t);
                return;
            }
            NativeImage image = small;
            Minecraft.getInstance().execute(() -> install(file, image));
        });
    }

    private static void install(File shown, NativeImage image) {
        Minecraft mc = Minecraft.getInstance();
        release();
        texture = new DynamicTexture(() -> "Alpaka screenshot preview", image);
        mc.getTextureManager().register(TEXTURE, texture);
        file = shown;
        shownAtMs = Util.getMillis();
        lastHoverMs = shownAtMs;
        hover = 0.0f;
        hitW = 0.0f;
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
        file = null;
        clickable = false;
    }

    /**
     * A click in an open screen. True when it hit the thumbnail and was used, so the screen under it
     * does not get it as well.
     */
    public static boolean mouseClicked(MouseButtonEvent event) {
        if (!clickable || file == null || event.button() != 0) return false;
        if (!contains(event.x(), event.y())) return false;
        if (AlpakaConfig.instance.screenshotPreviewClickAction == ScreenshotMessageFeature.PREVIEW_CLICK_NOTHING) {
            return false;
        }
        ScreenshotMessageFeature.previewClicked(file);
        return true;
    }

    private static boolean contains(double mx, double my) {
        return mx >= hitX && mx < hitX + hitW && my >= hitY && my < hitY + hitH;
    }

    /** The cursor's position in GUI pixels, or null when no screen is open and the cursor is captured. */
    private static double[] cursor(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() == null) return null;
        var window = mc.getWindow();
        return new double[] {
                mc.mouseHandler.xpos() * graphics.guiWidth() / window.getScreenWidth(),
                mc.mouseHandler.ypos() * graphics.guiHeight() / window.getScreenHeight()
        };
    }

    /** Whether a frame drawn now would contain the preview, or a capture is already waiting for one without. */
    public static boolean isOnScreen() {
        return texture != null || !pendingCaptures.isEmpty();
    }

    /**
     * A screenshot press while the preview is up: the preview goes, and the screenshot is taken once
     * a frame without it has been drawn.
     */
    public static void captureWithoutPreview(Runnable capture) {
        release();
        pendingCaptures.add(capture);
    }

    private static float duration() {
        return SHRINK_MS + AlpakaConfig.instance.screenshotPreviewSeconds * 1000.0f + EXIT_MS;
    }

    /** Draws the preview over everything but the toasts. Called once per frame. */
    public static void render(GuiGraphicsExtractor graphics) {
        if (!pendingCaptures.isEmpty()) {
            // This frame goes out without the preview; the captures run before the next one. Not
            // execute(): on the render thread that would run them now, inside this frame.
            Minecraft mc = Minecraft.getInstance();
            for (Runnable capture : pendingCaptures) mc.schedule(capture);
            pendingCaptures.clear();
            return;
        }
        if (texture == null) return;
        if (!isEnabled()) {
            release();
            return;
        }

        long now = Util.getMillis();
        long frameMs = now - lastHoverMs;
        lastHoverMs = now;
        clickable = false;

        // The cursor resting on the resting thumbnail holds the clock, so the picture stays put.
        double[] mouse = cursor(graphics);
        boolean hovered = mouse != null && hitW > 0.0f && contains(mouse[0], mouse[1])
                && AlpakaConfig.instance.screenshotPreviewClickAction != ScreenshotMessageFeature.PREVIEW_CLICK_NOTHING;
        float elapsed = frozenAtMs >= 0.0f ? frozenAtMs : now - shownAtMs;
        boolean resting = elapsed >= SHRINK_MS && elapsed < duration() - EXIT_MS;
        if (hovered && resting && frozenAtMs < 0.0f) {
            shownAtMs += frameMs;
            elapsed = now - shownAtMs;
        }
        hover = Math.clamp(hover + (hovered && resting ? 1 : -1) * frameMs / HOVER_MS, 0.0f, 1.0f);
        if (elapsed >= duration()) {
            release();
            hitW = 0.0f;
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

        // Hovered: a little larger, growing out of the corner it sits in.
        float grow = 1.0f + HOVER_GROW * easeOut(hover);
        x -= sw * s * (grow - 1.0f);
        y -= sh * s * (grow - 1.0f);
        s *= grow;

        // Exit: out to the right, picking up speed.
        float exitStart = duration() - EXIT_MS;
        if (elapsed > exitStart) {
            float t = (elapsed - exitStart) / EXIT_MS;
            x += (sw - thumbX + SHADOW) * t * t * t;
        }

        hitX = x;
        hitY = y;
        hitW = sw * s;
        hitH = sh * s;
        clickable = resting;
        if (hovered && resting) graphics.requestCursor(CursorTypes.POINTING_HAND);

        // Above whatever the screen drew, its own blurred layers included.
        graphics.nextStratum();

        // The picture itself, its corners rounding off and its shadow growing in as it shrinks.
        float frameIn = Math.clamp(e, 0.0f, 1.0f);
        Matrix3x2f pose = new Matrix3x2f(graphics.pose()).translate(x, y).scale(s, s);
        TextureSetup textures = TextureSetup.singleTexture(texture.getTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
        if (graphics instanceof AlpakaGuiElementSink sink) {
            sink.alpaka$submitElement(new RoundedImageRenderState(pose, 0, 0, Math.round(sw), Math.round(sh),
                    Math.round(RADIUS * frameIn * guiScale), guiScale * s,
                    Math.round(SHADOW * frameIn * guiScale), textures, sink.alpaka$currentScissor()));
        } else {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(s, s);
            graphics.blit(texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                    0, 0, Math.round(sw), Math.round(sh), 0.0f, 1.0f, 0.0f, 1.0f);
            graphics.pose().popMatrix();
        }

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

    private static float easeOut(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u;
    }

    /** Overshoots its end a little and settles back, like a spring with some damping. */
    private static float easeOutBack(float t) {
        float c1 = 1.15f;
        float c3 = c1 + 1.0f;
        float u = t - 1.0f;
        return 1.0f + c3 * u * u * u + c1 * u * u;
    }
}
