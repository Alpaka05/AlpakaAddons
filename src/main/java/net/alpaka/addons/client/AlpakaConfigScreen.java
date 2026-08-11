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

        // Render dark backdrop
        graphics.fill(0, 0, this.width, this.height, ModernGuiUtils.COLOR_BG_BACKDROP);

        // Apply open plopp scale animation
        float openScale = PloppAnimation.getOpenScale(openTimeMs);
        boolean isAnimatingOpen = openScale < 0.999f;

        if (isAnimatingOpen) {
            graphics.pose().pushMatrix();
            graphics.pose().scaleAround(openScale, openScale, this.width / 2.0f, this.height / 2.0f);
        }

        int headerHeight = 46;
        int sidebarWidth = 220;

        // 1. Render Left Sidebar Background
        ModernGuiUtils.drawRect(graphics, 0, headerHeight, sidebarWidth, this.height - headerHeight, ModernGuiUtils.COLOR_SIDEBAR_BG);
        ModernGuiUtils.drawRect(graphics, sidebarWidth - 1, headerHeight, 1, this.height - headerHeight, ModernGuiUtils.COLOR_CARD_BORDER);

        // 2. Render Main Content Panel Background
        int contentX = sidebarWidth;
        int contentY = headerHeight;
        int contentW = this.width - sidebarWidth;
        int contentH = this.height - headerHeight;
        ModernGuiUtils.drawRect(graphics, contentX, contentY, contentW, contentH, ModernGuiUtils.COLOR_PANEL_BG);

        // 3. Render Top Header Bar
        ModernGuiUtils.drawRect(graphics, 0, 0, this.width, headerHeight, ModernGuiUtils.COLOR_SIDEBAR_BG);
        ModernGuiUtils.drawRect(graphics, 0, headerHeight - 1, this.width, 1, ModernGuiUtils.COLOR_ACCENT);

        // Header Title
        String mainTitle = "🦙 ALPAKA ADDONS";
        graphics.text(this.font, Component.literal(mainTitle), 16, 12, ModernGuiUtils.COLOR_TEXT_PRIMARY);
        graphics.text(this.font, Component.literal("v1.0.28"), 16 + this.font.width(mainTitle) + 8, 14, ModernGuiUtils.COLOR_TEXT_MUTED);

        // Search Bar in Header
        int searchW = 260;
        int searchH = 26;
        int searchX = this.width - 220 - searchW;
        int searchY = 10;

        boolean isHoveringSearch = mouseX >= searchX && mouseX <= searchX + searchW && mouseY >= searchY && mouseY <= searchY + searchH;
        int searchBorder = searchFocused ? ModernGuiUtils.COLOR_ACCENT : (isHoveringSearch ? ModernGuiUtils.COLOR_ACCENT_DIM : ModernGuiUtils.COLOR_CARD_BORDER);

        ModernGuiUtils.drawRect(graphics, searchX, searchY, searchW, searchH, ModernGuiUtils.COLOR_CARD_BG);
        ModernGuiUtils.drawOutline(graphics, searchX, searchY, searchW, searchH, searchBorder);

        String searchDisplayText = searchQuery;
        if (searchDisplayText.isEmpty() && !searchFocused) {
            graphics.text(this.font, Component.literal("🔍 Settings suchen..."), searchX + 8, searchY + (searchH - 8) / 2, ModernGuiUtils.COLOR_TEXT_MUTED);
        } else {
            if (searchFocused && (System.currentTimeMillis() - cursorBlinkTimer) % 1000 < 500) {
                searchDisplayText += "|";
            }
            graphics.text(this.font, Component.literal("🔍 " + searchDisplayText), searchX + 8, searchY + (searchH - 8) / 2, ModernGuiUtils.COLOR_TEXT_PRIMARY);
        }

        if (!searchQuery.isEmpty()) {
            // Clear search button '✕'
            int clearX = searchX + searchW - 20;
            int clearY = searchY + 4;
            boolean hoverClear = mouseX >= clearX && mouseX <= clearX + 16 && mouseY >= clearY && mouseY <= clearY + 18;
            graphics.text(this.font, Component.literal("✕"), clearX + 4, searchY + (searchH - 8) / 2, hoverClear ? ModernGuiUtils.COLOR_ACCENT : ModernGuiUtils.COLOR_TEXT_MUTED);
        }

        // Close / Done Button in Header
        int closeW = 90;
        int closeH = 26;
        int closeX = this.width - closeW - 16;
        int closeY = 10;
        boolean isHoveringClose = mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH;
        ModernGuiUtils.drawModernButton(graphics, this.font, closeX, closeY, closeW, closeH, "Fertig ✕", isHoveringClose, true);

        // 4. Render Sidebar Categories
        ConfigCategory[] categories = ConfigCategory.values();
        int catY = headerHeight + 12;
        int catH = 36;
        int catW = sidebarWidth - 16;

        for (ConfigCategory cat : categories) {
            boolean isSelected = (cat == activeCategory);
            boolean isHovered = mouseX >= 8 && mouseX <= 8 + catW && mouseY >= catY && mouseY <= catY + catH;

            int catBg = isSelected ? ModernGuiUtils.COLOR_CARD_BG : (isHovered ? ModernGuiUtils.COLOR_CARD_BG_HOVER : ModernGuiUtils.COLOR_SIDEBAR_BG);
            int catBorder = isSelected ? ModernGuiUtils.COLOR_ACCENT : (isHovered ? ModernGuiUtils.COLOR_ACCENT_DIM : 0x00000000);

            ModernGuiUtils.drawRect(graphics, 8, catY, catW, catH, catBg);
            if (catBorder != 0) {
                ModernGuiUtils.drawOutline(graphics, 8, catY, catW, catH, catBorder);
            }

            if (isSelected) {
                ModernGuiUtils.drawRect(graphics, 8, catY, 4, catH, ModernGuiUtils.COLOR_ACCENT);
            }

            // Category Label
            int labelColor = isSelected ? ModernGuiUtils.COLOR_ACCENT : (isHovered ? ModernGuiUtils.COLOR_TEXT_PRIMARY : ModernGuiUtils.COLOR_TEXT_MUTED);
            graphics.text(this.font, Component.literal(cat.getFullLabel()), 20, catY + (catH - 8) / 2, labelColor);

            // Option Count Badge
            List<ConfigOption> optionsForCat = AlpakaConfigRegistry.getOptions(cat, searchQuery);
            String countText = String.valueOf(optionsForCat.size());
            int countX = 8 + catW - this.font.width(countText) - 10;
            graphics.text(this.font, Component.literal(countText), countX, catY + (catH - 8) / 2, ModernGuiUtils.COLOR_TEXT_DARK);

            catY += catH + 4;
        }

        // 5. Render Options in Main Panel
        List<ConfigOption> options = AlpakaConfigRegistry.getOptions(activeCategory, searchQuery);

        // Clamp scrolling
        int totalContentHeight = 60 + options.size() * 64 + 40;
        int maxScroll = Math.max(0, totalContentHeight - contentH);
        targetScrollY = Math.max(0, Math.min(maxScroll, targetScrollY));

        int startOptionY = contentY + 20 - (int) scrollY;

        // Render Category Header inside Content Area
        String catTitle = activeCategory.getIcon() + "  " + activeCategory.getDisplayName();
        if (!searchQuery.isEmpty()) {
            catTitle = "🔍 Suchergebnisse für: \"" + searchQuery + "\"";
        }
        graphics.text(this.font, Component.literal(catTitle), contentX + 24, startOptionY, ModernGuiUtils.COLOR_TEXT_PRIMARY);
        graphics.text(this.font, Component.literal(activeCategory.getDescription()), contentX + 24, startOptionY + 14, ModernGuiUtils.COLOR_TEXT_MUTED);

        startOptionY += 36;

        if (options.isEmpty()) {
            // Empty state view
            String emptyMsg = "Keine Einstellungen für \"" + searchQuery + "\" gefunden.";
            graphics.text(this.font, Component.literal(emptyMsg), contentX + 24, startOptionY + 20, ModernGuiUtils.COLOR_TOGGLE_OFF_TEXT);
        } else {
            int cardW = contentW - 48;
            int cardH = 54;

            for (ConfigOption opt : options) {
                // Check if card is visible within viewport
                if (startOptionY + cardH >= contentY && startOptionY <= contentY + contentH) {
                    if (opt.getType() == ConfigOption.Type.HEADER) {
                        // Section Header Divider
                        ModernGuiUtils.drawRect(graphics, contentX + 24, startOptionY + 10, cardW, 1, ModernGuiUtils.COLOR_CARD_BORDER);
                        graphics.text(this.font, Component.literal("• " + opt.getTitle().toUpperCase()), contentX + 24, startOptionY + 16, ModernGuiUtils.COLOR_ACCENT);
                        startOptionY += 36;
                        continue;
                    }

                    boolean isCardHovered = mouseX >= contentX + 24 && mouseX <= contentX + 24 + cardW &&
                                           mouseY >= startOptionY && mouseY <= startOptionY + cardH &&
                                           mouseY >= contentY && mouseY <= contentY + contentH;

                    opt.updateHoverProgress(isCardHovered, deltaSec);
                    opt.updateClickProgress(deltaSec);

                    // Apply micro pop scale animation on hover & click
                    float popScale = 1.0f + 0.02f * opt.getHoverProgress() - 0.02f * opt.getClickProgress();

                    graphics.pose().pushMatrix();
                    float cardCenterX = contentX + 24 + cardW / 2.0f;
                    float cardCenterY = startOptionY + cardH / 2.0f;
                    graphics.pose().scaleAround(popScale, popScale, cardCenterX, cardCenterY);

                    // Draw Modern Card Frame
                    ModernGuiUtils.drawModernCard(graphics, contentX + 24, startOptionY, cardW, cardH, isCardHovered, false);

                    // Option Title
                    graphics.text(this.font, Component.literal(opt.getTitle()), contentX + 40, startOptionY + 12, ModernGuiUtils.COLOR_TEXT_PRIMARY);

                    // Option Category Tag & Description
                    String desc = opt.getDescription();
                    graphics.text(this.font, Component.literal(desc), contentX + 40, startOptionY + 28, ModernGuiUtils.COLOR_TEXT_MUTED);

                    // Render Right-Hand Control Widget
                    int widgetW = 120;
                    int widgetH = 26;
                    int widgetX = contentX + 24 + cardW - widgetW - 16;
                    int widgetY = startOptionY + (cardH - widgetH) / 2;

                    boolean isWidgetHovered = mouseX >= widgetX && mouseX <= widgetX + widgetW && mouseY >= widgetY && mouseY <= widgetY + widgetH;

                    if (opt.getType() == ConfigOption.Type.BOOLEAN) {
                        ModernGuiUtils.drawModernToggle(graphics, this.font, widgetX, widgetY, widgetW, widgetH, opt.getBool(), isWidgetHovered);
                    } else if (opt.getType() == ConfigOption.Type.SLIDER) {
                        double normVal = opt.getSliderNormalizedValue();
                        ModernGuiUtils.drawModernSlider(graphics, this.font, widgetX, widgetY, widgetW, widgetH, normVal, opt.getFormattedValue(), isWidgetHovered);
                    } else if (opt.getType() == ConfigOption.Type.ACTION) {
                        ModernGuiUtils.drawModernButton(graphics, this.font, widgetX, widgetY, widgetW, widgetH, opt.getActionLabel(), isWidgetHovered, false);
                    }

                    graphics.pose().popMatrix();
                }

                startOptionY += cardH + 10;
            }
        }

        if (isAnimatingOpen) {
            graphics.pose().popMatrix();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        int headerHeight = 46;
        int sidebarWidth = 220;

        // Close / Done button click
        int closeW = 90;
        int closeH = 26;
        int closeX = this.width - closeW - 16;
        int closeY = 10;
        if (mouseX >= closeX && mouseX <= closeX + closeW && mouseY >= closeY && mouseY <= closeY + closeH) {
            playPloppSound();
            this.onClose();
            return true;
        }

        // Search bar click
        int searchW = 260;
        int searchH = 26;
        int searchX = this.width - 220 - searchW;
        int searchY = 10;

        if (searchQuery.length() > 0) {
            // Check clear button '✕'
            int clearX = searchX + searchW - 20;
            int clearY = searchY + 4;
            if (mouseX >= clearX && mouseX <= clearX + 16 && mouseY >= clearY && mouseY <= clearY + 18) {
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
        if (mouseX >= 0 && mouseX <= sidebarWidth && mouseY >= headerHeight) {
            ConfigCategory[] categories = ConfigCategory.values();
            int catY = headerHeight + 12;
            int catH = 36;
            int catW = sidebarWidth - 16;

            for (ConfigCategory cat : categories) {
                if (mouseX >= 8 && mouseX <= 8 + catW && mouseY >= catY && mouseY <= catY + catH) {
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

        // Options List click
        int contentX = sidebarWidth;
        int contentY = headerHeight;
        int contentW = this.width - sidebarWidth;
        int contentH = this.height - headerHeight;

        if (mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= contentY && mouseY <= contentY + contentH) {
            List<ConfigOption> options = AlpakaConfigRegistry.getOptions(activeCategory, searchQuery);
            int startOptionY = contentY + 20 + 36 - (int) scrollY;
            int cardW = contentW - 48;
            int cardH = 54;

            for (ConfigOption opt : options) {
                if (opt.getType() == ConfigOption.Type.HEADER) {
                    startOptionY += 36;
                    continue;
                }

                int widgetW = 120;
                int widgetH = 26;
                int widgetX = contentX + 24 + cardW - widgetW - 16;
                int widgetY = startOptionY + (cardH - widgetH) / 2;

                boolean isWidgetClicked = mouseX >= widgetX && mouseX <= widgetX + widgetW && mouseY >= widgetY && mouseY <= widgetY + widgetH;
                boolean isCardClicked = mouseX >= contentX + 24 && mouseX <= contentX + 24 + cardW && mouseY >= startOptionY && mouseY <= startOptionY + cardH;

                if (isWidgetClicked || isCardClicked) {
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

                startOptionY += cardH + 10;
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
            int headerHeight = 46;
            int sidebarWidth = 220;
            int contentX = sidebarWidth;
            int contentW = this.width - sidebarWidth;
            int cardW = contentW - 48;
            int widgetW = 120;
            int widgetX = contentX + 24 + cardW - widgetW - 16;

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
