package net.alpaka.addons.client;

import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.Consumer;

public class ColorPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<Integer> onSave;
    private int r, g, b, a;

    private int activeSlider = -1; // 0: R, 1: G, 2: B, 3: A

    // Interactive HEX Text Field state
    private String hexInput = "";
    private boolean hexFocused = false;
    private long cursorBlinkTimer = 0L;

    // 10 Color Presets
    private static final int[] PRESETS = new int[] {
            0xFF00E5FF, // Cyan
            0xFF00E676, // Green
            0xFFFF5252, // Red
            0xFFFFEA00, // Yellow
            0xFF29B6F6, // Blue
            0xFFAB47BC, // Purple
            0xFFFF9800, // Orange
            0xFFFF4081, // Pink
            0xFFFFFFFF, // White
            0xFF181B24  // Dark
    };

    public ColorPickerScreen(Screen parent, String title, int initialColor, Consumer<Integer> onSave) {
        super(GuiFont.text(title));
        this.parent = parent;
        this.onSave = onSave;
        this.a = (initialColor >> 24) & 0xFF;
        this.r = (initialColor >> 16) & 0xFF;
        this.g = (initialColor >> 8) & 0xFF;
        this.b = initialColor & 0xFF;
        this.hexInput = String.format("#%02X%02X%02X%02X", a, r, g, b);
    }

    private void playSound() {
        try {
            CustomSoundFeature.playButtonClickSound();
        } catch (Throwable ignored) {}
    }

    private void syncHexFromColor() {
        if (!hexFocused) {
            this.hexInput = String.format("#%02X%02X%02X%02X", a, r, g, b);
        }
    }

    private void tryParseHexInput() {
        String clean = hexInput.trim().replace("#", "");
        if (clean.length() == 6) {
            try {
                long val = Long.parseLong(clean, 16);
                this.a = 255;
                this.r = (int) ((val >> 16) & 0xFF);
                this.g = (int) ((val >> 8) & 0xFF);
                this.b = (int) (val & 0xFF);
            } catch (NumberFormatException ignored) {}
        } else if (clean.length() == 8) {
            try {
                long val = Long.parseUnsignedLong(clean, 16);
                this.a = (int) ((val >> 24) & 0xFF);
                this.r = (int) ((val >> 16) & 0xFF);
                this.g = (int) ((val >> 8) & 0xFF);
                this.b = (int) (val & 0xFF);
            } catch (NumberFormatException ignored) {}
        }
    }

    /** The panel's preferred size; smaller screens get a smaller, compact panel. */
    private static final int PREF_W = 460;
    private static final int PREF_H = 320;
    /** Space kept free around the panel, as the config screen keeps. */
    private static final int MARGIN = 8;

    /**
     * Where everything goes, worked out once per frame and shared by drawing and input.
     *
     * The panel used to be a fixed 460 by 320, laid out three times over in three methods. At the most
     * common size - 1080p at Auto GUI scale, which is 480 by 270 - it hung off the top and bottom of the
     * screen, Save included. Now it is clamped to the screen, and below the full height it switches to
     * a compact layout with a shorter header, preview and slider rows.
     */
    private record Layout(int winX, int winY, int winW, int winH, int headerH, int titleY, int closeX, int closeY,
                          int prevX, int prevY, int prevW, int prevH, int hexY, int hexH, int presetLabelY,
                          int gridY, int swatch, int swatchGap, int rightX, int rightW, int sliderY, int rowGap,
                          int sliderLabelGap, int sliderH, int btnY, int btnH, int saveX, int saveW,
                          int cancelX, int cancelW) {

        static Layout of(int width, int height) {
            int winW = Math.max(300, Math.min(PREF_W, width - MARGIN * 2));
            int winH = Math.min(PREF_H, height - MARGIN * 2);
            int winX = (width - winW) / 2;
            int winY = Math.max(MARGIN / 2, (height - winH) / 2);
            boolean compact = winH < PREF_H;

            int headerH = compact ? 28 : 38;
            int contentY = winY + headerH + (compact ? 8 : 14);
            int prevX = winX + 20;
            int prevW = 140;
            int prevH = compact ? 44 : 90;
            int hexY = contentY + prevH + (compact ? 6 : 10);
            int hexH = compact ? 20 : 24;
            int presetLabelY = hexY + hexH + (compact ? 8 : 12);
            int gridY = presetLabelY + (compact ? 12 : 14);
            int swatch = compact ? 18 : 22;
            int swatchGap = compact ? 5 : 7;

            int rightX = winX + 180;
            int rightW = winW - 200;
            int rowGap = compact ? 34 : 44;
            int sliderLabelGap = compact ? 12 : 14;
            int sliderH = compact ? 16 : 22;

            int btnH = compact ? 22 : 26;
            int btnY = winY + winH - btnH - (compact ? 10 : 12);
            int saveW = 110;
            int saveX = winX + winW - saveW - 16;
            int cancelW = 100;
            int cancelX = saveX - cancelW - 10;

            return new Layout(winX, winY, winW, winH, headerH, winY + (headerH - 12) / 2 + 1, winX + winW - 28,
                    winY + (headerH - 18) / 2, prevX, contentY, prevW, prevH, hexY, hexH, presetLabelY, gridY,
                    swatch, swatchGap, rightX, rightW, contentY, rowGap, sliderLabelGap, sliderH, btnY, btnH,
                    saveX, saveW, cancelX, cancelW);
        }

        int swatchX(int i) { return prevX + (i % 5) * (swatch + swatchGap); }
        int swatchY(int i) { return gridY + (i / 5) * (swatch + swatchGap); }
        int sliderTop(int i) { return sliderY + i * rowGap + sliderLabelGap; }
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Translucent dark backdrop (game visible behind color picker)
        graphics.fill(0, 0, this.width, this.height, 0x70000000);

        Layout l = Layout.of(this.width, this.height);

        // Panel frame
        ModernGuiUtils.drawRect(graphics, l.winX(), l.winY(), l.winW(), l.winH(), ModernGuiUtils.COLOR_PANEL_BG);
        ModernGuiUtils.drawOutline(graphics, l.winX(), l.winY(), l.winW(), l.winH(), ModernGuiUtils.COLOR_CARD_BORDER);

        // Header bar
        ModernGuiUtils.drawRect(graphics, l.winX(), l.winY(), l.winW(), l.headerH(), ModernGuiUtils.COLOR_SIDEBAR_BG);
        ModernGuiUtils.drawRect(graphics, l.winX(), l.winY() + l.headerH() - 1, l.winW(), 1, ModernGuiUtils.getAccentColor());

        graphics.text(this.font, this.title, l.winX() + 16, l.titleY(), ModernGuiUtils.COLOR_TEXT_PRIMARY, false);

        // Close '✕' button in header
        boolean hoverClose = inside(mouseX, mouseY, l.closeX(), l.closeY(), 18, 18);
        graphics.text(this.font, GuiFont.text("✕"), l.closeX() + 4, l.closeY() + 3, hoverClose ? ModernGuiUtils.getAccentColor() : ModernGuiUtils.COLOR_TEXT_MUTED, false);

        // LEFT COLUMN: Color Preview, HEX Code, Presets
        int prevX = l.prevX(), prevY = l.prevY(), prevW = l.prevW(), prevH = l.prevH();

        // Preview box alpha pattern + solid fill
        ModernGuiUtils.drawRect(graphics, prevX, prevY, prevW, prevH, 0xFF000000);
        int currentColor = (a << 24) | (r << 16) | (g << 8) | b;
        ModernGuiUtils.drawRect(graphics, prevX + 2, prevY + 2, prevW - 4, prevH - 4, currentColor);
        ModernGuiUtils.drawOutline(graphics, prevX, prevY, prevW, prevH, ModernGuiUtils.COLOR_CARD_BORDER);

        // HEX Input Field Box
        int hexY = l.hexY(), hexH = l.hexH();
        boolean hoverHex = inside(mouseX, mouseY, prevX, hexY, prevW, hexH);
        int hexBorder = hexFocused ? ModernGuiUtils.getAccentColor() : (hoverHex ? ModernGuiUtils.getAccentDimColor() : ModernGuiUtils.COLOR_CARD_BORDER);

        ModernGuiUtils.drawRect(graphics, prevX, hexY, prevW, hexH, ModernGuiUtils.COLOR_CARD_BG);
        ModernGuiUtils.drawOutline(graphics, prevX, hexY, prevW, hexH, hexBorder);

        String displayText = hexFocused ? hexInput : String.format("#%02X%02X%02X%02X", a, r, g, b);
        if (hexFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
            displayText += "|";
        }
        int hexStrX = prevX + (prevW - GuiFont.width(this.font, displayText)) / 2;
        graphics.text(this.font, GuiFont.text(displayText), hexStrX, hexY + (hexH - 10) / 2 + 1, hexFocused ? ModernGuiUtils.getAccentColor() : ModernGuiUtils.COLOR_TEXT_PRIMARY, false);

        // Presets Header
        graphics.text(this.font, GuiFont.text("Presets:"), prevX, l.presetLabelY(), ModernGuiUtils.COLOR_TEXT_MUTED, false);

        // Presets Grid (5 cols x 2 rows)
        int swatchSize = l.swatch();
        for (int i = 0; i < PRESETS.length; i++) {
            int sx = l.swatchX(i);
            int sy = l.swatchY(i);

            boolean isHovered = inside(mouseX, mouseY, sx, sy, swatchSize, swatchSize);
            int presetColor = PRESETS[i];

            ModernGuiUtils.drawRect(graphics, sx, sy, swatchSize, swatchSize, 0xFF000000);
            ModernGuiUtils.drawRect(graphics, sx + 1, sy + 1, swatchSize - 2, swatchSize - 2, presetColor);
            ModernGuiUtils.drawOutline(graphics, sx, sy, swatchSize, swatchSize, isHovered ? ModernGuiUtils.getAccentColor() : ModernGuiUtils.COLOR_CARD_BORDER);
        }

        // RIGHT COLUMN: Sliders (R, G, B, A)
        String[] sliderNames = new String[] {"Red", "Green", "Blue", "Alpha (Transparency)"};
        int[] sliderVals = new int[] {r, g, b, a};

        for (int i = 0; i < 4; i++) {
            int labelY = l.sliderY() + i * l.rowGap();
            String labelText = sliderNames[i] + ": " + sliderVals[i];
            graphics.text(this.font, GuiFont.text(labelText), l.rightX(), labelY, ModernGuiUtils.COLOR_TEXT_PRIMARY, false);

            int swY = l.sliderTop(i);
            boolean isHovered = inside(mouseX, mouseY, l.rightX(), swY, l.rightW(), l.sliderH());
            double norm = sliderVals[i] / 255.0;

            ModernGuiUtils.drawModernSlider(graphics, this.font, l.rightX(), swY, l.rightW(), l.sliderH(), norm, String.valueOf(sliderVals[i]), isHovered);
        }

        // BOTTOM ACTION BUTTONS
        boolean hoverSave = inside(mouseX, mouseY, l.saveX(), l.btnY(), l.saveW(), l.btnH());
        boolean hoverCancel = inside(mouseX, mouseY, l.cancelX(), l.btnY(), l.cancelW(), l.btnH());

        ModernGuiUtils.drawModernButton(graphics, this.font, l.cancelX(), l.btnY(), l.cancelW(), l.btnH(), "Cancel", hoverCancel, false);
        ModernGuiUtils.drawModernButton(graphics, this.font, l.saveX(), l.btnY(), l.saveW(), l.btnH(), "Save ✓", hoverSave, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);

        Layout l = Layout.of(this.width, this.height);

        // Close '✕' button
        if (inside(mouseX, mouseY, l.closeX(), l.closeY(), 18, 18)) {
            playSound();
            this.onClose();
            return true;
        }

        // HEX Input Field click
        if (inside(mouseX, mouseY, l.prevX(), l.hexY(), l.prevW(), l.hexH())) {
            playSound();
            this.hexFocused = true;
            this.cursorBlinkTimer = System.currentTimeMillis();
            if (this.hexInput.isEmpty()) {
                this.hexInput = String.format("#%02X%02X%02X%02X", a, r, g, b);
            }
            return true;
        } else {
            this.hexFocused = false;
            syncHexFromColor();
        }

        // Presets grid click
        for (int i = 0; i < PRESETS.length; i++) {
            if (inside(mouseX, mouseY, l.swatchX(i), l.swatchY(i), l.swatch(), l.swatch())) {
                playSound();
                int color = PRESETS[i];
                this.a = (color >> 24) & 0xFF;
                this.r = (color >> 16) & 0xFF;
                this.g = (color >> 8) & 0xFF;
                this.b = color & 0xFF;
                syncHexFromColor();
                return true;
            }
        }

        // Sliders click
        for (int i = 0; i < 4; i++) {
            if (inside(mouseX, mouseY, l.rightX(), l.sliderTop(i), l.rightW(), l.sliderH())) {
                playSound();
                this.activeSlider = i;
                updateSliderVal(i, mouseX, l.rightX(), l.rightW());
                return true;
            }
        }

        // Save & Cancel Buttons
        if (inside(mouseX, mouseY, l.saveX(), l.btnY(), l.saveW(), l.btnH())) {
            playSound();
            int finalColor = (a << 24) | (r << 16) | (g << 8) | b;
            onSave.accept(finalColor);
            onClose();
            return true;
        }

        if (inside(mouseX, mouseY, l.cancelX(), l.btnY(), l.cancelW(), l.btnH())) {
            playSound();
            onClose();
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (activeSlider >= 0) {
            Layout l = Layout.of(this.width, this.height);
            updateSliderVal(activeSlider, event.x(), l.rightX(), l.rightW());
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            this.activeSlider = -1;
        }
        return super.mouseReleased(event);
    }

    private void updateSliderVal(int sliderIdx, double mouseX, int rightX, int rightW) {
        double norm = Math.max(0.0, Math.min(1.0, (mouseX - rightX) / (double) rightW));
        int val = (int) Math.round(norm * 255.0);
        switch (sliderIdx) {
            case 0 -> this.r = val;
            case 1 -> this.g = val;
            case 2 -> this.b = val;
            case 3 -> this.a = val;
        }
        syncHexFromColor();
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (hexFocused) {
            int codePoint = event.codepoint();
            char c = (char) codePoint;
            if (c == '#' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')) {
                if (hexInput.length() < 9) {
                    hexInput += c;
                    tryParseHexInput();
                    return true;
                }
            }
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (hexFocused) {
            if (event.key() == 259) { // GLFW_KEY_BACKSPACE
                if (!hexInput.isEmpty()) {
                    hexInput = hexInput.substring(0, hexInput.length() - 1);
                    tryParseHexInput();
                }
                return true;
            } else if (event.key() == 256 || event.key() == 257) { // ESCAPE or ENTER
                hexFocused = false;
                syncHexFromColor();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }
}
