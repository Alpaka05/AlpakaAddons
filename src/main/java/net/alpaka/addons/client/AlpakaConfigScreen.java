package net.alpaka.addons.client;

import net.alpaka.addons.client.gui.AlpakaConfigRegistry;
import net.alpaka.addons.client.gui.ConfigCategory;
import net.alpaka.addons.client.gui.ConfigOption;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.client.gui.PloppAnimation;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

public class AlpakaConfigScreen extends Screen {
    private final Screen parent;
    private ConfigCategory activeCategory = ConfigCategory.ALL;

    // Search state
    private String searchQuery = "";
    private boolean searchFocused = false;
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
        super(Component.literal("Alpaka Addons Config"));
        this.parent = parent;
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

        // Render translucent backdrop so game is visible behind and around the config panel
        graphics.fill(0, 0, this.width, this.height, 0x70000000);

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

        // 3. Top Header Bar
        ModernGuiUtils.drawRect(graphics, winX, winY, winW, headerHeight, ModernGuiUtils.COLOR_SIDEBAR_BG);
        ModernGuiUtils.drawRect(graphics, winX, winY + headerHeight - 1, winW, 1, ModernGuiUtils.COLOR_ACCENT);

        // Header Title
        String mainTitle = "ALPAKA ADDONS";
        graphics.text(this.font, Component.literal(mainTitle), winX + 12, winY + 11, ModernGuiUtils.COLOR_TEXT_PRIMARY);
        graphics.text(this.font, Component.literal("v1.0.29"), winX + 12 + this.font.width(mainTitle) + 6, winY + 12, ModernGuiUtils.COLOR_TEXT_MUTED);

        if (activeCategory == ConfigCategory.VIEWMODEL && searchQuery.isEmpty()) {
            graphics.text(this.font, Component.literal("👁 Live Hand View"), winX + 12 + this.font.width(mainTitle) + 60, winY + 12, ModernGuiUtils.COLOR_ACCENT);
        }

        // Close / Done Button in Header (low-key)
        int closeW = 68;
        int closeH = 22;
        int closeX = winX + winW - closeW - 8;
        int closeY = winY + 8;
        boolean isHoveringClose = mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH;
        ModernGuiUtils.drawModernButton(graphics, this.font, closeX, closeY, closeW, closeH, "Done ✕", isHoveringClose, false);

        // Search Bar in Header
        int searchW = 160;
        int searchH = 22;
        int searchX = closeX - searchW - 8;
        int searchY = winY + 8;

        boolean isHoveringSearch = mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + searchH;
        int searchBorder = searchFocused ? ModernGuiUtils.COLOR_ACCENT : (isHoveringSearch ? ModernGuiUtils.COLOR_ACCENT_DIM : ModernGuiUtils.COLOR_CARD_BORDER);

        ModernGuiUtils.drawRect(graphics, searchX, searchY, searchW, searchH, ModernGuiUtils.COLOR_CARD_BG);
        ModernGuiUtils.drawOutline(graphics, searchX, searchY, searchW, searchH, searchBorder);

        String searchDisplayText = searchQuery;
        if (searchDisplayText.isEmpty() && !searchFocused) {
            graphics.text(this.font, Component.literal("Search settings..."), searchX + 8, searchY + (searchH - 8) / 2, ModernGuiUtils.COLOR_TEXT_MUTED);
        } else {
            if (searchFocused && (System.currentTimeMillis() - cursorBlinkTimer) % 1000 < 500) {
                searchDisplayText += "|";
            }
            graphics.text(this.font, Component.literal(searchDisplayText), searchX + 8, searchY + (searchH - 8) / 2, ModernGuiUtils.COLOR_TEXT_PRIMARY);
        }

        if (!searchQuery.isEmpty()) {
            int clearX = searchX + searchW - 18;
            int clearY = searchY + 3;
            boolean hoverClear = mouseX >= clearX && mouseX <= clearX + 14 && mouseY >= clearY && mouseY <= clearY + 16;
            graphics.text(this.font, Component.literal("✕"), clearX + 2, searchY + (searchH - 8) / 2, hoverClear ? ModernGuiUtils.COLOR_ACCENT : ModernGuiUtils.COLOR_TEXT_MUTED);
        }

        // 4. Sidebar Categories with Scissor Clipping to keep categories strictly inside the panel
        ConfigCategory[] categories = ConfigCategory.values();
        int catH = 28;
        int catSpacing = 4;
        int catW = sidebarWidth - 16;

        int sideClipX = winX + 4;
        int sideClipY = winY + headerHeight + 4;
        int sideClipW = sidebarWidth - 8;
        int sideClipH = winH - headerHeight - 8;

        int totalSidebarH = categories.length * (catH + catSpacing) + 8;
        maxSidebarScrollY = Math.max(0, totalSidebarH - sideClipH);
        targetSidebarScrollY = Math.max(0, Math.min(maxSidebarScrollY, targetSidebarScrollY));

