package net.alpaka.addons.client;

import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.ConfigCategory;
import net.alpaka.addons.client.gui.ConfigOption;
import net.alpaka.addons.client.gui.ConfigTree;
import net.alpaka.addons.client.gui.EditableText;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.client.gui.PloppAnimation;
import net.alpaka.addons.client.hud.HudEditorScreen;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.ModVersion;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
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
    private static final int FEATURE_ROW_H = AlpakaStyle.CARD_H;
    private static final int FEATURE_ROW_GAP = AlpakaStyle.CARD_GAP;
    private static final int SETTING_ROW_H = 16;
    private static final int SLIDER_ROW_H = 24;
    private static final int ACTION_ROW_H = 18;
    /** Space above and below a full-width button, so a hovered row next to it doesn't merge with it into one box. */
    private static final int ACTION_GAP = 2;
    private static final int TOGGLE_W = AlpakaStyle.TOGGLE_W;
    private static final int TOGGLE_H = AlpakaStyle.TOGGLE_H;
    private static final int SWATCH_W = 16;
    private static final int SWATCH_H = 9;
    private static final int TEXT_FIELD_W = 76;
    private static final int CHOICE_FIELD_W = 76;
    private static final int KEY_FIELD_W = 76;
    private static final int PREVIEW_LABEL_H = 12;
    private static final int PREVIEW_PAD = 5;
    /** How far an open list sits in from the row it belongs to. */
    private static final int LIST_INDENT = 8;
    private static final int MAX_TEXT_OPTION_LENGTH = 32;
    private static final int MAX_SEARCH_LENGTH = 35;
    private static final int PRESET_CARD_H = 20;
    private static final int PRESET_GAP = 4;
    private static final int PRESET_BUTTON_H = 14;
    private static final int PRESET_ICON = 14;
    private static final int PRESET_MIN_FIELD_W = 60;
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
    private boolean descriptionOpen = false;
    /** How tall the selected feature's settings were last frame, and for which feature. */
    private int settingsHeight = 0;
    private ConfigTree.Feature settingsHeightFor = null;

    /** How many lines of a feature's description show at least before it is opened. */
    private static final int DESCRIPTION_LINES = 3;

    /**
     * Dropdowns opened in the details panel. Every list starts folded each time the config opens,
     * so a long list of switches doesn't push the rest of the feature's settings out of view.
     */
    private final Set<ConfigOption> expanded = new HashSet<>();

    private ConfigOption focusedText = null;
    /**
     * The name fields of the preset cards, one per preset by position, so typing into one goes
     * through the same path as every other text setting. Grown as presets are added.
     */
    private final List<ConfigOption> presetNameFields = new ArrayList<>();
    /** The choice setting whose list is open, if any; one at a time. */
    private ConfigOption openChoice = null;
    /** The keybind waiting for its new key or mouse button, if any. */
    private ConfigOption listeningKey = null;

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

    /**
     * The screen opened on the feature that holds the option with this id, for the HUD editor's
     * right click. Null when no feature holds it.
     */
    public static AlpakaConfigScreen openOnOption(Screen parent, String optionId) {
        for (ConfigTree.Group group : ConfigTree.groups()) {
            for (ConfigTree.Section section : group.sections) {
                for (ConfigTree.Feature feature : section.features) {
                    boolean holds = feature.master != null && feature.master.getId().equals(optionId);
                    for (ConfigOption option : feature.settings) {
                        if (optionId.equals(option.getId())) holds = true;
                    }
                    if (!holds) continue;
                    AlpakaConfigScreen screen = new AlpakaConfigScreen(parent, "");
                    screen.selectSection(section);
                    screen.selectFeature(feature);
                    screen.revealSelected = true;
                    return screen;
                }
            }
        }
        return null;
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
        if (section != selectedSection) revealSection = true;
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
    /** Set when the section changed, so the sidebar scrolls it into view if it is out of sight. */
    private boolean revealSection = false;
    /**
     * Characters typed in the first moments after opening are dropped: they belong to the key press
     * that opened the menu, and would otherwise start a search with that key's letter.
     */
    private static final long TYPE_TO_SEARCH_GUARD_MS = 200L;

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
            if (selectedSection != selectedFeature.section) revealSection = true;
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

        // Opened from the custom main menu, the menu itself is what shows behind the window.
        if (this.parent instanceof net.alpaka.addons.features.mainmenu.CustomMainMenuScreen menu) {
            menu.extractAsBackdrop(graphics, this.width, this.height, partialTick);
        }

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
            if (hoveredTip instanceof ConfigTree.Feature) drawExpandedName(graphics, hoveredTipText);
            else AlpakaStyle.tooltip(graphics, this.font, this.width, this.height, mouseX, mouseY, hoveredTipText);
        }

        AlpakaStyle.endOpen(graphics, scaling);

        // Notices over the menu, where the HUD would hide them behind it.
        net.alpaka.addons.features.notification.AlpakaNotifications.renderOverScreen(graphics);
    }

    /** Where the hovered feature's cut name starts, and its row's background, for drawing it in full. */
    private int nameExpandX;
    private int nameExpandY;
    private int nameExpandBg;
    private int nameExpandRight;

    /** The hovered feature's whole name over its row, on a pill of the row's color that reaches just past it. */
    private void drawExpandedName(GuiGraphicsExtractor graphics, String name) {
        graphics.nextStratum();
        int textW = GuiFont.width(this.font, name);
        int x = nameExpandX - 4;
        // At least to the row's end, so a switch is either fully there or fully covered.
        int w = Math.min(Math.max(textW + 8, nameExpandRight - x), this.width - 4 - x);
        ModernGuiUtils.drawRoundedRect(graphics, x, nameExpandY + 2, w, FEATURE_ROW_H - 4, AlpakaStyle.ROW_RADIUS, nameExpandBg);
        graphics.text(this.font, GuiFont.text(name), nameExpandX, nameExpandY + (FEATURE_ROW_H - 8) / 2, AlpakaStyle.TEXT, false);
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
            int tx = detailX + PAD + 2;
            // A search mixes features of every section, so the header says which one this is from.
            if (searching() && selectedFeature.section != null) {
                String section = selectedFeature.section.title;
                int sectionW = GuiFont.width(this.font, section);
                if (sectionW + 14 < room / 2) {
                    graphics.text(this.font, GuiFont.text(section), tx, y + (HEADER_H - 8) / 2, AlpakaStyle.TEXT_MUTED, false);
                    AlpakaStyle.chevron(graphics, tx + sectionW + 6, y + HEADER_H / 2, false, AlpakaStyle.TEXT_MUTED);
                    tx += sectionW + 13;
                    room -= sectionW + 13;
                }
            }
            String name = ellipsize(selectedFeature.title, room);
            AlpakaStyle.title(graphics, this.font, name, tx, y);
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

        // Search: the search field every window has, over the feature column.
        int sx = featX + 4;
        int sw = featW - 8;
        int sh = 18;
        int sy = y + (HEADER_H - sh) / 2;
        boolean hover = inside(mouseX, mouseY, sx, sy, sw, sh);
        int[] clear = AlpakaStyle.searchClearBox(sx, sy, sw, sh);
        boolean hoverClear = !search.isEmpty() && inside(mouseX, mouseY, clear[0], clear[1], clear[2], clear[2]);
        searchScrollPx = AlpakaStyle.searchField(graphics, this.font, sx, sy, sw, sh, search, "Type to search…",
                searchFocused, hover, hoverClear, caretBlinkFrom);
        // The field first, the clear button after: the later hit is the one on top.
        int scrolled = searchScrollPx;
        int tx = sx + AlpakaStyle.SEARCH_TEXT_INSET;
        hits.add(Hit.quiet(sx, sy, sw, sh, (mx, my) -> {
            searchFocused = true;
            caretBlinkFrom = System.currentTimeMillis();
            search.setCaretFromX((int) mx - tx + scrolled, s -> GuiFont.width(this.font, s));
        }));
        if (!search.isEmpty()) {
            hits.add(new Hit(clear[0], clear[1], clear[2], clear[2], (mx, my) -> {
                search.setText("");
                onSearchChanged();
            }));
        }
    }

    private void drawSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h, float deltaSec) {
        // The HUD editor's button sits in the bottom corner, below the sections, so it is one
        // click away whichever section is open.
        int footerH = AlpakaStyle.BUTTON_H + PAD + 9;
        drawHudEditorButton(graphics, mouseX, mouseY, x, y + h - AlpakaStyle.BUTTON_H - PAD, w);

        int clipY = y + 4;
        int clipH = h - 8 - footerH;
        int contentH = 4;
        List<ConfigTree.Group> groups = ConfigTree.groups();
        for (int i = 0; i < groups.size(); i++) {
            contentH += GROUP_LABEL_H + groups.get(i).sections.size() * SECTION_ROW_H + (i < groups.size() - 1 ? 9 : 0);
        }
        sidebarScroll.max = Math.max(0, contentH - clipH);
        if (revealSection && selectedSection != null) {
            revealSection = false;
            // Where the section's row sits in the list, to bring it into view if it is out of sight.
            int top = 2;
            int groupTop = 0;
            for (int i = 0; i < groups.size(); i++) {
                groupTop = top;
                top += GROUP_LABEL_H;
                int index = groups.get(i).sections.indexOf(selectedSection);
                if (index >= 0) {
                    top += index * SECTION_ROW_H;
                    break;
                }
                top += groups.get(i).sections.size() * SECTION_ROW_H + 9;
            }
            // Scrolled so the list starts at the section's group label wherever that keeps the
            // section in view, so no label or row is left cut in half at the top.
            boolean out = top < sidebarScroll.target || top + SECTION_ROW_H > sidebarScroll.target + clipH;
            if (out) {
                sidebarScroll.target = top + SECTION_ROW_H + 4 - groupTop <= clipH ? groupTop
                        : top + SECTION_ROW_H + 4 - clipH;
            }
        }
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
                // How every list of the mod says what is selected: its dot and its name in the
                // accent color, nothing behind it and nothing at its edge.
                int color = selected ? AlpakaStyle.accentText() : hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT;
                String label = ellipsize(section.title, w - PAD - 14);
                AlpakaStyle.listDot(graphics, x + PAD + 1, rowY + 5, color);
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
                // Not where the list fades out at the bottom: half faded, right over the line above
                // the HUD Editor button, it read as that line drawn twice.
                boolean underFade = sidebarScroll.offset() + 1 < sidebarScroll.max && rowY + 4 >= clipY + clipH - 10;
                if (!underFade) ModernGuiUtils.drawRect(graphics, x + PAD, rowY + 4, w - PAD * 2, 1, AlpakaStyle.LINE);
                rowY += 9;
            }
        }
        AlpakaStyle.scrollFades(graphics, x, clipY, w, clipH, sidebarScroll.offset() > 0, sidebarScroll.offset() + 1 < sidebarScroll.max);
        graphics.disableScissor();
    }

    private void drawHudEditorButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w) {
        int bx = x + PAD;
        int bw = w - PAD * 2;
        ModernGuiUtils.drawRect(graphics, bx, y - 9, bw, 1, AlpakaStyle.LINE);
        boolean hover = inside(mouseX, mouseY, bx, y, bw, AlpakaStyle.BUTTON_H);
        AlpakaStyle.button(graphics, this.font, bx, y, bw, AlpakaStyle.BUTTON_H,
                ellipsize("HUD Editor", bw - 8), hover, AlpakaStyle.ButtonKind.PLAIN);
        hits.add(new Hit(bx, y, bw, AlpakaStyle.BUTTON_H,
                (mx, my) -> this.minecraft.gui.setScreen(new HudEditorScreen(this))));
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
                boolean inView = top >= featureScroll.target && top + FEATURE_ROW_H + 4 <= featureScroll.target + clipH;
                if (!inView) {
                    // Opened on a feature, the list jumps there, centred; stepping with the arrow
                    // keys only scrolls as far as the next row needs, and glides.
                    boolean opening = System.currentTimeMillis() - openTimeMs < 300;
                    featureScroll.target = opening ? Math.max(0, top - clipH / 2 + FEATURE_ROW_H)
                            : top < featureScroll.target ? top : top + FEATURE_ROW_H + 6 - clipH;
                    if (opening) featureScroll.current = Math.max(0, Math.min(featureScroll.max, featureScroll.target));
                }
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

                // The chevron, which says the feature has settings, keeps its place whether drawn or
                // not, so every switch lines up.
                int right = rowX + rowW - 6;
                // An action-only row opens the confirm dialog, not settings: no chevron, and its
                // name gets the room.
                boolean action = onlyDangerous(feature);
                if (!feature.panelOptions().isEmpty() && !action) {
                    AlpakaStyle.chevron(graphics, right - 2, rowY + FEATURE_ROW_H / 2, false,
                            selected || hover ? AlpakaStyle.TEXT_SOFT : AlpakaStyle.TEXT_MUTED);
                }
                if (!action) right -= 8;
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

                // The selected feature's name in the accent color, as the sidebar names its section.
                int titleColor = selected ? AlpakaStyle.accentTextOn(AlpakaStyle.ROW_SELECTED) : AlpakaStyle.TEXT;
                // A "feature" that is only a dangerous action (Reset All Settings) says so by its
                // name in the danger color, as its button does.
                if (onlyDangerous(feature)) titleColor = hover || selected ? AlpakaStyle.DANGER : AlpakaStyle.DANGER_SOFT;
                int titleW = (feature.hasToggle() ? toggleX : right) - (rowX + 8) - 4;
                String shownTitle = ellipsize(feature.title, titleW);
                graphics.text(this.font, GuiFont.text(shownTitle), rowX + 8, rowY + (FEATURE_ROW_H - 8) / 2, titleColor, false);
                // In search results, the section it comes from after the name, where there is room.
                if (searching() && feature.section != null) {
                    int after = rowX + 8 + GuiFont.width(this.font, shownTitle) + 6;
                    int room = (feature.hasToggle() ? toggleX : right) - 6 - after;
                    String section = feature.section.title;
                    if (shownTitle.equals(feature.title) && GuiFont.width(this.font, section) <= room) {
                        graphics.text(this.font, GuiFont.text(section), after, rowY + (FEATURE_ROW_H - 8) / 2, AlpakaStyle.TEXT_MUTED, false);
                    }
                }
                // A name cut short shows in full where it is, the row's own background growing to
                // the right under it, rather than as a tooltip over the settings.
                // Only with the mouse on the name itself, so the switch beside it stays in sight.
                if (hover && mouseX < (feature.hasToggle() ? toggleX - 3 : right) && !shownTitle.equals(feature.title)) {
                    hoveredTipThisFrame = feature;
                    hoveredTipTextThisFrame = feature.title;
                    nameExpandX = rowX + 8;
                    nameExpandY = rowY;
                    nameExpandBg = background;
                    nameExpandRight = rowX + rowW;
                }
            }
            rowY += FEATURE_ROW_H + FEATURE_ROW_GAP;
        }
        AlpakaStyle.scrollFades(graphics, x, clipY, w, clipH, featureScroll.offset() > 0, featureScroll.offset() + 1 < featureScroll.max);
        graphics.disableScissor();
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

        // In setup mode the feature list with its switches is folded away, so the feature's own
        // switch moves to the top of its settings.
        if (setupMode && setupProgress > 0.5f && feature.hasToggle()) {
            ConfigOption master = feature.master;
            boolean shown = visible(rowY, SETTING_ROW_H, clipY, clipH);
            boolean hover = inside(mouseX, mouseY, rowX, rowY, rowW, SETTING_ROW_H) && inside(mouseX, mouseY, x, clipY, w, clipH);
            if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, SETTING_ROW_H, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
            int right = rowX + rowW - 4;
            // While the list is still folding it animates the switch already.
            if (setupProgress >= 0.999f) master.updateToggleProgress(deltaSec);
            graphics.text(this.font, GuiFont.text("Enabled"), rowX + 4, rowY + 4, AlpakaStyle.TEXT, false);
            drawToggle(graphics, right - TOGGLE_W, rowY + (SETTING_ROW_H - TOGGLE_H) / 2, master.getToggleProgress());
            if (shown) hits.add(new Hit(rowX, Math.max(rowY, clipY), rowW, SETTING_ROW_H, (mx, my) -> master.toggleBool()));
            rowY += SETTING_ROW_H;
        }

        // The description, as a paragraph under the header; a click folds it to its first line, and
        // that choice holds for every feature.
        String description = feature.description == null ? "" : feature.description;
        if (!description.isEmpty()) {
            List<FormattedText> lines = GuiFont.splitLines(this.font, description, rowW - 18);
            // At most three lines at first, so the settings start high enough to be seen; a click
            // shows the rest.
            // More where the settings below leave the room (measured last frame), so a feature with
            // few settings never hides its description behind a click.
            int fits = settingsHeightFor == feature ? (clipH - settingsHeight - 12) / (LINE_H + 1) : 0;
            int limit = Math.max(DESCRIPTION_LINES, fits);
            boolean foldable = lines.size() > limit;
            int shownLines = descriptionOpen || !foldable ? lines.size() : limit;
            int blockH = shownLines * (LINE_H + 1) + 5;
            boolean hover = foldable && inside(mouseX, mouseY, rowX, rowY, rowW, blockH) && inside(mouseX, mouseY, x, clipY, w, clipH);
            if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, blockH, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
            int lineY = rowY + 3;
            for (int i = 0; i < shownLines; i++) {
                FormattedText line = lines.get(i);
                if (i == shownLines - 1 && shownLines < lines.size()) {
                    // The last line shown says there is more.
                    String cut = line.getString().replaceAll("[\\s,;:.]+$", "") + "…";
                    graphics.text(this.font, GuiFont.text(ellipsize(cut, rowW - 18)), rowX + 4, lineY, AlpakaStyle.TEXT_MUTED, false);
                } else {
                    graphics.text(this.font, Language.getInstance().getVisualOrder(line), rowX + 4, lineY, AlpakaStyle.TEXT_MUTED, false);
                }
                lineY += LINE_H + 1;
            }
            if (foldable) {
                // Down while there is more to show, up once it is all there: it unfolds in place.
                AlpakaStyle.verticalChevron(graphics, rowX + rowW - 7, rowY + 7, descriptionOpen, hover ? AlpakaStyle.TEXT_SOFT : AlpakaStyle.TEXT_MUTED);
                if (visible(rowY, blockH, clipY, clipH)) {
                    hits.add(new Hit(rowX, Math.max(rowY, clipY), rowW, blockH, (mx, my) -> descriptionOpen = !descriptionOpen));
                }
            }
            rowY += blockH + 4;
        }

        List<ConfigOption> options = feature.panelOptions();
        int settingsTop = rowY;
        if (options.isEmpty()) {
            graphics.text(this.font, GuiFont.text("No further settings."), rowX + 4, rowY + 4, AlpakaStyle.TEXT_MUTED, false);
            rowY += SETTING_ROW_H;
        }
        for (ConfigOption option : options) {
            rowY = drawSetting(graphics, mouseX, mouseY, option, rowX, rowY, rowW, x, w, clipY, clipH, deltaSec);
        }

        settingsHeight = rowY - settingsTop;
        settingsHeightFor = feature;
        // rowY - startY is the content's height whatever the scroll; adding the offset again made the
        // limit grow with every turn of the wheel, until the column scrolled away into emptiness.
        detailScroll.max = Math.max(0, rowY - startY + 6 - clipH);
        detailScroll.update(deltaSec);
        AlpakaStyle.scrollFades(graphics, x, clipY, w, clipH, detailScroll.offset() > 0, detailScroll.offset() + 1 < detailScroll.max);
        graphics.disableScissor();
    }

    /** Draws one setting row and returns the y the next one starts at. */
    private int drawSetting(GuiGraphicsExtractor graphics, int mouseX, int mouseY, ConfigOption option,
                            int rowX, int rowY, int rowW, int colX, int colW, int clipY, int clipH, float deltaSec) {
        if (option.getType() == ConfigOption.Type.PRESETS) {
            return drawPresets(graphics, mouseX, mouseY, rowX, rowY, rowW, colX, colW, clipY, clipH);
        }
        List<net.minecraft.util.FormattedCharSequence> previewLines = option.getType() == ConfigOption.Type.PREVIEW
                ? this.font.split(option.getPreviewText(), rowW - 2 * PREVIEW_PAD) : List.of();
        int rowH = switch (option.getType()) {
            case SLIDER -> option.getChoices() != null ? SETTING_ROW_H : SLIDER_ROW_H;
            case PREVIEW -> PREVIEW_LABEL_H + Math.max(1, previewLines.size()) * LINE_H + 2 * PREVIEW_PAD + 2;
            case ACTION -> option.isColor() ? SETTING_ROW_H : ACTION_ROW_H;
            default -> SETTING_ROW_H;
        };
        int buttonGap = option.getType() == ConfigOption.Type.ACTION && !option.isColor() ? ACTION_GAP : 0;
        rowY += buttonGap;
        boolean shown = visible(rowY, rowH, clipY, clipH);
        boolean hover = inside(mouseX, mouseY, rowX, rowY, rowW, rowH) && inside(mouseX, mouseY, colX, clipY, colW, clipH);
        if (hover) {
            hoveredTipThisFrame = option;
            hoveredTipTextThisFrame = listeningKey == option
                    ? "Press a key or a mouse button. Escape unbinds it, a click anywhere keeps it as it was."
                    : option.getDescription();
        }
        // A row wholly outside the column's clip would draw nothing: its text would be laid out only
        // for the scissor to drop it. Skip it, keeping a switch's animation running so its knob is in
        // the right place when the row scrolls back in. An open list counts as part of its row, and
        // the y returned is exactly what drawing it would have returned.
        if (!shown) {
            int blockH = rowH;
            if (option.getType() == ConfigOption.Type.DROPDOWN && expanded.contains(option)) {
                blockH = rowH + option.getEntries().size() * SETTING_ROW_H + 2;
            } else if (option.getType() == ConfigOption.Type.SLIDER && option.getChoices() != null && openChoice == option) {
                blockH = rowH + option.getChoices().length * SETTING_ROW_H + 2;
            }
            if (!visible(rowY, blockH, clipY, clipH)) {
                if (option.getType() == ConfigOption.Type.BOOLEAN) option.updateToggleProgress(deltaSec);
                return rowY + blockH + buttonGap;
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
                // Inset by the knob's radius, so the knob at either end lines up with the label and
                // with the switches above and below instead of poking past them.
                int trackX = rowX + 7;
                int trackW = rowW - 14;
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
                boolean open = expanded.contains(option);
                if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, rowH, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
                String count = option.getEnabledEntryCount() + "/" + option.getEntryCount();
                int countW = GuiFont.width(this.font, count);
                drawLabel(graphics, option.getTitle(), rowX + 4, labelY, right - countW - 20 - (rowX + 4));
                graphics.text(this.font, GuiFont.text(count), right - 12 - countW, labelY, AlpakaStyle.TEXT_MUTED, false);
                // It unfolds in place, so it points down and up like a select field, not right.
                AlpakaStyle.verticalChevron(graphics, right - 4, rowY + 8, open, hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT);
                if (shown) {
                    hits.add(new Hit(rowX, hitY, rowW, rowH, (mx, my) -> {
                        if (!expanded.remove(option)) {
                            expanded.add(option);
                            revealList = option;
                        }
                    }));
                }
                if (open) {
                    int entryY = rowY + rowH;
                    for (ConfigOption.ToggleEntry entry : option.getEntries()) {
                        boolean entryHover = inside(mouseX, mouseY, rowX + LIST_INDENT, entryY, rowW - LIST_INDENT, SETTING_ROW_H) && inside(mouseX, mouseY, colX, clipY, colW, clipH);
                        // Indented under the row they belong to, as an open choice list is.
                        if (entryHover) ModernGuiUtils.drawRoundedRect(graphics, rowX + LIST_INDENT, entryY, rowW - LIST_INDENT, SETTING_ROW_H, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
                        int entryLabelW = right - TOGGLE_W - 8 - (rowX + LIST_INDENT + 6);
                        String entryLabel = ellipsize(entry.getLabel(), entryLabelW);
                        graphics.text(this.font, GuiFont.text(entryLabel), rowX + LIST_INDENT + 6, entryY + 4,
                                entry.get() ? AlpakaStyle.TEXT_SOFT : AlpakaStyle.TEXT_MUTED, false);
                        if (entryHover && !entryLabel.equals(entry.getLabel())) {
                            hoveredTipThisFrame = entry;
                            hoveredTipTextThisFrame = entry.getLabel();
                        }
                        drawToggle(graphics, right - TOGGLE_W, entryY + (SETTING_ROW_H - TOGGLE_H) / 2, entry.get() ? 1.0f : 0.0f);
                        if (visible(entryY, SETTING_ROW_H, clipY, clipH)) {
                            hits.add(new Hit(rowX + LIST_INDENT, Math.max(entryY, clipY), rowW - LIST_INDENT, SETTING_ROW_H, (mx, my) -> entry.toggle()));
                        }
                        entryY += SETTING_ROW_H;
                    }
                    revealOpened(option, rowY, entryY + 2, clipY, clipH);
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
                AlpakaStyle.textField(graphics, this.font, fieldX, rowY + 2, fieldW, rowH - 4, option.getShownText(),
                        option.getPlaceholder(), focused, fieldHover, caretBlinkFrom);
                if (shown) {
                    hits.add(Hit.quiet(rowX, hitY, rowW, rowH, (mx, my) -> {
                        focusedText = option;
                        searchFocused = false;
                        caretBlinkFrom = System.currentTimeMillis();
                    }));
                }
            }
            case KEYBIND -> {
                KeyMapping mapping = option.getKeyMapping();
                if (mapping == null) return rowY + rowH;
                if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, rowH, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
                boolean listening = listeningKey == option;
                int fieldW = Math.min(KEY_FIELD_W, rowW / 2);
                int fieldX = right - fieldW;
                int fieldY = rowY + 2;
                int fieldH = rowH - 4;
                // Reset, a circular arrow left of the field, only while the key is not its default.
                int resetW = fieldH;
                int resetX = fieldX - 3 - resetW;
                boolean showReset = !mapping.isDefault() && !listening;
                drawLabel(graphics, option.getTitle(), rowX + 4, labelY, (showReset ? resetX : fieldX) - 8 - (rowX + 4));
                boolean resetHover = showReset && hover && inside(mouseX, mouseY, resetX, fieldY, resetW, fieldH);
                if (showReset) {
                    AlpakaStyle.resetButton(graphics, resetX, fieldY, resetW, resetHover);
                    if (resetHover) hoveredTipTextThisFrame = "Back to the default key: "
                            + mapping.getDefaultKey().getDisplayName().getString() + ".";
                }
                boolean fieldHover = hover && inside(mouseX, mouseY, fieldX, fieldY, fieldW, fieldH);
                ModernGuiUtils.drawRoundedRect(graphics, fieldX, fieldY, fieldW, fieldH, AlpakaStyle.FIELD_RADIUS,
                        fieldHover && !listening ? AlpakaStyle.fieldHover() : AlpakaStyle.FIELD);
                ModernGuiUtils.drawRect(graphics, fieldX + 3, fieldY + fieldH - 1, fieldW - 6, 1,
                        listening ? AlpakaStyle.accentIndicator() : AlpakaStyle.LINE);
                String value;
                int color;
                if (listening) {
                    value = "Press a Key";
                    color = AlpakaStyle.accentText();
                } else if (mapping.isUnbound()) {
                    value = "Not Bound";
                    color = AlpakaStyle.TEXT_MUTED;
                } else {
                    value = mapping.getTranslatedKeyMessage().getString();
                    // Red when another key mapping shares the key, as vanilla's Controls shows it,
                    // and the tooltip says which.
                    KeyMapping other = AlpakaKeyBinds.conflict(mapping);
                    color = other != null ? AlpakaStyle.DANGER_SOFT : AlpakaStyle.TEXT;
                    if (other != null && hover && !resetHover) {
                        hoveredTipTextThisFrame = option.getDescription() + " " + value + " is also bound to "
                                + Component.translatable(other.getName()).getString() + ".";
                    }
                }
                String keyText = ellipsize(value, fieldW - 8);
                ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text(keyText), fieldX + fieldW / 2, fieldY + (fieldH - 8) / 2, color);
                if (shown) {
                    hits.add(new Hit(rowX, hitY, rowW, rowH, (mx, my) -> {
                        listeningKey = option;
                        focusedText = null;
                        searchFocused = false;
                    }));
                    if (showReset) {
                        hits.add(new Hit(resetX, Math.max(fieldY, clipY), resetW, fieldH,
                                (mx, my) -> AlpakaKeyBinds.set(mapping, mapping.getDefaultKey())));
                    }
                }
            }
            default -> {
            }
        }
        return rowY + rowH + buttonGap;
    }

    /**
     * The viewmodel presets: a card each, in the order they were made, with the name as a text field
     * (empty shows "Preset n"), Load, Save and Delete; the active one carries the accent. Below them
     * a button that adds a preset holding the current settings.
     */
    private int drawPresets(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int rowX, int rowY, int rowW,
                            int colX, int colW, int clipY, int clipH) {
        AlpakaConfig config = AlpakaConfig.instance;
        List<AlpakaConfig.ItemPreset> presets = config.itemPresets;
        boolean inColumn = inside(mouseX, mouseY, colX, clipY, colW, clipH);
        int y = rowY;
        for (int i = 0; i < presets.size(); i++) {
            int index = i;
            boolean active = config.activeItemPresetIndex == i;
            if (!visible(y, PRESET_CARD_H, clipY, clipH)) {
                y += PRESET_CARD_H + PRESET_GAP;
                continue;
            }
            int midY = y + PRESET_CARD_H / 2;
            // The active preset is a selected card like any other: the shared ring, and its number
            // in the accent.
            ModernGuiUtils.drawRoundedRect(graphics, rowX, y, rowW, PRESET_CARD_H, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
            if (active) AlpakaStyle.selectedOutline(graphics, rowX, y, rowW, PRESET_CARD_H, AlpakaStyle.ROW_RADIUS);

            String number = String.valueOf(i + 1);
            int numberX = rowX + 9 - GuiFont.width(this.font, number) / 2;
            graphics.text(this.font, GuiFont.text(number), numberX, midY - 4, active ? AlpakaStyle.accentTextOn(AlpakaStyle.ROW) : AlpakaStyle.TEXT_MUTED, false);

            // From the right: delete, Save, Load; the name field takes what is left.
            int right = rowX + rowW - 3;
            int deleteX = right - PRESET_ICON;
            int saveW = GuiFont.width(this.font, "Save") + 8;
            int saveX = deleteX - 2 - saveW;
            int loadW = GuiFont.width(this.font, "Load") + 8;
            int loadX = saveX - 2 - loadW;
            int buttonY = midY - PRESET_BUTTON_H / 2;
            int fieldX = rowX + 17;
            // Every name field ends a little short of Load, but never so early that a name has no
            // room left.
            int fieldGap = 6;
            int fieldEnd = Math.max(fieldX + PRESET_MIN_FIELD_W, loadX - fieldGap);
            int fieldW = Math.max(20, fieldEnd - fieldX);
            ConfigOption nameField = presetNameField(i);
            boolean fieldHover = inColumn && inside(mouseX, mouseY, fieldX, y + 4, fieldW, PRESET_CARD_H - 8);
            AlpakaStyle.textField(graphics, this.font, fieldX, y + 4, fieldW, PRESET_CARD_H - 8, nameField.getText(),
                    nameField.getPlaceholder(), focusedText == nameField, fieldHover, caretBlinkFrom);

            boolean loadHover = inColumn && inside(mouseX, mouseY, loadX, buttonY, loadW, PRESET_BUTTON_H);
            boolean saveHover = inColumn && inside(mouseX, mouseY, saveX, buttonY, saveW, PRESET_BUTTON_H);
            boolean deleteHover = inColumn && inside(mouseX, mouseY, deleteX, midY - PRESET_ICON / 2, PRESET_ICON, PRESET_ICON);
            AlpakaStyle.button(graphics, this.font, loadX, buttonY, loadW, PRESET_BUTTON_H, "Load", loadHover, AlpakaStyle.ButtonKind.PRIMARY);
            // A plain button would vanish on the card, which has the same gray; this one sits a step lighter.
            ModernGuiUtils.drawRoundedRect(graphics, saveX, buttonY, saveW, PRESET_BUTTON_H, AlpakaStyle.ROW_RADIUS,
                    saveHover ? ModernGuiUtils.lerpColor(AlpakaStyle.ROW_SELECTED, AlpakaStyle.TEXT, 0.06f) : AlpakaStyle.ROW_SELECTED);
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text("Save"), saveX + saveW / 2, midY - 4,
                    saveHover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT);
            AlpakaStyle.trashButton(graphics, deleteX, midY - PRESET_ICON / 2, PRESET_ICON, deleteHover);

            int hitY = Math.max(y, clipY);
            hits.add(Hit.quiet(fieldX, Math.max(y + 4, clipY), fieldW, PRESET_CARD_H - 8, (mx, my) -> {
                focusedText = nameField;
                searchFocused = false;
                caretBlinkFrom = System.currentTimeMillis();
            }));
            hits.add(new Hit(loadX, hitY, loadW, PRESET_CARD_H, (mx, my) -> config.loadPreset(index)));
            hits.add(new Hit(saveX, hitY, saveW, PRESET_CARD_H, (mx, my) -> this.minecraft.gui.setScreen(
                    new AlpakaConfirmScreen(this, "Save to " + config.presetName(index),
                            "Saves your current viewmodel and swing settings to " + config.presetName(index)
                                    + ", replacing what is saved there now.",
                            "Save", false, () -> config.savePreset(index)))));
            hits.add(new Hit(deleteX, hitY, PRESET_ICON, PRESET_CARD_H, (mx, my) -> this.minecraft.gui.setScreen(
                    new AlpakaConfirmScreen(this, "Delete Preset",
                            "Deletes " + config.presetName(index) + ". Your current settings stay as they are. "
                                    + "This cannot be undone.",
                            "Delete", true, () -> config.deletePreset(index)))));
            y += PRESET_CARD_H + PRESET_GAP;
        }

        // Adds a preset holding the current settings, with its name field ready to type into.
        boolean addHover = inColumn && inside(mouseX, mouseY, rowX, y, rowW, ACTION_ROW_H);
        AlpakaStyle.button(graphics, this.font, rowX, y, rowW, ACTION_ROW_H, "+ New Preset", addHover, AlpakaStyle.ButtonKind.PLAIN);
        if (visible(y, ACTION_ROW_H, clipY, clipH)) {
            hits.add(new Hit(rowX, Math.max(y, clipY), rowW, ACTION_ROW_H, (mx, my) -> {
                config.addPreset();
                focusedText = presetNameField(config.itemPresets.size() - 1);
                searchFocused = false;
                caretBlinkFrom = System.currentTimeMillis();
            }));
        }
        y += ACTION_ROW_H + 4;
        for (FormattedText line : GuiFont.splitLines(this.font, "A new preset starts with your current settings.", rowW - 8)) {
            graphics.text(this.font, Language.getInstance().getVisualOrder(line), rowX + 4, y, AlpakaStyle.TEXT_MUTED, false);
            y += LINE_H + 1;
        }
        return y + 4;
    }

    /** The name field of the preset at this position, made on first use. */
    private ConfigOption presetNameField(int index) {
        while (presetNameFields.size() <= index) {
            int i = presetNameFields.size();
            presetNameFields.add(new ConfigOption("preset_name_" + i, "Name", "", ConfigCategory.VIEWMODEL,
                    () -> i < AlpakaConfig.instance.itemPresets.size() ? nullToEmpty(AlpakaConfig.instance.itemPresets.get(i).name) : "",
                    value -> {
                        if (i < AlpakaConfig.instance.itemPresets.size()) AlpakaConfig.instance.itemPresets.get(i).name = value;
                    },
                    "Preset " + (i + 1), ""));
        }
        return presetNameFields.get(index);
    }

    private static String nullToEmpty(String text) {
        return text == null ? "" : text;
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
        AlpakaStyle.selectField(graphics, this.font, fieldX, rowY + 2, fieldW, rowH - 4, names[current], open, hover);
        if (visible(rowY, rowH, clipY, clipH)) {
            hits.add(new Hit(rowX, Math.max(rowY, clipY), rowW, rowH, (mx, my) -> {
                openChoice = clickedWithChoiceOpen == option ? null : option;
                if (openChoice != null) revealList = option;
            }));
        }
        int y = rowY + rowH;
        if (!open) return y;

        for (int i = 0; i < names.length; i++) {
            int index = i;
            boolean entryHover = inside(mouseX, mouseY, rowX + LIST_INDENT, y, rowW - LIST_INDENT, SETTING_ROW_H) && inside(mouseX, mouseY, colX, clipY, colW, clipH);
            AlpakaStyle.choiceEntry(graphics, this.font, rowX + LIST_INDENT, y, rowW - LIST_INDENT, SETTING_ROW_H, names[i], i == current, entryHover);
            if (visible(y, SETTING_ROW_H, clipY, clipH)) {
                hits.add(new Hit(rowX + LIST_INDENT, Math.max(y, clipY), rowW - LIST_INDENT, SETTING_ROW_H, (mx, my) -> {
                    option.setFloat(index);
                }));
            }
            y += SETTING_ROW_H;
        }
        revealOpened(option, rowY, y + 2, clipY, clipH);
        return y + 2;
    }

    /** The choice list that was open when the current click came in, which the click has closed. */
    private ConfigOption clickedWithChoiceOpen = null;

    /** The list just opened, if it still has to be scrolled into view. */
    private ConfigOption revealList = null;

    /**
     * Scrolls the settings so a list that was just opened shows in full - or as much of it as fits,
     * its row kept at the top - instead of opening out of sight below the column's edge.
     */
    private void revealOpened(ConfigOption option, int top, int bottom, int clipY, int clipH) {
        if (revealList != option) return;
        revealList = null;
        int overflow = bottom - (clipY + clipH) + 4;
        if (overflow > 0) detailScroll.by(Math.min(overflow, top - clipY));
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
        if (listeningKey != null) {
            // The middle and side buttons bind; left and right click are what the menu is used with,
            // so they leave the key as it was, as a click outside a text field does.
            ConfigOption option = listeningKey;
            listeningKey = null;
            if (event.button() > 1 && option.getKeyMapping() != null) {
                AlpakaKeyBinds.set(option.getKeyMapping(), InputConstants.Type.MOUSE.getOrCreate(event.button()));
            }
            return true;
        }
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        double mx = event.x();
        double my = event.y();

        ConfigOption previousText = focusedText;
        focusedText = null;
        // An open choice list closes on any click that isn't on it; its own row toggles it again.
        ConfigOption previousChoice = openChoice;
        openChoice = null;
        clickedWithChoiceOpen = previousChoice;
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
        // In setup mode the sidebar and the feature list are folded away and only the settings are
        // left, so the wheel turns those wherever the mouse is.
        if (setupProgress >= 0.999f || mouseX >= detailX) detailScroll.by(-scrollY * 24);
        else if (mouseX < featX) sidebarScroll.by(-scrollY * 20);
        else featureScroll.by(-scrollY * 24);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (focusedText != null) {
            int codePoint = event.codepoint();
            if (codePoint >= 32 && codePoint != 127 && focusedText.getText().length() < focusedText.getMaxLength()) {
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
        // Typing with nothing focused starts a search, so finding a setting never needs a click.
        int codePoint = event.codepoint();
        if (listeningKey == null && Character.isLetterOrDigit(codePoint) && Character.isBmpCodePoint(codePoint)
                && System.currentTimeMillis() - openTimeMs > TYPE_TO_SEARCH_GUARD_MS) {
            searchFocused = true;
            search.setText("");
            if (search.insert((char) codePoint)) onSearchChanged();
            caretBlinkFrom = System.currentTimeMillis();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (listeningKey != null) {
            // Escape unbinds, as in vanilla's Controls.
            ConfigOption option = listeningKey;
            listeningKey = null;
            if (option.getKeyMapping() != null) {
                AlpakaKeyBinds.set(option.getKeyMapping(), key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
                        ? InputConstants.UNKNOWN : InputConstants.getKey(event));
            }
            return true;
        }
        if (event.hasControlDownWithQuirk() && key == org.lwjgl.glfw.GLFW.GLFW_KEY_F) {
            if (focusedText != null) AlpakaConfig.save();
            focusedText = null;
            openChoice = null;
            searchFocused = true;
            caretBlinkFrom = System.currentTimeMillis();
            search.selectAll();
            return true;
        }
        if (focusedText != null) {
            // Pasting is how a key or a name usually arrives; the first line of the clipboard,
            // cut to what the field takes.
            if (event.isPaste()) {
                String clip = this.minecraft == null ? null : this.minecraft.keyboardHandler.getClipboard();
                if (clip != null) {
                    int newline = clip.indexOf('\n');
                    if (newline >= 0) clip = clip.substring(0, newline);
                    clip = clip.replace("\r", "").replace("\t", " ").trim();
                    String joined = focusedText.getText() + clip;
                    focusedText.setText(joined.substring(0, Math.min(joined.length(), focusedText.getMaxLength())));
                    caretBlinkFrom = System.currentTimeMillis();
                }
                return true;
            }
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
            // Every other key belongs to the field while it is being typed into; Space must never
            // switch the feature behind it.
            return true;
        }
        if (openChoice != null && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            openChoice = null;
            return true;
        }
        // Up and Down walk the feature list, the search results included; Enter switches the
        // selected feature on or off once the search box has let go of the keyboard.
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
            int direction = key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP ? -1 : 1;
            if (focusedText != null) return true;
            // An open choice list takes the arrows: they step its value, which applies at once.
            if (openChoice != null) {
                String[] names = openChoice.getChoices();
                int next = Math.max(0, Math.min(names.length - 1, openChoice.getChoiceIndex() + direction));
                openChoice.setFloat(next);
                return true;
            }
            stepListed(direction);
            return true;
        }
        if (openChoice != null && (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)) {
            openChoice = null;
            return true;
        }
        if (searchFocused) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                // The first Escape empties the search, the next one closes the menu.
                if (!search.isEmpty()) {
                    search.setText("");
                    onSearchChanged();
                }
                searchFocused = false;
                search.clearSelection();
                return true;
            }
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
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
        if (searchFocused) return super.keyPressed(event);
        if ((key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER
                || key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) && selectedFeature != null && selectedFeature.hasToggle()) {
            AlpakaStyle.click();
            selectedFeature.master.toggleBool();
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && searching()) {
            search.setText("");
            onSearchChanged();
            return true;
        }
        return super.keyPressed(event);
    }

    /** Whether the feature has no switch and nothing but actions that go through the confirm dialog. */
    private static boolean onlyDangerous(ConfigTree.Feature feature) {
        if (feature.hasToggle() || feature.panelOptions().isEmpty()) return false;
        for (ConfigOption option : feature.panelOptions()) {
            if (option.getType() != ConfigOption.Type.ACTION || option.isColor() || !option.isDangerous()) return false;
        }
        return true;
    }

    /** Selects the feature above or below the selected one in the middle column, and scrolls to it. */
    private void stepListed(int direction) {
        List<ConfigTree.Feature> features = listedFeatures();
        if (features.isEmpty()) return;
        int index = features.indexOf(selectedFeature);
        int next = index < 0 ? 0 : Math.max(0, Math.min(features.size() - 1, index + direction));
        selectFeature(features.get(next));
        revealSelected = true;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(this.parent);
    }

    /** A drag the screen never saw end must not leave saves deferred for the rest of the session. */
    @Override
    public void removed() {
        draggedSlider = null;
        listeningKey = null;
        AlpakaConfig.endDeferredSaves();
        super.removed();
    }
}
