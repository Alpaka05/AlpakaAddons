package net.alpaka.addons.client;

import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.AlpakaStyle.ButtonKind;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

import static net.alpaka.addons.client.gui.AlpakaStyle.inside;

/**
 * Picks a colour for a config option, in the config menu's look.
 *
 * Left, the colour as it is now, its hex code to type into, and a row of presets. Right, one slider
 * per channel whose track runs through that channel's range with the others held, so the sliders
 * themselves show where each one leads. Nothing is applied until Save; Escape or Cancel leave the
 * option as it was, and Reset in the header goes back to the option's default.
 */
public class ColorPickerScreen extends Screen {
    private final Screen parent;
    private final Consumer<Integer> onSave;
    /** The option's default, which Reset in the header goes back to; null hides it. */
    private final Integer defaultColor;
    private int r, g, b, a;

    private int activeSlider = -1; // 0: R, 1: G, 2: B, 3: A

    private String hexInput = "";
    private boolean hexFocused = false;
    private long caretFrom = 0L;
    private long openTimeMs = 0L;

    private static final int[] PRESETS = new int[] {
            0xFF29B6B2, // Teal, the default accent
            0xFF00E5FF, // Cyan
            0xFF29B6F6, // Blue
            0xFFAB47BC, // Purple
            0xFFFF4081, // Pink
            0xFFFF5252, // Red
            0xFFFF9800, // Orange
            0xFFFFEA00, // Yellow
            0xFF00E676, // Green
            0xFFFFFFFF, // White
    };

    private static final String[] CHANNELS = {"Red", "Green", "Blue", "Opacity"};

    public ColorPickerScreen(Screen parent, String title, int initialColor, Consumer<Integer> onSave) {
        this(parent, title, initialColor, null, onSave);
    }

    public ColorPickerScreen(Screen parent, String title, int initialColor, Integer defaultColor, Consumer<Integer> onSave) {
        super(GuiFont.text(title));
        this.parent = parent;
        this.onSave = onSave;
        this.defaultColor = defaultColor;
        setColor(initialColor);
        syncHexFromColor();
    }

    @Override
    protected void init() {
        openTimeMs = System.currentTimeMillis();
    }

    private void setColor(int color) {
        this.a = (color >> 24) & 0xFF;
        this.r = (color >> 16) & 0xFF;
        this.g = (color >> 8) & 0xFF;
        this.b = color & 0xFF;
    }