        graphics.enableScissor(sideClipX, sideClipY, sideClipX + sideClipW, sideClipY + sideClipH);

        int catY = sideClipY + 4 - (int) sidebarScrollY;

        for (ConfigCategory cat : categories) {
            boolean isSelected = (cat == activeCategory);
            boolean isHovered = mouseX >= winX + 8 && mouseX <= winX + 8 + catW &&
                                mouseY >= catY && mouseY <= catY + catH &&
                                mouseY >= sideClipY && mouseY <= sideClipY + sideClipH;

            int catBg = isSelected ? ModernGuiUtils.COLOR_CARD_BG : (isHovered ? ModernGuiUtils.COLOR_CARD_BG_HOVER : ModernGuiUtils.COLOR_SIDEBAR_BG);
            int catBorder = isSelected ? ModernGuiUtils.COLOR_ACCENT_DIM : (isHovered ? ModernGuiUtils.COLOR_CARD_BORDER : 0x00000000);

            ModernGuiUtils.drawRect(graphics, winX + 8, catY, catW, catH, catBg);
            if (catBorder != 0) {
                ModernGuiUtils.drawOutline(graphics, winX + 8, catY, catW, catH, catBorder);
            }

            // Category Label: Shift text right when selected
            int textX = winX + (isSelected ? 22 : 14);
            int labelColor = isSelected ? ModernGuiUtils.COLOR_ACCENT : (isHovered ? ModernGuiUtils.COLOR_TEXT_PRIMARY : ModernGuiUtils.COLOR_TEXT_MUTED);
            graphics.text(this.font, Component.literal(cat.getFullLabel()), textX, catY + (catH - 8) / 2, labelColor);

            // Option Count Badge
            List<ConfigOption> optionsForCat = AlpakaConfigRegistry.getOptions(cat, searchQuery);
            String countText = String.valueOf(optionsForCat.size());
            int countX = winX + 8 + catW - this.font.width(countText) - 8;
            graphics.text(this.font, Component.literal(countText), countX, catY + (catH - 8) / 2, ModernGuiUtils.COLOR_TEXT_DARK);

            catY += catH + catSpacing;
        }

        graphics.disableScissor();

        // 5. Render Options in Main Panel with Scissor Clipping to prevent scrolling overlap
        List<ConfigOption> options = AlpakaConfigRegistry.getOptions(activeCategory, searchQuery);

        int totalContentHeight = 40 + options.size() * 52 + 20;
        maxScrollY = Math.max(0, totalContentHeight - contentH);
        targetScrollY = Math.max(0, Math.min(maxScrollY, targetScrollY));

        int clipY = contentY + 6;
        int clipH = contentH - 12;

        graphics.enableScissor(contentX, clipY, contentX + contentW, clipY + clipH);

        int startOptionY = contentY + 12 - (int) scrollY;

        // Render Category Header inside Content Area
        String catTitle = activeCategory.getDisplayName();
        if (!searchQuery.isEmpty()) {
            catTitle = "Search results for: \"" + searchQuery + "\"";
        }
        graphics.text(this.font, Component.literal(catTitle), contentX + 16, startOptionY, ModernGuiUtils.COLOR_TEXT_PRIMARY);
        graphics.text(this.font, Component.literal(activeCategory.getDescription()), contentX + 16, startOptionY + 13, ModernGuiUtils.COLOR_TEXT_MUTED);

        startOptionY += 30;

