package net.alpaka.addons.client;

import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.ConfigOption;
import net.alpaka.addons.client.gui.ConfigTree;
import net.alpaka.addons.client.gui.EditableText;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.client.gui.PloppAnimation;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.ModVersion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The config menu, redesigned for 2.0: three columns instead of one long list of cards.
 *
 * <ul>
 *   <li>Left, the sections - the old headers - grouped under their categories.</li>
 *   <li>In the middle, the features of the chosen section, one slim row each with its switch.</li>
 *   <li>On the right, everything that belongs to the chosen feature: its description, folded
 *       away on request, and its settings as plain rows without boxes.</li>
 * </ul>
 *
 * What belongs to which feature comes from {@link ConfigTree}. Searching lists every feature with a
 * match, from any section.
 *
 * Clickable areas are collected while drawing and tested against on the next click, so what is
 * drawn and what reacts can never disagree about where a row is.
 */
public class AlpakaConfigScreen extends Screen implements net.alpaka.addons.client.gui.NoticeHost {

    // ---- Colors (the rest are AlpakaStyle's) --------------------------------------------------

    /** A preview's text color where its own codes set none: the chat's white. */
    private static final int CHAT_TEXT = 0xFFFFFFFF;

    // ---- Layout, in GUI pixels ------------------------------------------------------------------

    private static final int HEADER_H = AlpakaStyle.HEADER_H;
    private static final int PAD = AlpakaStyle.PAD;
    private static final int LINE_H = 9;
    private static final int GROUP_LABEL_H = 13;
    private static final int SECTION_ROW_H = 13;
    private static final int FEATURE_ROW_H = 20;
    private static final int FEATURE_ROW_GAP = 3;
    private static final int SETTING_ROW_H = 16;
    private static final int SLIDER_ROW_H = 24;
    private static final int ACTION_ROW_H = 18;
    private static final int TOGGLE_W = 16;
    private static final int TOGGLE_H = 9;
    private static final int SWATCH_W = 16;
    private static final int SWATCH_H = 9;
    private static final int TEXT_FIELD_W = 76;
    private static final int CHOICE_FIELD_W = 84;
    private static final int PREVIEW_LABEL_H = 12;
    private static final int PREVIEW_PAD = 5;
    private static final int MAX_TEXT_OPTION_LENGTH = 32;
    private static final int MAX_SEARCH_LENGTH = 35;
    private static final long TOOLTIP_DELAY_MS = AlpakaStyle.TOOLTIP_DELAY_MS;

    // ---- State ----------------------------------------------------------------------------------

    private final Screen parent;
    /** The version under the logo; it cannot change while the game runs. */
    private final String versionLabel = "v" + ModVersion.mod().split("\\+")[0];
    /**
     * The build part after it, {@code +133.bc91868}, so a test build says which commit it is. Empty
     * on a release build.
     */
    private final String buildLabel = ModVersion.mod().contains("+")
            ? ModVersion.mod().substring(ModVersion.mod().indexOf('+')) : "";
    private final EditableText search = new EditableText(MAX_SEARCH_LENGTH);
    /** How far the search text is scrolled left to keep the caret in view, this frame. */
    private int searchScrollPx = 0;
    private boolean searchFocused = false;
    private long caretBlinkFrom = 0L;

    private ConfigTree.Section selectedSection;
    private ConfigTree.Feature selectedFeature;

    /** Whether the details panel shows the feature's description; one choice for every feature. */
    private boolean descriptionOpen = true;

    /** Dropdowns folded shut in the details panel. Open by default, so nothing is hidden until asked. */
    private final Set<ConfigOption> collapsed = new HashSet<>();

    private ConfigOption focusedText = null;
    /** The choice setting whose list is open, if any; one at a time. */
    private ConfigOption openChoice = null;

    private final Scroll sidebarScroll = new Scroll();
    private final Scroll featureScroll = new Scroll();
    private final Scroll detailScroll = new Scroll();

    /** A slider being dragged, and the track it is dragged along. */
    private ConfigOption draggedSlider = null;
    private int dragTrackX;
    private int dragTrackW;

    private long openTimeMs = 0L;
    private long lastFrameMs = System.currentTimeMillis();

    /**
     * Setup mode, for the Viewmodel section: the menu slides to the edge away from the main hand and folds down to the
     * selected feature's settings, so the item in hand stays in view while it is adjusted. Toggled
     * by the Setup button in the header, which only the Viewmodel section shows.
     */
    private boolean setupMode = false;
    /** 0 for the full menu, 1 for setup mode, eased in between. */
    private float setupProgress = 0.0f;

    /**
     * What is under the mouse with a tooltip to show, and since when: a setting, with its
     * description, or a feature whose name had to be cut, with the whole name.
     */
    private Object hoveredTip = null;
    private String hoveredTipText = null;
    private long hoveredSince = 0L;

    /** Clickable areas of the last frame, front-most last. */
    private final List<Hit> hits = new ArrayList<>();

    private interface ClickAction {
        void run(double mouseX, double mouseY);
    }

    /**
     * A place that answers a click. Pressing a control clicks; grabbing a slider or putting the
     * caret in a field does not, as vanilla's own sliders and fields don't.
     */
    private record Hit(int x, int y, int w, int h, ClickAction action, boolean sound) {
        Hit(int x, int y, int w, int h, ClickAction action) {
            this(x, y, w, h, action, true);
        }

        static Hit quiet(int x, int y, int w, int h, ClickAction action) {
            return new Hit(x, y, w, h, action, false);
        }

        boolean contains(double mx, double my) {
            return AlpakaStyle.inside(mx, my, x, y, w, h);
        }
    }

    /** A column's scroll position, eased toward its target. */
    private static final class Scroll {
        double current;
        double target;
        double max;

        void update(float deltaSec) {
            target = Math.max(0, Math.min(max, target));
            current += (target - current) * Math.min(1.0f, deltaSec * 14.0f);
            if (Math.abs(target - current) < 0.05) current = target;
        }

        void by(double amount) {
            target = Math.max(0, Math.min(max, target + amount));
        }

        void reset() {
            current = 0;
            target = 0;
        }

        int offset() {
            return (int) Math.round(current);
        }
    }

    public AlpakaConfigScreen(Screen parent) {
        this(parent, "");
    }

