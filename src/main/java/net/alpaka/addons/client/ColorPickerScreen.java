package net.alpaka.addons.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.alpaka.addons.client.gui.AlpakaGuiElementSink;
import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.AlpakaStyle.ButtonKind;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.Hsv;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.client.gui.RoundedImageRenderState;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.config.Chroma;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.Locale;

import static net.alpaka.addons.client.gui.AlpakaStyle.inside;

/**
 * Picks a color for a config option, in the config menu's look.
 *
 * Left, a color wheel: the hue runs round it and the saturation out from its grey middle, and a
 * click or drag puts the point anywhere on it. Right, the color as it is now with its hex code to
 * type into, a brightness and an opacity slider, and the presets: the built-in ones, then the
 * player's own, then a plus that saves the color now as one more. Nothing is applied until
 * Save; Escape or Cancel leave the option as it was, and Reset in the header goes back to the
 * option's default.
 *
 * An option that offers a rainbow ({@code ConfigOption.withChroma}) also has a Chroma switch in
 * the header. While it is on, the point runs round the wheel the way the rainbow moves, and the
 * sliders on the right set its speed, saturation and, for a rainbow along text, its size.
 */
public class ColorPickerScreen extends Screen implements net.alpaka.addons.client.gui.NoticeHost {
    private final Screen parent;
    private final Consumer<Integer> onSave;
    /** The option's default, which Reset in the header goes back to; null hides it. */
    private final Integer defaultColor;
    /**
     * The color twice: as RGB, which the hex field, the presets and Save use, and as wheel
     * coordinates, which the wheel and the brightness slider move. Whichever side is changed updates
     * the other, so a preset or a typed hex code comes back out exactly as it went in.
     */
    private int rgb, a;
    private Hsv hsv = new Hsv(0.0f, 0.0f, 1.0f);

    /** What the mouse is dragging: the wheel, one of the sliders, or nothing. */
    private Drag drag = Drag.NONE;

    private enum Drag { NONE, WHEEL, BRIGHTNESS, OPACITY, SPEED, SATURATION, SIZE }

    /**
     * The rainbow an option offers: the one in the live config, which Save writes into, its default
     * for Reset, and whether it runs along text (which adds the Size slider).
     */
    public record ChromaSpec(Chroma live, Chroma defaults, boolean spread) {
    }

    /** The option's rainbow, or null for a plain color; {@link #chroma} is the copy being edited. */
    private final ChromaSpec chromaSpec;
    private final Chroma chroma;

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

    /** Own presets beyond this push the oldest out; with the plus that fills the second row. */
    private static final int MAX_CUSTOM = 9;
    private static final int PRESETS_PER_ROW = 10;

    /** The slot the mouse rests on, and since when, for the tooltip; -1 for none. */
    private int hoveredSlot = -1;
    private long hoverSince = 0L;

    public ColorPickerScreen(Screen parent, String title, int initialColor, Consumer<Integer> onSave) {
        this(parent, title, initialColor, null, onSave);
    }

    public ColorPickerScreen(Screen parent, String title, int initialColor, Integer defaultColor, Consumer<Integer> onSave) {
        this(parent, title, initialColor, defaultColor, onSave, null);
    }

    public ColorPickerScreen(Screen parent, String title, int initialColor, Integer defaultColor, Consumer<Integer> onSave,
                             ChromaSpec chromaSpec) {
        super(GuiFont.text(title));
        this.parent = parent;
        this.onSave = onSave;
        this.defaultColor = defaultColor;
        this.chromaSpec = chromaSpec;
        this.chroma = chromaSpec == null ? null : chromaSpec.live().copy();
        setColor(initialColor);
        syncHexFromColor();
    }

    @Override
    protected void init() {
        // Once only: a resize is not a fresh opening.
        if (openTimeMs == 0L) openTimeMs = System.currentTimeMillis();
    }

    private void setColor(int color) {
        this.a = (color >>> 24) & 0xFF;
        this.rgb = color & 0x00FFFFFF;
        this.hsv = Hsv.of(rgb, hsv);
    }