        if (options.isEmpty()) {
            String emptyMsg = "No settings found for \"" + searchQuery + "\".";
            graphics.text(this.font, Component.literal(emptyMsg), contentX + 16, startOptionY + 12, ModernGuiUtils.COLOR_TOGGLE_OFF_TEXT);
        } else {
            int cardW = contentW - 28;
            int cardH = 44;

            for (ConfigOption opt : options) {
                if (startOptionY + cardH >= clipY && startOptionY <= clipY + clipH) {
                    if (opt.getType() == ConfigOption.Type.HEADER) {
                        ModernGuiUtils.drawRect(graphics, contentX + 14, startOptionY + 8, cardW, 1, ModernGuiUtils.COLOR_CARD_BORDER);
                        graphics.text(this.font, Component.literal("• " + opt.getTitle().toUpperCase()), contentX + 14, startOptionY + 14, ModernGuiUtils.COLOR_ACCENT);
                        startOptionY += 30;
                        continue;
                    }

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
                                  (opt.getType() == ConfigOption.Type.ACTION ? (opt.getId().contains("color") ? 38 : 80) : 85);
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
                            graphics.text(this.font, Component.literal(descLines.get(i).getString()), contentX + 24, startOptionY + 20 + i * 9, ModernGuiUtils.COLOR_TEXT_MUTED);
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
                    } else if (opt.getType() == ConfigOption.Type.ACTION) {
                        boolean isWidgetHovered = mouseX >= widgetX && mouseX <= widgetX + widgetW && mouseY >= widgetY && mouseY <= widgetY + widgetH && mouseY >= clipY && mouseY <= clipY + clipH;

                        if (opt.getId().contains("color")) {
                            int colorVal = opt.getId().contains("fill") ? net.alpaka.addons.config.AlpakaConfig.instance.blockFillColor : net.alpaka.addons.config.AlpakaConfig.instance.blockOutlineColor;
                            ModernGuiUtils.drawModernColorButton(graphics, this.font, widgetX, widgetY, widgetW, widgetH, colorVal, isWidgetHovered);
                        } else {
                            ModernGuiUtils.drawModernButton(graphics, this.font, widgetX, widgetY, widgetW, widgetH, opt.getActionLabel(), isWidgetHovered, false);
                        }
                    }

                    graphics.pose().popMatrix();
                }

                startOptionY += cardH + 6;
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

        // Close / Done button click
        int closeW = 68;
        int closeH = 22;
        int closeX = winX + winW - closeW - 8;
        int closeY = winY + 8;
        if (mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH) {
            playPloppSound();
            this.onClose();
            return true;
        }

        // Search bar click
        int searchW = 160;
        int searchH = 22;
        int searchX = closeX - searchW - 8;
        int searchY = winY + 8;

        if (searchQuery.length() > 0) {
            int clearX = searchX + searchW - 18;
            int clearY = searchY + 3;
            if (mouseX >= clearX && mouseX <= clearX + 14 && mouseY >= clearY && mouseY <= clearY + 16) {
                playPloppSound();
                this.searchQuery = "";
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
            ConfigCategory[] categories = ConfigCategory.values();
            int catH = 28;
            int catSpacing = 4;
            int catW = sidebarWidth - 16;
            int catY = sideClipY + 4 - (int) sidebarScrollY;

            for (ConfigCategory cat : categories) {
                if (mouseX >= winX + 8 && mouseX <= winX + 8 + catW && mouseY >= catY && mouseY <= catY + catH && mouseY >= sideClipY && mouseY <= sideClipY + sideClipH) {
                    playPloppSound();
                    this.activeCategory = cat;
                    this.searchQuery = "";
                    this.targetScrollY = 0.0;
                    this.scrollY = 0.0;
                    return true;
                }
                catY += catH + catSpacing;
            }
        }

        // Options List click (bounded by scissor viewport)
        int clipY = contentY + 6;
        int clipH = contentH - 12;

        if (mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= clipY && mouseY <= clipY + clipH) {
            List<ConfigOption> options = AlpakaConfigRegistry.getOptions(activeCategory, searchQuery);
            int startOptionY = contentY + 12 + 30 - (int) scrollY;
            int cardW = contentW - 28;
            int cardH = 44;

            for (ConfigOption opt : options) {
                if (opt.getType() == ConfigOption.Type.HEADER) {
                    startOptionY += 30;
                    continue;
                }

                int widgetW = (opt.getType() == ConfigOption.Type.BOOLEAN) ? 32 :
                              (opt.getType() == ConfigOption.Type.ACTION ? (opt.getId().contains("color") ? 38 : 80) : 85);
                int widgetH = (opt.getType() == ConfigOption.Type.BOOLEAN) ? 15 : 18;
                int widgetX = contentX + 14 + cardW - widgetW - 10;
                int widgetY = startOptionY + (cardH - widgetH) / 2;

                boolean isWidgetClicked = mouseX >= widgetX && mouseX <= widgetX + widgetW && mouseY >= widgetY && mouseY <= widgetY + widgetH;
                boolean isCardClicked = mouseX >= contentX + 14 && mouseX <= contentX + 14 + cardW && mouseY >= startOptionY && mouseY <= startOptionY + cardH;

                if ((isWidgetClicked || isCardClicked) && startOptionY + cardH >= clipY && startOptionY <= clipY + clipH) {
                    opt.triggerClickAnimation();

                    if (opt.getType() == ConfigOption.Type.BOOLEAN) {
                        playPloppSound();
                        opt.toggleBool();
                        return true;
                    } else if (opt.getType() == ConfigOption.Type.SLIDER) {
                        if (isWidgetClicked) {
                            playPloppSound();
                            this.draggedOption = opt;
                            double norm = Math.max(0.0, Math.min(1.0, (mouseX - widgetX) / (double) widgetW));
                            opt.setSliderNormalizedValue(norm);
                            opt.setDragging(true);
                            return true;
                        }
                    } else if (opt.getType() == ConfigOption.Type.ACTION) {
                        playPloppSound();
                        opt.triggerAction(this);
                        return true;
                    }
                }

                startOptionY += cardH + 6;
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
        if (searchFocused) {
            int codePoint = event.codepoint();
            if (codePoint >= 32 && codePoint != 127) {
                if (searchQuery.length() < 35) {
                    searchQuery += (char) codePoint;
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
        if (searchFocused) {
            if (event.key() == 259) { // GLFW_KEY_BACKSPACE
                if (!searchQuery.isEmpty()) {
                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
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
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}

