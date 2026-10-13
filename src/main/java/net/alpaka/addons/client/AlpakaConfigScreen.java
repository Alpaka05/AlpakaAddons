package net.alpaka.addons.client;

import net.alpaka.addons.client.gui.ConfigOption;
import net.alpaka.addons.client.gui.ConfigTree;
import net.alpaka.addons.client.gui.EditableText;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.client.gui.PloppAnimation;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.alpaka.addons.utils.ModVersion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
public class AlpakaConfigScreen extends Screen {

    // ---- Colours --------------------------------------------------------------------------------

    private static final int COLOR_BACKDROP = 0x80000000;
    private static final int COLOR_PANEL = 0xFF131313;
    private static final int COLOR_LINE = 0xFF262626;
    private static final int COLOR_DIVIDER = 0xFF3A3A3A;
    private static final int COLOR_ROW = 0xFF1E1E1E;
    private static final int COLOR_ROW_HOVER = 0xFF242424;
    private static final int COLOR_ROW_SELECTED = 0xFF2A2A2A;
    private static final int COLOR_FIELD = 0xFF1F1F1F;
    /** Behind a preview: the dark of the chat's own background. */
    private static final int COLOR_PREVIEW = 0xFF0D0D0D;
    private static final int COLOR_TEXT = 0xFFEDEDED;
    private static final int COLOR_TEXT_SOFT = 0xFFBDBDBD;
    private static final int COLOR_TEXT_MUTED = 0xFF7C7C7C;
    private static final int COLOR_TRACK = 0xFF3A3A3A;
    private static final int COLOR_DANGER = 0xFFE5534B;

    // ---- Layout, in GUI pixels ------------------------------------------------------------------

    private static final int HEADER_H = 30;
    private static final int PAD = 8;
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
    private static final long TOOLTIP_DELAY_MS = 450L;
    private static final long CONFIRM_WINDOW_MS = 3000L;
    private static final String CONFIRM_LABEL = "Click again";

    // ---- State ----------------------------------------------------------------------------------

    private final Screen parent;
    private final EditableText search = new EditableText(MAX_SEARCH_LENGTH);
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
    private ConfigOption armedOption = null;
    private long armedUntilMs = 0L;

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

    /** The setting under the mouse, and since when, for its tooltip. */
    private ConfigOption hoveredSetting = null;
    private long hoveredSince = 0L;

    /** Clickable areas of the last frame, front-most last. */
    private final List<Hit> hits = new ArrayList<>();

    private interface ClickAction {
        void run(double mouseX, double mouseY);
    }

