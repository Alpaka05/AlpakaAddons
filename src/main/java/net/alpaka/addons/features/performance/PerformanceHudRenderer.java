package net.alpaka.addons.features.performance;

import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.HudPanel;
import net.alpaka.addons.client.hud.VisiblePlacement;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Locale;

/** One line on the HUD with the frame rate, the ping and the server's tick rate. */
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
    private static int cachedFps = Integer.MIN_VALUE;
    private static int cachedPing = Integer.MIN_VALUE;
    private static int cachedTenthTps = Integer.MIN_VALUE;
    private static int cachedParts = -1;
    private static Style cachedStyle = null;
    private static Component cachedText = null;

    private PerformanceHudRenderer() {}

    static Component text() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        int parts = (cfg.performanceHudShowFps ? 1 : 0) | (cfg.performanceHudShowPing ? 2 : 0) | (cfg.performanceHudShowTps ? 4 : 0);
        int fps = cfg.performanceHudShowFps ? PerformanceStats.fps() : 0;
        int ping = cfg.performanceHudShowPing ? PerformanceStats.pingMs() : 0;
        float tps = cfg.performanceHudShowTps ? PerformanceStats.tps() : 0.0f;
        int tenthTps = Float.isNaN(tps) ? -1 : Math.round(tps * 10.0f);
        boolean smooth = cfg.smoothOn(cfg.performanceHudSmoothFont);
        Style style = smooth ? GuiFont.style() : Style.EMPTY;

        if (cachedText == null || parts != cachedParts || fps != cachedFps || ping != cachedPing
                || tenthTps != cachedTenthTps || !style.equals(cachedStyle)) {
            cachedParts = parts;
            cachedFps = fps;
            cachedPing = ping;
            cachedTenthTps = tenthTps;
            cachedStyle = style;
            cachedText = HudPanel.text(build(parts, fps, ping, tenthTps), smooth);
        }
        return cachedText;
    }

    private static Component build(int parts, int fps, int ping, int tenthTps) {
        MutableComponent line = Component.empty();
        if ((parts & 1) != 0) {
            part(line, "FPS ", Integer.toString(fps), fps >= 60 ? GOOD : fps >= 30 ? FAIR : POOR);
        }
        if ((parts & 2) != 0) {
            if (ping < 0) part(line, "Ping ", "--", UNKNOWN);
            else part(line, "Ping ", ping + "ms", ping <= 150 ? GOOD : ping <= 250 ? FAIR : POOR);
        }
        if ((parts & 4) != 0) {
            if (tenthTps < 0) part(line, "TPS ", "--", UNKNOWN);
            else part(line, "TPS ", String.format(Locale.ROOT, "%.1f", tenthTps / 10.0f),
                    tenthTps >= 180 ? GOOD : tenthTps >= 150 ? FAIR : POOR);
        }
        return line;
    }

    private static void part(MutableComponent line, String label, String value, int color) {
        if (!line.getSiblings().isEmpty()) line.append(Component.literal("  "));
        line.append(Component.literal(label).withColor(LABEL));
        line.append(Component.literal(value).withColor(color));
    }

    private static boolean anyPartShown() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        return cfg.performanceHudShowFps || cfg.performanceHudShowPing || cfg.performanceHudShowTps;
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
        if (AlpakaConfig.instance.blurOn(AlpakaConfig.instance.performanceHudBlur)) {
            HudPanel.aroundText(graphics, font.width(text), 9, scale);
        }
        graphics.text(font, text, 0, 0, 0xFFFFFFFF, HudPanel.TEXT_SHADOW);
        graphics.pose().popMatrix();
    }

    public static int getWidth(Font font, float scale) {
        return anyPartShown() ? (int) Math.ceil(font.width(text()) * scale) : 0;
    }

    public static int getHeight(float scale) {
        return (int) Math.ceil(9 * scale);
    }
}