    /** Opens with the search box already filled, for {@code /aa <term>}. */
    public AlpakaConfigScreen(Screen parent, String initialSearch) {
        super(GuiFont.text("Alpaka Addons Config"));
        this.parent = parent;
        this.search.setText(initialSearch == null ? "" : initialSearch.trim());
        List<ConfigTree.Group> groups = ConfigTree.groups();
        if (!groups.isEmpty() && !groups.get(0).sections.isEmpty()) {
            selectSection(groups.get(0).sections.get(0));
        }
        if (searching()) selectFirstResult();
    }

    /** For the screenshot tour: the screen opened on a section, and on a feature in it if given. */
    static AlpakaConfigScreen openOn(String sectionTitle, String featureTitle) {
        AlpakaConfigScreen screen = new AlpakaConfigScreen(null, "");
        for (ConfigTree.Group group : ConfigTree.groups()) {
            for (ConfigTree.Section section : group.sections) {
                if (!section.title.equals(sectionTitle)) continue;
                screen.selectSection(section);
                for (ConfigTree.Feature feature : section.features) {
                    if (feature.title.equals(featureTitle)) screen.selectFeature(feature);
                }
                screen.revealSelected = true;
            }
        }
        return screen;
    }

    /** For the screenshot tour: starts in setup mode, already folded. */
    AlpakaConfigScreen inSetupMode() {
        setupMode = true;
        setupProgress = 1.0f;
        return this;
    }

    /** For the screenshot tour: opens the list of the selected feature's choice setting. */
    AlpakaConfigScreen withChoiceOpen() {
        if (selectedFeature != null) {
            for (ConfigOption option : selectedFeature.panelOptions()) {
                if (option.getChoices() != null) openChoice = option;
            }
        }
        return this;
    }

    /** In setup mode a singleplayer world keeps running, so a test swing actually plays out. */
    @Override
    public boolean isPauseScreen() {
        return !setupMode;
    }

    @Override
    protected void init() {
        // Once only: back from a dialog or a picker the menu is simply there again, not opening anew.
        if (openTimeMs == 0L) openTimeMs = System.currentTimeMillis();
        lastFrameMs = System.currentTimeMillis();
        ellipsized.clear();
    }

    // ---- Selection ------------------------------------------------------------------------------

    private boolean searching() {
        return !search.getText().isBlank();
    }

    private String query() {
        return ConfigOption.normalizeQuery(search.getText());
    }

    private void selectSection(ConfigTree.Section section) {
        selectedSection = section;
        if (!isViewmodelSection()) setupMode = false;
        selectedFeature = section.features.isEmpty() ? null : section.features.get(0);
        featureScroll.reset();
        detailScroll.reset();
    }

    private void selectFeature(ConfigTree.Feature feature) {
        if (feature == selectedFeature) return;
        selectedFeature = feature;
        detailScroll.reset();
    }

    /** Set when a feature was chosen from outside the list, which then scrolls it into view. */
    private boolean revealSelected = false;

    /**
     * The search results for the text in {@link #listedFor}. The tree and every title and keyword in
     * it are fixed once built, so the same text always finds the same features; callers only read the
     * list.
     */
    private String listedFor = null;
    private List<ConfigTree.Feature> listedCache = null;

    /** The features the middle column lists: the section's, or every match while searching. */
    private List<ConfigTree.Feature> listedFeatures() {
        if (!searching()) return selectedSection == null ? List.of() : selectedSection.features;
        String raw = search.getText();
        if (!raw.equals(listedFor)) {
            String q = query();
            List<ConfigTree.Feature> found = new ArrayList<>();
            for (ConfigTree.Group group : ConfigTree.groups()) {
                for (ConfigTree.Section section : group.sections) {
                    for (ConfigTree.Feature feature : section.features) {
                        if (feature.matches(q)) found.add(feature);
                    }
                }
            }
            listedCache = found;
            listedFor = raw;
        }
        return listedCache;
    }

    private void selectFirstResult() {
        List<ConfigTree.Feature> found = listedFeatures();
        selectedFeature = found.isEmpty() ? null : found.get(0);
        featureScroll.reset();
        detailScroll.reset();
    }

    private void onSearchChanged() {
        if (searching()) {
            List<ConfigTree.Feature> found = listedFeatures();
            if (!found.contains(selectedFeature)) selectFirstResult();
        } else if (selectedFeature != null) {
            selectedSection = selectedFeature.section;
        }
        featureScroll.reset();
    }

    // ---- Geometry -------------------------------------------------------------------------------

    /** The full menu's width, before setup mode folds it. */
    private int fullPanelW() {
        return Math.max(240, Math.min(Math.min(640, this.width - 16), this.width - 8));
    }

    /** The sidebar: as wide as its longest section name needs, the rest going to the features. */
    private int fullSidebarW() {
        return Math.max(84, Math.min(104, (int) (fullPanelW() * 0.19)));
    }

    /** The features' column: wide enough that most names fit beside their switch uncut. */
    private int fullFeatureColumnW() {
        return (int) ((fullPanelW() - fullSidebarW()) * 0.46);
    }

    /** Setup mode's width: the settings column alone, a little wider than in the full menu. */
    private int setupPanelW() {
        int detail = fullPanelW() - fullSidebarW() - fullFeatureColumnW();
        return Math.min(this.width - 16, Math.max(detail, 190));
    }

    private int panelW() {
        return Math.round(lerp(fullPanelW(), setupPanelW(), setupProgress));
    }

    private int panelH() {
        return Math.max(160, Math.min(Math.min(380, this.height - 16), this.height - 8));
    }