    private void setHsv(Hsv next) {
        this.hsv = next;
        this.rgb = next.rgb() & 0x00FFFFFF;
        syncHexFromColor();
    }

    private int currentColor() {
        return (a << 24) | rgb;
    }

    private boolean chromaOn() {
        return chroma != null && chroma.enabled;
    }

    /** Whether anything differs from the option's default, which is when Reset shows. */
    private boolean differsFromDefault() {
        if (defaultColor != null && currentColor() != defaultColor) return true;
        return chroma != null && !chroma.sameAs(chromaSpec.defaults());
    }

    private boolean canReset() {
        return defaultColor != null && differsFromDefault();
    }

    private String hexOf() {
        return a == 255 ? String.format(Locale.ROOT, "#%06X", rgb) : String.format(Locale.ROOT, "#%08X", currentColor());
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

    // ---- Presets --------------------------------------------------------------------------------

    private static java.util.List<Integer> customPresets() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        if (cfg.customColorPresets == null) cfg.customColorPresets = new java.util.ArrayList<>();
        return cfg.customColorPresets;
    }

    /** The built-in presets, then the player's own. */
    private static int slotCount() {
        return PRESETS.length + customPresets().size();
    }

    private static int slotColor(int i) {
        return i < PRESETS.length ? PRESETS[i] : customPresets().get(i - PRESETS.length);
    }

    private boolean isPreset(int color) {
        for (int i = 0; i < slotCount(); i++) {
            if (slotColor(i) == color) return true;
        }
        return false;
    }

    /**
     * Saves the color now as a preset. It is kept at once, whether the picker is then saved or
     * cancelled: the presets are a palette of their own, not part of the option being edited.
     */
    private void addCurrentAsPreset() {
        if (isPreset(currentColor())) return;
        java.util.List<Integer> custom = customPresets();
        while (custom.size() >= MAX_CUSTOM) custom.remove(0);
        custom.add(currentColor());
        AlpakaConfig.save();
    }

    private static void removeCustomPreset(int slot) {
        customPresets().remove(slot - PRESETS.length);
        AlpakaConfig.save();
    }

    // ---- The wheel's picture --------------------------------------------------------------------

    private static final int WHEEL_TEXTURE_SIZE = 512;
    private static final Identifier WHEEL_TEXTURE = Identifier.fromNamespaceAndPath("alpaka", "color_wheel");
    private static DynamicTexture wheelTexture;

    /**
     * The wheel at full brightness, made once and kept: 1 MB is not worth making again for every
     * picker. Red is to the right and the hues run counter-clockwise. The pixels outside the circle
     * carry the color of its rim, so the anti-aliased edge the rounded-image shader cuts stays that
     * color instead of fading through black; their alpha is 0 for the plain blit fallback.
     */
    private static DynamicTexture wheelTexture() {
        if (wheelTexture == null) {
            int n = WHEEL_TEXTURE_SIZE;
            NativeImage image = new NativeImage(n, n, false);
            for (int py = 0; py < n; py++) {
                for (int px = 0; px < n; px++) {
                    double dx = (px + 0.5) / n * 2.0 - 1.0;
                    double dy = (py + 0.5) / n * 2.0 - 1.0;
                    double dist = Math.hypot(dx, dy);
                    float hue = (float) (Math.atan2(-dy, dx) / (2.0 * Math.PI));
                    if (hue < 0.0f) hue += 1.0f;
                    int color = new Hsv(hue, (float) Math.min(1.0, dist), 1.0f).rgb();
                    double edge = (1.0 - dist) * n / 2.0 + 0.5;
                    int alpha = (int) Math.round(Math.max(0.0, Math.min(1.0, edge)) * 255.0);
                    image.setPixel(px, py, (alpha << 24) | (color & 0x00FFFFFF));
                }
            }
            wheelTexture = new DynamicTexture(() -> "Alpaka color wheel", image);
            Minecraft.getInstance().getTextureManager().register(WHEEL_TEXTURE, wheelTexture);
        }
        return wheelTexture;
    }

