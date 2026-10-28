package net.alpaka.addons.features.itemsearch;

import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.client.gui.PloppAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.sounds.SoundEvents;

/**
 * The few pieces of the config menu's look the Item Search window draws with: its colors, panel,
 * header, buttons and tooltip, from {@link ModernGuiUtils}.
 */
final class ItemSearchStyle {
    private ItemSearchStyle() {}

    static final int PAD = 8;
    static final int HEADER_H = 33;
    static final int BUTTON_H = 18;
    static final int ROW_RADIUS = 4;
    static final int FIELD_RADIUS = 3;
    static final int PANEL = ModernGuiUtils.COLOR_PANEL_BG;
    static final int FIELD = 0xFF1F1F1F;
    static final int ROW = ModernGuiUtils.COLOR_CARD_BG;
    static final int ROW_SELECTED = ModernGuiUtils.COLOR_CARD_BG_HOVER;
    static final int LINE = 0xFF2A2A2A;
    static final int TEXT = ModernGuiUtils.COLOR_TEXT_PRIMARY;
    static final int TEXT_SOFT = 0xFFBDBDBD;
    static final int TEXT_MUTED = ModernGuiUtils.COLOR_TEXT_MUTED;

    enum ButtonKind { PLAIN, PRIMARY }

    static int accent() {
        return ModernGuiUtils.getAccentColor();
    }

    static int accentDim() {
        return (accent() & 0x00FFFFFF) | 0x66000000;
    }

    static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }

    static int headerButtonY(int headerY) {
        return headerY + (HEADER_H - BUTTON_H) / 2;
    }

    static void backdrop(GuiGraphicsExtractor graphics, int width, int height) {
        graphics.fill(0, 0, width, height, 0x80000000);
    }

    static boolean beginOpen(GuiGraphicsExtractor graphics, long openTimeMs, int width, int height) {
        float scale = PloppAnimation.getOpenScale(openTimeMs);
        if (scale >= 0.999f) return false;
        graphics.pose().pushMatrix();
        graphics.pose().scaleAround(scale, scale, width / 2.0f, height / 2.0f);
        return true;
    }

    static void endOpen(GuiGraphicsExtractor graphics, boolean pushed) {
        if (pushed) graphics.pose().popMatrix();
    }

    static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        ModernGuiUtils.drawPanelShadow(graphics, x, y, w, h, ModernGuiUtils.PANEL_RADIUS, 1.0f);
        ModernGuiUtils.drawRoundedPanel(graphics, x, y, w, h, ModernGuiUtils.PANEL_RADIUS, PANEL, ModernGuiUtils.COLOR_CARD_BORDER);
    }

    static void header(GuiGraphicsExtractor graphics, Font font, String title, int x, int y, int w) {
        graphics.text(font, GuiFont.text(title), x + PAD + 2, y + (HEADER_H - 8) / 2, TEXT, false);
        ModernGuiUtils.drawRect(graphics, x + PAD, y + HEADER_H - 1, w - PAD * 2, 1, LINE);
    }

    static void button(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h, String label, boolean hover, ButtonKind kind) {
        ModernGuiUtils.drawModernButton(graphics, font, x, y, w, h, label, hover, kind == ButtonKind.PRIMARY);
    }

    static void scrollFades(GuiGraphicsExtractor graphics, int x, int y, int w, int h, boolean above, boolean below) {
        int fade = 6;
        int clear = PANEL & 0x00FFFFFF;
        if (above) graphics.fillGradient(x, y, x + w, y + fade, PANEL, clear);
        if (below) graphics.fillGradient(x, y + h - fade, x + w, y + h, clear, PANEL);
    }

    static String ellipsize(Font font, String text, int maxW) {
        if (text == null || maxW <= 0) return "";
        if (GuiFont.width(font, text) <= maxW) return text;
        int end = text.length();
        while (end > 0 && GuiFont.width(font, text.substring(0, end) + "…") > maxW) end--;
        return text.substring(0, end).trim() + "…";
    }

    static void tooltip(GuiGraphicsExtractor graphics, Font font, int screenW, int screenH, int mouseX, int mouseY, String text) {
        graphics.nextStratum();
        java.util.List<FormattedText> lines = GuiFont.splitLines(font, text, 170);
        int w = 0;
        for (FormattedText line : lines) w = Math.max(w, font.width(line));
        int h = lines.size() * 10 + 6;
        w += 10;
        int x = Math.min(mouseX + 10, screenW - w - 4);
        int y = Math.min(mouseY + 10, screenH - h - 4);
        ModernGuiUtils.drawRoundedPanel(graphics, x, y, w, h, ROW_RADIUS, 0xFF101010, ModernGuiUtils.COLOR_CARD_BORDER);
        int ly = y + 4;
        for (FormattedText line : lines) {
            graphics.text(font, net.minecraft.locale.Language.getInstance().getVisualOrder(line), x + 5, ly, TEXT_SOFT, false);
            ly += 10;
        }
    }

    static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