    private int panelX() {
        float centred = (this.width - fullPanelW()) / 2.0f;
        // Away from the hand holding the item: the left edge for the right hand, and the other way.
        boolean rightHanded = this.minecraft == null
                || this.minecraft.options.mainHand().get() == net.minecraft.world.entity.HumanoidArm.RIGHT;
        float side = rightHanded ? 8 : this.width - setupPanelW() - 8;
        return Math.round(lerp(centred, side, setupProgress));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private boolean isViewmodelSection() {
        return selectedSection != null && "Viewmodel".equals(selectedSection.title) && !searching();
    }

    /** The Setup button, at the right end of the header. */
    private static final int SETUP_BTN_W = 44;

    private int panelY() {
        return (this.height - panelH()) / 2;
    }

    private int sidebarW() {
        return Math.round(fullSidebarW() * (1.0f - setupProgress));
    }

    private int featureColumnW() {
        return Math.round(fullFeatureColumnW() * (1.0f - setupProgress));
    }

    // ---- Drawing --------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        float deltaSec = Math.min(0.1f, (now - lastFrameMs) / 1000.0f);
        lastFrameMs = now;
        hits.clear();

        if (setupMode && !isViewmodelSection()) setupMode = false;
        float setupTarget = setupMode ? 1.0f : 0.0f;
        setupProgress = PloppAnimation.interpolate(setupProgress, setupTarget, deltaSec, 11.0f);
        if (Math.abs(setupProgress - setupTarget) < 0.003f) setupProgress = setupTarget;

        // In setup mode the world stays undimmed: it is what is being adjusted.
        AlpakaStyle.backdrop(graphics, this.width, this.height, 1.0f - setupProgress);

        boolean scaling = AlpakaStyle.beginOpen(graphics, openTimeMs, this.width, this.height);

        int x = panelX();
        int y = panelY();
        int w = panelW();
        int h = panelH();
        AlpakaStyle.panel(graphics, x, y, w, h);

        int sideW = sidebarW();
        int featW = featureColumnW();
        int featX = x + sideW;
        int detailX = featX + featW;
        int detailW = x + w - detailX;
        int bodyY = y + HEADER_H;
        int bodyH = h - HEADER_H;

        hoveredTipThisFrame = null;
        hoveredTipTextThisFrame = null;

        drawHeader(graphics, mouseX, mouseY, x, y, w, sideW, featX, featW, detailX, detailW);
        AlpakaStyle.headerLine(graphics, x, y, w);
        // Folding away in setup mode: clipped to their shrinking widths, and gone once folded.
        if (setupProgress < 0.999f) {
            graphics.enableScissor(x, bodyY, detailX, y + h);
            drawSidebar(graphics, mouseX, mouseY, x, bodyY, sideW, bodyH, deltaSec);
            drawFeatures(graphics, mouseX, mouseY, featX, bodyY, featW, bodyH, deltaSec);
            graphics.disableScissor();
            ModernGuiUtils.drawRect(graphics, detailX, bodyY + 4, 1, bodyH - 8, AlpakaStyle.DIVIDER);
        }
        drawDetails(graphics, mouseX, mouseY, detailX + 1, bodyY, detailW - 1, bodyH, deltaSec);

        if (hoveredTipThisFrame != hoveredTip) {
            hoveredTip = hoveredTipThisFrame;
            hoveredTipText = hoveredTipTextThisFrame;
            hoveredSince = now;
        }
        if (hoveredTip != null && now - hoveredSince >= TOOLTIP_DELAY_MS && draggedSlider == null) {
            AlpakaStyle.tooltip(graphics, this.font, this.width, this.height, mouseX, mouseY, hoveredTipText);
        }

        AlpakaStyle.endOpen(graphics, scaling);

        // Notices over the menu, where the HUD would hide them behind it.
        net.alpaka.addons.features.notification.AlpakaNotifications.renderOverScreen(graphics);
    }

    private Object hoveredTipThisFrame = null;
    private String hoveredTipTextThisFrame = null;