    private record Hit(int x, int y, int w, int h, ClickAction action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
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
        openTimeMs = System.currentTimeMillis();
        lastFrameMs = openTimeMs;
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

    /** The features the middle column lists: the section's, or every match while searching. */
    private List<ConfigTree.Feature> listedFeatures() {
        if (!searching()) return selectedSection == null ? List.of() : selectedSection.features;
        String q = query();
        List<ConfigTree.Feature> found = new ArrayList<>();
        for (ConfigTree.Group group : ConfigTree.groups()) {
            for (ConfigTree.Section section : group.sections) {
                for (ConfigTree.Feature feature : section.features) {
                    if (feature.matches(q)) found.add(feature);
                }
            }
        }
        return found;
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

    private int fullSidebarW() {
        return Math.max(84, Math.min(120, (int) (fullPanelW() * 0.21)));
    }

    private int fullFeatureColumnW() {
        return (int) ((fullPanelW() - fullSidebarW()) * 0.42);
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

        // Against an identity matrix: other GUI mods transform Screen's render calls for their own
        // open animations, and the backdrop must not slide along with them.
        graphics.pose().pushMatrix();
        graphics.pose().identity();
        // In setup mode the world stays undimmed: it is what is being adjusted.
        int backdropAlpha = Math.round(((COLOR_BACKDROP >>> 24) & 0xFF) * (1.0f - setupProgress));
        if (backdropAlpha > 0) graphics.fill(0, 0, this.width, this.height, backdropAlpha << 24);
        graphics.pose().popMatrix();

        float openScale = PloppAnimation.getOpenScale(openTimeMs);
        boolean scaling = openScale < 0.999f;
        if (scaling) {
            graphics.pose().pushMatrix();
            graphics.pose().scaleAround(openScale, openScale, this.width / 2.0f, this.height / 2.0f);
        }

        int x = panelX();
        int y = panelY();
        int w = panelW();
        int h = panelH();
        ModernGuiUtils.drawPanelShadow(graphics, x, y, w, h, ModernGuiUtils.PANEL_RADIUS, 1.0f);
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, ModernGuiUtils.PANEL_RADIUS, COLOR_PANEL);

        int sideW = sidebarW();
        int featW = featureColumnW();
        int featX = x + sideW;
        int detailX = featX + featW;
        int detailW = x + w - detailX;
        int bodyY = y + HEADER_H;
        int bodyH = h - HEADER_H;

        hoveredSettingThisFrame = null;

        drawHeader(graphics, mouseX, mouseY, x, y, w, sideW, featX, featW, detailX, detailW);
        ModernGuiUtils.drawRect(graphics, x + PAD, bodyY - 1, w - PAD * 2, 1, COLOR_LINE);
        // Folding away in setup mode: clipped to their shrinking widths, and gone once folded.
        if (setupProgress < 0.999f) {
            graphics.enableScissor(x, bodyY, detailX, y + h);
            drawSidebar(graphics, mouseX, mouseY, x, bodyY, sideW, bodyH, deltaSec);
            drawFeatures(graphics, mouseX, mouseY, featX, bodyY, featW, bodyH, deltaSec);
            graphics.disableScissor();
            ModernGuiUtils.drawRect(graphics, detailX, bodyY + 4, 1, bodyH - 8, COLOR_DIVIDER);
        }
        drawDetails(graphics, mouseX, mouseY, detailX + 1, bodyY, detailW - 1, bodyH, deltaSec);

        if (hoveredSettingThisFrame != hoveredSetting) {
            hoveredSetting = hoveredSettingThisFrame;
            hoveredSince = now;
        }
        if (hoveredSetting != null && now - hoveredSince >= TOOLTIP_DELAY_MS && draggedSlider == null) {
            drawTooltip(graphics, mouseX, mouseY, hoveredSetting.getDescription());
        }

        if (scaling) graphics.pose().popMatrix();
    }

    private ConfigOption hoveredSettingThisFrame = null;

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
            if (hoverLeft) ModernGuiUtils.drawRoundedRect(graphics, leftX, ay, arrow, arrow, 3, COLOR_ROW_SELECTED);
            if (hoverRight) ModernGuiUtils.drawRoundedRect(graphics, rightX, ay, arrow, arrow, 3, COLOR_ROW_SELECTED);
            drawSideChevron(graphics, leftX + arrow / 2, ay + arrow / 2, true, hoverLeft ? ModernGuiUtils.getAccentColor() : COLOR_TEXT_SOFT);
            drawSideChevron(graphics, rightX + arrow / 2, ay + arrow / 2, false, hoverRight ? ModernGuiUtils.getAccentColor() : COLOR_TEXT_SOFT);
            hits.add(new Hit(leftX, ay, arrow, arrow, (mx, my) -> stepFeature(-1)));
            hits.add(new Hit(rightX, ay, arrow, arrow, (mx, my) -> stepFeature(1)));
            if (selectedFeature != null) {
                int from = leftX + arrow + 2;
                int room = rightX - 2 - from;
                String name = ellipsize(selectedFeature.title, room);
                int nameW = GuiFont.width(this.font, name);
                graphics.text(this.font, GuiFont.text(name), from + (room - nameW) / 2, y + (HEADER_H - 8) / 2, COLOR_TEXT, false);
            }
        } else if (selectedFeature != null) {
            int room = detailW - PAD * 2 - 4 - (setupButton ? SETUP_BTN_W + 6 : 0);
            String name = ellipsize(selectedFeature.title, room);
            graphics.text(this.font, GuiFont.text(name), detailX + PAD + 2, y + (HEADER_H - 8) / 2, COLOR_TEXT, false);
        }
        if (setupButton) {
            int bx = detailX + detailW - PAD - SETUP_BTN_W;
            int by = y + (HEADER_H - net.alpaka.addons.client.gui.AlpakaStyle.BUTTON_H) / 2;
            boolean hover = inside(mouseX, mouseY, bx, by, SETUP_BTN_W, net.alpaka.addons.client.gui.AlpakaStyle.BUTTON_H);
            net.alpaka.addons.client.gui.AlpakaStyle.button(graphics, this.font, bx, by, SETUP_BTN_W,
                    net.alpaka.addons.client.gui.AlpakaStyle.BUTTON_H, setupMode ? "Back" : "Setup", hover,
                    setupMode ? net.alpaka.addons.client.gui.AlpakaStyle.ButtonKind.PRIMARY
                            : net.alpaka.addons.client.gui.AlpakaStyle.ButtonKind.PLAIN);
            hits.add(new Hit(bx, by, SETUP_BTN_W, net.alpaka.addons.client.gui.AlpakaStyle.BUTTON_H,
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

    private void drawLogoAndSearch(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int featX, int featW) {
        // Logo: the name large, the version small beneath it.
        Component title = GuiFont.title("ALPAKA");
        if (title == null) title = GuiFont.text("ALPAKA");
        graphics.text(this.font, title, x + PAD + 2, y + 5, COLOR_TEXT, false);
        graphics.text(this.font, GuiFont.text("v" + ModVersion.mod().split("\\+")[0]), x + PAD + 2, y + 19, COLOR_TEXT_MUTED, false);

        // Search: a pill over the feature column.
        int sx = featX + 4;
        int sw = featW - 8;
        int sh = 18;
        int sy = y + (HEADER_H - sh) / 2;
        boolean hover = inside(mouseX, mouseY, sx, sy, sw, sh);
        ModernGuiUtils.drawRoundedRect(graphics, sx, sy, sw, sh, sh / 2, COLOR_FIELD);
        if (searchFocused) ModernGuiUtils.drawRoundedOutline(graphics, sx, sy, sw, sh, sh / 2, ModernGuiUtils.getAccentDimColor());
        else if (hover) ModernGuiUtils.drawRoundedOutline(graphics, sx, sy, sw, sh, sh / 2, COLOR_LINE);

        int tx = sx + 9;
        int ty = sy + (sh - 8) / 2;
        int iconX = sx + sw - 16;
        if (search.isEmpty() && !searchFocused) {
            graphics.text(this.font, GuiFont.text("Search..."), tx, ty, COLOR_TEXT_MUTED, false);
        } else {
            String text = search.getText();
            if (searchFocused && search.hasSelection()) {
                int from = tx + GuiFont.width(this.font, text.substring(0, search.getSelectionStart()));
                int to = tx + GuiFont.width(this.font, text.substring(0, search.getSelectionEnd()));
                ModernGuiUtils.drawRect(graphics, from, ty - 1, to - from, 10, ModernGuiUtils.getAccentDimColor());
            }
            graphics.text(this.font, GuiFont.text(text), tx, ty, COLOR_TEXT, false);
            if (searchFocused && (System.currentTimeMillis() - caretBlinkFrom) / 500 % 2 == 0) {
                int caretX = tx + GuiFont.width(this.font, text.substring(0, search.getCaret()));
                ModernGuiUtils.drawRect(graphics, caretX, ty - 1, 1, 10, COLOR_TEXT);
            }
        }
        // The field first, the clear button after: the later hit is the one on top.
        hits.add(new Hit(sx, sy, sw, sh, (mx, my) -> {
            searchFocused = true;
            caretBlinkFrom = System.currentTimeMillis();
            search.setCaretFromX((int) mx - tx, s -> GuiFont.width(this.font, s));
        }));
        if (!search.isEmpty()) {
            boolean hoverClear = inside(mouseX, mouseY, iconX - 2, sy, 14, sh);
            graphics.text(this.font, GuiFont.text("✕"), iconX + 1, ty, hoverClear ? ModernGuiUtils.getAccentColor() : COLOR_TEXT_MUTED, false);
            hits.add(new Hit(iconX - 2, sy, 14, sh, (mx, my) -> {
                search.setText("");
                onSearchChanged();
            }));
        } else {
            drawSearchIcon(graphics, iconX + 2, sy + 4, searchFocused ? ModernGuiUtils.getAccentColor() : COLOR_TEXT_MUTED);
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
            graphics.text(this.font, GuiFont.text(group.title), x + PAD, rowY + 2, COLOR_TEXT_MUTED, false);
            rowY += GROUP_LABEL_H;
            for (ConfigTree.Section section : group.sections) {
                boolean selected = !searching() && section == selectedSection;
                boolean hover = inside(mouseX, mouseY, x, rowY, w, SECTION_ROW_H) && inside(mouseX, mouseY, x, clipY, w, clipH);
                int color = selected ? ModernGuiUtils.getAccentColor() : hover ? COLOR_TEXT : COLOR_TEXT_SOFT;
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
                ModernGuiUtils.drawRect(graphics, x + PAD, rowY + 4, w - PAD * 2, 1, COLOR_LINE);
                rowY += 9;
            }
        }
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
            graphics.text(this.font, GuiFont.text(ellipsize(empty, rowW - 8)), rowX + 6, clipY + 6, COLOR_TEXT_MUTED, false);
        }
        int rowY = clipY + 2 - featureScroll.offset();
        for (ConfigTree.Feature feature : features) {
            if (visible(rowY, FEATURE_ROW_H, clipY, clipH)) {
                boolean selected = feature == selectedFeature;
                boolean hover = inside(mouseX, mouseY, rowX, rowY, rowW, FEATURE_ROW_H) && inside(mouseX, mouseY, x, clipY, w, clipH);
                int background = selected ? COLOR_ROW_SELECTED : hover ? COLOR_ROW_HOVER : COLOR_ROW;
                ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, FEATURE_ROW_H, 5, background);

                // The dots keep their place whether drawn or not, so every switch lines up.
                int right = rowX + rowW - 6;
                if (!feature.panelOptions().isEmpty()) {
                    drawDots(graphics, right - 1, rowY + (FEATURE_ROW_H - 7) / 2, selected ? COLOR_TEXT_SOFT : COLOR_TEXT_MUTED);
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

                int titleColor = selected ? ModernGuiUtils.getAccentColor() : COLOR_TEXT;
                int titleW = (feature.hasToggle() ? toggleX : right) - (rowX + 8) - 6;
                graphics.text(this.font, GuiFont.text(ellipsize(feature.title, titleW)), rowX + 8, rowY + (FEATURE_ROW_H - 8) / 2, titleColor, false);
            }
            rowY += FEATURE_ROW_H + FEATURE_ROW_GAP;
        }
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
            if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, SETTING_ROW_H, 4, COLOR_ROW);
            graphics.text(this.font, GuiFont.text("Description"), rowX + 4, rowY + 4, COLOR_TEXT, false);
            drawChevron(graphics, rowX + rowW - 8, rowY + 8, descriptionOpen, COLOR_TEXT_SOFT);
            if (visible(rowY, SETTING_ROW_H, clipY, clipH)) {
                hits.add(new Hit(rowX, Math.max(rowY, clipY), rowW, SETTING_ROW_H, (mx, my) -> descriptionOpen = !descriptionOpen));
            }
            rowY += SETTING_ROW_H;
            if (descriptionOpen) {
                for (FormattedText line : GuiFont.splitLines(this.font, description, rowW - 8)) {
                    graphics.text(this.font, Language.getInstance().getVisualOrder(line), rowX + 4, rowY + 1, COLOR_TEXT_MUTED, false);
                    rowY += LINE_H + 1;
                }
                rowY += 5;
            }
        }

        List<ConfigOption> options = feature.panelOptions();
        if (options.isEmpty()) {
            graphics.text(this.font, GuiFont.text("No further settings."), rowX + 4, rowY + 4, COLOR_TEXT_MUTED, false);
            rowY += SETTING_ROW_H;
        }
        for (ConfigOption option : options) {
            rowY = drawSetting(graphics, mouseX, mouseY, option, rowX, rowY, rowW, x, w, clipY, clipH, deltaSec);
        }

        detailScroll.max = Math.max(0, (rowY + detailScroll.offset()) - startY + 6 - clipH);
        detailScroll.update(deltaSec);
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
        if (hover) hoveredSettingThisFrame = option;
        int labelY = rowY + 4;
        int right = rowX + rowW - 4;
        int hitY = Math.max(rowY, clipY);

        switch (option.getType()) {
            case BOOLEAN -> {
                if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, rowH, 4, COLOR_ROW);
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
                graphics.text(this.font, GuiFont.text(value), right - valueW, labelY, COLOR_TEXT, false);
                int trackX = rowX + 4;
                int trackW = rowW - 8;
                int trackY = rowY + 17;
                int knobX = trackX + (int) Math.round(option.getSliderNormalizedValue() * trackW);
                ModernGuiUtils.drawRect(graphics, trackX, trackY, trackW, 1, COLOR_TRACK);
                ModernGuiUtils.drawRect(graphics, trackX, trackY, knobX - trackX, 1, ModernGuiUtils.getAccentColor());
                boolean dragging = draggedSlider == option;
                int knob = hover || dragging ? 7 : 6;
                ModernGuiUtils.drawRoundedRect(graphics, knobX - knob / 2, trackY - knob / 2, knob, knob, knob / 2, ModernGuiUtils.getAccentColor());
                if (shown) {
                    hits.add(new Hit(rowX, hitY, rowW, rowH, (mx, my) -> {
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
                    if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, rowH, 4, COLOR_ROW);
                    drawLabel(graphics, option.getTitle(), rowX + 4, labelY, right - SWATCH_W - 8 - (rowX + 4));
                    int sy = rowY + (rowH - SWATCH_H) / 2;
                    ModernGuiUtils.drawRoundedRect(graphics, right - SWATCH_W, sy, SWATCH_W, SWATCH_H, 2, 0xFF000000);
                    ModernGuiUtils.drawRoundedRect(graphics, right - SWATCH_W + 1, sy + 1, SWATCH_W - 2, SWATCH_H - 2, 2, option.getColor());
                    if (shown) hits.add(new Hit(rowX, hitY, rowW, rowH, (mx, my) -> option.triggerAction(this)));
                } else {
                    boolean armed = armedOption == option && System.currentTimeMillis() < armedUntilMs;
                    boolean danger = option.getId().equals("disable_all_features");
                    String label = armed ? CONFIRM_LABEL : option.getTitle();
                    int color = hover || armed ? (danger ? COLOR_DANGER : ModernGuiUtils.getAccentColor()) : danger ? 0xFFC9726C : COLOR_TEXT;
                    if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, rowH, 4, COLOR_ROW);
                    String text = ellipsize(label, rowW - 8);
                    graphics.text(this.font, GuiFont.text(text), rowX + (rowW - GuiFont.width(this.font, text)) / 2, rowY + (rowH - 8) / 2, color, false);
                    if (shown) {
                        hits.add(new Hit(rowX, hitY, rowW, rowH, (mx, my) -> {
                            if (option.isConfirmFirst() && !(armedOption == option && System.currentTimeMillis() < armedUntilMs)) {
                                armedOption = option;
                                armedUntilMs = System.currentTimeMillis() + CONFIRM_WINDOW_MS;
                                return;
                            }
                            armedOption = null;
                            option.triggerAction(this);
                        }));
                    }
                }
            }
            case DROPDOWN -> {
                boolean open = !collapsed.contains(option);
                if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, rowH, 4, COLOR_ROW);
                String count = option.getEnabledEntryCount() + "/" + option.getEntryCount();
                int countW = GuiFont.width(this.font, count);
                drawLabel(graphics, option.getTitle(), rowX + 4, labelY, right - countW - 20 - (rowX + 4));
                graphics.text(this.font, GuiFont.text(count), right - 12 - countW, labelY, COLOR_TEXT_MUTED, false);
                drawChevron(graphics, right - 4, rowY + 8, open, COLOR_TEXT_SOFT);
                if (shown) {
                    hits.add(new Hit(rowX, hitY, rowW, rowH, (mx, my) -> {
                        if (!collapsed.remove(option)) collapsed.add(option);
                    }));
                }
                if (open) {
                    int entryY = rowY + rowH;
                    for (ConfigOption.ToggleEntry entry : option.getEntries()) {
                        boolean entryHover = inside(mouseX, mouseY, rowX, entryY, rowW, SETTING_ROW_H) && inside(mouseX, mouseY, colX, clipY, colW, clipH);
                        if (entryHover) ModernGuiUtils.drawRoundedRect(graphics, rowX, entryY, rowW, SETTING_ROW_H, 4, COLOR_ROW);
                        ModernGuiUtils.drawRect(graphics, rowX + 6, entryY + 2, 1, SETTING_ROW_H - 4, COLOR_LINE);
                        int entryLabelW = right - TOGGLE_W - 8 - (rowX + 14);
                        graphics.text(this.font, GuiFont.text(ellipsize(entry.getLabel(), entryLabelW)), rowX + 14, entryY + 4,
                                entry.get() ? COLOR_TEXT_SOFT : COLOR_TEXT_MUTED, false);
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
                graphics.text(this.font, GuiFont.text(option.getTitle()), rowX + 4, rowY + 2, COLOR_TEXT_MUTED, false);
                int boxY = rowY + PREVIEW_LABEL_H;
                int boxH = rowH - PREVIEW_LABEL_H - 2;
                ModernGuiUtils.drawRoundedRect(graphics, rowX, boxY, rowW, boxH, 4, COLOR_PREVIEW);
                int lineY = boxY + PREVIEW_PAD;
                for (net.minecraft.util.FormattedCharSequence line : previewLines) {
                    graphics.text(this.font, line, rowX + PREVIEW_PAD, lineY, 0xFFFFFFFF, true);
                    lineY += LINE_H;
                }
            }
            case TEXT -> {
                if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, rowH, 4, COLOR_ROW);
                int fieldW = Math.min(TEXT_FIELD_W, rowW / 2);
                int fieldX = right - fieldW;
                drawLabel(graphics, option.getTitle(), rowX + 4, labelY, fieldX - 8 - (rowX + 4));
                boolean focused = focusedText == option;
                ModernGuiUtils.drawRoundedRect(graphics, fieldX, rowY + 2, fieldW, rowH - 4, 3, COLOR_FIELD);
                ModernGuiUtils.drawRect(graphics, fieldX + 3, rowY + rowH - 3, fieldW - 6, 1,
                        focused ? ModernGuiUtils.getAccentColor() : COLOR_LINE);
                String value = option.getText();
                boolean empty = value.isEmpty();
                String shownText = ellipsize(empty && !focused ? option.getPlaceholder() : value, fieldW - 8);
                graphics.text(this.font, GuiFont.text(shownText), fieldX + 4, labelY, empty ? COLOR_TEXT_MUTED : COLOR_TEXT, false);
                if (focused && (System.currentTimeMillis() - caretBlinkFrom) / 500 % 2 == 0) {
                    int caretX = fieldX + 4 + Math.min(fieldW - 8, GuiFont.width(this.font, value));
                    ModernGuiUtils.drawRect(graphics, caretX, labelY - 1, 1, 10, COLOR_TEXT);
                }
                if (shown) {
                    hits.add(new Hit(rowX, hitY, rowW, rowH, (mx, my) -> {
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
        int fieldW = Math.min(CHOICE_FIELD_W, rowW / 2);
        int fieldX = right - fieldW;
        if (hover) ModernGuiUtils.drawRoundedRect(graphics, rowX, rowY, rowW, rowH, 4, COLOR_ROW);
        drawLabel(graphics, option.getTitle(), rowX + 4, rowY + 4, fieldX - 8 - (rowX + 4));
        ModernGuiUtils.drawRoundedRect(graphics, fieldX, rowY + 2, fieldW, rowH - 4, 3, COLOR_FIELD);
        String value = ellipsize(names[current], fieldW - 16);
        graphics.text(this.font, GuiFont.text(value), fieldX + 4, rowY + 4, COLOR_TEXT, false);
        drawChevron(graphics, fieldX + fieldW - 6, rowY + rowH / 2, open, COLOR_TEXT_SOFT);
        if (visible(rowY, rowH, clipY, clipH)) {
            hits.add(new Hit(rowX, Math.max(rowY, clipY), rowW, rowH, (mx, my) -> openChoice = open ? null : option));
        }
        int y = rowY + rowH;
        if (!open) return y;

        for (int i = 0; i < names.length; i++) {
            int index = i;
            boolean entryHover = inside(mouseX, mouseY, rowX, y, rowW, SETTING_ROW_H) && inside(mouseX, mouseY, colX, clipY, colW, clipH);
            if (entryHover) ModernGuiUtils.drawRoundedRect(graphics, rowX, y, rowW, SETTING_ROW_H, 4, COLOR_ROW);
            ModernGuiUtils.drawRect(graphics, rowX + 6, y + 2, 1, SETTING_ROW_H - 4, i == current ? ModernGuiUtils.getAccentColor() : COLOR_LINE);
            int color = i == current ? ModernGuiUtils.getAccentColor() : entryHover ? COLOR_TEXT : COLOR_TEXT_SOFT;
            graphics.text(this.font, GuiFont.text(ellipsize(names[i], rowW - 24)), rowX + 14, y + 4, color, false);
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
        graphics.text(this.font, GuiFont.text(ellipsize(label, maxW)), x, y, COLOR_TEXT_SOFT, false);
    }

    /**
     * A slim switch: a rounded outline with a dot that slides across, grey when off and in the
     * accent colour when on.
     */
    private static void drawToggle(GuiGraphicsExtractor graphics, int x, int y, float progress) {
        int accent = ModernGuiUtils.getAccentColor();
        int outline = ModernGuiUtils.lerpColor(0xFF6E6E6E, accent, progress);
        int knob = ModernGuiUtils.lerpColor(0xFF8A8A8A, accent, progress);
        ModernGuiUtils.drawRoundedRect(graphics, x, y, TOGGLE_W, TOGGLE_H, TOGGLE_H / 2,
                ModernGuiUtils.lerpColor(0x00000000, (accent & 0x00FFFFFF) | 0x26000000, progress));
        ModernGuiUtils.drawRoundedOutline(graphics, x, y, TOGGLE_W, TOGGLE_H, TOGGLE_H / 2, outline);
        int size = TOGGLE_H - 4;
        int travel = TOGGLE_W - 4 - size;
        int kx = x + 2 + Math.round(travel * progress);
        ModernGuiUtils.drawRoundedRect(graphics, kx, y + 2, size, size, size / 2, knob);
    }

    /**
     * A small chevron, pointing down when open and right when closed: two smooth strokes as quads,
     * so the diagonals stay clean at every GUI scale instead of stepping pixel by pixel.
     */
    private void drawChevron(GuiGraphicsExtractor graphics, int cx, int cy, boolean open, int color) {
        int guiScale = this.minecraft == null ? 2 : (int) Math.max(1, this.minecraft.getWindow().getGuiScale());
        net.alpaka.addons.features.wheel.WheelMesh mesh = new net.alpaka.addons.features.wheel.WheelMesh(1.0f / guiScale);
        float s = 2.5f;
        float t = 1.1f;
        if (open) {
            stroke(mesh, cx - s, cy - s / 2, cx, cy + s / 2, t, color);
            stroke(mesh, cx, cy + s / 2, cx + s, cy - s / 2, t, color);
        } else {
            stroke(mesh, cx - s / 2, cy - s, cx + s / 2, cy, t, color);
            stroke(mesh, cx + s / 2, cy, cx - s / 2, cy + s, t, color);
        }
        mesh.submit(graphics);
    }

    /** A chevron pointing left or right, a little larger than the dropdowns' one, for stepping. */
    private void drawSideChevron(GuiGraphicsExtractor graphics, int cx, int cy, boolean left, int color) {
        int guiScale = this.minecraft == null ? 2 : (int) Math.max(1, this.minecraft.getWindow().getGuiScale());
        net.alpaka.addons.features.wheel.WheelMesh mesh = new net.alpaka.addons.features.wheel.WheelMesh(1.0f / guiScale);
        float s = 3.0f;
        float t = 1.2f;
        float d = left ? -1 : 1;
        stroke(mesh, cx - d * s / 2, cy - s, cx + d * s / 2, cy, t, color);
        stroke(mesh, cx + d * s / 2, cy, cx - d * s / 2, cy + s, t, color);
        mesh.submit(graphics);
    }

    /** A straight stroke of the given thickness, with square ends half a thickness past each point. */
    private static void stroke(net.alpaka.addons.features.wheel.WheelMesh mesh, float x0, float y0, float x1, float y1, float thickness, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len == 0) return;
        float ux = dx / len * thickness / 2;
        float uy = dy / len * thickness / 2;
        float nx = -uy;
        float ny = ux;
        mesh.quad(x0 - ux + nx, y0 - uy + ny, color, x1 + ux + nx, y1 + uy + ny, color,
                x1 + ux - nx, y1 + uy - ny, color, x0 - ux - nx, y0 - uy - ny, color);
    }

    private void drawTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, String text) {
        if (text == null || text.isBlank()) return;
        int maxW = 170;
        List<FormattedText> lines = GuiFont.splitLines(this.font, text, maxW);
        int w = 0;
        for (FormattedText line : lines) w = Math.max(w, this.font.width(line));
        int h = lines.size() * (LINE_H + 1) + 6;
        w += 10;
        int x = Math.min(mouseX + 10, this.width - w - 4);
        int y = Math.min(mouseY + 10, this.height - h - 4);
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, 4, 0xF01A1A1A);
        ModernGuiUtils.drawRoundedOutline(graphics, x, y, w, h, 4, COLOR_DIVIDER);
        int ly = y + 4;
        for (FormattedText line : lines) {
            graphics.text(this.font, Language.getInstance().getVisualOrder(line), x + 5, ly, COLOR_TEXT_SOFT, false);
            ly += LINE_H + 1;
        }
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static boolean visible(int y, int h, int clipY, int clipH) {
        return y + h > clipY && y < clipY + clipH;
    }

    /** The text cut to fit the width, with an ellipsis where it was cut. */
    private String ellipsize(String text, int maxW) {
        if (text == null) return "";
        if (maxW <= 0) return "";
        if (GuiFont.width(this.font, text) <= maxW) return text;
        String dots = "…";
        int end = text.length();
        while (end > 0 && GuiFont.width(this.font, text.substring(0, end) + dots) > maxW) end--;
        return text.substring(0, end).trim() + dots;
    }

    private void playClick() {
        try {
            CustomSoundFeature.playButtonClickSound();
        } catch (Throwable ignored) {
        }
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
                playClick();
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
