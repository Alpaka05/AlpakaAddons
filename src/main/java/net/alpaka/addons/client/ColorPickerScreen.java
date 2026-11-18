package net.alpaka.addons.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.alpaka.addons.client.gui.AlpakaGuiElementSink;
import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.AlpakaStyle.ButtonKind;
import net.alpaka.addons.client.gui.EditableText;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.Hsv;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.client.gui.RoundedImageRenderState;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.config.Chroma;
import net.alpaka.addons.config.GradientPreset;
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
 * An option that offers more than one color ({@code ConfigOption.withGradient},
 * {@code ConfigOption.withChroma}) has its modes on top of the right column: Solid, Gradient and
 * Chroma, only those it offers. In Gradient both colors sit on the wheel and next to the hex code,
 * and whichever is picked out is the one the wheel and the sliders change. In Chroma the point
 * runs round the wheel the way the rainbow moves, and the sliders set its speed, saturation and,
 * for a rainbow along text, its size. Each mode has presets of its own: a gradient preset sets
 * both colors, a Chroma preset the whole rainbow.
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

    /**
     * The second color of an option that can be a gradient: its end color now and by default,
     * whether the gradient is on now and by default, and what takes both on Save.
     */
    public record GradientSpec(int end, int defaultEnd, boolean on, boolean defaultOn,
                               java.util.function.BiConsumer<Boolean, Integer> onSave) {
    }

    private enum Mode {
        SOLID("Solid"), GRADIENT("Gradient"), CHROMA("Chroma");

        final String label;

        Mode(String label) { this.label = label; }
    }

    /** The option's gradient, or null; the edited state is {@link #gradientOn} and the two stops. */
    private final GradientSpec gradientSpec;
    private boolean gradientOn;
    /**
     * In Gradient, the color not picked out: the end while the start is edited, and the other way
     * round. The picked-out one is the color the wheel and the sliders work on ({@link #rgb}).
     */
    private int otherStop;
    private boolean editingEnd = false;
    /** The modes this option offers, in the order the row shows them. */
    private final java.util.List<Mode> modes = new java.util.ArrayList<>();

    /** The hex code's text, caret and selection; only '#' and hex digits go in. */
    private final EditableText hexInput = new EditableText(9).filter(c -> c == '#' || Character.digit(c, 16) >= 0);
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
        this(parent, title, initialColor, defaultColor, onSave, chromaSpec, null);
    }

    public ColorPickerScreen(Screen parent, String title, int initialColor, Integer defaultColor, Consumer<Integer> onSave,
                             ChromaSpec chromaSpec, GradientSpec gradientSpec) {
        super(GuiFont.text(title));
        this.parent = parent;
        this.onSave = onSave;
        this.defaultColor = defaultColor;
        this.chromaSpec = chromaSpec;
        this.chroma = chromaSpec == null ? null : chromaSpec.live().copy();
        this.gradientSpec = gradientSpec;
        this.gradientOn = gradientSpec != null && gradientSpec.on();
        this.otherStop = gradientSpec == null ? 0 : gradientSpec.end();
        if (gradientSpec != null || chromaSpec != null) {
            modes.add(Mode.SOLID);
            if (gradientSpec != null) modes.add(Mode.GRADIENT);
            if (chromaSpec != null) modes.add(Mode.CHROMA);
        }
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

    private Mode mode() {
        if (chromaOn()) return Mode.CHROMA;
        return gradientSpec != null && gradientOn ? Mode.GRADIENT : Mode.SOLID;
    }

    private void setMode(Mode next) {
        if (chroma != null) chroma.enabled = next == Mode.CHROMA;
        if (next == Mode.CHROMA) return;
        if (gradientSpec != null) gradientOn = next == Mode.GRADIENT;
        // Solid is the start color, so that is what stays under the wheel.
        if (next == Mode.SOLID) selectStop(false);
    }

    /** The option's own color, the gradient's start. */
    private int startColor() {
        return editingEnd ? otherStop : currentColor();
    }

    private int endColor() {
        return editingEnd ? currentColor() : otherStop;
    }

    /** Picks out the gradient's start or end for the wheel and the sliders. */
    private void selectStop(boolean end) {
        if (end == editingEnd) return;
        int now = currentColor();
        setColor(otherStop);
        otherStop = now;
        editingEnd = end;
        hexFocused = false;
        syncHexFromColor();
    }

    /** Whether anything differs from the option's default, which is when Reset shows. */
    private boolean differsFromDefault() {
        if (defaultColor != null && startColor() != defaultColor) return true;
        if (gradientSpec != null && (gradientOn != gradientSpec.defaultOn() || endColor() != gradientSpec.defaultEnd())) {
            return true;
        }
        return chroma != null && !chroma.sameAs(chromaSpec.defaults());
    }

    private boolean canReset() {
        return defaultColor != null && differsFromDefault();
    }

    private String hexOf() {
        return a == 255 ? String.format(Locale.ROOT, "#%06X", rgb) : String.format(Locale.ROOT, "#%08X", currentColor());
    }

    private void syncHexFromColor() {
        if (!hexFocused) this.hexInput.setText(hexOf());
    }

    private void tryParseHexInput() {
        String clean = hexInput.getText().trim().replace("#", "");
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
    //
    // Each mode has presets of its own: colors for Solid, gradients for Gradient and rainbows for
    // Chroma. The row shows only the mode that is on, so a preset always sets the whole of that
    // mode (a gradient both its colors, a rainbow its speed, size and saturation) and never half of
    // another one.

    private static final GradientPreset[] GRADIENT_PRESETS = {
            new GradientPreset(0xFF55FFFF, 0xFFFF55FF), // Cyan to pink
            new GradientPreset(0xFFFFE15A, 0xFFFF4A3A), // Gold to red
            new GradientPreset(0xFF29B6F6, 0xFF00E676), // Blue to green
            new GradientPreset(0xFFAB47BC, 0xFFFF4081), // Purple to pink
            new GradientPreset(0xFFFFEA00, 0xFFFF9800), // Yellow to orange
            new GradientPreset(0xFF00E5FF, 0xFF29B6B2), // Cyan to teal
            new GradientPreset(0xFFFF9800, 0xFFFF1744), // Orange to red
            new GradientPreset(0xFF5C6BC0, 0xFFAB47BC), // Indigo to purple
            new GradientPreset(0xFF00E676, 0xFFFFEA00), // Green to yellow
            new GradientPreset(0xFFFFFFFF, 0xFF9FB4D0), // White to steel
    };

    private static final Chroma[] CHROMA_PRESETS = {
            new Chroma(true, 1.0f, 30.0f, 80.0f),  // The default
            new Chroma(true, 0.5f, 30.0f, 45.0f),  // Slow pastel
            new Chroma(true, 2.0f, 15.0f, 100.0f), // Fast and vivid
            new Chroma(true, 0.25f, 60.0f, 85.0f), // Calm and wide
            new Chroma(true, 1.0f, 8.0f, 90.0f),   // Tight bands
    };

    private static AlpakaConfig cfg() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        if (cfg.customColorPresets == null) cfg.customColorPresets = new java.util.ArrayList<>();
        if (cfg.customGradientPresets == null) cfg.customGradientPresets = new java.util.ArrayList<>();
        if (cfg.customChromaPresets == null) cfg.customChromaPresets = new java.util.ArrayList<>();
        return cfg;
    }

    /** How many presets the mode now has built in, before the player's own. */
    private int builtInCount() {
        return switch (mode()) {
            case SOLID -> PRESETS.length;
            case GRADIENT -> GRADIENT_PRESETS.length;
            case CHROMA -> CHROMA_PRESETS.length;
        };
    }

    /** The player's own presets of the mode now. */
    private java.util.List<?> customList() {
        return switch (mode()) {
            case SOLID -> cfg().customColorPresets;
            case GRADIENT -> cfg().customGradientPresets;
            case CHROMA -> cfg().customChromaPresets;
        };
    }

    /** The built-in presets of the mode now, then the player's own. */
    private int slotCount() {
        return builtInCount() + customList().size();
    }

    private int solidAt(int i) {
        return i < PRESETS.length ? PRESETS[i] : cfg().customColorPresets.get(i - PRESETS.length);
    }

    private GradientPreset gradientAt(int i) {
        return i < GRADIENT_PRESETS.length ? GRADIENT_PRESETS[i] : cfg().customGradientPresets.get(i - GRADIENT_PRESETS.length);
    }

    private Chroma chromaAt(int i) {
        return i < CHROMA_PRESETS.length ? CHROMA_PRESETS[i] : cfg().customChromaPresets.get(i - CHROMA_PRESETS.length);
    }

    /** Whether the slot holds exactly what the picker shows now, which rings it. */
    private boolean slotIsCurrent(int i) {
        return switch (mode()) {
            case SOLID -> solidAt(i) == currentColor();
            case GRADIENT -> {
                GradientPreset g = gradientAt(i);
                yield g.start == startColor() && g.end == endColor();
            }
            case CHROMA -> sameRainbow(chromaAt(i), chroma);
        };
    }

    /** Two rainbows look the same; the size counts only where the picker has its slider. */
    private boolean sameRainbow(Chroma a, Chroma b) {
        return a.speed() == b.speed() && a.saturation01() == b.saturation01()
                && (!chromaSpec.spread() || a.size() == b.size());
    }

    private boolean currentIsPreset() {
        for (int i = 0; i < slotCount(); i++) {
            if (slotIsCurrent(i)) return true;
        }
        return false;
    }

    /** Puts a preset into the picker: the color, both colors of a gradient, or the whole rainbow. */
    private void applySlot(int i) {
        switch (mode()) {
            case SOLID -> {
                setColor(solidAt(i));
                syncHexFromColor();
            }
            case GRADIENT -> {
                GradientPreset g = gradientAt(i);
                editingEnd = false;
                setColor(g.start);
                otherStop = g.end;
                syncHexFromColor();
            }
            case CHROMA -> {
                Chroma c = chromaAt(i);
                chroma.speed = c.speed();
                chroma.saturation = c.saturation01() * 100.0f;
                chroma.size = c.size();
            }
        }
    }

    /**
     * Saves what the picker shows now as a preset of its mode. It is kept at once, whether the
     * picker is then saved or cancelled: the presets are a palette of their own, not part of the
     * option being edited.
     */
    private void addCurrentAsPreset() {
        if (currentIsPreset()) return;
        switch (mode()) {
            case SOLID -> addCapped(cfg().customColorPresets, currentColor());
            case GRADIENT -> addCapped(cfg().customGradientPresets, new GradientPreset(startColor(), endColor()));
            case CHROMA -> {
                Chroma saved = chroma.copy();
                saved.enabled = true;
                addCapped(cfg().customChromaPresets, saved);
            }
        }
        AlpakaConfig.save();
    }

    private static <T> void addCapped(java.util.List<T> list, T entry) {
        while (list.size() >= MAX_CUSTOM) list.remove(0);
        list.add(entry);
    }

    private void removeCustomPreset(int slot) {
        customList().remove(slot - builtInCount());
        AlpakaConfig.save();
    }

    /**
     * A preset as a round swatch showing what it sets: one color, the gradient from left to right,
     * or the rainbow moving across it at the preset's speed and saturation.
     */
    private void drawSlot(GuiGraphicsExtractor graphics, int i, int x, int y) {
        switch (mode()) {
            case SOLID -> ModernGuiUtils.drawRoundedRect(graphics, x, y, SWATCH, SWATCH, (SWATCH + 1) / 2, solidAt(i));
            case GRADIENT -> {
                GradientPreset g = gradientAt(i);
                roundSwatch(graphics, x, y, t -> lerpArgb(g.start, g.end, t));
            }
            case CHROMA -> {
                Chroma c = chromaAt(i);
                double shift = c.shift(System.currentTimeMillis());
                // Across a dozen letters, as on text, so a tight rainbow shows more bands than a
                // wide one; without text, one whole rainbow.
                double across = chromaSpec.spread() ? 12.0 : c.size();
                roundSwatch(graphics, x, y, t -> 0xFF000000 | c.rgb(shift, t * across));
            }
        }
    }

    /** A round swatch filled left to right by {@code colorAt}, as thin vertical bands of the circle. */
    private static void roundSwatch(GuiGraphicsExtractor graphics, int x, int y,
                                    java.util.function.DoubleToIntFunction colorAt) {
        int bands = 16;
        float r = SWATCH / 2.0f;
        float cx = x + r;
        float cy = y + r;
        float feather = 1.0f / Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        net.alpaka.addons.features.wheel.WheelMesh mesh = new net.alpaka.addons.features.wheel.WheelMesh(feather);
        for (int b = 0; b < bands; b++) {
            float t0 = b / (float) bands;
            float t1 = (b + 1) / (float) bands;
            float x0 = x + t0 * SWATCH;
            float x1 = x + t1 * SWATCH;
            float h0 = (float) Math.sqrt(Math.max(0.0f, r * r - (x0 - cx) * (x0 - cx)));
            float h1 = (float) Math.sqrt(Math.max(0.0f, r * r - (x1 - cx) * (x1 - cx)));
            int c0 = colorAt.applyAsInt(t0);
            int c1 = colorAt.applyAsInt(t1);
            mesh.quad(x0, cy - h0, c0, x0, cy + h0, c0, x1, cy + h1, c1, x1, cy - h1, c1);
        }
        mesh.submit(graphics);
    }

    private static int lerpArgb(int from, int to, double t) {
        int a = (int) Math.round(((from >>> 24) & 0xFF) * (1 - t) + ((to >>> 24) & 0xFF) * t);
        int r = (int) Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
        int g = (int) Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
        int b = (int) Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private String presetNoun() {
        return switch (mode()) {
            case SOLID -> "color";
            case GRADIENT -> "gradient";
            case CHROMA -> "rainbow";
        };
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
    /** With the mode row above them the rows sit closer, so two rows of presets still fit. */
    private static final int SLIDER_ROW_H_COMPACT = 24;
    /** The mode row: the names, a line under them, and the gap to what follows. */
    private static final int MODE_H = 18;
    /** Each of a gradient's two colors beside the hex code. */
    private static final int STOP_W = 20;
    private static final int FIELD_H = 16;
    private static final int PREVIEW_W = 28;
    private static final int RESET_W = 40;

    /** The header's Reset button sits centred in the header, as every header button does. */
    private static int headerButtonY(Layout l) {
        return AlpakaStyle.headerButtonY(l.y());
    }

    /**
     * Where everything goes, shared by drawing and input. The wheel takes the left side down to the
     * window's bottom edge, beside the Cancel and Save buttons, which sit to the right of it. The
     * right column starts with the mode row when the option has modes, then the hex row (none in
     * Chroma, which has no single color to type), the sliders and the presets.
     */
    private record Layout(int x, int y, int w, int h, int bodyY, int top, int wheelX, int wheelY, int wheelSize,
                          int rightX, int rightW, int rowH, boolean hexRow, boolean twoStops, int sliderRows,
                          int btnY, int saveX, int cancelX, int btnW, int resetX) {

        static Layout of(int width, int height, boolean modeRow, Mode mode, boolean spread) {
            int w = Math.max(280, Math.min(PREF_W, width - 16));
            int h = Math.max(180, Math.min(PREF_H, height - 16));
            int x = (width - w) / 2;
            int y = (height - h) / 2;
            int bodyY = y + AlpakaStyle.HEADER_H + 10;
            int top = bodyY + (modeRow ? MODE_H : 0);
            int bottom = y + h - AlpakaStyle.PAD - 4;
            int wheelSize = Math.max(64, Math.min(MAX_WHEEL, Math.min(bottom - bodyY, w * 2 / 5)));
            int wheelX = x + AlpakaStyle.PAD + 6;
            int wheelY = bodyY + (bottom - bodyY - wheelSize) / 2;
            int rightX = wheelX + wheelSize + 18;
            int rightW = x + w - AlpakaStyle.PAD - 6 - rightX;
            int btnY = y + h - AlpakaStyle.BUTTON_H - AlpakaStyle.PAD;
            int btnW = 64;
            int saveX = x + w - AlpakaStyle.PAD - btnW;
            int cancelX = saveX - btnW - 6;
            int resetX = x + w - AlpakaStyle.PAD - RESET_W;
            return new Layout(x, y, w, h, bodyY, top, wheelX, wheelY, wheelSize, rightX, rightW,
                    modeRow ? SLIDER_ROW_H_COMPACT : SLIDER_ROW_H, mode != Mode.CHROMA, mode == Mode.GRADIENT,
                    mode == Mode.CHROMA && spread ? 3 : 2, btnY, saveX, cancelX, btnW, resetX);
        }

        double wheelCx() { return wheelX + wheelSize / 2.0; }
        double wheelCy() { return wheelY + wheelSize / 2.0; }
        double wheelR() { return wheelSize / 2.0; }
        /** Left of the hex code: the color now, or a gradient's start and end side by side. */
        int stopX(boolean end) { return rightX + (end ? STOP_W + 4 : 0); }
        int hexX() { return rightX + (twoStops ? 2 * STOP_W + 4 : PREVIEW_W) + 6; }
        int hexW() { return rightX + rightW - hexX(); }
        int sliderLabelY(int i) { return top + (hexRow ? FIELD_H + 8 : 0) + i * rowH; }
        int presetLabelY() { return sliderLabelY(sliderRows) + 6; }
        int trackY(int i) { return sliderLabelY(i) + 17; }
        // The track's round ends reach 3 px past its ends, so it is inset to stay in the column.
        int trackX() { return rightX + 3; }
        int trackW() { return rightW - 6; }
        int swatchX(int i) { return rightX + (i % PRESETS_PER_ROW) * (rightW - SWATCH) / (PRESETS_PER_ROW - 1); }
        int swatchY(int i) { return presetLabelY() + 12 + (i / PRESETS_PER_ROW) * (SWATCH + 6); }
    }

    private Layout layout() {
        return Layout.of(this.width, this.height, modes.size() > 1, mode(), chromaSpec != null && chromaSpec.spread());
    }

    // ---- Drawing --------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        AlpakaStyle.backdrop(graphics, this.width, this.height);
        boolean pushed = AlpakaStyle.beginOpen(graphics, openTimeMs, this.width, this.height);
        Layout l = layout();
        int accent = ModernGuiUtils.getAccentColor();

        AlpakaStyle.panel(graphics, l.x(), l.y(), l.w(), l.h());
        AlpakaStyle.header(graphics, this.font, this.title.getString(), l.x(), l.y(), l.w());

        drawModes(graphics, l, mouseX, mouseY);

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
            presetTooltip(graphics, mouseX, mouseY);
            net.alpaka.addons.features.notification.AlpakaNotifications.renderOverScreen(graphics);
            return;
        }
        int shade = Math.round((1.0f - hsv.val()) * 255.0f);
        if (shade > 0) ModernGuiUtils.drawRoundedRect(graphics, l.wheelX(), l.wheelY(), ws, ws, ws / 2, shade << 24);
        ModernGuiUtils.drawRoundedOutline(graphics, l.wheelX() - 1, l.wheelY() - 1, ws + 2, ws + 2, ws / 2 + 1, AlpakaStyle.LINE);
        float[] knob = wheelPoint(l, hsv.hue(), hsv.sat());
        boolean hoverWheel = overWheel(l, mouseX, mouseY);
        if (mode() == Mode.GRADIENT) {
            // The other color as a smaller point, joined to this one by a dotted line, so both ends
            // of the gradient show where they sit; a click on it picks it out.
            Hsv other = Hsv.of(otherStop & 0x00FFFFFF, new Hsv(0.0f, 0.0f, 1.0f));
            float[] o = wheelPoint(l, other.hue(), other.sat());
            int dots = Math.max(2, (int) (Math.hypot(o[0] - knob[0], o[1] - knob[1]) / 7.0));
            for (int i = 1; i < dots; i++) {
                float t = i / (float) dots;
                int dx = Math.round(knob[0] + (o[0] - knob[0]) * t);
                int dy = Math.round(knob[1] + (o[1] - knob[1]) * t);
                ModernGuiUtils.drawRoundedRect(graphics, dx - 1, dy - 1, 2, 2, 1, 0xCCFFFFFF);
            }
            AlpakaStyle.knob(graphics, o[0], o[1], overOtherStop(l, mouseX, mouseY) ? 10 : 9, otherStop | 0xFF000000);
        }
        AlpakaStyle.knob(graphics, knob[0], knob[1], hoverWheel || drag == Drag.WHEEL ? 12 : 11, rgb);

        // The color now, and its hex code to type into. hexInput mirrors the color whenever the field
        // is not being typed in (syncHexFromColor runs after every change and on leaving the field),
        // so it is drawn as it is rather than formatted anew every frame.
        if (l.twoStops()) {
            drawStop(graphics, l, false, mouseX, mouseY);
            drawStop(graphics, l, true, mouseX, mouseY);
        } else {
            AlpakaStyle.swatch(graphics, l.rightX(), l.top(), PREVIEW_W, FIELD_H, currentColor());
        }
        boolean hoverHex = inside(mouseX, mouseY, l.hexX(), l.top(), l.hexW(), FIELD_H);
        AlpakaStyle.textField(graphics, this.font, l.hexX(), l.top(), l.hexW(), FIELD_H,
                hexInput, null, "#RRGGBB", hexFocused, hoverHex, caretFrom);

        // Brightness runs from black to the color at full brightness; opacity from the window's own
        // color, which is what a see-through color shows over, to the color itself.
        int full = new Hsv(hsv.hue(), hsv.sat(), 1.0f).rgb();
        slider(graphics, l, 0, "Brightness", hsv.val(), 0xFF000000, full, mouseX, mouseY, drag == Drag.BRIGHTNESS);
        slider(graphics, l, 1, "Opacity", a / 255.0f, AlpakaStyle.ROW_SELECTED, 0xFF000000 | rgb, mouseX, mouseY, drag == Drag.OPACITY);

        drawPresets(graphics, l, mouseX, mouseY);

        drawButtons(graphics, l, mouseX, mouseY);

        AlpakaStyle.endOpen(graphics, pushed);
        if (l.twoStops()) {
            for (boolean end : new boolean[] {false, true}) {
                if (inside(mouseX, mouseY, l.stopX(end), l.top(), STOP_W, FIELD_H)) {
                    AlpakaStyle.tooltip(graphics, this.font, this.width, this.height, mouseX, mouseY,
                            end ? "End color" : "Start color");
                }
            }
        }
        presetTooltip(graphics, mouseX, mouseY);
        // Notices over the window, as over the config menu.
        net.alpaka.addons.features.notification.AlpakaNotifications.renderOverScreen(graphics);
    }

    private float[] wheelPoint(Layout l, float hue, float sat) {
        double angle = hue * 2.0 * Math.PI;
        return new float[] {(float) (l.wheelCx() + Math.cos(angle) * sat * l.wheelR()),
                (float) (l.wheelCy() - Math.sin(angle) * sat * l.wheelR())};
    }

    /** Whether the mouse is on the gradient's other point on the wheel, which a click picks out. */
    private boolean overOtherStop(Layout l, double mouseX, double mouseY) {
        if (mode() != Mode.GRADIENT) return false;
        Hsv other = Hsv.of(otherStop & 0x00FFFFFF, new Hsv(0.0f, 0.0f, 1.0f));
        float[] o = wheelPoint(l, other.hue(), other.sat());
        return Math.hypot(mouseX - o[0], mouseY - o[1]) <= 6.0;
    }

    /** One of a gradient's colors beside the hex code, ringed in the accent while it is picked out. */
    private void drawStop(GuiGraphicsExtractor graphics, Layout l, boolean end, int mouseX, int mouseY) {
        int sx = l.stopX(end);
        int sy = l.top();
        boolean picked = end == editingEnd;
        boolean hover = inside(mouseX, mouseY, sx, sy, STOP_W, FIELD_H);
        if (picked || hover) {
            ModernGuiUtils.drawRoundedOutline(graphics, sx - 2, sy - 2, STOP_W + 4, FIELD_H + 4, 4,
                    picked ? ModernGuiUtils.getAccentColor() : AlpakaStyle.TEXT_MUTED);
        }
        AlpakaStyle.swatch(graphics, sx, sy, STOP_W, FIELD_H, end ? endColor() : startColor());
    }

    /**
     * The modes on top of the right column, as the config lists its choices: a dot and the name,
     * both in the accent for the mode that is on.
     */
    private void drawModes(GuiGraphicsExtractor graphics, Layout l, int mouseX, int mouseY) {
        if (modes.size() < 2) return;
        int accent = ModernGuiUtils.getAccentColor();
        int cx = l.rightX();
        for (Mode m : modes) {
            int w = modeWidth(m);
            boolean on = m == mode();
            boolean hover = inside(mouseX, mouseY, cx - 3, l.bodyY() - 4, w + 6, 16);
            ModernGuiUtils.drawRoundedRect(graphics, cx, l.bodyY() + 1, 5, 5, 2, on ? accent : hover ? AlpakaStyle.TEXT_MUTED : AlpakaStyle.DIVIDER);
            graphics.text(this.font, GuiFont.text(m.label), cx + 9, l.bodyY(), on ? accent : hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT, false);
            cx += w + 14;
        }
        ModernGuiUtils.drawRect(graphics, l.rightX(), l.bodyY() + 12, l.rightW(), 1, AlpakaStyle.LINE);
    }

    private int modeWidth(Mode m) {
        return 9 + GuiFont.width(this.font, m.label);
    }

    /** The mode under the mouse in the mode row, or null. */
    private Mode modeAt(Layout l, double mouseX, double mouseY) {
        if (modes.size() < 2) return null;
        int cx = l.rightX();
        for (Mode m : modes) {
            int w = modeWidth(m);
            if (inside(mouseX, mouseY, cx - 3, l.bodyY() - 4, w + 6, 16)) return m;
            cx += w + 14;
        }
        return null;
    }

    /** The presets of the mode now, the current one ringed, and the plus after the last one. */
    private void drawPresets(GuiGraphicsExtractor graphics, Layout l, int mouseX, int mouseY) {
        int accent = ModernGuiUtils.getAccentColor();
        AlpakaStyle.groupLabel(graphics, this.font, "Presets", l.rightX(), l.presetLabelY());
        int slots = slotCount();
        boolean alreadyPreset = currentIsPreset();
        int hovered = -1;
        for (int i = 0; i <= slots; i++) {
            int sx = l.swatchX(i);
            int sy = l.swatchY(i);
            boolean hover = inside(mouseX, mouseY, sx - 2, sy - 2, SWATCH + 4, SWATCH + 4);
            if (hover) hovered = i;
            int round = (SWATCH + 1) / 2;
            if (i == slots) {
                // The plus: a dashed circle, an empty slot to fill, muted while what the picker
                // shows is a preset already.
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
            boolean current = slotIsCurrent(i);
            if (current || hover) {
                ModernGuiUtils.drawRoundedOutline(graphics, sx - 2, sy - 2, SWATCH + 4, SWATCH + 4, round + 2,
                        current ? accent : AlpakaStyle.TEXT_MUTED);
            }
            drawSlot(graphics, i, sx, sy);
        }
        if (hovered != hoveredSlot) {
            hoveredSlot = hovered;
            hoverSince = System.currentTimeMillis();
        }
    }

    private void presetTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (hoveredSlot < builtInCount() || System.currentTimeMillis() - hoverSince < AlpakaStyle.TOOLTIP_DELAY_MS) return;
        String noun = presetNoun();
        String tip = hoveredSlot < slotCount() ? "Right-click to remove this preset."
                : currentIsPreset() ? "This " + noun + " is a preset already."
                : "Save the current " + noun + " as a preset.";
        AlpakaStyle.tooltip(graphics, this.font, this.width, this.height, mouseX, mouseY, tip);
    }

    /** A click on a preset or the plus; true when it hit one. */
    private boolean clickPresets(Layout l, double mouseX, double mouseY) {
        for (int i = 0; i <= slotCount(); i++) {
            if (inside(mouseX, mouseY, l.swatchX(i) - 2, l.swatchY(i) - 2, SWATCH + 4, SWATCH + 4)) {
                if (i == slotCount()) {
                    if (currentIsPreset()) return true;
                    AlpakaStyle.click();
                    addCurrentAsPreset();
                    return true;
                }
                AlpakaStyle.click();
                applySlot(i);
                return true;
            }
        }
        return false;
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
     * saturation, and the sliders set the rainbow.
     */
    private void drawChroma(GuiGraphicsExtractor graphics, Layout l, int mouseX, int mouseY) {
        double shift = chroma.shift(System.currentTimeMillis());
        float hue = chroma.hue(shift, 0.0);
        float sat = chroma.saturation01();
        int now = chroma.rgb(shift, 0.0);
        ModernGuiUtils.drawRoundedOutline(graphics, l.wheelX() - 1, l.wheelY() - 1, l.wheelSize() + 2, l.wheelSize() + 2,
                l.wheelSize() / 2 + 1, AlpakaStyle.LINE);
        float[] knob = wheelPoint(l, hue, sat);
        AlpakaStyle.knob(graphics, knob[0], knob[1], 11, now);

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
        drawPresets(graphics, l, mouseX, mouseY);
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

    private static boolean overSlider(Layout l, int row, double mouseX, double mouseY) {
        return inside(mouseX, mouseY, l.rightX() - 4, l.sliderLabelY(row), l.rightW() + 8, l.rowH());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        Layout l = layout();
        if (event.button() == 1) {
            for (int i = builtInCount(); i < slotCount(); i++) {
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
            editingEnd = false;
            setColor(defaultColor);
            syncHexFromColor();
            if (chroma != null) chroma.set(chromaSpec.defaults());
            if (gradientSpec != null) {
                gradientOn = gradientSpec.defaultOn();
                otherStop = gradientSpec.defaultEnd();
            }
            return true;
        }

        Mode clickedMode = modeAt(l, mouseX, mouseY);
        if (clickedMode != null) {
            if (clickedMode != mode()) {
                AlpakaStyle.click();
                setMode(clickedMode);
            }
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
            return clickPresets(l, mouseX, mouseY) || clickButtons(l, mouseX, mouseY)
                    || super.mouseClicked(event, doubleClick);
        }

        if (l.twoStops()) {
            for (boolean end : new boolean[] {false, true}) {
                if (inside(mouseX, mouseY, l.stopX(end) - 2, l.top() - 2, STOP_W + 4, FIELD_H + 4)) {
                    if (end != editingEnd) AlpakaStyle.click();
                    selectStop(end);
                    return true;
                }
            }
        }

        if (inside(mouseX, mouseY, l.hexX(), l.top(), l.hexW(), FIELD_H)) {
            if (!wasHex) {
                AlpakaStyle.click();
                hexInput.setText(hexOf());
            }
            AlpakaStyle.clickTextField(this.font, hexInput, null, l.hexX(), l.hexW(), event.x(), wasHex, event.hasShiftDown());
            hexFocused = true;
            caretFrom = System.currentTimeMillis();
            return true;
        }
        syncHexFromColor();

        if (clickPresets(l, mouseX, mouseY)) return true;

        if (overOtherStop(l, mouseX, mouseY)) {
            // The gradient's other point: picked out, and dragged from where it sits.
            AlpakaStyle.click();
            selectStop(!editingEnd);
            drag = Drag.WHEEL;
            return true;
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
            dragTo(layout(), event.x(), event.y());
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
            if (hexInput.insert(event.codepoint())) tryParseHexInput();
            caretFrom = System.currentTimeMillis();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (hexFocused) {
            int key = event.key();
            String before = hexInput.getText();
            if (hexInput.keyPressed(event)) {
                if (!hexInput.getText().equals(before)) tryParseHexInput();
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
        // The rainbow and the gradient first: the color's setter saves the config, which then
        // carries them all.
        if (chroma != null) chromaSpec.live().set(chroma);
        if (gradientSpec != null) gradientSpec.onSave().accept(gradientOn, endColor());
        onSave.accept(startColor());
        onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(this.parent);
    }
}
