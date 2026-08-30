package net.alpaka.addons.client;

import net.alpaka.addons.client.gui.AlpakaConfigRegistry;
import net.alpaka.addons.client.gui.ConfigCategory;
import net.alpaka.addons.client.gui.ConfigOption;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.client.gui.PloppAnimation;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.alpaka.addons.utils.ModVersion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class AlpakaConfigScreen extends Screen {
    private final Screen parent;
    private ConfigCategory activeCategory = ConfigCategory.GENERAL;

    // Search state
    private String searchQuery = "";
    private boolean searchFocused = false;

    /** The inline text option currently accepting keystrokes, or null. */
    private ConfigOption focusedTextOption = null;
    private long cursorBlinkTimer = 0L;

    // Scrolling & Layout
    private double scrollY = 0.0;
    private double targetScrollY = 0.0;
    private double maxScrollY = 0.0;

    private double sidebarScrollY = 0.0;
    private double targetSidebarScrollY = 0.0;
    private double maxSidebarScrollY = 0.0;

    // Smooth panel horizontal shift animation for Viewmodel live hand preview
    private double currentWinX = -1.0;

    private long openTimeMs = 0L;
    private long lastFrameTime = System.currentTimeMillis();

    // Currently dragged slider option
    private ConfigOption draggedOption = null;

    public AlpakaConfigScreen(Screen parent) {
        this(parent, "");
    }

    /**
     * Opens with the search box already filled, for {@code /aa <term>}.
     *
     * The General tab holds only a handful of settings, so a term typed at the command line would
     * almost always come up empty there. ensureValidActiveCategory moves to the first tab that has
     * a hit, which is what makes the term land on a result rather than on an empty General page.
     */
    public AlpakaConfigScreen(Screen parent, String initialSearch) {
        super(Component.literal("Alpaka Addons Config"));
        this.parent = parent;
        this.searchQuery = initialSearch == null ? "" : initialSearch.trim();
        if (!this.searchQuery.isEmpty()) {
            ensureValidActiveCategory();
        }
    }

    /** The visible-category list, and the search term it was built for. */
    private List<ConfigCategory> cachedVisibleCategories = null;
    private String cachedVisibleCategoriesQuery = null;

    /**
     * The categories worth showing in the sidebar, cached against the search term.
     *
     * Rendering asks for this twice per frame and the click handler once more, and each pass used to
     * walk and count every category's options - with the filtering underneath it re-running too.
     * The answer only changes on a keystroke.
     */
    private List<ConfigCategory> getVisibleCategories() {
        if (cachedVisibleCategories != null && searchQuery.equals(cachedVisibleCategoriesQuery)) {
            return cachedVisibleCategories;
        }

        List<ConfigCategory> list = new ArrayList<>();
        for (ConfigCategory cat : ConfigCategory.values()) {
            if (searchQuery.isEmpty() || AlpakaConfigRegistry.countOptions(cat, searchQuery) > 0) {
                list.add(cat);
            }
        }

        cachedVisibleCategories = list;
        cachedVisibleCategoriesQuery = searchQuery;
        return list;
    }

    private void ensureValidActiveCategory() {
        List<ConfigCategory> visible = getVisibleCategories();
        if (!visible.isEmpty() && !visible.contains(activeCategory)) {
            activeCategory = visible.get(0);
            targetScrollY = 0;
            scrollY = 0;
        }
    }

    /** Feature card height. Shared so every layout pass agrees on where each row starts. */
    private static final int CARD_H = 44;

    /** Height of one tickable line inside an expanded dropdown. */
    private static final int DROPDOWN_ENTRY_H = 18;

    /** Gap between a dropdown's card and its first line, and below its last. */
    private static final int DROPDOWN_TOP_PAD = 2;
    private static final int DROPDOWN_BOTTOM_PAD = 6;

    private static final int CHECKBOX_SIZE = 11;

    /** Cap on an inline text option's value, long enough for any account name. */
    private static final int MAX_TEXT_OPTION_LENGTH = 32;

    /**
     * Vertical space one option occupies, expansion included.
     *
     * Every pass over the list - measuring total scroll height, drawing, and hit-testing clicks -
     * goes through this. They previously each inlined the same arithmetic, which is exactly the kind
     * of duplication that lets a dropdown draw in one place and be clickable in another.
     */
    /**
     * Height of the heading above a category's options.
     *
     * Shared rather than written out at each site. Drawing skipped it for one category while
     * hit-testing added it unconditionally, so on that tab every click landed on the option above
     * the one under the cursor.
     */
    private static final int CATEGORY_HEADER_H = 30;

    private static int optionItemHeight(ConfigOption opt) {
        if (opt.getType() == ConfigOption.Type.HEADER) return 30;
        int height = CARD_H + 6;
        if (opt.getType() == ConfigOption.Type.DROPDOWN && opt.isExpanded()) {
            height += DROPDOWN_TOP_PAD + opt.getEntryCount() * DROPDOWN_ENTRY_H + DROPDOWN_BOTTOM_PAD;
        }
        return height;
    }

    @Override
    protected void init() {
        this.openTimeMs = System.currentTimeMillis();
        this.lastFrameTime = System.currentTimeMillis();
        this.currentWinX = -1.0;
    }

    private void playPloppSound() {
        try {
            CustomSoundFeature.playButtonClickSound();
        } catch (Throwable ignored) {}
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        float deltaSec = Math.min(0.1f, (now - lastFrameTime) / 1000.0f);
        lastFrameTime = now;

        // Smooth scroll interpolation for main content and sidebar
        scrollY += (targetScrollY - scrollY) * Math.min(1.0f, deltaSec * 14.0f);
        sidebarScrollY += (targetSidebarScrollY - sidebarScrollY) * Math.min(1.0f, deltaSec * 14.0f);

        // Render translucent backdrop so game is visible behind and around the config panel.
        // Drawn against an identity matrix rather than whatever is already on the pose stack -
        // other installed GUI mods (SmoothGui and friends) apply their own open-transition
        // transform around Screen's render calls, and without this the fill inherited that
        // transform and slid along with it instead of staying still behind the panel.
        graphics.pose().pushMatrix();
        graphics.pose().identity();
        graphics.fill(0, 0, this.width, this.height, 0x70000000);
        graphics.pose().popMatrix();

        // Calculate Window Panel Dimensions (compact window leaving game visible around sides)
        int winW = Math.min(660, Math.max(480, (int) (this.width * 0.70)));
        int winH = Math.min(440, Math.max(340, (int) (this.height * 0.68)));

        int centerWinX = (this.width - winW) / 2;
        int sideWinX = Math.max(12, (this.width - winW) / 10);
        int targetWinX = (activeCategory == ConfigCategory.VIEWMODEL && searchQuery.isEmpty()) ? sideWinX : centerWinX;

        if (currentWinX < 0) {
            currentWinX = targetWinX;
        } else {
            currentWinX += (targetWinX - currentWinX) * Math.min(1.0f, deltaSec * 12.0f);
        }

        int winX = (int) Math.round(currentWinX);
        int winY = (this.height - winH) / 2;

        int headerHeight = 38;
        int sidebarWidth = 160;

        int contentX = winX + sidebarWidth;
        int contentY = winY + headerHeight;
        int contentW = winW - sidebarWidth;
        int contentH = winH - headerHeight;

        // Apply open plopp scale animation
        float openScale = PloppAnimation.getOpenScale(openTimeMs);
        boolean isAnimatingOpen = openScale < 0.999f;

        if (isAnimatingOpen) {
            graphics.pose().pushMatrix();
            graphics.pose().scaleAround(openScale, openScale, this.width / 2.0f, this.height / 2.0f);
        }

        // Multi-layered soft drop shadow around floating window panel for smooth edges & depth
        for (int i = 1; i <= 6; i++) {
            int shadowAlpha = (int) (0x24 * (1.0f - (float) i / 6.0f));
            ModernGuiUtils.drawRect(graphics, winX - i, winY - i, winW + i * 2, winH + i * 2, (shadowAlpha << 24));
        }
        ModernGuiUtils.drawOutline(graphics, winX - 1, winY - 1, winW + 2, winH + 2, 0x60000000);

        // 1. Outer Panel Base & Border
        ModernGuiUtils.drawRect(graphics, winX, winY, winW, winH, ModernGuiUtils.COLOR_PANEL_BG);
        ModernGuiUtils.drawOutline(graphics, winX, winY, winW, winH, ModernGuiUtils.COLOR_CARD_BORDER);

        // 2. Left Sidebar Background
        ModernGuiUtils.drawRect(graphics, winX, winY + headerHeight, sidebarWidth, winH - headerHeight, ModernGuiUtils.COLOR_SIDEBAR_BG);
        ModernGuiUtils.drawRect(graphics, winX + sidebarWidth - 1, winY + headerHeight, 1, winH - headerHeight, ModernGuiUtils.COLOR_CARD_BORDER);

        // Top Header Section & Title
        ModernGuiUtils.drawRect(graphics, winX, winY, winW, headerHeight, ModernGuiUtils.COLOR_PANEL_BG);
        ModernGuiUtils.drawRect(graphics, winX, winY + headerHeight - 1, winW, 1, ModernGuiUtils.getAccentColor());

        String mainTitle = "ALPAKA ADDONS";
        int titleWidth = this.font.width(mainTitle);
        graphics.text(this.font, Component.literal(mainTitle), winX + 12, winY + 12, ModernGuiUtils.COLOR_TEXT_PRIMARY);
        graphics.text(this.font, Component.literal("v" + ModVersion.mod()), winX + 12 + titleWidth + 6, winY + 12, ModernGuiUtils.COLOR_TEXT_MUTED);

        // Render Search Box
        int searchX = winX + winW - 170;
        int searchY = winY + 8;
        int searchW = 158;
        int searchH = 22;

        boolean isHoveringSearch = mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + searchH;
        int searchBg = ModernGuiUtils.COLOR_CARD_BG;
        int searchBorder = searchFocused ? ModernGuiUtils.getAccentColor() : (isHoveringSearch ? ModernGuiUtils.getAccentDimColor() : ModernGuiUtils.COLOR_CARD_BORDER);

        ModernGuiUtils.drawRect(graphics, searchX, searchY, searchW, searchH, searchBg);
        ModernGuiUtils.drawOutline(graphics, searchX, searchY, searchW, searchH, searchBorder);

        String displayText = searchQuery;
        if (searchQuery.isEmpty() && !searchFocused) {
            displayText = "🔍 Search...";
            graphics.text(this.font, Component.literal(displayText), searchX + 6, searchY + (searchH - 8) / 2, ModernGuiUtils.COLOR_TEXT_MUTED);
        } else {
            if (searchFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
                displayText += "|";
            }
            graphics.text(this.font, Component.literal(displayText), searchX + 6, searchY + (searchH - 8) / 2, ModernGuiUtils.COLOR_TEXT_PRIMARY);
        }

        // Search clear button 'X'
        if (!searchQuery.isEmpty()) {
            int clearX = searchX + searchW - 14;
            boolean hoverClear = mouseX >= clearX && mouseX <= clearX + 10 && mouseY >= searchY && mouseY <= searchY + searchH;
            graphics.text(this.font, Component.literal("✕"), clearX + 2, searchY + (searchH - 8) / 2, hoverClear ? ModernGuiUtils.getAccentColor() : ModernGuiUtils.COLOR_TEXT_MUTED);
        }
        // 4. Sidebar Categories with Scissor Clipping to keep categories strictly inside the panel
        ensureValidActiveCategory();
        List<ConfigCategory> categories = getVisibleCategories();
        int catItemH = 32;
        int catSpacing = 4;

        int sideClipX = winX + 4;
        int sideClipY = winY + headerHeight + 4;
        int sideClipW = sidebarWidth - 8;
        int sideClipH = winH - headerHeight - 8;

        int totalSidebarH = categories.size() * (catItemH + catSpacing) + 8;
        maxSidebarScrollY = Math.max(0, totalSidebarH - sideClipH);
        targetSidebarScrollY = Math.max(0, Math.min(maxSidebarScrollY, targetSidebarScrollY));

        graphics.enableScissor(sideClipX, sideClipY, sideClipX + sideClipW, sideClipY + sideClipH);

        int itemY = sideClipY + 4 - (int) sidebarScrollY;

        for (ConfigCategory cat : categories) {
            boolean isSelected = (activeCategory == cat);
            boolean isHovered = mouseX >= winX + 8 && mouseX <= winX + 8 + (sidebarWidth - 20) &&
                                mouseY >= itemY && mouseY <= itemY + catItemH &&
                                mouseY >= sideClipY && mouseY <= sideClipY + sideClipH;

            int catBg = isSelected ? ModernGuiUtils.COLOR_CARD_BG : (isHovered ? ModernGuiUtils.COLOR_CARD_BG_HOVER : 0x00000000);
            int catBorder = isSelected ? ModernGuiUtils.getAccentDimColor() : (isHovered ? ModernGuiUtils.COLOR_CARD_BORDER : 0x00000000);

            if (catBg != 0) {
                ModernGuiUtils.drawRect(graphics, winX + 8, itemY, sidebarWidth - 20, catItemH, catBg);
            }
            if (catBorder != 0) {
                ModernGuiUtils.drawOutline(graphics, winX + 8, itemY, sidebarWidth - 20, catItemH, catBorder);
            }

            // Category Label: Shift text right when selected
            int textX = winX + (isSelected ? 22 : 14);
            int labelColor = isSelected ? ModernGuiUtils.getAccentColor() : (isHovered ? ModernGuiUtils.COLOR_TEXT_PRIMARY : ModernGuiUtils.COLOR_TEXT_MUTED);
            graphics.text(this.font, Component.literal(cat.getDisplayName()), textX, itemY + (catItemH - 8) / 2, labelColor);

            // Category Settings Count Badge
            String countText = String.valueOf(AlpakaConfigRegistry.countOptions(cat, searchQuery));
            int countX = winX + 8 + (sidebarWidth - 20) - this.font.width(countText) - 10;
            int countColor = isSelected ? ModernGuiUtils.getAccentColor() : (isHovered ? ModernGuiUtils.COLOR_TEXT_MUTED : ModernGuiUtils.COLOR_TEXT_DARK);
            graphics.text(this.font, Component.literal(countText), countX, itemY + (catItemH - 8) / 2, countColor);

            if (isSelected) {
                ModernGuiUtils.drawRect(graphics, winX + 8, itemY, 3, catItemH, ModernGuiUtils.getAccentColor());
            }

            itemY += catItemH + catSpacing;
        }

        graphics.disableScissor();

        // 5. Render Options in Main Panel with Scissor Clipping to prevent scrolling overlap
        List<ConfigOption> options = AlpakaConfigRegistry.getOptions(activeCategory, searchQuery);

        int totalContentHeight = 40 + 20;
        for (ConfigOption opt : options) {
            totalContentHeight += optionItemHeight(opt);
        }
        maxScrollY = Math.max(0, totalContentHeight - contentH);
        targetScrollY = Math.max(0, Math.min(maxScrollY, targetScrollY));

        int clipY = contentY + 6;
        int clipH = contentH - 12;

        graphics.enableScissor(contentX, clipY, contentX + contentW, clipY + clipH);

        int startOptionY = contentY + 12 - (int) scrollY;

        // Render Category Header inside Content Area
        String catTitle = activeCategory.getHeading();
        if (!searchQuery.isEmpty()) {
            catTitle = "Search results for: \"" + searchQuery + "\"";
        }
        graphics.text(this.font, Component.literal(catTitle), contentX + 16, startOptionY, ModernGuiUtils.COLOR_TEXT_PRIMARY);
        graphics.text(this.font, Component.literal(activeCategory.getDescription()), contentX + 16, startOptionY + 13, ModernGuiUtils.COLOR_TEXT_MUTED);
        startOptionY += CATEGORY_HEADER_H;

        if (options.isEmpty()) {
            String emptyMsg = "No settings found for \"" + searchQuery + "\".";
            graphics.text(this.font, Component.literal(emptyMsg), contentX + 16, startOptionY + 12, ModernGuiUtils.COLOR_TOGGLE_OFF_TEXT);
        } else {
            int cardW = contentW - 28;
            int cardH = CARD_H;

            for (ConfigOption opt : options) {
                int itemHeight = optionItemHeight(opt);

                if (startOptionY + itemHeight >= clipY && startOptionY <= clipY + clipH) {
                    if (opt.getType() == ConfigOption.Type.HEADER) {
                        ModernGuiUtils.drawRect(graphics, contentX + 14, startOptionY + 8, cardW, 1, ModernGuiUtils.COLOR_CARD_BORDER);
                        graphics.text(this.font, Component.literal("• " + opt.getTitle().toUpperCase()), contentX + 14, startOptionY + 14, ModernGuiUtils.getAccentColor());
                    } else {
                        boolean isCardHovered = mouseX >= contentX + 14 && mouseX <= contentX + 14 + cardW &&
                                               mouseY >= startOptionY && mouseY <= startOptionY + cardH &&
                                               mouseY >= clipY && mouseY <= clipY + clipH;

                        opt.updateHoverProgress(isCardHovered, deltaSec);
                        opt.updateClickProgress(deltaSec);

                        float popScale = 1.0f + 0.015f * opt.getHoverProgress() - 0.015f * opt.getClickProgress();

                        graphics.pose().pushMatrix();
                        float cardCenterX = contentX + 14 + cardW / 2.0f;
                        float cardCenterY = startOptionY + cardH / 2.0f;
                        graphics.pose().scaleAround(popScale, popScale, cardCenterX, cardCenterY);

                        ModernGuiUtils.drawModernCard(graphics, contentX + 14, startOptionY, cardW, cardH, isCardHovered, false);

                        // Right Control Widgets (Compact sizing to fit smaller panel)
                        int widgetW = (opt.getType() == ConfigOption.Type.BOOLEAN) ? 32 :
                                      (opt.getType() == ConfigOption.Type.TEXT ? 120 :
                                      (opt.getType() == ConfigOption.Type.ACTION ? (opt.getId().contains("color") ? 38 : 80) : 90));
                        int widgetH = (opt.getType() == ConfigOption.Type.BOOLEAN) ? 15 : 18;
                        int widgetX = contentX + 14 + cardW - widgetW - 10;
                        int widgetY = startOptionY + (cardH - widgetH) / 2;

                        // Title & Description (Wrapped to maxDescW to prevent overlap with control widgets)
                        graphics.text(this.font, Component.literal(opt.getTitle()), contentX + 24, startOptionY + 8, ModernGuiUtils.COLOR_TEXT_PRIMARY);

                        String desc = opt.getDescription();
                        int maxDescW = widgetX - (contentX + 24) - 10;
                        List<net.minecraft.network.chat.FormattedText> descLines = this.font.getSplitter().splitLines(desc, maxDescW, net.minecraft.network.chat.Style.EMPTY);

                        if (descLines.size() > 1) {
                            for (int i = 0; i < Math.min(2, descLines.size()); i++) {
                                // Drawn through the visual order rather than as a plain string: a
                                // wrapped line carries the styles the splitter tracked across the
                                // break, and getString() would flatten them away. That is what lets
                                // a description colour a clause - a caveat, say - while the flat
                                // colour below still applies to everything unstyled.
                                graphics.text(this.font, net.minecraft.locale.Language.getInstance().getVisualOrder(descLines.get(i)),
                                        contentX + 24, startOptionY + 20 + i * 9, ModernGuiUtils.COLOR_TEXT_MUTED);
                            }
                        } else {
                            graphics.text(this.font, Component.literal(desc), contentX + 24, startOptionY + 22, ModernGuiUtils.COLOR_TEXT_MUTED);
                        }

                        if (opt.getType() == ConfigOption.Type.BOOLEAN) {
                            boolean isWidgetHovered = mouseX >= widgetX && mouseX <= widgetX + widgetW && mouseY >= widgetY && mouseY <= widgetY + widgetH && mouseY >= clipY && mouseY <= clipY + clipH;
                            ModernGuiUtils.drawModernToggle(graphics, this.font, widgetX, widgetY, widgetW, widgetH, opt.getBool(), isWidgetHovered);
                        } else if (opt.getType() == ConfigOption.Type.SLIDER) {
                            boolean isWidgetHovered = mouseX >= widgetX && mouseX <= widgetX + widgetW && mouseY >= widgetY && mouseY <= widgetY + widgetH && mouseY >= clipY && mouseY <= clipY + clipH;
                            double normVal = opt.getSliderNormalizedValue();
                            ModernGuiUtils.drawModernSlider(graphics, this.font, widgetX, widgetY, widgetW, widgetH, normVal, opt.getFormattedValue(), isWidgetHovered);
                        } else if (opt.getType() == ConfigOption.Type.DROPDOWN) {
                            boolean isWidgetHovered = mouseX >= widgetX && mouseX <= widgetX + widgetW && mouseY >= widgetY && mouseY <= widgetY + widgetH && mouseY >= clipY && mouseY <= clipY + clipH;
                            // Summary doubles as the affordance: how many lines are on, and which way
                            // the arrow points, so a collapsed dropdown still says what it contains.
                            String summary = opt.getEnabledEntryCount() + "/" + opt.getEntryCount() + (opt.isExpanded() ? " ▲" : " ▼");
                            ModernGuiUtils.drawModernButton(graphics, this.font, widgetX, widgetY, widgetW, widgetH, summary, isWidgetHovered, opt.isExpanded());
                        } else if (opt.getType() == ConfigOption.Type.TEXT) {
                            boolean isWidgetHovered = mouseX >= widgetX && mouseX <= widgetX + widgetW && mouseY >= widgetY && mouseY <= widgetY + widgetH && mouseY >= clipY && mouseY <= clipY + clipH;
                            ModernGuiUtils.drawModernTextField(graphics, this.font, widgetX, widgetY, widgetW, widgetH,
                                    opt.getText(), opt.getPlaceholder(), focusedTextOption == opt, isWidgetHovered);
                        } else if (opt.getType() == ConfigOption.Type.ACTION) {
                            boolean isWidgetHovered = mouseX >= widgetX && mouseX <= widgetX + widgetW && mouseY >= widgetY && mouseY <= widgetY + widgetH && mouseY >= clipY && mouseY <= clipY + clipH;

                            if (opt.getId().contains("color")) {
                                int colorVal = switch (opt.getId()) {
                                    case "menu_accent_color" -> net.alpaka.addons.config.AlpakaConfig.instance.menuAccentColor;
                                    case "block_fill_color" -> net.alpaka.addons.config.AlpakaConfig.instance.blockFillColor;
                                    case "pangolin_highlight_color" -> net.alpaka.addons.config.AlpakaConfig.instance.pangolinHighlightColor;
                                    default -> net.alpaka.addons.config.AlpakaConfig.instance.blockOutlineColor;
                                };
                                ModernGuiUtils.drawModernColorButton(graphics, this.font, widgetX, widgetY, widgetW, widgetH, colorVal, isWidgetHovered);
                            } else if (opt.getId().equals("disable_all_features")) {
                                ModernGuiUtils.drawModernDestructiveButton(graphics, this.font, widgetX, widgetY, widgetW, widgetH, opt.getActionLabel(), isWidgetHovered);
                            } else {
                                ModernGuiUtils.drawModernButton(graphics, this.font, widgetX, widgetY, widgetW, widgetH, opt.getActionLabel(), isWidgetHovered, false);
                            }
                        }

                        graphics.pose().popMatrix();

                        // Drawn after popMatrix on purpose: the lines belong to the list, not to the
                        // card, and must not inherit the card's hover pop-scale.
                        if (opt.getType() == ConfigOption.Type.DROPDOWN && opt.isExpanded()) {
                            int entryY = startOptionY + cardH + DROPDOWN_TOP_PAD;
                            for (ConfigOption.ToggleEntry entry : opt.getEntries()) {
                                boolean entryHovered = mouseX >= contentX + 14 && mouseX <= contentX + 14 + cardW
                                        && mouseY >= entryY && mouseY < entryY + DROPDOWN_ENTRY_H
                                        && mouseY >= clipY && mouseY <= clipY + clipH;

                                if (entryHovered) {
                                    ModernGuiUtils.drawRect(graphics, contentX + 14, entryY, cardW, DROPDOWN_ENTRY_H, ModernGuiUtils.COLOR_CARD_BG_HOVER);
                                }

                                int boxX = contentX + 14 + 22;
                                int boxY = entryY + (DROPDOWN_ENTRY_H - CHECKBOX_SIZE) / 2;
                                ModernGuiUtils.drawModernCheckbox(graphics, this.font, boxX, boxY, CHECKBOX_SIZE, entry.get(), entryHovered);

                                int labelColor = entry.get() ? ModernGuiUtils.COLOR_TEXT_PRIMARY : ModernGuiUtils.COLOR_TEXT_MUTED;
                                graphics.text(this.font, Component.literal(entry.getLabel()),
                                        boxX + CHECKBOX_SIZE + 7, entryY + (DROPDOWN_ENTRY_H - 8) / 2, labelColor);

                                entryY += DROPDOWN_ENTRY_H;
                            }
                        }
                    }
                }

                startOptionY += itemHeight;
            }
        }

        graphics.disableScissor();

        if (isAnimatingOpen) {
            graphics.pose().popMatrix();
        }
    }

    private int getEffectiveWinX(int winW) {
        if (currentWinX >= 0) {
            return (int) Math.round(currentWinX);
        }
        int centerWinX = (this.width - winW) / 2;
        int sideWinX = Math.max(12, (this.width - winW) / 10);
        return (activeCategory == ConfigCategory.VIEWMODEL && searchQuery.isEmpty()) ? sideWinX : centerWinX;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Released up front: any click that then lands on a text field re-focuses it below, and
        // every other click - sidebar, toggle, backdrop - should drop the caret.
        ConfigOption previouslyFocusedText = focusedTextOption;
        focusedTextOption = null;
        if (previouslyFocusedText != null) net.alpaka.addons.config.AlpakaConfig.save();
        double mouseX = event.x();
        double mouseY = event.y();

        int winW = Math.min(660, Math.max(480, (int) (this.width * 0.70)));
        int winH = Math.min(440, Math.max(340, (int) (this.height * 0.68)));
        int winX = getEffectiveWinX(winW);
        int winY = (this.height - winH) / 2;

        int headerHeight = 38;
        int sidebarWidth = 160;

        int contentX = winX + sidebarWidth;
        int contentY = winY + headerHeight;
        int contentW = winW - sidebarWidth;
        int contentH = winH - headerHeight;

        // Search bar click
        int searchW = 158;
        int searchH = 22;
        int searchX = winX + winW - 170;
        int searchY = winY + 8;

        if (!searchQuery.isEmpty()) {
            int clearX = searchX + searchW - 14;
            if (mouseX >= clearX && mouseX <= clearX + 10 && mouseY >= searchY && mouseY <= searchY + searchH) {
                playPloppSound();
                this.searchQuery = "";
                this.targetScrollY = 0.0;
                this.scrollY = 0.0;
                return true;
            }
        }

        if (mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + searchH) {
            this.searchFocused = true;
            this.cursorBlinkTimer = System.currentTimeMillis();
            return true;
        } else {
            this.searchFocused = false;
        }

        // Category Sidebar click
        int sideClipY = winY + headerHeight + 4;
        int sideClipH = winH - headerHeight - 8;

        if (mouseX >= winX && mouseX <= winX + sidebarWidth && mouseY >= sideClipY && mouseY <= sideClipY + sideClipH) {
            List<ConfigCategory> categories = getVisibleCategories();
            int catItemH = 32;
            int catSpacing = 4;
            int catW = sidebarWidth - 20;
            int itemY = sideClipY + 4 - (int) sidebarScrollY;

            for (ConfigCategory cat : categories) {
                if (mouseX >= winX + 8 && mouseX <= winX + 8 + catW &&
                    mouseY >= itemY && mouseY <= itemY + catItemH &&
                    mouseY >= sideClipY && mouseY <= sideClipY + sideClipH) {
                    playPloppSound();
                    this.activeCategory = cat;
                    // Keep searchQuery so search term remains active in search bar when swapping categories!
                    this.targetScrollY = 0.0;
                    this.scrollY = 0.0;
                    return true;
                }
                itemY += catItemH + catSpacing;
            }
        }

        // Options List click (bounded by scissor viewport)
        int clipY = contentY + 6;
        int clipH = contentH - 12;

        if (mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= clipY && mouseY <= clipY + clipH) {
            List<ConfigOption> options = AlpakaConfigRegistry.getOptions(activeCategory, searchQuery);
            int startOptionY = contentY + 12 + CATEGORY_HEADER_H - (int) scrollY;
            int cardW = contentW - 28;
            int cardH = CARD_H;

            for (ConfigOption opt : options) {
                int itemHeight = optionItemHeight(opt);

                if (opt.getType() == ConfigOption.Type.HEADER) {
                    startOptionY += itemHeight;
                    continue;
                }

                if (opt.getType() == ConfigOption.Type.TEXT) {
                    int tw = 120;
                    int th = 18;
                    int tx = contentX + 14 + cardW - tw - 10;
                    int ty = startOptionY + (cardH - th) / 2;
                    if (mouseX >= tx && mouseX <= tx + tw && mouseY >= ty && mouseY <= ty + th) {
                        playPloppSound();
                        focusedTextOption = opt;
                        searchFocused = false;
                        cursorBlinkTimer = System.currentTimeMillis();
                        return true;
                    }
                }

                int widgetW = (opt.getType() == ConfigOption.Type.BOOLEAN) ? 32 :
                              (opt.getType() == ConfigOption.Type.TEXT ? 120 :
                              (opt.getType() == ConfigOption.Type.ACTION ? (opt.getId().contains("color") ? 38 : 80) : 90));
                int widgetH = (opt.getType() == ConfigOption.Type.BOOLEAN) ? 16 : 18;
                int widgetX = contentX + 14 + cardW - widgetW - 10;
                int widgetY = startOptionY + (cardH - widgetH) / 2;

                // Expanded lines are hit-tested before the card. They sit below it so the two cannot
                // overlap, but checking them first keeps that independent of the card's exact height.
                if (opt.getType() == ConfigOption.Type.DROPDOWN && opt.isExpanded()) {
                    int entryY = startOptionY + cardH + DROPDOWN_TOP_PAD;
                    for (ConfigOption.ToggleEntry entry : opt.getEntries()) {
                        boolean insideViewport = entryY + DROPDOWN_ENTRY_H >= clipY && entryY <= clipY + clipH;
                        if (insideViewport
                                && mouseX >= contentX + 14 && mouseX <= contentX + 14 + cardW
                                && mouseY >= entryY && mouseY < entryY + DROPDOWN_ENTRY_H) {
                            playPloppSound();
                            entry.toggle();
                            return true;
                        }
                        entryY += DROPDOWN_ENTRY_H;
                    }
                }

                boolean isWidgetClicked = mouseX >= widgetX && mouseX <= widgetX + widgetW && mouseY >= widgetY && mouseY <= widgetY + widgetH;
                boolean isCardClicked = mouseX >= contentX + 14 && mouseX <= contentX + 14 + cardW && mouseY >= startOptionY && mouseY <= startOptionY + cardH;

                if ((isWidgetClicked || isCardClicked) && startOptionY + itemHeight >= clipY && startOptionY <= clipY + clipH) {
                    opt.triggerClickAnimation();

                    if (opt.getType() == ConfigOption.Type.BOOLEAN) {
                        playPloppSound();
                        opt.toggleBool();
                        return true;
                    } else if (opt.getType() == ConfigOption.Type.SLIDER) {
                        playPloppSound();
                        this.draggedOption = opt;
                        // Held back until the button comes up: every setter saves, and a drag fires
                        // one per mouse-move event - each a full serialise and file write.
                        net.alpaka.addons.config.AlpakaConfig.beginDeferredSaves();
                        double norm = Math.max(0.0, Math.min(1.0, (mouseX - widgetX) / (double) widgetW));
                        opt.setSliderNormalizedValue(norm);
                        opt.setDragging(true);
                        return true;
                    } else if (opt.getType() == ConfigOption.Type.DROPDOWN) {
                        playPloppSound();
                        opt.toggleExpanded();
                        return true;
                    } else if (opt.getType() == ConfigOption.Type.ACTION) {
                        playPloppSound();
                        opt.triggerAction(this);
                        return true;
                    }
                }

                startOptionY += itemHeight;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            if (draggedOption != null) {
                draggedOption.setDragging(false);
                draggedOption = null;
                // One write for the whole drag, with the value the slider actually ended on.
                net.alpaka.addons.config.AlpakaConfig.endDeferredSaves();
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (draggedOption != null && draggedOption.getType() == ConfigOption.Type.SLIDER) {
            int winW = Math.min(660, Math.max(480, (int) (this.width * 0.70)));
            int winX = getEffectiveWinX(winW);
            int sidebarWidth = 160;
            int contentX = winX + sidebarWidth;
            int contentW = winW - sidebarWidth;
            int cardW = contentW - 28;
            int widgetW = 90;
            int widgetX = contentX + 14 + cardW - widgetW - 10;

            double norm = Math.max(0.0, Math.min(1.0, (event.x() - widgetX) / (double) widgetW));
            draggedOption.setSliderNormalizedValue(norm);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            int winW = Math.min(660, Math.max(480, (int) (this.width * 0.70)));
            int winH = Math.min(440, Math.max(340, (int) (this.height * 0.68)));
            int winX = getEffectiveWinX(winW);
            int winY = (this.height - winH) / 2;
            int headerHeight = 38;
            int sidebarWidth = 160;

            if (mouseX >= winX && mouseX <= winX + sidebarWidth && mouseY >= winY + headerHeight && mouseY <= winY + winH) {
                // Independent scroll for category sidebar on left
                this.targetSidebarScrollY = Math.max(0, Math.min(maxSidebarScrollY, targetSidebarScrollY - scrollY * 24.0));
                return true;
            } else {
                // Independent scroll for feature list on right
                this.targetScrollY = Math.max(0, Math.min(maxScrollY, targetScrollY - scrollY * 28.0));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (focusedTextOption != null) {
            int codePoint = event.codepoint();
            if (codePoint >= 32 && codePoint != 127) {
                String current = focusedTextOption.getText();
                if (current.length() < MAX_TEXT_OPTION_LENGTH) {
                    focusedTextOption.setText(current + (char) codePoint);
                }
            }
            return true;
        }

        if (searchFocused) {
            int codePoint = event.codepoint();
            if (codePoint >= 32 && codePoint != 127) {
                if (searchQuery.length() < 35) {
                    searchQuery += (char) codePoint;
                    ensureValidActiveCategory();
                    targetScrollY = 0;
                    scrollY = 0;
                    return true;
                }
            }
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Cmd+F on Mac, Ctrl+F on Windows to focus search bar
        if (event.hasControlDownWithQuirk() && (event.key() == 70 || event.key() == 102)) { // GLFW_KEY_F
            this.searchFocused = true;
            this.cursorBlinkTimer = System.currentTimeMillis();
            return true;
        }

        if (focusedTextOption != null) {
            if (event.key() == 259) { // GLFW_KEY_BACKSPACE
                String current = focusedTextOption.getText();
                if (!current.isEmpty()) focusedTextOption.setText(current.substring(0, current.length() - 1));
                return true;
            }
            // Escape and Enter both just commit and let go; the value is already written through.
            if (event.key() == 256 || event.key() == 257 || event.key() == 335) {
                focusedTextOption = null;
                net.alpaka.addons.config.AlpakaConfig.save();
                return true;
            }
        }

        if (searchFocused) {
            if (event.key() == 259) { // GLFW_KEY_BACKSPACE
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    ensureValidActiveCategory();
                    targetScrollY = 0;
                    scrollY = 0;
                }
                return true;
            } else if (event.key() == 256) { // GLFW_KEY_ESCAPE
                searchFocused = false;
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        // Safety net: a drag interrupted by the screen closing (Escape, a keybind) never sees its
        // mouse-up, and leaving saves deferred would swallow every later write for the session.
        if (draggedOption != null) {
            draggedOption.setDragging(false);
            draggedOption = null;
            net.alpaka.addons.config.AlpakaConfig.endDeferredSaves();
        }

        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}

