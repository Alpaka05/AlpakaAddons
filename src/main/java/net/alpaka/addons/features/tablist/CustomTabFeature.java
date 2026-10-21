package net.alpaka.addons.features.tablist;

import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.HudPanel;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.mixin.PlayerTabOverlayAccessor;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom Tab: Hypixel SkyBlock's player list redrawn as one clean panel.
 *
 * Vanilla draws the list as four fixed columns of 20 slots, every slot its own gray box, empty
 * ones included, with Hypixel's ads above and below. Here the slots are read into categories and
 * sections ({@link TabListParser}), laid out without the empty slots ({@link CustomTabLayout}) and
 * drawn on a single panel - blurred like the other HUDs', in the menu font if asked.
 *
 * Only on SkyBlock, where the list has that shape; anywhere else vanilla's list is drawn as it is,
 * but the toggle still applies to it.
 *
 * The panel hangs from the top edge of the screen like a dock: its top corners sit above the
 * screen, so only the bottom ones are rounded, and with Slide Animation it slides down out of the
 * edge when it opens and back up into it when it closes.
 *
 * With Toggle Tab List, a press of the player list key shows the list until the next press,
 * rather than only while the key is held.
 */
public final class CustomTabFeature {

    /** How often the list is read again while it is up; Hypixel updates it about once a second. */
    private static final long REBUILD_MS = 250L;
    /** The panel's top edge: the screen's own. */
    private static final int TOP = 0;
    /**
     * How far the panel reaches above the screen, in the tab's pixels: its corner radius and its
     * shadow, so the top corners are cut off by the edge and the panel reads as hanging from it.
     */
    private static final int DOCK_OVERLAP = HudPanel.RADIUS + HudPanel.SHADOW;
    /** How long the slide takes, open or closed. */
    private static final float SLIDE_SECONDS = 0.18f;
    private static final int SCREEN_MARGIN = 10;
    /** The panel without blur: vanilla's list background. */
    private static final int PLAIN_BACKGROUND = 0x80000000;

    public static final String[] LAYOUT_NAMES = {"Side by Side", "Stacked"};

    private static boolean toggledOpen = false;

    /** How far the list is out, 0 (hidden above the screen) to 1 (fully down), and when it last moved. */
    private static float slide = 0.0f;
    private static long slideAtNs = 0L;

    /** A made-up SkyBlock list the screenshot tour shows in place of a live one; null in play. */
    public record DevSample(List<Component> slots, Component header, Component footer) {}

    private static DevSample devSample = null;

    private static CustomTabLayout cached = null;
    /** The scale the cached layout is drawn at: the chosen one, or smaller where it would not fit. */
    private static float cachedScale = 1.0f;
    private static long cachedAtMs = 0L;
    private static String cachedKey = "";

    private CustomTabFeature() {}

    public static boolean isEnabled() {
        return AlpakaConfig.instance.customTabEnabled;
    }

    private static boolean toggles() {
        return isEnabled() && AlpakaConfig.instance.customTabToggle;
    }

    /**
     * A key press in the world: with the toggle on, the player list key flips the list. The press
     * is not taken, so vanilla still sees its key mapping go down; {@link #listShown} decides.
     */
    public static void onKey(KeyEvent event, boolean repeat) {
        if (repeat || !toggles()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) return;
        if (mc.options.keyPlayerList.matches(event)) toggledOpen = !toggledOpen;
    }

