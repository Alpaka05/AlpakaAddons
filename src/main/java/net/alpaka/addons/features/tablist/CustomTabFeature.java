package net.alpaka.addons.features.tablist;

import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.HudPanel;
import com.mojang.blaze3d.platform.InputConstants;
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
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

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
 * rather than only while the key is held. With Show in Menus it stays up in the chat and in
 * inventories and chests, drawn above the menu rather than under its dimming.
 *
 * Progress Bars draws Hypixel's progress as bars ({@link TabProgressBars}), and Highlight Changes
 * lights a row up for a moment when its value changes ({@link TabChangeFlash}).
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
    /** The menu the list was last decided for, and whether the list was up as that menu opened. */
    private static Screen menu = null;
    private static boolean carriedIntoMenu = false;
    private static boolean lastShown = false;

    /** The rows' last values and lights; forgotten on a new server, where everything is new. */
    private static final TabChangeFlash FLASH = new TabChangeFlash();
    private static Object flashLevel = null;
    /** "Players (19)" is still the same category at 20 players. */
    private static final java.util.regex.Pattern TITLE_COUNT = java.util.regex.Pattern.compile("\\s*\\(.*\\)\\s*$");
    private static final int BAR_TRACK = 0x40FFFFFF;
    /** A widget's box: a faint fill, and an edge a little brighter so the box reads as one. */
    private static final int BOX_FILL = 0x14FFFFFF;
    private static final int BOX_EDGE = 0x26FFFFFF;
    /** The light behind a changed row at its brightest. */
    private static final int FLASH_ALPHA = 0x50;

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
        Screen screen = mc.gui.screen();
        // In an inventory the key flips the list too, where the list may stay up; in the chat Tab
        // completes what is typed.
        if (screen != null && !(screen instanceof AbstractContainerScreen<?> && showsOver(screen))) return;
        if (mc.options.keyPlayerList.matches(event)) toggledOpen = !toggledOpen;
    }

    /**
     * Whether a key press is to be swallowed: the player list key held down from the world into
     * the chat repeats, and each repeat would complete the typed text or cycle the chat tabs.
     */
    public static boolean takesRepeat(KeyEvent event, boolean repeat) {
        Minecraft mc = Minecraft.getInstance();
        return repeat && carriedIntoMenu && mc.gui.screen() instanceof ChatScreen
                && mc.options.keyPlayerList.matches(event);
    }

    /** Whether the player list is up: held down normally, or flipped open with the toggle. */
    public static boolean listShown(boolean keyDown) {
        if (!isEnabled()) return keyDown;
        Minecraft mc = Minecraft.getInstance();
        Screen screen = mc.gui.screen();
        if (screen == null) {
            menu = null;
            carriedIntoMenu = false;
            return lastShown = toggles() ? toggledOpen : keyDown;
        }
        if (screen != menu) {
            menu = screen;
            carriedIntoMenu = lastShown;
        }
        if (!showsOver(screen)) return lastShown = false;
        // Held: a menu keeps key mappings from updating, so the key itself is asked.
        boolean up = toggles() ? toggledOpen : playerListKeyHeld(mc);
        if (!up) carriedIntoMenu = false;
        // In the chat Tab completes what is typed and cycles the chat tabs, so there the list only
        // stays up when it already was as the chat opened - not when Tab is pressed in it.
        if (screen instanceof ChatScreen) up = up && carriedIntoMenu;
        return lastShown = up;
    }

    /** The menus the list may stay up in: the chat, and inventories and chests. */
    private static boolean showsOver(Screen screen) {
        return AlpakaConfig.instance.customTabInMenus
                && (screen instanceof ChatScreen || screen instanceof AbstractContainerScreen<?>);
    }

    /** Whether the player list key is physically down, whatever screen is open. */
    private static boolean playerListKeyHeld(Minecraft mc) {
        InputConstants.Key key = InputConstants.getKey(mc.options.keyPlayerList.saveString());
        return switch (key.getType()) {
            case KEYSYM -> key.getValue() != GLFW.GLFW_KEY_UNKNOWN && InputConstants.isKeyDown(mc.getWindow(), key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(mc.getWindow().handle(), key.getValue()) == GLFW.GLFW_PRESS;
            case SCANCODE -> false;
        };
    }

    /**
     * Draws the list in place of vanilla's. Returns false where vanilla's list is to be drawn
     * instead: with the feature off, and off SkyBlock.
     */
    public static boolean render(GuiGraphicsExtractor graphics, Hud hud) {
        if (!takesOver()) return false;
        // With a menu open the menu's own hook draws it instead, above the menu's dimming.
        if (Minecraft.getInstance().gui.screen() == null) draw(graphics, hud.getTabList());
        return true;
    }

    /**
     * Draws the list over an open menu. Called from the Screen hook after the menu's content and
     * before its tooltips, as the peeked chat is, so a hovered item's tooltip still covers it.
     */
    public static void renderOverMenu(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() == null || !takesOver()) return;
        // No Chat Tabs check here: listShown only keeps the list in the chat when it came in with
        // it, so Tab cycling the chat tabs never brings it up.
        draw(graphics, mc.gui.hud.getTabList());
    }

    /** Whether the mod draws the list rather than vanilla: switched on, and on SkyBlock. */
    private static boolean takesOver() {
        return isEnabled() && (devSample != null || SkyblockUtils.isOnSkyblock());
    }

    private static void draw(GuiGraphicsExtractor graphics, PlayerTabOverlay tabList) {
        Minecraft mc = Minecraft.getInstance();
        boolean shown = mc.player != null && (devSample != null || listShown(mc.options.keyPlayerList.isDown()));
        tabList.setVisible(shown);
        if (!advanceSlide(shown, AlpakaConfig.instance.customTabAnimated, System.nanoTime())) {
            cached = null;
            return;
        }

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        CustomTabLayout layout = layout(tabList, screenWidth, screenHeight);
        if (layout == null || layout.texts.isEmpty()) return;
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

        if (AlpakaConfig.instance.customTabSectionBoxes) {
            for (CustomTabLayout.Box box : layout.boxes) drawBox(graphics, box);
        }

        Font font = mc.font;
        ClientPacketListener connection = mc.getConnection();
        boolean highlight = AlpakaConfig.instance.customTabHighlightChanges;
        long nowMs = System.currentTimeMillis();
        for (CustomTabLayout.Text text : layout.texts) {
            int x = text.x();
            if (text.face() != null) {
                PlayerInfo info = connection != null ? connection.getPlayerInfo(text.face()) : null;
                if (info != null) PlayerFaceExtractor.extractRenderState(graphics, info.getSkin(), x, text.y(), CustomTabLayout.FACE);
                x += CustomTabLayout.FACE + CustomTabLayout.FACE_GAP;
            }
            TabListParser.Line line = text.line();
            if (highlight && line != null && line.key() != null) {
                float flash = FLASH.strength(line.key(), nowMs);
                if (flash > 0.0f) {
                    int width = CustomTabLayout.lineWidth(line, font::width);
                    graphics.fill(x - 2, text.y() - 1, x + width + 2, text.y() + CustomTabLayout.LINE_H,
                            Math.round(flash * FLASH_ALPHA) << 24 | 0xFFFFFF);
                }
            }
            graphics.text(font, text.text(), x, text.y(), 0xFFFFFFFF, HudPanel.TEXT_SHADOW);
            if (line != null && line.bar() != null) drawBar(graphics, font, line, x, text.y());
        }
        graphics.pose().popMatrix();
    }

    /**
     * Moves the slide toward open or closed by the time since the last frame. Without the
     * animation it jumps straight there. Returns whether anything of the list is to be drawn.
     */
    static boolean advanceSlide(boolean shown, boolean animated, long now) {
        // A long gap - the list closed, the game paused - starts the slide afresh, not halfway.
        float seconds = slideAtNs == 0L ? 0.0f : Math.min(0.1f, (now - slideAtNs) / 1.0e9f);
        slideAtNs = now;
        if (!animated) {
            slide = shown ? 1.0f : 0.0f;
        } else {
            float step = seconds / SLIDE_SECONDS;
            slide = Math.max(0.0f, Math.min(1.0f, slide + (shown ? step : -step)));
        }
        // Only once it is fully closed and staying closed: reset on the first frame of opening, the
        // clock would start over every frame and the list would never come out.
        if (slide <= 0.0f && !shown) slideAtNs = 0L;
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
                + "/" + stacked + "/" + hideAdverts + "/" + AlpakaConfig.instance.customTabSectionBoxes;
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
        if (AlpakaConfig.instance.customTabProgressBars) categories = TabProgressBars.apply(categories);
        categories = keyed(categories);
        if (AlpakaConfig.instance.customTabHighlightChanges) observe(categories, now);
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
                        lines.add(line.withText(GuiFont.restyle(line.text()), GuiFont.restyle(line.after())));
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
        return CustomTabLayout.build(header, categories, footer, stacked, maxWidth, maxHeight, true,
                AlpakaConfig.instance.customTabSectionBoxes, font::width);
    }

    /** Shows the sample, or the live list again with null; for the screenshot tour only. */
    public static void devShow(DevSample sample) {
        devSample = sample;
        cached = null;
    }

    /** A widget's box, its corner pixels left out so it reads as rounded at the tab's own scale. */
    private static void drawBox(GuiGraphicsExtractor graphics, CustomTabLayout.Box box) {
        int x0 = box.x0(), y0 = box.y0(), x1 = box.x1(), y1 = box.y1();
        graphics.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, BOX_FILL);
        graphics.fill(x0 + 1, y0, x1 - 1, y0 + 1, BOX_EDGE);
        graphics.fill(x0 + 1, y1 - 1, x1 - 1, y1, BOX_EDGE);
        graphics.fill(x0, y0 + 1, x0 + 1, y1 - 1, BOX_EDGE);
        graphics.fill(x1 - 1, y0 + 1, x1, y1 - 1, BOX_EDGE);
    }

    /** A bar after a row's text, and the text that follows it. */
    private static void drawBar(GuiGraphicsExtractor graphics, Font font, TabListParser.Line line, int x, int y) {
        TabListParser.Bar bar = line.bar();
        int x0 = x + CustomTabLayout.barX(font.width(line.text()));
        int y0 = y + CustomTabLayout.BAR_TOP;
        int x1 = x0 + CustomTabLayout.BAR_W;
        // Drawn in half pixels, which a GUI scale of 2 or more still shows whole, so the ends round
        // off more finely than the bar's 4 pixels of height would allow.
        graphics.pose().pushMatrix();
        graphics.pose().translate(x0, y0);
        graphics.pose().scale(0.5f, 0.5f);
        int width = CustomTabLayout.BAR_W * 2;
        roundedBar(graphics, width, width, BAR_TRACK);
        int filled = Math.round(width * Math.max(0.0f, Math.min(1.0f, bar.fraction())));
        if (filled > 0) roundedBar(graphics, width, filled, bar.color());
        graphics.pose().popMatrix();
        if (font.width(line.after()) > 0) {
            graphics.text(font, line.after(), x1 + CustomTabLayout.BAR_GAP, y, 0xFFFFFFFF, HudPanel.TEXT_SHADOW);
        }
    }

    /** How far each half-pixel row of a bar starts in from its ends: a round cap of the bar's height. */
    private static final int[] BAR_CAP = {2, 1, 0, 0, 0, 0, 1, 2};

    /** A bar {@code width} half pixels long with round ends, filled up to {@code upTo} of it. */
    private static void roundedBar(GuiGraphicsExtractor graphics, int width, int upTo, int color) {
        for (int row = 0; row < BAR_CAP.length; row++) {
            int start = BAR_CAP[row];
            int end = Math.min(upTo, width - BAR_CAP[row]);
            if (end > start) graphics.fill(start, row, end, row + 1, color);
        }
    }

    /** Every widget row with the key it is known by from one read to the next. */
    private static List<TabListParser.Category> keyed(List<TabListParser.Category> categories) {
        List<TabListParser.Category> out = new ArrayList<>(categories.size());
        for (TabListParser.Category category : categories) {
            String categoryKey = TITLE_COUNT.matcher(TabListParser.plain(category.title()).trim()).replaceAll("");
            List<List<TabListParser.Line>> sections = new ArrayList<>(category.sections().size());
            for (List<TabListParser.Line> section : category.sections()) {
                String widget = TabChangeFlash.label(TabListParser.plain(section.get(0).text()));
                List<TabListParser.Line> lines = new ArrayList<>(section.size());
                for (int i = 0; i < section.size(); i++) {
                    TabListParser.Line line = section.get(i);
                    // Players come and go; that is not a value changing.
                    lines.add(line.player() != null ? line
                            : line.withKey(TabChangeFlash.key(categoryKey, widget, TabListParser.plain(line.text()), i)));
                }
                sections.add(lines);
            }
            out.add(new TabListParser.Category(category.title(), sections));
        }
        return out;
    }

    /** Hands every keyed row's current value to the light, a new server starting it afresh. */
    private static void observe(List<TabListParser.Category> categories, long nowMs) {
        Object level = Minecraft.getInstance().level;
        if (level != flashLevel) {
            FLASH.reset();
            flashLevel = level;
        }
        for (TabListParser.Category category : categories) {
            for (List<TabListParser.Line> section : category.sections()) {
                for (TabListParser.Line line : section) {
                    if (line.key() == null) continue;
                    String value = TabListParser.plain(line.text()) + " " + TabListParser.plain(line.after())
                            + (line.bar() != null ? " " + Math.round(line.bar().fraction() * 1000) : "");
                    FLASH.observe(line.key(), value, nowMs);
                }
            }
        }
    }

    private static List<Component> restyled(List<Component> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (Component line : lines) out.add(GuiFont.restyle(line));
        return out;
    }
}
