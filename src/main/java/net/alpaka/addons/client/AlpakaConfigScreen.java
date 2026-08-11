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

        // Smooth scroll interpolation
        scrollY += (targetScrollY - scrollY) * Math.min(1.0f, deltaSec * 14.0f);

        // Render translucent backdrop so game is visible behind and around the config panel
        graphics.fill(0, 0, this.width, this.height, 0x70000000);

        // Calculate Window Panel Dimensions
        int winW = Math.min(880, Math.max(600, this.width - 70));
        int winH = Math.min(560, Math.max(400, this.height - 40));
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        int headerHeight = 44;
        int sidebarWidth = 200;

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
        graphics.text(this.font, Component.literal(mainTitle), winX + 16, winY + 14, ModernGuiUtils.COLOR_TEXT_PRIMARY);
        graphics.text(this.font, Component.literal("v1.0.29"), winX + 16 + this.font.width(mainTitle) + 8, winY + 15, ModernGuiUtils.COLOR_TEXT_MUTED);

        // Close / Done Button in Header (low-key, not fully primary highlighted)
        int closeW = 86;
        int closeH = 24;
        int closeX = winX + winW - closeW - 12;
        int closeY = winY + 10;
        boolean isHoveringClose = mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH;
        ModernGuiUtils.drawModernButton(graphics, this.font, closeX, closeY, closeW, closeH, "Done ✕", isHoveringClose, false);

        // Search Bar in Header
        int searchW = 210;
        int searchH = 24;
        int searchX = closeX - searchW - 12;
        int searchY = winY + 10;

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

        // 4. Sidebar Categories (Selecting shifts text right instead of thick bar border)
        ConfigCategory[] categories = ConfigCategory.values();
        int catY = winY + headerHeight + 10;
        int catH = 34;
        int catW = sidebarWidth - 16;

        for (ConfigCategory cat : categories) {
            boolean isSelected = (cat == activeCategory);
            boolean isHovered = mouseX >= winX + 8 && mouseX <= winX + 8 + catW && mouseY >= catY && mouseY <= catY + catH;

            int catBg = isSelected ? ModernGuiUtils.COLOR_CARD_BG : (isHovered ? ModernGuiUtils.COLOR_CARD_BG_HOVER : ModernGuiUtils.COLOR_SIDEBAR_BG);
            int catBorder = isSelected ? ModernGuiUtils.COLOR_ACCENT_DIM : (isHovered ? ModernGuiUtils.COLOR_CARD_BORDER : 0x00000000);

            ModernGuiUtils.drawRect(graphics, winX + 8, catY, catW, catH, catBg);
            if (catBorder != 0) {
                ModernGuiUtils.drawOutline(graphics, winX + 8, catY, catW, catH, catBorder);
            }

            // Category Label: Shift text right when selected
            int textX = winX + (isSelected ? 24 : 16);
            int labelColor = isSelected ? ModernGuiUtils.COLOR_ACCENT : (isHovered ? ModernGuiUtils.COLOR_TEXT_PRIMARY : ModernGuiUtils.COLOR_TEXT_MUTED);
            graphics.text(this.font, Component.literal(cat.getFullLabel()), textX, catY + (catH - 8) / 2, labelColor);

            // Option Count Badge
            List<ConfigOption> optionsForCat = AlpakaConfigRegistry.getOptions(cat, searchQuery);
            String countText = String.valueOf(optionsForCat.size());
            int countX = winX + 8 + catW - this.font.width(countText) - 10;
            graphics.text(this.font, Component.literal(countText), countX, catY + (catH - 8) / 2, ModernGuiUtils.COLOR_TEXT_DARK);

            catY += catH + 4;
        }

        // 5. Render Options in Main Panel with Scissor Clipping to prevent scrolling overlap
        List<ConfigOption> options = AlpakaConfigRegistry.getOptions(activeCategory, searchQuery);

        int totalContentHeight = 50 + options.size() * 60 + 30;
        int maxScroll = Math.max(0, totalContentHeight - contentH);
        targetScrollY = Math.max(0, Math.min(maxScroll, targetScrollY));

        int clipY = contentY + 6;
        int clipH = contentH - 12;

        graphics.enableScissor(contentX, clipY, contentX + contentW, clipY + clipH);

        int startOptionY = contentY + 16 - (int) scrollY;

        // Render Category Header inside Content Area
        String catTitle = activeCategory.getDisplayName();
        if (!searchQuery.isEmpty()) {
            catTitle = "Search results for: \"" + searchQuery + "\"";
        }
        graphics.text(this.font, Component.literal(catTitle), contentX + 20, startOptionY, ModernGuiUtils.COLOR_TEXT_PRIMARY);
        graphics.text(this.font, Component.literal(activeCategory.getDescription()), contentX + 20, startOptionY + 14, ModernGuiUtils.COLOR_TEXT_MUTED);

        startOptionY += 34;

        if (options.isEmpty()) {
            String emptyMsg = "No settings found for \"" + searchQuery + "\".";
            graphics.text(this.font, Component.literal(emptyMsg), contentX + 20, startOptionY + 16, ModernGuiUtils.COLOR_TOGGLE_OFF_TEXT);
        } else {
            int cardW = contentW - 36;
            int cardH = 50;

            for (ConfigOption opt : options) {
                if (startOptionY + cardH >= clipY && startOptionY <= clipY + clipH) {
                    if (opt.getType() == ConfigOption.Type.HEADER) {
                        ModernGuiUtils.drawRect(graphics, contentX + 20, startOptionY + 10, cardW, 1, ModernGuiUtils.COLOR_CARD_BORDER);
                        graphics.text(this.font, Component.literal("• " + opt.getTitle().toUpperCase()), contentX + 20, startOptionY + 16, ModernGuiUtils.COLOR_ACCENT);
                        startOptionY += 34;
                        continue;
                    }

                    boolean isCardHovered = mouseX >= contentX + 20 && mouseX <= contentX + 20 + cardW &&
                                           mouseY >= startOptionY && mouseY <= startOptionY + cardH &&
                                           mouseY >= clipY && mouseY <= clipY + clipH;

                    opt.updateHoverProgress(isCardHovered, deltaSec);
                    opt.updateClickProgress(deltaSec);

                    float popScale = 1.0f + 0.015f * opt.getHoverProgress() - 0.015f * opt.getClickProgress();

                    graphics.pose().pushMatrix();
                    float cardCenterX = contentX + 20 + cardW / 2.0f;
                    float cardCenterY = startOptionY + cardH / 2.0f;
                    graphics.pose().scaleAround(popScale, popScale, cardCenterX, cardCenterY);

                    ModernGuiUtils.drawModernCard(graphics, contentX + 20, startOptionY, cardW, cardH, isCardHovered, false);

                    // Title & Description
                    graphics.text(this.font, Component.literal(opt.getTitle()), contentX + 32, startOptionY + 11, ModernGuiUtils.COLOR_TEXT_PRIMARY);
                    graphics.text(this.font, Component.literal(opt.getDescription()), contentX + 32, startOptionY + 26, ModernGuiUtils.COLOR_TEXT_MUTED);

                    // Right Control Widgets
                    if (opt.getType() == ConfigOption.Type.BOOLEAN) {
                        int widgetW = 54;
                        int widgetH = 22;
                        int widgetX = contentX + 20 + cardW - widgetW - 14;
                        int widgetY = startOptionY + (cardH - widgetH) / 2;
                        boolean isWidgetHovered = mouseX >= widgetX && mouseX <= widgetX + widgetW && mouseY >= widgetY && mouseY <= widgetY + widgetH && mouseY >= clipY && mouseY <= clipY + clipH;
                        ModernGuiUtils.drawModernToggle(graphics, this.font, widgetX, widgetY, widgetW, widgetH, opt.getBool(), isWidgetHovered);
                    } else if (opt.getType() == ConfigOption.Type.SLIDER) {
                        int widgetW = 120;
                        int widgetH = 24;
                        int widgetX = contentX + 20 + cardW - widgetW - 14;
                        int widgetY = startOptionY + (cardH - widgetH) / 2;
                        boolean isWidgetHovered = mouseX >= widgetX && mouseX <= widgetX + widgetW && mouseY >= widgetY && mouseY <= widgetY + widgetH && mouseY >= clipY && mouseY <= clipY + clipH;
                        double normVal = opt.getSliderNormalizedValue();
                        ModernGuiUtils.drawModernSlider(graphics, this.font, widgetX, widgetY, widgetW, widgetH, normVal, opt.getFormattedValue(), isWidgetHovered);
                    } else if (opt.getType() == ConfigOption.Type.ACTION) {
                        int widgetW = opt.getId().contains("color") ? 64 : 110;
                        int widgetH = 24;
                        int widgetX = contentX + 20 + cardW - widgetW - 14;
                        int widgetY = startOptionY + (cardH - widgetH) / 2;
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

                startOptionY += cardH + 8;
            }
        }

        graphics.disableScissor();

        if (isAnimatingOpen) {
            graphics.pose().popMatrix();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        int winW = Math.min(880, Math.max(600, this.width - 70));
        int winH = Math.min(560, Math.max(400, this.height - 40));
        int winX = (this.width - winW) / 2;
        int winY = (this.height - winH) / 2;

        int headerHeight = 44;
        int sidebarWidth = 200;

        int contentX = winX + sidebarWidth;
        int contentY = winY + headerHeight;
        int contentW = winW - sidebarWidth;
        int contentH = winH - headerHeight;

        // Close / Done button click
        int closeW = 86;
        int closeH = 24;
        int closeX = winX + winW - closeW - 12;
        int closeY = winY + 10;
        if (mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH) {
            playPloppSound();
            this.onClose();
            return true;
        }

        // Search bar click
        int searchW = 210;
        int searchH = 24;
        int searchX = closeX - searchW - 12;
        int searchY = winY + 10;

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
        if (mouseX >= winX && mouseX <= winX + sidebarWidth && mouseY >= winY + headerHeight && mouseY <= winY + winH) {
            ConfigCategory[] categories = ConfigCategory.values();
            int catY = winY + headerHeight + 10;
            int catH = 34;
            int catW = sidebarWidth - 16;

            for (ConfigCategory cat : categories) {
                if (mouseX >= winX + 8 && mouseX <= winX + 8 + catW && mouseY >= catY && mouseY <= catY + catH) {
                    playPloppSound();
                    this.activeCategory = cat;
                    this.searchQuery = "";
                    this.targetScrollY = 0.0;
                    this.scrollY = 0.0;
                    return true;
                }
                catY += catH + 4;
            }
        }

        // Options List click (bounded by scissor viewport)
        int clipY = contentY + 6;
        int clipH = contentH - 12;

        if (mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= clipY && mouseY <= clipY + clipH) {
            List<ConfigOption> options = AlpakaConfigRegistry.getOptions(activeCategory, searchQuery);
            int startOptionY = contentY + 16 + 34 - (int) scrollY;
            int cardW = contentW - 36;
            int cardH = 50;

            for (ConfigOption opt : options) {
                if (opt.getType() == ConfigOption.Type.HEADER) {
                    startOptionY += 34;
                    continue;
                }

                int widgetW = (opt.getType() == ConfigOption.Type.BOOLEAN) ? 54 : (opt.getType() == ConfigOption.Type.ACTION ? (opt.getId().contains("color") ? 64 : 110) : 120);
                int widgetH = (opt.getType() == ConfigOption.Type.BOOLEAN) ? 22 : 24;
                int widgetX = contentX + 20 + cardW - widgetW - 14;
                int widgetY = startOptionY + (cardH - widgetH) / 2;

                boolean isWidgetClicked = mouseX >= widgetX && mouseX <= widgetX + widgetW && mouseY >= widgetY && mouseY <= widgetY + widgetH;
                boolean isCardClicked = mouseX >= contentX + 20 && mouseX <= contentX + 20 + cardW && mouseY >= startOptionY && mouseY <= startOptionY + cardH;

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

                startOptionY += cardH + 8;
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
            int winW = Math.min(880, Math.max(600, this.width - 70));
            int winX = (this.width - winW) / 2;
            int sidebarWidth = 200;
            int contentX = winX + sidebarWidth;
            int contentW = winW - sidebarWidth;
            int cardW = contentW - 36;
            int widgetW = 120;
            int widgetX = contentX + 20 + cardW - widgetW - 14;

            double norm = Math.max(0.0, Math.min(1.0, (event.x() - widgetX) / (double) widgetW));
            draggedOption.setSliderNormalizedValue(norm);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            this.targetScrollY -= scrollY * 28.0;
            return true;
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