    private int currentColor() {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int channel(int i) {
        return switch (i) {
            case 0 -> r;
            case 1 -> g;
            case 2 -> b;
            default -> a;
        };
    }

    private String hexOf() {
        return a == 255 ? String.format("#%02X%02X%02X", r, g, b) : String.format("#%02X%02X%02X%02X", a, r, g, b);
    }

    private void playSound() {
        try {
            CustomSoundFeature.playButtonClickSound();
        } catch (Throwable ignored) {}
    }

    private void syncHexFromColor() {
        if (!hexFocused) this.hexInput = hexOf();
    }

    private void tryParseHexInput() {
        String clean = hexInput.trim().replace("#", "");
        try {
            if (clean.length() == 6) {
                long val = Long.parseLong(clean, 16);
                setColor(0xFF000000 | (int) val);
            } else if (clean.length() == 8) {
                setColor((int) Long.parseUnsignedLong(clean, 16));
            }
        } catch (NumberFormatException ignored) {}
    }

    // ---- Layout ---------------------------------------------------------------------------------

    private static final int PREF_W = 330;
    private static final int PREF_H = 209;
    private static final int LEFT_W = 112;
    private static final int SWATCH = 16;
    private static final int SWATCH_GAP = 8;
    private static final int SLIDER_ROW_H = 28;
    private static final int FIELD_H = 16;
    private static final int RESET_W = 40;

    /** Where everything goes, shared by drawing and input. */
    /** The header's Reset button sits centred in the header, as every header button does. */
    private static int headerButtonY(Layout l) {
        return l.y() + (AlpakaStyle.HEADER_H - AlpakaStyle.BUTTON_H) / 2;
    }

    private record Layout(int x, int y, int w, int h, int bodyY, int leftX, int previewH, int hexY,
                          int presetLabelY, int gridY, int rightX, int rightW, int btnY, int saveX, int cancelX,
                          int btnW, int resetX) {

        static Layout of(int width, int height) {
            int w = Math.max(260, Math.min(PREF_W, width - 16));
            int h = Math.max(170, Math.min(PREF_H, height - 16));
            int x = (width - w) / 2;
            int y = (height - h) / 2;
            int bodyY = y + AlpakaStyle.HEADER_H + 8;
            int leftX = x + AlpakaStyle.PAD + 4;
            int btnY = y + h - AlpakaStyle.BUTTON_H - AlpakaStyle.PAD;
            int previewH = Math.max(28, Math.min(52, btnY - bodyY - 6 - FIELD_H - 8 - 12 - 2 * SWATCH - SWATCH_GAP - 8));
            int hexY = bodyY + previewH + 6;
            int presetLabelY = hexY + FIELD_H + 8;
            int gridY = presetLabelY + 12;
            int rightX = leftX + LEFT_W + 16;
            int rightW = x + w - AlpakaStyle.PAD - 4 - rightX;
            int btnW = 64;
            int saveX = x + w - AlpakaStyle.PAD - btnW;
            int cancelX = saveX - btnW - 6;
            int resetX = x + w - AlpakaStyle.PAD - RESET_W;
            return new Layout(x, y, w, h, bodyY, leftX, previewH, hexY, presetLabelY, gridY, rightX, rightW,
                    btnY, saveX, cancelX, btnW, resetX);
        }

        int swatchX(int i) { return leftX + (i % 5) * (SWATCH + (LEFT_W - 5 * SWATCH) / 4); }
        int swatchY(int i) { return gridY + (i / 5) * (SWATCH + SWATCH_GAP); }
        int sliderLabelY(int i) { return bodyY + i * SLIDER_ROW_H; }
        int trackY(int i) { return sliderLabelY(i) + 17; }
    }

    // ---- Drawing --------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        AlpakaStyle.backdrop(graphics, this.width, this.height);
        boolean pushed = AlpakaStyle.beginOpen(graphics, openTimeMs, this.width, this.height);
        Layout l = Layout.of(this.width, this.height);
        int accent = ModernGuiUtils.getAccentColor();

        AlpakaStyle.panel(graphics, l.x(), l.y(), l.w(), l.h());
        AlpakaStyle.header(graphics, this.font, this.title.getString(), l.x(), l.y(), l.w());

        // Reset, in the header: only while the colour differs from the default.
        if (defaultColor != null && currentColor() != defaultColor) {
            boolean hover = inside(mouseX, mouseY, l.resetX(), headerButtonY(l), RESET_W, AlpakaStyle.BUTTON_H);
            AlpakaStyle.button(graphics, this.font, l.resetX(), headerButtonY(l), RESET_W, AlpakaStyle.BUTTON_H, "Reset", hover, ButtonKind.PLAIN);
        }

        // The colour now, over a checkerboard where it is see-through.
        int px = l.leftX();
        int py = l.bodyY();
        ModernGuiUtils.drawRoundedRect(graphics, px, py, LEFT_W, l.previewH(), 5, AlpakaStyle.FIELD);
        if (a < 255) {
            graphics.enableScissor(px + 2, py + 2, px + LEFT_W - 2, py + l.previewH() - 2);
            for (int cy = 0; cy * 6 < l.previewH(); cy++) {
                for (int cx = 0; cx * 6 < LEFT_W; cx++) {
                    if ((cx + cy) % 2 == 0) ModernGuiUtils.drawRect(graphics, px + cx * 6, py + cy * 6, 6, 6, 0xFF3A3A3A);
                }
            }
            graphics.disableScissor();
        }
        ModernGuiUtils.drawRoundedRect(graphics, px, py, LEFT_W, l.previewH(), 5, currentColor());
        ModernGuiUtils.drawRoundedOutline(graphics, px, py, LEFT_W, l.previewH(), 5, AlpakaStyle.LINE);

        // The hex code, to type into.
        boolean hoverHex = inside(mouseX, mouseY, px, l.hexY(), LEFT_W, FIELD_H);
        AlpakaStyle.textField(graphics, this.font, px, l.hexY(), LEFT_W, FIELD_H,
                hexFocused ? hexInput : hexOf(), "#RRGGBB", hexFocused, hoverHex, caretFrom);

        // Presets, the current one ringed.
        AlpakaStyle.groupLabel(graphics, this.font, "Presets", px, l.presetLabelY());
        for (int i = 0; i < PRESETS.length; i++) {
            int sx = l.swatchX(i);
            int sy = l.swatchY(i);
            boolean hover = inside(mouseX, mouseY, sx, sy, SWATCH, SWATCH);
            boolean current = PRESETS[i] == currentColor();
            if (current || hover) {
                ModernGuiUtils.drawRoundedOutline(graphics, sx - 2, sy - 2, SWATCH + 4, SWATCH + 4, 6,
                        current ? accent : AlpakaStyle.TEXT_MUTED);
            }
            ModernGuiUtils.drawRoundedRect(graphics, sx, sy, SWATCH, SWATCH, 4, PRESETS[i]);
        }

        // One slider per channel; each track runs through its channel with the others as they are.
        for (int i = 0; i < 4; i++) {
            int labelY = l.sliderLabelY(i);
            int value = channel(i);
            String shown = i == 3 ? Math.round(value / 2.55f) + "%" : String.valueOf(value);
            graphics.text(this.font, GuiFont.text(CHANNELS[i]), l.rightX(), labelY + 4, AlpakaStyle.TEXT_SOFT, false);
            int valueW = GuiFont.width(this.font, shown);
            graphics.text(this.font, GuiFont.text(shown), l.rightX() + l.rightW() - valueW, labelY + 4, AlpakaStyle.TEXT, false);

            int rgb = currentColor() & 0x00FFFFFF;
            int from;
            int to;
            switch (i) {
                case 0 -> { from = rgb & 0x00FFFF; to = rgb | 0xFF0000; }
                case 1 -> { from = rgb & 0xFF00FF; to = rgb | 0x00FF00; }
                case 2 -> { from = rgb & 0xFFFF00; to = rgb | 0x0000FF; }
                default -> { from = 0x2A2A2A; to = rgb; }
            }
            boolean hover = inside(mouseX, mouseY, l.rightX() - 4, labelY, l.rightW() + 8, SLIDER_ROW_H);
            AlpakaStyle.gradientTrack(graphics, l.rightX(), l.trackY(i), l.rightW(), 0xFF000000 | from, 0xFF000000 | to,
                    value / 255.0, rgb, hover, activeSlider == i);
        }

        // Cancel and Save.
        boolean hoverCancel = inside(mouseX, mouseY, l.cancelX(), l.btnY(), l.btnW(), AlpakaStyle.BUTTON_H);
        boolean hoverSave = inside(mouseX, mouseY, l.saveX(), l.btnY(), l.btnW(), AlpakaStyle.BUTTON_H);
        AlpakaStyle.button(graphics, this.font, l.cancelX(), l.btnY(), l.btnW(), AlpakaStyle.BUTTON_H, "Cancel", hoverCancel, ButtonKind.PLAIN);
        AlpakaStyle.button(graphics, this.font, l.saveX(), l.btnY(), l.btnW(), AlpakaStyle.BUTTON_H, "Save", hoverSave, ButtonKind.PRIMARY);

        AlpakaStyle.endOpen(graphics, pushed);
    }