    /** The wheel's picture as a circle, its edge anti-aliased by the mod's rounded-image shader. */
    private static void drawWheel(GuiGraphicsExtractor graphics, int x, int y, int size) {
        DynamicTexture texture = wheelTexture();
        if (graphics instanceof AlpakaGuiElementSink sink) {
            int scale = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
            TextureSetup textures = TextureSetup.singleTexture(texture.getTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            sink.alpaka$submitElement(new RoundedImageRenderState(new Matrix3x2f(graphics.pose()),
                    x, y, x + size, y + size, size * scale / 2, scale, 0, textures, sink.alpaka$currentScissor()));
        } else {
            graphics.blit(texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                    x, y, x + size, y + size, 0.0f, 1.0f, 0.0f, 1.0f);
        }
    }

    // ---- Layout ---------------------------------------------------------------------------------

    private static final int PREF_W = 360;
    private static final int PREF_H = 216;
    private static final int MAX_WHEEL = 148;
    private static final int SWATCH = 13;
    private static final int SLIDER_ROW_H = 28;
    private static final int FIELD_H = 16;
    private static final int PREVIEW_W = 28;
    private static final int RESET_W = 40;
    private static final int TOGGLE_W = 16;
    private static final int TOGGLE_H = 9;

    /** The header's Reset button sits centred in the header, as every header button does. */
    private static int headerButtonY(Layout l) {
        return AlpakaStyle.headerButtonY(l.y());
    }

    /**
     * Where everything goes, shared by drawing and input. The wheel takes the left side down to the
     * window's bottom edge, beside the Cancel and Save buttons, which sit to the right of it.
     */
    private record Layout(int x, int y, int w, int h, int bodyY, int wheelX, int wheelY, int wheelSize,
                          int rightX, int rightW, int presetLabelY, int btnY, int saveX, int cancelX,
                          int btnW, int resetX) {

        static Layout of(int width, int height) {
            int w = Math.max(280, Math.min(PREF_W, width - 16));
            int h = Math.max(180, Math.min(PREF_H, height - 16));
            int x = (width - w) / 2;
            int y = (height - h) / 2;
            int bodyY = y + AlpakaStyle.HEADER_H + 10;
            int bottom = y + h - AlpakaStyle.PAD - 4;
            int wheelSize = Math.max(64, Math.min(MAX_WHEEL, Math.min(bottom - bodyY, w * 2 / 5)));
            int wheelX = x + AlpakaStyle.PAD + 6;
            int wheelY = bodyY + (bottom - bodyY - wheelSize) / 2;
            int rightX = wheelX + wheelSize + 18;
            int rightW = x + w - AlpakaStyle.PAD - 6 - rightX;
            int presetLabelY = bodyY + FIELD_H + 8 + 2 * SLIDER_ROW_H + 6;
            int btnY = y + h - AlpakaStyle.BUTTON_H - AlpakaStyle.PAD;
            int btnW = 64;
            int saveX = x + w - AlpakaStyle.PAD - btnW;
            int cancelX = saveX - btnW - 6;
            int resetX = x + w - AlpakaStyle.PAD - RESET_W;
            return new Layout(x, y, w, h, bodyY, wheelX, wheelY, wheelSize, rightX, rightW, presetLabelY,
                    btnY, saveX, cancelX, btnW, resetX);
        }

        double wheelCx() { return wheelX + wheelSize / 2.0; }
        double wheelCy() { return wheelY + wheelSize / 2.0; }
        double wheelR() { return wheelSize / 2.0; }
        int hexX() { return rightX + PREVIEW_W + 6; }
        int hexW() { return rightX + rightW - hexX(); }
        int sliderLabelY(int i) { return bodyY + FIELD_H + 8 + i * SLIDER_ROW_H; }
        /** The Chroma switch sits in the header left of Reset, whose place it keeps free. */
        int chromaToggleX() { return resetX - 10 - TOGGLE_W; }
        int chromaToggleY() { return y + (AlpakaStyle.HEADER_H - TOGGLE_H) / 2; }
        int trackY(int i) { return sliderLabelY(i) + 17; }
        // The track's round ends reach 3 px past its ends, so it is inset to stay in the column.
        int trackX() { return rightX + 3; }
        int trackW() { return rightW - 6; }
        int swatchX(int i) { return rightX + (i % PRESETS_PER_ROW) * (rightW - SWATCH) / (PRESETS_PER_ROW - 1); }
        int swatchY(int i) { return presetLabelY + 12 + (i / PRESETS_PER_ROW) * (SWATCH + 6); }
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

        // The Chroma switch, for options that offer a rainbow, with its name in front.
        if (chroma != null) {
            int tx = l.chromaToggleX();
            int ty = l.chromaToggleY();
            String label = "Chroma";
            int labelW = GuiFont.width(this.font, label);
            boolean hoverToggle = overChromaToggle(l, mouseX, mouseY);
            graphics.text(this.font, GuiFont.text(label), tx - 6 - labelW, ty + (TOGGLE_H - 8) / 2,
                    hoverToggle || chroma.enabled ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT, false);
            AlpakaStyle.toggle(graphics, tx, ty, TOGGLE_W, TOGGLE_H, chroma.enabled ? 1.0f : 0.0f);
        }

        // Reset, in the header: only while something differs from the default.
        if (canReset()) {
            boolean hover = inside(mouseX, mouseY, l.resetX(), headerButtonY(l), RESET_W, AlpakaStyle.BUTTON_H);
            AlpakaStyle.button(graphics, this.font, l.resetX(), headerButtonY(l), RESET_W, AlpakaStyle.BUTTON_H, "Reset", hover, ButtonKind.PLAIN);
        }

        // The wheel, darkened as far as the brightness is down, and a ring where the color sits on it.
        int ws = l.wheelSize();
        drawWheel(graphics, l.wheelX(), l.wheelY(), ws);
        if (chromaOn()) {
            drawChroma(graphics, l, mouseX, mouseY);
            AlpakaStyle.endOpen(graphics, pushed);
            net.alpaka.addons.features.notification.AlpakaNotifications.renderOverScreen(graphics);
            return;
        }
        int shade = Math.round((1.0f - hsv.val()) * 255.0f);
        if (shade > 0) ModernGuiUtils.drawRoundedRect(graphics, l.wheelX(), l.wheelY(), ws, ws, ws / 2, shade << 24);
        ModernGuiUtils.drawRoundedOutline(graphics, l.wheelX() - 1, l.wheelY() - 1, ws + 2, ws + 2, ws / 2 + 1, AlpakaStyle.LINE);
        double angle = hsv.hue() * 2.0 * Math.PI;
        float knobX = (float) (l.wheelCx() + Math.cos(angle) * hsv.sat() * l.wheelR());
        float knobY = (float) (l.wheelCy() - Math.sin(angle) * hsv.sat() * l.wheelR());
        boolean hoverWheel = overWheel(l, mouseX, mouseY);
        AlpakaStyle.knob(graphics, knobX, knobY, hoverWheel || drag == Drag.WHEEL ? 12 : 11, rgb);

        // The color now, and its hex code to type into. hexInput mirrors the color whenever the field
        // is not being typed in (syncHexFromColor runs after every change and on leaving the field),
        // so it is drawn as it is rather than formatted anew every frame.
        AlpakaStyle.swatch(graphics, l.rightX(), l.bodyY(), PREVIEW_W, FIELD_H, currentColor());
        boolean hoverHex = inside(mouseX, mouseY, l.hexX(), l.bodyY(), l.hexW(), FIELD_H);
        AlpakaStyle.textField(graphics, this.font, l.hexX(), l.bodyY(), l.hexW(), FIELD_H,
                hexInput, "#RRGGBB", hexFocused, hoverHex, caretFrom);

        // Brightness runs from black to the color at full brightness; opacity from the window's own
        // color, which is what a see-through color shows over, to the color itself.
        int full = new Hsv(hsv.hue(), hsv.sat(), 1.0f).rgb();
        slider(graphics, l, 0, "Brightness", hsv.val(), 0xFF000000, full, mouseX, mouseY, drag == Drag.BRIGHTNESS);
        slider(graphics, l, 1, "Opacity", a / 255.0f, AlpakaStyle.ROW_SELECTED, 0xFF000000 | rgb, mouseX, mouseY, drag == Drag.OPACITY);

        // Presets, the current one ringed, and the plus after the last one.
        AlpakaStyle.groupLabel(graphics, this.font, "Presets", l.rightX(), l.presetLabelY());
        int slots = slotCount();
        boolean alreadyPreset = isPreset(currentColor());
        int hovered = -1;
        for (int i = 0; i <= slots; i++) {
            int sx = l.swatchX(i);
            int sy = l.swatchY(i);
            boolean hover = inside(mouseX, mouseY, sx - 2, sy - 2, SWATCH + 4, SWATCH + 4);
            if (hover) hovered = i;
            int round = (SWATCH + 1) / 2;
            if (i == slots) {
                // The plus: a dashed circle, an empty slot to fill, muted while the color is a
                // preset already.
                int line = alreadyPreset ? AlpakaStyle.DIVIDER : hover ? accent : AlpakaStyle.TEXT_MUTED;
                if (hover && !alreadyPreset) {
                    ModernGuiUtils.drawRoundedRect(graphics, sx, sy, SWATCH, SWATCH, round, AlpakaStyle.ROW_HOVER);
                }
                float cx = sx + SWATCH / 2.0f;
                float cy = sy + SWATCH / 2.0f;
                AlpakaStyle.dashedCircle(graphics, cx, cy, SWATCH / 2.0f, 10, line);
                AlpakaStyle.plus(graphics, cx, cy, 3.0f, line);
                continue;
            }
            boolean current = slotColor(i) == currentColor();
            if (current || hover) {
                ModernGuiUtils.drawRoundedOutline(graphics, sx - 2, sy - 2, SWATCH + 4, SWATCH + 4, round + 2,
                        current ? accent : AlpakaStyle.TEXT_MUTED);
            }
            ModernGuiUtils.drawRoundedRect(graphics, sx, sy, SWATCH, SWATCH, round, slotColor(i));
        }
        if (hovered != hoveredSlot) {
            hoveredSlot = hovered;
            hoverSince = System.currentTimeMillis();
        }

        drawButtons(graphics, l, mouseX, mouseY);

        AlpakaStyle.endOpen(graphics, pushed);
        if (hoveredSlot >= PRESETS.length && System.currentTimeMillis() - hoverSince >= AlpakaStyle.TOOLTIP_DELAY_MS) {
            String tip = hoveredSlot < slots ? "Right-click to remove this preset."
                    : alreadyPreset ? "This color is a preset already."
                    : "Save the current color as a preset.";
            AlpakaStyle.tooltip(graphics, this.font, this.width, this.height, mouseX, mouseY, tip);
        }
        // Notices over the window, as over the config menu.
        net.alpaka.addons.features.notification.AlpakaNotifications.renderOverScreen(graphics);
    }

    /** Cancel and Save. */
    private void drawButtons(GuiGraphicsExtractor graphics, Layout l, int mouseX, int mouseY) {
        boolean hoverCancel = inside(mouseX, mouseY, l.cancelX(), l.btnY(), l.btnW(), AlpakaStyle.BUTTON_H);
        boolean hoverSave = inside(mouseX, mouseY, l.saveX(), l.btnY(), l.btnW(), AlpakaStyle.BUTTON_H);
        AlpakaStyle.button(graphics, this.font, l.cancelX(), l.btnY(), l.btnW(), AlpakaStyle.BUTTON_H, "Cancel", hoverCancel, ButtonKind.PLAIN);
        AlpakaStyle.button(graphics, this.font, l.saveX(), l.btnY(), l.btnW(), AlpakaStyle.BUTTON_H, "Save", hoverSave, ButtonKind.PRIMARY);
    }

    /**
     * The picker with Chroma on: the point runs round the wheel as the rainbow moves, at its
     * saturation, the preview shows the color of the moment, and the sliders set the rainbow.
     */
    private void drawChroma(GuiGraphicsExtractor graphics, Layout l, int mouseX, int mouseY) {
        double shift = chroma.shift(System.currentTimeMillis());
        float hue = chroma.hue(shift, 0.0);
        float sat = chroma.saturation01();
        int now = chroma.rgb(shift, 0.0);
        ModernGuiUtils.drawRoundedOutline(graphics, l.wheelX() - 1, l.wheelY() - 1, l.wheelSize() + 2, l.wheelSize() + 2,
                l.wheelSize() / 2 + 1, AlpakaStyle.LINE);
        double angle = hue * 2.0 * Math.PI;
        float knobX = (float) (l.wheelCx() + Math.cos(angle) * sat * l.wheelR());
        float knobY = (float) (l.wheelCy() - Math.sin(angle) * sat * l.wheelR());
        AlpakaStyle.knob(graphics, knobX, knobY, 11, now);

        // The preview, and in place of the hex code a few letters in the rainbow, as text shows it.
        AlpakaStyle.swatch(graphics, l.rightX(), l.bodyY(), PREVIEW_W, FIELD_H, 0xFF000000 | now);
        AlpakaStyle.textField(graphics, this.font, l.hexX(), l.bodyY(), l.hexW(), FIELD_H, "", "", false, false, 0L);
        String sample = "Chroma";
        int tx = l.hexX() + 5;
        int ty = l.bodyY() + (FIELD_H - 8) / 2;
        for (int i = 0; i < sample.length(); i++) {
            String letter = sample.substring(i, i + 1);
            graphics.text(this.font, GuiFont.text(letter), tx, ty, 0xFF000000 | chroma.rgb(shift, i), false);
            tx += GuiFont.width(this.font, letter);
        }

        float speedNorm = (chroma.speed() - Chroma.MIN_SPEED) / (Chroma.MAX_SPEED - Chroma.MIN_SPEED);
        slider(graphics, l, 0, "Speed", String.format(Locale.ROOT, "%.2fx", chroma.speed()), speedNorm,
                mouseX, mouseY, drag == Drag.SPEED);
        int pure = new Hsv(hue, 1.0f, 1.0f).rgb();
        slider(graphics, l, 1, "Saturation", sat, 0xFFFFFFFF, pure, mouseX, mouseY, drag == Drag.SATURATION, now);
        if (chromaSpec.spread()) {
            float sizeNorm = (chroma.size() - Chroma.MIN_SIZE) / (Chroma.MAX_SIZE - Chroma.MIN_SIZE);
            slider(graphics, l, 2, "Size", Math.round(chroma.size()) + " letters", sizeNorm,
                    mouseX, mouseY, drag == Drag.SIZE);
        }
        drawButtons(graphics, l, mouseX, mouseY);
    }

    /** A plain slider row: its name, its value, and the config's accent track. */
    private void slider(GuiGraphicsExtractor graphics, Layout l, int row, String label, String shown, float norm,
                        int mouseX, int mouseY, boolean dragging) {
        int labelY = l.sliderLabelY(row);
        graphics.text(this.font, GuiFont.text(label), l.rightX(), labelY + 4, AlpakaStyle.TEXT_SOFT, false);
        int valueW = GuiFont.width(this.font, shown);
        graphics.text(this.font, GuiFont.text(shown), l.rightX() + l.rightW() - valueW, labelY + 4, AlpakaStyle.TEXT, false);
        AlpakaStyle.sliderTrack(graphics, l.trackX(), l.trackY(row), l.trackW(), norm, overSlider(l, row, mouseX, mouseY), dragging);
    }

    private void slider(GuiGraphicsExtractor graphics, Layout l, int row, String label, float value,
                        int from, int to, int mouseX, int mouseY, boolean dragging) {
        slider(graphics, l, row, label, value, from, to, mouseX, mouseY, dragging, rgb);
    }

    private void slider(GuiGraphicsExtractor graphics, Layout l, int row, String label, float value,
                        int from, int to, int mouseX, int mouseY, boolean dragging, int knobColor) {
        int labelY = l.sliderLabelY(row);
        String shown = Math.round(value * 100.0f) + "%";
        graphics.text(this.font, GuiFont.text(label), l.rightX(), labelY + 4, AlpakaStyle.TEXT_SOFT, false);
        int valueW = GuiFont.width(this.font, shown);
        graphics.text(this.font, GuiFont.text(shown), l.rightX() + l.rightW() - valueW, labelY + 4, AlpakaStyle.TEXT, false);
        boolean hover = overSlider(l, row, mouseX, mouseY);
        AlpakaStyle.gradientTrack(graphics, l.trackX(), l.trackY(row), l.trackW(), from, to, value, knobColor, hover, dragging);
    }

    // ---- Input ----------------------------------------------------------------------------------

    private static boolean overWheel(Layout l, double mouseX, double mouseY) {
        return Math.hypot(mouseX - l.wheelCx(), mouseY - l.wheelCy()) <= l.wheelR() + 3;
    }

    private boolean overChromaToggle(Layout l, double mouseX, double mouseY) {
        if (chroma == null) return false;
        int labelW = GuiFont.width(this.font, "Chroma");
        return inside(mouseX, mouseY, l.chromaToggleX() - 6 - labelW - 2, l.chromaToggleY() - 4,
                labelW + 6 + TOGGLE_W + 6, TOGGLE_H + 8);
    }

    private static boolean overSlider(Layout l, int row, double mouseX, double mouseY) {
        return inside(mouseX, mouseY, l.rightX() - 4, l.sliderLabelY(row), l.rightW() + 8, SLIDER_ROW_H);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        Layout l = Layout.of(this.width, this.height);
        if (event.button() == 1 && !chromaOn()) {
            for (int i = PRESETS.length; i < slotCount(); i++) {
                if (inside(mouseX, mouseY, l.swatchX(i) - 2, l.swatchY(i) - 2, SWATCH + 4, SWATCH + 4)) {
                    AlpakaStyle.click();
                    removeCustomPreset(i);
                    return true;
                }
            }
        }
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);

        boolean wasHex = hexFocused;
        hexFocused = false;

        if (canReset() && inside(mouseX, mouseY, l.resetX(), headerButtonY(l), RESET_W, AlpakaStyle.BUTTON_H)) {
            AlpakaStyle.click();
            setColor(defaultColor);
            syncHexFromColor();
            if (chroma != null) chroma.set(chromaSpec.defaults());
            return true;
        }

        if (overChromaToggle(l, mouseX, mouseY)) {
            AlpakaStyle.click();
            chroma.enabled = !chroma.enabled;
            return true;
        }

        if (chromaOn()) {
            if (overSlider(l, 0, mouseX, mouseY)) drag = Drag.SPEED;
            else if (overSlider(l, 1, mouseX, mouseY)) drag = Drag.SATURATION;
            else if (chromaSpec.spread() && overSlider(l, 2, mouseX, mouseY)) drag = Drag.SIZE;
            if (drag != Drag.NONE) {
                dragTo(l, mouseX, mouseY);
                return true;
            }
            return clickButtons(l, mouseX, mouseY) || super.mouseClicked(event, doubleClick);
        }

        if (inside(mouseX, mouseY, l.hexX(), l.bodyY(), l.hexW(), FIELD_H)) {
            if (!wasHex) {
                AlpakaStyle.click();
                hexInput = hexOf();
            }
            hexFocused = true;
            caretFrom = System.currentTimeMillis();
            return true;
        }
        syncHexFromColor();

        for (int i = 0; i <= slotCount(); i++) {
            if (inside(mouseX, mouseY, l.swatchX(i) - 2, l.swatchY(i) - 2, SWATCH + 4, SWATCH + 4)) {
                if (i == slotCount()) {
                    if (isPreset(currentColor())) return true;
                    AlpakaStyle.click();
                    addCurrentAsPreset();
                    return true;
                }
                AlpakaStyle.click();
                setColor(slotColor(i));
                syncHexFromColor();
                return true;
            }
        }

        if (overWheel(l, mouseX, mouseY)) {
            drag = Drag.WHEEL;
        } else if (overSlider(l, 0, mouseX, mouseY)) {
            drag = Drag.BRIGHTNESS;
        } else if (overSlider(l, 1, mouseX, mouseY)) {
            drag = Drag.OPACITY;
        }
        if (drag != Drag.NONE) {
            dragTo(l, mouseX, mouseY);
            return true;
        }

        return clickButtons(l, mouseX, mouseY) || super.mouseClicked(event, doubleClick);
    }

