package net.alpaka.addons.features.performance;

import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.HudPanel;
import net.alpaka.addons.client.hud.VisiblePlacement;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.worldage.WorldAgeFeature;
import net.alpaka.addons.features.worldage.WorldClockTracker;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Util;

import java.util.Locale;

/** One line on the HUD with the server's day, the frame rate, the ping and the server's tick rate. */
public final class PerformanceHudRenderer {
    private static final int LABEL = 0xFFAAAAAA;
    private static final int GOOD = 0xFF55FF55;
    private static final int FAIR = 0xFFFFAA00;
    private static final int POOR = 0xFFFF5555;
    private static final int UNKNOWN = 0xFF777777;

    /**
     * The drawn line, kept between frames. Its numbers change about once a second at most, so it is
     * rebuilt only when one of them, the parts shown or the font does; the box is measured from it.
     */
    private static long cachedDay = Long.MIN_VALUE;
    private static int cachedFps = Integer.MIN_VALUE;
    private static int cachedPing = Integer.MIN_VALUE;
    private static int cachedTenthTps = Integer.MIN_VALUE;
    private static int cachedParts = -1;
    private static Style cachedStyle = null;
    private static Component cachedText = null;

    /** The values of the four parts, in the order they are drawn, each rolling when it changes. */
    private static final RollingValue DAY = new RollingValue();
    private static final RollingValue FPS = new RollingValue();
    private static final RollingValue PING = new RollingValue();
    private static final RollingValue TPS = new RollingValue();

    private PerformanceHudRenderer() {}

    static Component text() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        int parts = (cfg.performanceHudShowFps ? 1 : 0) | (cfg.performanceHudShowPing ? 2 : 0) | (cfg.performanceHudShowTps ? 4 : 0)
                | (cfg.performanceHudShowDay ? 8 : 0);
        long day = cfg.performanceHudShowDay ? WorldAgeFeature.getWorldDay() : 0L;
        int fps = cfg.performanceHudShowFps ? PerformanceStats.fps() : 0;
        int ping = cfg.performanceHudShowPing ? PerformanceStats.pingMs() : 0;
        float tps = cfg.performanceHudShowTps ? PerformanceStats.tps() : 0.0f;
        int tenthTps = Float.isNaN(tps) ? -1 : Math.round(tps * 10.0f);
        boolean smooth = cfg.smoothOn(cfg.performanceHudSmoothFont);
        Style style = smooth ? GuiFont.style() : Style.EMPTY;