    // ---- Input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);

        Layout l = Layout.of(this.width, this.height);
        boolean wasHex = hexFocused;
        hexFocused = false;

        if (defaultColor != null && currentColor() != defaultColor
                && inside(mouseX, mouseY, l.resetX(), headerButtonY(l), RESET_W, AlpakaStyle.BUTTON_H)) {
            playSound();
            setColor(defaultColor);
            syncHexFromColor();
            return true;
        }

        if (inside(mouseX, mouseY, l.leftX(), l.hexY(), LEFT_W, FIELD_H)) {
            if (!wasHex) {
                playSound();
                hexInput = hexOf();
            }
            hexFocused = true;
            caretFrom = System.currentTimeMillis();
            return true;
        }
        syncHexFromColor();

        for (int i = 0; i < PRESETS.length; i++) {
            if (inside(mouseX, mouseY, l.swatchX(i) - 2, l.swatchY(i) - 2, SWATCH + 4, SWATCH + 4)) {
                playSound();
                setColor(PRESETS[i]);
                syncHexFromColor();
                return true;
            }
        }

        for (int i = 0; i < 4; i++) {
            if (inside(mouseX, mouseY, l.rightX() - 4, l.sliderLabelY(i), l.rightW() + 8, SLIDER_ROW_H)) {
                activeSlider = i;
                updateSliderVal(i, mouseX, l);
                return true;
            }
        }

        if (inside(mouseX, mouseY, l.saveX(), l.btnY(), l.btnW(), AlpakaStyle.BUTTON_H)) {
            playSound();
            onSave.accept(currentColor());
            onClose();
            return true;
        }
        if (inside(mouseX, mouseY, l.cancelX(), l.btnY(), l.btnW(), AlpakaStyle.BUTTON_H)) {
            playSound();
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (activeSlider >= 0) {
            updateSliderVal(activeSlider, event.x(), Layout.of(this.width, this.height));
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) this.activeSlider = -1;
        return super.mouseReleased(event);
    }

    private void updateSliderVal(int i, double mouseX, Layout l) {
        double norm = Math.max(0.0, Math.min(1.0, (mouseX - l.rightX()) / (double) l.rightW()));
        int val = (int) Math.round(norm * 255.0);
        switch (i) {
            case 0 -> this.r = val;
            case 1 -> this.g = val;
            case 2 -> this.b = val;
            default -> this.a = val;
        }
        syncHexFromColor();
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (hexFocused) {
            char c = (char) event.codepoint();
            if ((c == '#' || Character.digit(c, 16) >= 0) && hexInput.length() < 9) {
                hexInput += c;
                tryParseHexInput();
            }
            caretFrom = System.currentTimeMillis();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (hexFocused) {
            int key = event.key();
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!hexInput.isEmpty()) {
                    hexInput = hexInput.substring(0, hexInput.length() - 1);
                    tryParseHexInput();
                }
                caretFrom = System.currentTimeMillis();
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                hexFocused = false;
                syncHexFromColor();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(this.parent);
    }
}