    private boolean clickButtons(Layout l, double mouseX, double mouseY) {
        if (inside(mouseX, mouseY, l.saveX(), l.btnY(), l.btnW(), AlpakaStyle.BUTTON_H)) {
            save();
            return true;
        }
        if (inside(mouseX, mouseY, l.cancelX(), l.btnY(), l.btnW(), AlpakaStyle.BUTTON_H)) {
            AlpakaStyle.click();
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (drag != Drag.NONE) {
            dragTo(Layout.of(this.width, this.height), event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) this.drag = Drag.NONE;
        return super.mouseReleased(event);
    }

    /**
     * Moves whatever is being dragged to the mouse. On the wheel the point follows the mouse's
     * direction from the middle and stops at the rim when the mouse goes past it, so a drag can run
     * round the edge without slipping off.
     */
    private void dragTo(Layout l, double mouseX, double mouseY) {
        switch (drag) {
            case WHEEL -> {
                double dx = mouseX - l.wheelCx();
                double dy = l.wheelCy() - mouseY;
                float sat = (float) Math.min(1.0, Math.hypot(dx, dy) / l.wheelR());
                float hue = sat > 0.0f ? (float) (Math.atan2(dy, dx) / (2.0 * Math.PI)) : hsv.hue();
                if (hue < 0.0f) hue += 1.0f;
                // A point on a fully dark wheel would show nothing, so picking a color brings the
                // brightness back up.
                float val = hsv.val() < 0.05f ? 1.0f : hsv.val();
                setHsv(new Hsv(hue, sat, val));
            }
            case BRIGHTNESS -> setHsv(new Hsv(hsv.hue(), hsv.sat(), (float) sliderNorm(l, mouseX)));
            case OPACITY -> {
                a = (int) Math.round(sliderNorm(l, mouseX) * 255.0);
                syncHexFromColor();
            }
            // Rounded to the steps the values are shown in, so a saved value reads as shown.
            case SPEED -> chroma.speed = Math.round(
                    (Chroma.MIN_SPEED + sliderNorm(l, mouseX) * (Chroma.MAX_SPEED - Chroma.MIN_SPEED)) * 20.0) / 20.0f;
            case SATURATION -> chroma.saturation = Math.round(sliderNorm(l, mouseX) * 100.0);
            case SIZE -> chroma.size = Math.round(
                    Chroma.MIN_SIZE + sliderNorm(l, mouseX) * (Chroma.MAX_SIZE - Chroma.MIN_SIZE));
            default -> {}
        }
    }

    private static double sliderNorm(Layout l, double mouseX) {
        return Math.max(0.0, Math.min(1.0, (mouseX - l.trackX()) / (double) l.trackW()));
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
        // Enter saves, as Save does; Escape leaves without saving, as Cancel does.
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(event);
    }

    private void save() {
        AlpakaStyle.click();
        // The rainbow first: the color's setter saves the config, which then carries both.
        if (chroma != null) chromaSpec.live().set(chroma);
        onSave.accept(currentColor());
        onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(this.parent);
    }
}