        if (cachedText == null || parts != cachedParts || day != cachedDay || fps != cachedFps || ping != cachedPing
                || tenthTps != cachedTenthTps || !style.equals(cachedStyle)) {
            cachedParts = parts;
            cachedDay = day;
            cachedFps = fps;
            cachedPing = ping;
            cachedTenthTps = tenthTps;
            cachedStyle = style;
            cachedText = HudPanel.text(build(parts, day, fps, ping, tenthTps), smooth);
        }
        return cachedText;
    }

    private static Component build(int parts, long day, int fps, int ping, int tenthTps) {
        long now = Util.getMillis();
        MutableComponent line = Component.empty();
        if ((parts & 8) != 0) {
            if (day == WorldClockTracker.UNKNOWN_DAY) part(line, "Day ", DAY, "--", UNKNOWN, Double.NaN, now);
            else part(line, "Day ", DAY, Long.toString(day), WorldAgeFeature.getDayColor(day), day, now);
        }
        if ((parts & 1) != 0) {
            part(line, "FPS ", FPS, Integer.toString(fps), fps >= 60 ? GOOD : fps >= 30 ? FAIR : POOR, fps, now);
        }
        if ((parts & 2) != 0) {
            if (ping < 0) part(line, "Ping ", PING, "--", UNKNOWN, Double.NaN, now);
            else part(line, "Ping ", PING, ping + "ms", ping <= 150 ? GOOD : ping <= 250 ? FAIR : POOR, ping, now);
        }
        if ((parts & 4) != 0) {
            if (tenthTps < 0) part(line, "TPS ", TPS, "--", UNKNOWN, Double.NaN, now);
            else part(line, "TPS ", TPS, String.format(Locale.ROOT, "%.1f", tenthTps / 10.0f),
                    tenthTps >= 180 ? GOOD : tenthTps >= 150 ? FAIR : POOR, tenthTps, now);
        }
        return line;
    }

    private static void part(MutableComponent line, String label, RollingValue rolling, String value, int color,
                             double number, long now) {
        rolling.set(value, color, number, now);
        if (!line.getSiblings().isEmpty()) line.append(Component.literal("  "));
        line.append(Component.literal(label).withColor(LABEL));
        line.append(Component.literal(value).withColor(color));
    }

    /**
     * Draws the line part by part while a value is still rolling, if Animate Changes is on. Outside a roll the cached line is
     * drawn in one go, so the HUD costs nothing extra between changes.
     */
    private static boolean drawRolling(GuiGraphicsExtractor graphics, Font font, Style style, boolean shadow) {
        AlpakaConfig cfg = AlpakaConfig.instance;
        long now = Util.getMillis();
        if (!cfg.performanceHudAnimate) return false;
        if (!(cfg.performanceHudShowDay && DAY.animating(now) || cfg.performanceHudShowFps && FPS.animating(now)
                || cfg.performanceHudShowPing && PING.animating(now) || cfg.performanceHudShowTps && TPS.animating(now))) {
            return false;
        }
        float x = 0.0f;
        boolean first = true;
        if (cfg.performanceHudShowDay) { x = drawPart(graphics, font, style, shadow, x, first, "Day ", DAY, now); first = false; }
        if (cfg.performanceHudShowFps) { x = drawPart(graphics, font, style, shadow, x, first, "FPS ", FPS, now); first = false; }
        if (cfg.performanceHudShowPing) { x = drawPart(graphics, font, style, shadow, x, first, "Ping ", PING, now); first = false; }
        if (cfg.performanceHudShowTps) drawPart(graphics, font, style, shadow, x, first, "TPS ", TPS, now);
        return true;
    }

    private static float drawPart(GuiGraphicsExtractor graphics, Font font, Style style, boolean shadow, float x,
                                  boolean first, String label, RollingValue rolling, long now) {
        String text = first ? label : "  " + label;
        RollingValue.text(graphics, font, text, style, LABEL, 1.0f, x, 0.0f, shadow);
        x += RollingValue.width(font, text, style);
        return x + rolling.draw(graphics, font, style, x, now, shadow);
    }

    private static boolean anyPartShown() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        return cfg.performanceHudShowDay || cfg.performanceHudShowFps || cfg.performanceHudShowPing || cfg.performanceHudShowTps;
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        AlpakaConfig cfg = AlpakaConfig.instance;
        if (!cfg.performanceHudEnabled || !anyPartShown()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || mc.level == null || mc.player == null) return;
        // Hidden in menus, as the other HUDs are; the chat screen keeps it.
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) return;

        PerformanceStats.markShown();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        VisiblePlacement placement = PerformanceHudElement.INSTANCE.visiblePlacement(screenWidth, screenHeight);
        renderHud(graphics, placement.getAnchorX(), placement.getAnchorY(), cfg.performanceHudScale);
    }

    public static void renderHud(GuiGraphicsExtractor graphics, int x, int y, float scale) {
        Font font = Minecraft.getInstance().font;
        if (font == null || !anyPartShown()) return;

        Component text = text();
        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().scale(scale, scale);
        AlpakaConfig cfg = AlpakaConfig.instance;
        if (cfg.blurOn(cfg.performanceHudBlur)) {
            HudPanel.aroundText(graphics, font.width(text), 9, scale);
        }
        Style style = cfg.smoothOn(cfg.performanceHudSmoothFont) ? GuiFont.style() : Style.EMPTY;
        if (!drawRolling(graphics, font, style, HudPanel.TEXT_SHADOW)) {
            graphics.text(font, text, 0, 0, 0xFFFFFFFF, HudPanel.TEXT_SHADOW);
        }
        graphics.pose().popMatrix();
    }

    public static int getWidth(Font font, float scale) {
        return anyPartShown() ? (int) Math.ceil(font.width(text()) * scale) : 0;
    }

    public static int getHeight(float scale) {
        return (int) Math.ceil(9 * scale);
    }
}