    private void drawHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w,
                            int sideW, int featX, int featW, int detailX, int detailW) {
        // The logo and the search fold away with their columns in setup mode.
        if (setupProgress < 0.999f) {
            graphics.enableScissor(x, y, detailX, y + HEADER_H);
            drawLogoAndSearch(graphics, mouseX, mouseY, x, y, featX, featW);
            graphics.disableScissor();
        }

        // The chosen feature's name, over its settings; the Setup button beside it on Viewmodel.
        boolean setupButton = isViewmodelSection();
        boolean stepper = setupButton && setupMode && setupProgress > 0.5f && selectedSection.features.size() > 1;
        if (stepper) {
            // In setup mode the feature list is folded away, so the header steps through the
            // section's features instead: arrows either side of the name.
            int arrow = 14;
            int ay = y + (HEADER_H - arrow) / 2;
            int leftX = detailX + PAD - 2;
            int rightX = detailX + detailW - PAD - SETUP_BTN_W - 4 - arrow;
            boolean hoverLeft = inside(mouseX, mouseY, leftX, ay, arrow, arrow);
            boolean hoverRight = inside(mouseX, mouseY, rightX, ay, arrow, arrow);
            AlpakaStyle.chevronButton(graphics, leftX, ay, arrow, true, hoverLeft, true);
            AlpakaStyle.chevronButton(graphics, rightX, ay, arrow, false, hoverRight, true);
            hits.add(new Hit(leftX, ay, arrow, arrow, (mx, my) -> stepFeature(-1)));
            hits.add(new Hit(rightX, ay, arrow, arrow, (mx, my) -> stepFeature(1)));
            if (selectedFeature != null) {
                int from = leftX + arrow + 2;
                int room = rightX - 2 - from;
                String name = ellipsize(selectedFeature.title, room);
                int nameW = GuiFont.width(this.font, name);
                AlpakaStyle.title(graphics, this.font, name, from + (room - nameW) / 2, y);
            }
        } else if (selectedFeature != null) {
            int room = detailW - PAD * 2 - 4 - (setupButton ? SETUP_BTN_W + 6 : 0);
            String name = ellipsize(selectedFeature.title, room);
            AlpakaStyle.title(graphics, this.font, name, detailX + PAD + 2, y);
        }
        if (setupButton) {
            int bx = detailX + detailW - PAD - SETUP_BTN_W;
            int by = AlpakaStyle.headerButtonY(y);
            boolean hover = inside(mouseX, mouseY, bx, by, SETUP_BTN_W, AlpakaStyle.BUTTON_H);
            AlpakaStyle.button(graphics, this.font, bx, by, SETUP_BTN_W,
                    AlpakaStyle.BUTTON_H, setupMode ? "Back" : "Setup", hover,
                    setupMode ? AlpakaStyle.ButtonKind.PRIMARY
                            : AlpakaStyle.ButtonKind.PLAIN);
            hits.add(new Hit(bx, by, SETUP_BTN_W, AlpakaStyle.BUTTON_H,
                    (mx, my) -> setupMode = !setupMode));
        }
    }

    /** The previous or next feature of the selected section, wrapping round; for setup mode. */
    private void stepFeature(int direction) {
        List<ConfigTree.Feature> features = selectedSection.features;
        if (features.isEmpty()) return;
        int index = Math.max(0, features.indexOf(selectedFeature));
        selectFeature(features.get(Math.floorMod(index + direction, features.size())));
        revealSelected = true;
    }

    /**
     * The version under the logo. The build part follows it, shrunk as far as needed to end before
     * {@code right}: in the smallest layout a full test build's version would run under the search.
     */
    private void drawVersion(GuiGraphicsExtractor graphics, int x, int y, int right) {
        Component version = GuiFont.text(versionLabel);
        graphics.text(this.font, version, x, y, AlpakaStyle.TEXT_MUTED, false);
        if (buildLabel.isEmpty()) return;
        Component build = GuiFont.text(buildLabel);
        int bx = x + this.font.width(version);
        int width = this.font.width(build);
        float scale = Math.min(1f, Math.max(0.5f, (right - bx) / (float) width));
        graphics.pose().pushMatrix();
        // Bottom-aligned with the version, so the smaller text sits on the same line.
        graphics.pose().translate(bx, y + 8 * (1 - scale));
        graphics.pose().scale(scale, scale);
        graphics.text(this.font, build, 0, 0, AlpakaStyle.TEXT_MUTED, false);
        graphics.pose().popMatrix();
    }

    private void drawLogoAndSearch(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int featX, int featW) {
        // Logo: the name large, the version small beneath it.
        Component title = GuiFont.title("ALPAKA");
        if (title == null) title = GuiFont.text("ALPAKA");
        // In line with the sidebar's labels below it.
        graphics.text(this.font, title, x + PAD, y + 8, AlpakaStyle.TEXT, false);
        drawVersion(graphics, x + PAD, y + 22, featX + 4 - 6);

        // Search: a pill over the feature column.
        int sx = featX + 4;
        int sw = featW - 8;
        int sh = 18;
        int sy = y + (HEADER_H - sh) / 2;
        boolean hover = inside(mouseX, mouseY, sx, sy, sw, sh);
        ModernGuiUtils.drawRoundedRect(graphics, sx, sy, sw, sh, sh / 2, AlpakaStyle.FIELD);
        if (searchFocused) ModernGuiUtils.drawRoundedOutline(graphics, sx, sy, sw, sh, sh / 2, AlpakaStyle.accentDim());
        else if (hover) ModernGuiUtils.drawRoundedOutline(graphics, sx, sy, sw, sh, sh / 2, AlpakaStyle.LINE);

        int tx = sx + 9;
        int ty = sy + (sh - 8) / 2;
        int iconX = sx + sw - 16;
        // The text keeps clear of the icon: cut with an ellipsis at rest, scrolled to the caret
        // while typing.
        int room = iconX - 4 - tx;
        searchScrollPx = 0;
        if (search.isEmpty() && !searchFocused) {
            graphics.text(this.font, GuiFont.text("Search…"), tx, ty, AlpakaStyle.TEXT_MUTED, false);
        } else if (!searchFocused) {
            graphics.text(this.font, GuiFont.text(ellipsize(search.getText(), room)), tx, ty, AlpakaStyle.TEXT, false);
        } else {
            String text = search.getText();
            int caretPx = GuiFont.width(this.font, text.substring(0, search.getCaret()));
            searchScrollPx = Math.max(0, caretPx - room + 1);
            int ox = tx - searchScrollPx;
            graphics.enableScissor(tx, sy, tx + room + 1, sy + sh);
            if (search.hasSelection()) {
                int from = ox + GuiFont.width(this.font, text.substring(0, search.getSelectionStart()));
                int to = ox + GuiFont.width(this.font, text.substring(0, search.getSelectionEnd()));
                ModernGuiUtils.drawRect(graphics, from, ty - 1, to - from, 10, AlpakaStyle.accentDim());
            }
            graphics.text(this.font, GuiFont.text(text), ox, ty, AlpakaStyle.TEXT, false);
            if ((System.currentTimeMillis() - caretBlinkFrom) / 500 % 2 == 0) {
                ModernGuiUtils.drawRect(graphics, ox + caretPx, ty - 1, 1, 10, AlpakaStyle.TEXT);
            }
            graphics.disableScissor();
        }
        // The field first, the clear button after: the later hit is the one on top.
        int scrolled = searchScrollPx;
        hits.add(Hit.quiet(sx, sy, sw, sh, (mx, my) -> {
            searchFocused = true;
            caretBlinkFrom = System.currentTimeMillis();
            search.setCaretFromX((int) mx - tx + scrolled, s -> GuiFont.width(this.font, s));
        }));
        if (!search.isEmpty()) {
            boolean hoverClear = inside(mouseX, mouseY, iconX - 3, sy + 2, 14, 14);
            AlpakaStyle.iconButton(graphics, this.font, iconX - 3, sy + 2, 14, "✕", hoverClear, true, false);
            hits.add(new Hit(iconX - 3, sy + 2, 14, 14, (mx, my) -> {
                search.setText("");
                onSearchChanged();
            }));
        } else {
            drawSearchIcon(graphics, iconX + 2, sy + 4, searchFocused ? ModernGuiUtils.getAccentColor() : AlpakaStyle.TEXT_MUTED);
        }
    }

    /** A magnifying glass: a ring of four short strokes and a handle, from plain fills. */
    private static void drawSearchIcon(GuiGraphicsExtractor graphics, int x, int y, int color) {
        ModernGuiUtils.drawRoundedOutline(graphics, x, y, 7, 7, 3, color);
        ModernGuiUtils.drawRect(graphics, x + 6, y + 6, 1, 1, color);
        ModernGuiUtils.drawRect(graphics, x + 7, y + 7, 1, 1, color);
        ModernGuiUtils.drawRect(graphics, x + 8, y + 8, 1, 1, color);
    }

    private void drawSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h, float deltaSec) {
        int clipY = y + 4;
        int clipH = h - 8;
        int contentH = 4;
        List<ConfigTree.Group> groups = ConfigTree.groups();
        for (int i = 0; i < groups.size(); i++) {
            contentH += GROUP_LABEL_H + groups.get(i).sections.size() * SECTION_ROW_H + (i < groups.size() - 1 ? 9 : 0);
        }
        sidebarScroll.max = Math.max(0, contentH - clipH);
        sidebarScroll.update(deltaSec);

        graphics.enableScissor(x, clipY, x + w, clipY + clipH);
        int rowY = clipY + 2 - sidebarScroll.offset();
        for (int i = 0; i < groups.size(); i++) {
            ConfigTree.Group group = groups.get(i);
            AlpakaStyle.groupLabel(graphics, this.font, group.title, x + PAD, rowY + 2);
            rowY += GROUP_LABEL_H;
            for (ConfigTree.Section section : group.sections) {
                boolean selected = !searching() && section == selectedSection;
                boolean hover = inside(mouseX, mouseY, x, rowY, w, SECTION_ROW_H) && inside(mouseX, mouseY, x, clipY, w, clipH);
                int color = selected ? ModernGuiUtils.getAccentColor() : hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT;
                String label = ellipsize(section.title, w - PAD - 14);
                ModernGuiUtils.drawRoundedRect(graphics, x + PAD + 1, rowY + 5, 2, 2, 1, color);
                graphics.text(this.font, GuiFont.text(label), x + PAD + 7, rowY + 2, color, false);
                if (visible(rowY, SECTION_ROW_H, clipY, clipH)) {
                    hits.add(new Hit(x, Math.max(rowY, clipY), w, SECTION_ROW_H, (mx, my) -> {
                        if (searching()) {
                            search.setText("");
                            searchFocused = false;
                        }
                        selectSection(section);
                    }));
                }
                rowY += SECTION_ROW_H;
            }
            if (i < groups.size() - 1) {
                ModernGuiUtils.drawRect(graphics, x + PAD, rowY + 4, w - PAD * 2, 1, AlpakaStyle.LINE);
                rowY += 9;
            }
        }
        AlpakaStyle.scrollFades(graphics, x, clipY, w, clipH, sidebarScroll.offset() > 0, sidebarScroll.offset() + 1 < sidebarScroll.max);
        graphics.disableScissor();
    }

    private void drawFeatures(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h, float deltaSec) {
        List<ConfigTree.Feature> features = listedFeatures();
        int clipY = y + 4;
        int clipH = h - 8;
        int rowX = x + 4;
        int rowW = w - 8;
        featureScroll.max = Math.max(0, features.size() * (FEATURE_ROW_H + FEATURE_ROW_GAP) + 4 - clipH);
        if (revealSelected) {
            revealSelected = false;
            int index = features.indexOf(selectedFeature);
            if (index >= 0) {
                int top = index * (FEATURE_ROW_H + FEATURE_ROW_GAP);
                featureScroll.target = Math.max(0, top - clipH / 2 + FEATURE_ROW_H);
                featureScroll.current = Math.max(0, Math.min(featureScroll.max, featureScroll.target));
            }
        }
        featureScroll.update(deltaSec);

        graphics.enableScissor(x, clipY, x + w, clipY + clipH);
        if (features.isEmpty()) {
            String empty = searching() ? "Nothing matches \"" + search.getText().trim() + "\"" : "Nothing here yet";
            // Wrapped rather than cut, so a long search still shows in full.
            int lineY = clipY + 6;
            for (FormattedText line : GuiFont.splitLines(this.font, empty, rowW - 12)) {
                graphics.text(this.font, Language.getInstance().getVisualOrder(line), rowX + 6, lineY, AlpakaStyle.TEXT_MUTED, false);
                lineY += LINE_H + 1;
            }
        }
        int rowY = clipY + 2 - featureScroll.offset();
        for (ConfigTree.Feature feature : features) {
            if (visible(rowY, FEATURE_ROW_H, clipY, clipH)) {
                boolean selected = feature == selectedFeature;
                boolean hover = inside(mouseX, mouseY, rowX, rowY, rowW, FEATURE_ROW_H) && inside(mouseX, mouseY, x, clipY, w, clipH);
                int background = selected ? AlpakaStyle.ROW_SELECTED : hover ? AlpakaStyle.ROW_HOVER : AlpakaStyle.ROW;
                ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, FEATURE_ROW_H, AlpakaStyle.CARD_RADIUS, background);

                // The dots keep their place whether drawn or not, so every switch lines up.
                int right = rowX + rowW - 6;
                if (!feature.panelOptions().isEmpty()) {
                    drawDots(graphics, right - 1, rowY + (FEATURE_ROW_H - 7) / 2, selected ? AlpakaStyle.TEXT_SOFT : AlpakaStyle.TEXT_MUTED);
                }
                right -= 7;
                int toggleX = right - TOGGLE_W;
                int toggleY = rowY + (FEATURE_ROW_H - TOGGLE_H) / 2;
                // The row first, its switch after: the later hit is the one on top.
                hits.add(new Hit(rowX, Math.max(rowY, clipY), rowW, FEATURE_ROW_H, (mx, my) -> selectFeature(feature)));
                if (feature.hasToggle()) {
                    feature.master.updateToggleProgress(deltaSec);
                    drawToggle(graphics, toggleX, toggleY, feature.master.getToggleProgress());
                    ConfigOption master = feature.master;
                    hits.add(new Hit(toggleX - 3, Math.max(rowY, clipY), TOGGLE_W + 6, FEATURE_ROW_H, (mx, my) -> {
                        master.toggleBool();
                        selectFeature(feature);
                    }));
                }

                int titleColor = selected ? ModernGuiUtils.getAccentColor() : AlpakaStyle.TEXT;
                int titleW = (feature.hasToggle() ? toggleX : right) - (rowX + 8) - 6;
                String shownTitle = ellipsize(feature.title, titleW);
                graphics.text(this.font, GuiFont.text(shownTitle), rowX + 8, rowY + (FEATURE_ROW_H - 8) / 2, titleColor, false);
                // A name cut short shows in full as a tooltip.
                if (hover && !shownTitle.equals(feature.title)) {
                    hoveredTipThisFrame = feature;
                    hoveredTipTextThisFrame = feature.title;
                }
            }
            rowY += FEATURE_ROW_H + FEATURE_ROW_GAP;
        }
        AlpakaStyle.scrollFades(graphics, x, clipY, w, clipH, featureScroll.offset() > 0, featureScroll.offset() + 1 < featureScroll.max);
        graphics.disableScissor();
    }

    /** The "more settings" mark: three dots stacked. */
    private static void drawDots(GuiGraphicsExtractor graphics, int x, int y, int color) {
        for (int i = 0; i < 3; i++) ModernGuiUtils.drawRect(graphics, x, y + i * 3, 1, 1, color);
    }

    private void drawDetails(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h, float deltaSec) {
        int clipY = y + 4;
        int clipH = h - 8;
        int rowX = x + PAD - 2;
        int rowW = w - PAD * 2 + 2;
        graphics.enableScissor(x, clipY, x + w, clipY + clipH);
        ConfigTree.Feature feature = selectedFeature;
        if (feature == null) {
            graphics.disableScissor();
            return;
        }

        int rowY = clipY + 2 - detailScroll.offset();
        int startY = rowY;

        // Description, folded away on request.
        String description = feature.description == null ? "" : feature.description;
        if (!description.isEmpty()) {
            boolean hover = inside(mouseX, mouseY, rowX, rowY, rowW, SETTING_ROW_H) && inside(mouseX, mouseY, x, clipY, w, clipH);
            if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, SETTING_ROW_H, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
            graphics.text(this.font, GuiFont.text("Description"), rowX + 4, rowY + 4, AlpakaStyle.TEXT, false);
            AlpakaStyle.chevron(graphics, rowX + rowW - 8, rowY + 8, descriptionOpen, AlpakaStyle.TEXT_SOFT);
            if (visible(rowY, SETTING_ROW_H, clipY, clipH)) {
                hits.add(new Hit(rowX, Math.max(rowY, clipY), rowW, SETTING_ROW_H, (mx, my) -> descriptionOpen = !descriptionOpen));
            }
            rowY += SETTING_ROW_H;
            if (descriptionOpen) {
                for (FormattedText line : GuiFont.splitLines(this.font, description, rowW - 8)) {
                    graphics.text(this.font, Language.getInstance().getVisualOrder(line), rowX + 4, rowY + 1, AlpakaStyle.TEXT_MUTED, false);
                    rowY += LINE_H + 1;
                }
                rowY += 5;
            }
        }

        List<ConfigOption> options = feature.panelOptions();
        if (options.isEmpty()) {
            graphics.text(this.font, GuiFont.text("No further settings."), rowX + 4, rowY + 4, AlpakaStyle.TEXT_MUTED, false);
            rowY += SETTING_ROW_H;
        }
        for (ConfigOption option : options) {
            rowY = drawSetting(graphics, mouseX, mouseY, option, rowX, rowY, rowW, x, w, clipY, clipH, deltaSec);
        }

        detailScroll.max = Math.max(0, (rowY + detailScroll.offset()) - startY + 6 - clipH);
        detailScroll.update(deltaSec);
        AlpakaStyle.scrollFades(graphics, x, clipY, w, clipH, detailScroll.offset() > 0, detailScroll.offset() + 1 < detailScroll.max);
        graphics.disableScissor();
    }

    /** Draws one setting row and returns the y the next one starts at. */
    private int drawSetting(GuiGraphicsExtractor graphics, int mouseX, int mouseY, ConfigOption option,
                            int rowX, int rowY, int rowW, int colX, int colW, int clipY, int clipH, float deltaSec) {
        List<net.minecraft.util.FormattedCharSequence> previewLines = option.getType() == ConfigOption.Type.PREVIEW
                ? this.font.split(option.getPreviewText(), rowW - 2 * PREVIEW_PAD) : List.of();
        int rowH = switch (option.getType()) {
            case SLIDER -> option.getChoices() != null ? SETTING_ROW_H : SLIDER_ROW_H;
            case PREVIEW -> PREVIEW_LABEL_H + Math.max(1, previewLines.size()) * LINE_H + 2 * PREVIEW_PAD + 2;
            case ACTION -> option.isColor() ? SETTING_ROW_H : ACTION_ROW_H;
            default -> SETTING_ROW_H;
        };
        boolean shown = visible(rowY, rowH, clipY, clipH);
        boolean hover = inside(mouseX, mouseY, rowX, rowY, rowW, rowH) && inside(mouseX, mouseY, colX, clipY, colW, clipH);
        if (hover) {
            hoveredTipThisFrame = option;
            hoveredTipTextThisFrame = option.getDescription();
        }
        // A row wholly outside the column's clip would draw nothing: its text would be laid out only
        // for the scissor to drop it. Skip it, keeping a switch's animation running so its knob is in
        // the right place when the row scrolls back in. An open list counts as part of its row, and
        // the y returned is exactly what drawing it would have returned.
        if (!shown) {
            int blockH = rowH;
            if (option.getType() == ConfigOption.Type.DROPDOWN && !collapsed.contains(option)) {
                blockH = rowH + option.getEntries().size() * SETTING_ROW_H + 2;
            } else if (option.getType() == ConfigOption.Type.SLIDER && option.getChoices() != null && openChoice == option) {
                blockH = rowH + option.getChoices().length * SETTING_ROW_H + 2;
            }
            if (!visible(rowY, blockH, clipY, clipH)) {
                if (option.getType() == ConfigOption.Type.BOOLEAN) option.updateToggleProgress(deltaSec);
                return rowY + blockH;
            }
        }
        int labelY = rowY + 4;
        int right = rowX + rowW - 4;
        int hitY = Math.max(rowY, clipY);

        switch (option.getType()) {
            case BOOLEAN -> {
                if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, rowH, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
                option.updateToggleProgress(deltaSec);
                drawLabel(graphics, option.getTitle(), rowX + 4, labelY, right - TOGGLE_W - 8 - (rowX + 4));
                drawToggle(graphics, right - TOGGLE_W, rowY + (rowH - TOGGLE_H) / 2, option.getToggleProgress());
                if (shown) hits.add(new Hit(rowX, hitY, rowW, rowH, (mx, my) -> option.toggleBool()));
            }
            case SLIDER -> {
                if (option.getChoices() != null) {
                    return drawChoice(graphics, mouseX, mouseY, option, rowX, rowY, rowW, rowH, colX, colW, clipY, clipH, hover);
                }
                String value = option.getFormattedValue();
                int valueW = GuiFont.width(this.font, value);
                drawLabel(graphics, option.getTitle(), rowX + 4, labelY, right - valueW - 8 - (rowX + 4));
                graphics.text(this.font, GuiFont.text(value), right - valueW, labelY, AlpakaStyle.TEXT, false);
                int trackX = rowX + 4;
                int trackW = rowW - 8;
                int trackY = rowY + 17;
                AlpakaStyle.sliderTrack(graphics, trackX, trackY, trackW, option.getSliderNormalizedValue(),
                        hover, draggedSlider == option);
                if (shown) {
                    hits.add(Hit.quiet(rowX, hitY, rowW, rowH, (mx, my) -> {
                        draggedSlider = option;
                        dragTrackX = trackX;
                        dragTrackW = trackW;
                        // One write for the whole drag, not one per mouse move.
                        AlpakaConfig.beginDeferredSaves();
                        option.setSliderNormalizedValue(Math.max(0.0, Math.min(1.0, (mx - trackX) / (double) trackW)));
                    }));
                }
            }
            case ACTION -> {
                if (option.isColor()) {
                    if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, rowH, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
                    drawLabel(graphics, option.getTitle(), rowX + 4, labelY, right - SWATCH_W - 8 - (rowX + 4));
                    int sy = rowY + (rowH - SWATCH_H) / 2;
                    AlpakaStyle.swatch(graphics, right - SWATCH_W, sy, SWATCH_W, SWATCH_H, option.getColor());
                    // A white ring while the mouse is on the swatch itself, the spot that opens the picker.
                    if (hover && inside(mouseX, mouseY, right - SWATCH_W - 2, sy - 2, SWATCH_W + 4, SWATCH_H + 4)) {
                        ModernGuiUtils.drawRoundedOutline(graphics, right - SWATCH_W - 1, sy - 1, SWATCH_W + 2, SWATCH_H + 2, 3, AlpakaStyle.TEXT);
                    }
                    if (shown) hits.add(new Hit(rowX, hitY, rowW, rowH, (mx, my) -> option.triggerAction(this)));
                } else {
                    // A button the width of the column, named for what it does; red for what destroys.
                    AlpakaStyle.button(graphics, this.font, rowX, rowY, rowW, rowH, ellipsize(option.getActionLabel(), rowW - 12),
                            hover, option.isDangerous() ? AlpakaStyle.ButtonKind.DANGER : AlpakaStyle.ButtonKind.PLAIN);
                    if (shown) hits.add(new Hit(rowX, hitY, rowW, rowH, (mx, my) -> option.triggerAction(this)));
                }
            }
            case DROPDOWN -> {
                boolean open = !collapsed.contains(option);
                if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, rowH, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
                String count = option.getEnabledEntryCount() + "/" + option.getEntryCount();
                int countW = GuiFont.width(this.font, count);
                drawLabel(graphics, option.getTitle(), rowX + 4, labelY, right - countW - 20 - (rowX + 4));
                graphics.text(this.font, GuiFont.text(count), right - 12 - countW, labelY, AlpakaStyle.TEXT_MUTED, false);
                AlpakaStyle.chevron(graphics, right - 4, rowY + 8, open, AlpakaStyle.TEXT_SOFT);
                if (shown) {
                    hits.add(new Hit(rowX, hitY, rowW, rowH, (mx, my) -> {
                        if (!collapsed.remove(option)) collapsed.add(option);
                    }));
                }
                if (open) {
                    int entryY = rowY + rowH;
                    for (ConfigOption.ToggleEntry entry : option.getEntries()) {
                        boolean entryHover = inside(mouseX, mouseY, rowX, entryY, rowW, SETTING_ROW_H) && inside(mouseX, mouseY, colX, clipY, colW, clipH);
                        if (entryHover) ModernGuiUtils.drawRoundedRect(graphics, rowX, entryY, rowW, SETTING_ROW_H, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
                        ModernGuiUtils.drawRect(graphics, rowX + 6, entryY, 1, SETTING_ROW_H, AlpakaStyle.LINE);
                        int entryLabelW = right - TOGGLE_W - 8 - (rowX + 14);
                        graphics.text(this.font, GuiFont.text(ellipsize(entry.getLabel(), entryLabelW)), rowX + 14, entryY + 4,
                                entry.get() ? AlpakaStyle.TEXT_SOFT : AlpakaStyle.TEXT_MUTED, false);
                        drawToggle(graphics, right - TOGGLE_W, entryY + (SETTING_ROW_H - TOGGLE_H) / 2, entry.get() ? 1.0f : 0.0f);
                        if (visible(entryY, SETTING_ROW_H, clipY, clipH)) {
                            hits.add(new Hit(rowX, Math.max(entryY, clipY), rowW, SETTING_ROW_H, (mx, my) -> entry.toggle()));
                        }
                        entryY += SETTING_ROW_H;
                    }
                    return entryY + 2;
                }
            }
            case PREVIEW -> {
                // Drawn as chat draws it: vanilla's text with its shadow, on chat's dark backdrop.
                graphics.text(this.font, GuiFont.text(option.getTitle()), rowX + 4, rowY + 2, AlpakaStyle.TEXT_MUTED, false);
                int boxY = rowY + PREVIEW_LABEL_H;
                int boxH = rowH - PREVIEW_LABEL_H - 2;
                ModernGuiUtils.drawRoundedRect(graphics, rowX, boxY, rowW, boxH, AlpakaStyle.ROW_RADIUS, AlpakaStyle.PREVIEW);
                int lineY = boxY + PREVIEW_PAD;
                for (net.minecraft.util.FormattedCharSequence line : previewLines) {
                    graphics.text(this.font, line, rowX + PREVIEW_PAD, lineY, CHAT_TEXT, true);
                    lineY += LINE_H;
                }
            }
            case TEXT -> {
                if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, rowH, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
                int fieldW = Math.min(TEXT_FIELD_W, rowW / 2);
                int fieldX = right - fieldW;
                drawLabel(graphics, option.getTitle(), rowX + 4, labelY, fieldX - 8 - (rowX + 4));
                boolean focused = focusedText == option;
                boolean fieldHover = inside(mouseX, mouseY, fieldX, rowY + 2, fieldW, rowH - 4);
                AlpakaStyle.textField(graphics, this.font, fieldX, rowY + 2, fieldW, rowH - 4, option.getText(),
                        option.getPlaceholder(), focused, fieldHover, caretBlinkFrom);
                if (shown) {
                    hits.add(Hit.quiet(rowX, hitY, rowW, rowH, (mx, my) -> {
                        focusedText = option;
                        searchFocused = false;
                        caretBlinkFrom = System.currentTimeMillis();
                    }));
                }
            }
            default -> {
            }
        }
        return rowY + rowH;
    }

    /**
     * A slider over a list of names, drawn as a select field: the label, and the current name in a
     * field with a chevron. A click opens the names underneath, a click on one picks it.
     */
    private int drawChoice(GuiGraphicsExtractor graphics, int mouseX, int mouseY, ConfigOption option,
                           int rowX, int rowY, int rowW, int rowH, int colX, int colW, int clipY, int clipH, boolean hover) {
        String[] names = option.getChoices();
        boolean open = openChoice == option;
        int right = rowX + rowW - 4;
        int current = option.getChoiceIndex();
        // As wide as its longest name needs, but never at the cost of the setting's own label: the
        // field grows only into room the label leaves free, and keeps its usual width at least.
        int widest = 0;
        for (String name : names) widest = Math.max(widest, GuiFont.width(this.font, name));
        int labelRoom = rowW - 8 - GuiFont.width(this.font, option.getTitle()) - 12;
        int fieldW = Math.min(rowW / 2, Math.max(CHOICE_FIELD_W, Math.min(widest + 18, labelRoom)));
        int fieldX = right - fieldW;
        if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, rowH, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
        drawLabel(graphics, option.getTitle(), rowX + 4, rowY + 4, fieldX - 8 - (rowX + 4));
        AlpakaStyle.selectField(graphics, this.font, fieldX, rowY + 2, fieldW, rowH - 4, names[current], open);
        if (visible(rowY, rowH, clipY, clipH)) {
            hits.add(new Hit(rowX, Math.max(rowY, clipY), rowW, rowH, (mx, my) -> openChoice = open ? null : option));
        }
        int y = rowY + rowH;
        if (!open) return y;

        for (int i = 0; i < names.length; i++) {
            int index = i;
            boolean entryHover = inside(mouseX, mouseY, rowX, y, rowW, SETTING_ROW_H) && inside(mouseX, mouseY, colX, clipY, colW, clipH);
            AlpakaStyle.choiceEntry(graphics, this.font, rowX, y, rowW, SETTING_ROW_H, names[i], i == current, entryHover);
            if (visible(y, SETTING_ROW_H, clipY, clipH)) {
                hits.add(new Hit(rowX, Math.max(y, clipY), rowW, SETTING_ROW_H, (mx, my) -> {
                    option.setFloat(index);
                    openChoice = null;
                }));
            }
            y += SETTING_ROW_H;
        }
        return y + 2;
    }

    private void drawLabel(GuiGraphicsExtractor graphics, String label, int x, int y, int maxW) {
        graphics.text(this.font, GuiFont.text(ellipsize(label, maxW)), x, y, AlpakaStyle.TEXT_SOFT, false);
    }

    private static void drawToggle(GuiGraphicsExtractor graphics, int x, int y, float progress) {
        AlpakaStyle.toggle(graphics, x, y, TOGGLE_W, TOGGLE_H, progress);
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return AlpakaStyle.inside(mx, my, x, y, w, h);
    }

    private static boolean visible(int y, int h, int clipY, int clipH) {
        return y + h > clipY && y < clipY + clipH;
    }

    /** A label and the width it was cut to: the key for {@link #ellipsized}. */
    private record Fit(String text, int maxW) {
    }

    /**
     * ellipsize's answers. The same labels are cut to the same widths every frame, and cutting one
     * that overflows measures it once per character removed. A width depends on the menu font, so the
     * map is dropped when {@link GuiFont#style()} changes; it is also dropped in init(), which runs
     * again after a resize and after a resource reload changed the glyphs.
     */
    private final Map<Fit, String> ellipsized = new HashMap<>();
    private Style ellipsizedStyle;

    /** The text cut to fit the width, with an ellipsis where it was cut. */
    private String ellipsize(String text, int maxW) {
        if (text == null) return "";
        if (maxW <= 0) return "";
        Style style = GuiFont.style();
        if (style != ellipsizedStyle) {
            ellipsized.clear();
            ellipsizedStyle = style;
        }
        Fit key = new Fit(text, maxW);
        String cached = ellipsized.get(key);
        if (cached != null) return cached;
        String result = AlpakaStyle.ellipsize(this.font, text, maxW);
        // A text field being typed into adds one entry per value.
        if (ellipsized.size() >= 512) ellipsized.clear();
        ellipsized.put(key, result);
        return result;
    }

    // ---- Input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        double mx = event.x();
        double my = event.y();

        ConfigOption previousText = focusedText;
        focusedText = null;
        if (previousText != null) AlpakaConfig.save();
        boolean wasSearching = searchFocused;
        searchFocused = false;
        search.clearSelection();

        // Front-most first: later hits were drawn on top, a feature's switch over its row.
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(mx, my)) {
                if (hit.sound()) AlpakaStyle.click();
                hit.action().run(mx, my);
                return true;
            }
        }
        if (wasSearching && !searchFocused) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && draggedSlider != null) {
            draggedSlider = null;
            AlpakaConfig.endDeferredSaves();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggedSlider != null) {
            draggedSlider.setSliderNormalizedValue(Math.max(0.0, Math.min(1.0, (event.x() - dragTrackX) / (double) dragTrackW)));
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int featX = panelX() + sidebarW();
        int detailX = featX + featureColumnW();
        if (mouseX < featX) sidebarScroll.by(-scrollY * 20);
        else if (mouseX < detailX) featureScroll.by(-scrollY * 24);
        else detailScroll.by(-scrollY * 24);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (focusedText != null) {
            int codePoint = event.codepoint();
            if (codePoint >= 32 && codePoint != 127 && focusedText.getText().length() < MAX_TEXT_OPTION_LENGTH) {
                focusedText.setText(focusedText.getText() + (char) codePoint);
            }
            caretBlinkFrom = System.currentTimeMillis();
            return true;
        }
        if (searchFocused) {
            if (search.insert((char) event.codepoint())) onSearchChanged();
            caretBlinkFrom = System.currentTimeMillis();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (event.hasControlDownWithQuirk() && key == org.lwjgl.glfw.GLFW.GLFW_KEY_F) {
            searchFocused = true;
            caretBlinkFrom = System.currentTimeMillis();
            search.selectAll();
            return true;
        }
        if (focusedText != null) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
                String current = focusedText.getText();
                if (!current.isEmpty()) focusedText.setText(current.substring(0, current.length() - 1));
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                focusedText = null;
                AlpakaConfig.save();
                return true;
            }
        }
        if (openChoice != null && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            openChoice = null;
            return true;
        }
        if (searchFocused) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                searchFocused = false;
                search.clearSelection();
                return true;
            }
            String before = search.getText();
            if (search.keyPressed(key, event.hasControlDownWithQuirk(), event.hasShiftDown())) {
                if (!search.getText().equals(before)) onSearchChanged();
                caretBlinkFrom = System.currentTimeMillis();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(this.parent);
    }

    /** A drag the screen never saw end must not leave saves deferred for the rest of the session. */
    @Override
    public void removed() {
        draggedSlider = null;
        AlpakaConfig.endDeferredSaves();
        super.removed();
    }
}