    /** Whether the player list is up: held down normally, or flipped open with the toggle. */
    public static boolean listShown(boolean keyDown) {
        if (!toggles()) return keyDown;
        Minecraft mc = Minecraft.getInstance();
        // Toggled open stays out of the way of every screen but the chat.
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) return false;
        return toggledOpen;
    }

    /**
     * Draws the list in place of vanilla's. Returns false where vanilla's list is to be drawn
     * instead: with the feature off, and off SkyBlock.
     */
    public static boolean render(GuiGraphicsExtractor graphics, Hud hud) {
        if (!isEnabled() || (devSample == null && !SkyblockUtils.isOnSkyblock())) return false;
        Minecraft mc = Minecraft.getInstance();
        PlayerTabOverlay tabList = hud.getTabList();
        boolean shown = mc.player != null && (devSample != null || listShown(mc.options.keyPlayerList.isDown()));
        tabList.setVisible(shown);
        if (!advanceSlide(shown)) {
            cached = null;
            return true;
        }

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        CustomTabLayout layout = layout(tabList, screenWidth, screenHeight);
        if (layout == null || layout.texts.isEmpty()) return true;
        float scale = cachedScale;
        float guiScale = (float) mc.getWindow().getGuiScale();

        // Closed, the whole panel - shadow included - sits just above the screen.
        float hidden = 1.0f - easeOut(slide);
        float offset = -hidden * (layout.height + DOCK_OVERLAP) * scale;
        // The left edge on a whole screen pixel, as the scale is, so every glyph pixel lands on one.
        float left = Math.round((screenWidth - layout.width * scale) / 2.0f * guiScale) / guiScale;

        graphics.nextStratum();
        graphics.pose().pushMatrix();
        graphics.pose().translate(left, TOP + offset);
        graphics.pose().scale(scale, scale);

        if (!AlpakaConfig.instance.customTabBackground) {
            // Text only: its drop shadow keeps it readable over the world.
        } else if (AlpakaConfig.instance.blurOn(AlpakaConfig.instance.customTabBlur)) {
            HudPanel.submit(graphics, 0, -DOCK_OVERLAP, layout.width, layout.height, scale, HudPanel.TINT, 1.0f);
        } else {
            graphics.fill(0, 0, layout.width, layout.height, PLAIN_BACKGROUND);
        }

        Font font = mc.font;
        ClientPacketListener connection = mc.getConnection();
        for (CustomTabLayout.Text text : layout.texts) {
            int x = text.x();
            if (text.face() != null) {
                PlayerInfo info = connection != null ? connection.getPlayerInfo(text.face()) : null;
                if (info != null) PlayerFaceExtractor.extractRenderState(graphics, info.getSkin(), x, text.y(), CustomTabLayout.FACE);
                x += CustomTabLayout.FACE + CustomTabLayout.FACE_GAP;
            }
            graphics.text(font, text.text(), x, text.y(), 0xFFFFFFFF, HudPanel.TEXT_SHADOW);
        }
        graphics.pose().popMatrix();
        return true;
    }

    /**
     * Moves the slide toward open or closed by the time since the last frame. Without the
     * animation it jumps straight there. Returns whether anything of the list is to be drawn.
     */
    private static boolean advanceSlide(boolean shown) {
        long now = System.nanoTime();
        // A long gap - the list closed, the game paused - starts the slide afresh, not halfway.
        float seconds = slideAtNs == 0L ? 0.0f : Math.min(0.1f, (now - slideAtNs) / 1.0e9f);
        slideAtNs = now;
        if (!AlpakaConfig.instance.customTabAnimated) {
            slide = shown ? 1.0f : 0.0f;
        } else {
            float step = seconds / SLIDE_SECONDS;
            slide = Math.max(0.0f, Math.min(1.0f, slide + (shown ? step : -step)));
        }
        if (slide <= 0.0f) slideAtNs = 0L;
        return slide > 0.0f;
    }

    private static float easeOut(float t) {
        float inverse = 1.0f - t;
        return 1.0f - inverse * inverse * inverse;
    }

    /**
     * The layout, read again at most every {@link #REBUILD_MS} or when anything it depends on
     * changes. Reading the list builds a component for each of the 80 slots, and the smooth font
     * restyles each, which is work not worth doing every frame for text that changes once a second.
     */
    private static CustomTabLayout layout(PlayerTabOverlay tabList, int screenWidth, int screenHeight) {
        float guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        float scale = crisp(AlpakaConfig.instance.customTabScale, guiScale, false);
        boolean smooth = AlpakaConfig.instance.smoothOn(AlpakaConfig.instance.customTabSmoothFont);
        boolean stacked = AlpakaConfig.instance.customTabLayout == 1;
        boolean hideAdverts = AlpakaConfig.instance.customTabHideAds;
        String key = screenWidth + "x" + screenHeight + "@" + scale + "x" + guiScale + "/" + smooth + GuiFont.selectedIndex()
                + "/" + stacked + "/" + hideAdverts;
        long now = System.currentTimeMillis();
        if (cached != null && key.equals(cachedKey) && now - cachedAtMs < REBUILD_MS) return cached;

        PlayerTabOverlayAccessor accessor = (PlayerTabOverlayAccessor) tabList;
        List<Component> slots = new ArrayList<>();
        Component headerText = accessor.alpaka$getHeader();
        Component footerText = accessor.alpaka$getFooter();
        if (devSample != null) {
            slots.addAll(devSample.slots());
            headerText = devSample.header();
            footerText = devSample.footer();
        } else {
            for (PlayerInfo info : accessor.alpaka$getPlayerInfos()) {
                slots.add(tabList.getNameForDisplay(info));
            }
        }
        List<TabListParser.Category> categories = TabListParser.parse(slots);
        List<Component> header = TabListParser.headerFooterLines(headerText, hideAdverts);
        List<Component> footer = TabListParser.headerFooterLines(footerText, hideAdverts);

        if (smooth) {
            header = restyled(header);
            footer = restyled(footer);
            List<TabListParser.Category> styled = new ArrayList<>(categories.size());
            for (TabListParser.Category category : categories) {
                List<List<TabListParser.Line>> sections = new ArrayList<>();
                for (List<TabListParser.Line> section : category.sections()) {
                    List<TabListParser.Line> lines = new ArrayList<>(section.size());
                    for (TabListParser.Line line : section) {
                        lines.add(new TabListParser.Line(GuiFont.restyle(line.text()), line.indented(), line.player()));
                    }
                    sections.add(lines);
                }
                styled.add(new TabListParser.Category(GuiFont.restyle(category.title()), sections));
            }
            categories = styled;
        }

        // Laid out at the chosen scale first. A list taller than the screen - a big GUI scale, a
        // small window - is laid out once more at the scale that makes it fit, which can wrap it
        // differently, and then shrunk the rest of the way if it still does not. Every scale is
        // rounded to whole screen pixels per text pixel (see crisp), down when it has to fit.
        float available = screenHeight - TOP - SCREEN_MARGIN;
        CustomTabLayout layout = build(header, categories, footer, stacked, screenWidth, screenHeight, scale);
        if (layout.height * scale > available) {
            scale = crisp(available / layout.height, guiScale, true);
            layout = build(header, categories, footer, stacked, screenWidth, screenHeight, scale);
            if (layout.height * scale > available) scale = crisp(available / layout.height, guiScale, true);
        }
        cached = layout;
        cachedScale = scale;
        cachedAtMs = now;
        cachedKey = key;
        return cached;
    }

    /**
     * The scale rounded so one pixel of the text covers a whole number of screen pixels. At 0.87
     * of GUI scale 4, say, a glyph pixel spans 3.48 screen pixels, so some columns come out three
     * pixels wide and some four, and the text looks soft and uneven; 0.75 makes every one three.
     * Rounded down where the list has to fit, to the nearest otherwise. Below one screen pixel per
     * text pixel there is nothing whole to round to, and the scale is left as it is.
     */
    static float crisp(float scale, float guiScale, boolean down) {
        float pixels = scale * guiScale;
        float whole = down ? (float) Math.floor(pixels + 1.0e-4f) : Math.round(pixels);
        return whole < 1.0f ? scale : whole / guiScale;
    }

    private static CustomTabLayout build(List<Component> header, List<TabListParser.Category> categories,
                                         List<Component> footer, boolean stacked, int screenWidth, int screenHeight,
                                         float scale) {
        int maxWidth = (int) ((screenWidth - SCREEN_MARGIN * 2) / scale) - CustomTabLayout.PAD_X * 2;
        int maxHeight = (int) ((screenHeight - TOP - SCREEN_MARGIN) / scale) - CustomTabLayout.PAD_Y * 2
                - (header.size() + footer.size()) * CustomTabLayout.LINE_H - CustomTabLayout.ROW_GAP * 2;
        Font font = Minecraft.getInstance().font;
        return CustomTabLayout.build(header, categories, footer, stacked, maxWidth, maxHeight, true, font::width);
    }

    /** Shows the sample, or the live list again with null; for the screenshot tour only. */
    public static void devShow(DevSample sample) {
        devSample = sample;
        cached = null;
    }

    private static List<Component> restyled(List<Component> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (Component line : lines) out.add(GuiFont.restyle(line));
        return out;
    }
}
