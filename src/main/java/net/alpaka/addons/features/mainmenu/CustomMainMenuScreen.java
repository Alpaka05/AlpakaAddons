package net.alpaka.addons.features.mainmenu;

import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;

public class CustomMainMenuScreen extends Screen {

    public CustomMainMenuScreen() {
        super(Component.literal("Custom Main Menu"));
    }

    private void playPloppSound() {
        try {
            CustomSoundFeature.playButtonClickSound();
        } catch (Throwable ignored) {}
    }

    private void joinServer(String ip) {
        if (this.minecraft == null) return;
        playPloppSound();
        ServerAddress address = ServerAddress.parseString(ip);
        ServerData data = new ServerData(ip.contains("alpha") ? "Hypixel Alpha" : "Hypixel Network", ip, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(this, this.minecraft, address, data, false, null);
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int sidebarX = 40;
        int sidebarW = 230;
        int sidebarH = 320;
        int sidebarY = (this.height - sidebarH) / 2;

        int innerX = sidebarX + 14;
        int innerW = sidebarW - 28;

        // 1. Featured Ornate "JOIN HYPIXEL" Hero Action Button (Taller 50px Card)
        int heroY = sidebarY + 54;
        this.addRenderableWidget(new HeroJoinButton(innerX, heroY, innerW, 50, btn -> joinServer("mc.hypixel.net")));

        // 2. Secondary Server / Game Mode Buttons
        int listY = sidebarY + 116;
        int btnH = 26;
        int spacing = 32;

        // Join Alpha
        this.addRenderableWidget(new CustomMenuButton(innerX, listY, innerW, btnH,
                Component.literal("Join Alpha"), false, btn -> joinServer("alpha.hypixel.net")));

        // Multiplayer
        this.addRenderableWidget(new CustomMenuButton(innerX, listY + spacing, innerW, btnH,
                Component.literal("Multiplayer"), false, btn -> {
            if (this.minecraft != null) this.minecraft.setScreen(new JoinMultiplayerScreen(this));
        }));

        // Singleplayer
        this.addRenderableWidget(new CustomMenuButton(innerX, listY + spacing * 2, innerW, btnH,
                Component.literal("Singleplayer"), false, btn -> {
            if (this.minecraft != null) this.minecraft.setScreen(new SelectWorldScreen(this));
        }));

        // 3. Bottom System Actions Row (Options & Quit side by side)
        int bottomY = sidebarY + 270;
        int halfW = (innerW - 8) / 2;

        // Options
        this.addRenderableWidget(new CustomMenuButton(innerX, bottomY, halfW, btnH,
                Component.literal("Options"), false, btn -> {
            if (this.minecraft != null) this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options, false));
        }));

        // Quit Game
        this.addRenderableWidget(new CustomMenuButton(innerX + halfW + 8, bottomY, halfW, btnH,
                Component.literal("Quit"), true, btn -> {
            if (this.minecraft != null) this.minecraft.stop();
        }));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Render 3D Panorama from Minecraft / Active Resource Pack
        this.extractPanorama(graphics, partialTick);

        // Light translucent veil over panorama so text & sidebar remain clean & readable
        graphics.fill(0, 0, this.width, this.height, 0x30000000);

        int sidebarX = 40;
        int sidebarW = 230;
        int sidebarH = 320;
        int sidebarY = (this.height - sidebarH) / 2;

        // Multi-Layer Drop Shadow for Sidebar Panel
        for (int i = 1; i <= 6; i++) {
            int alpha = (int) (0x20 * (1.0f - (float) i / 6.0f));
            ModernGuiUtils.drawRect(graphics, sidebarX - i, sidebarY - i, sidebarW + i * 2, sidebarH + i * 2, (alpha << 24));
        }

        // Sidebar Panel Base & Frame
        ModernGuiUtils.drawRect(graphics, sidebarX, sidebarY, sidebarW, sidebarH, ModernGuiUtils.COLOR_PANEL_BG);
        ModernGuiUtils.drawOutline(graphics, sidebarX, sidebarY, sidebarW, sidebarH, ModernGuiUtils.COLOR_CARD_BORDER);

        // Top Gold Accent Line
        ModernGuiUtils.drawRect(graphics, sidebarX, sidebarY, sidebarW, 3, ModernGuiUtils.COLOR_ACCENT);

        // Render Fancy Ornate AlpakaAddons Header Banner Logo
        renderFancyHeader(graphics, sidebarX, sidebarW, sidebarY);

        // Section Divider Line
        ModernGuiUtils.drawRect(graphics, sidebarX + 14, sidebarY + 258, sidebarW - 28, 1, ModernGuiUtils.COLOR_CARD_BORDER);

        // Footer Version Label
        graphics.text(this.font, Component.literal("AlpakaAddons v1.0.29"), 12, this.height - 20, ModernGuiUtils.COLOR_TEXT_PRIMARY);
        graphics.text(this.font, Component.literal("Minecraft 1.21.1 • Fabric"), 12, this.height - 10, ModernGuiUtils.COLOR_TEXT_MUTED);
    }

    // Fancy Ornate AlpakaAddons Header Banner Logo Vector Engine
    private void renderFancyHeader(GuiGraphicsExtractor graphics, int sidebarX, int sidebarW, int sidebarY) {
        int centerX = sidebarX + sidebarW / 2;
        int headY = sidebarY + 12;

        // Top Golden Decorative Crown Crest
        ModernGuiUtils.drawRect(graphics, centerX - 12, sidebarY + 5, 24, 2, ModernGuiUtils.COLOR_ACCENT);
        ModernGuiUtils.drawRect(graphics, centerX - 6, sidebarY + 3, 12, 2, 0xFFF59E0B);
        ModernGuiUtils.drawRect(graphics, centerX - 2, sidebarY + 1, 4, 2, 0xFFFFFFFF);

        // Decorative Golden Wing Ornaments (Left & Right)
        for (int i = 0; i < 4; i++) {
            int lineW = 32 - i * 6;
            ModernGuiUtils.drawRect(graphics, sidebarX + 16, headY + i * 3, lineW, 1, 0xFFE5B849);
            ModernGuiUtils.drawRect(graphics, sidebarX + sidebarW - 16 - lineW, headY + i * 3, lineW, 1, 0xFFE5B849);
        }

        // Main Title: "ALPAKA ADDONS" with 3D Gold Shadow
        graphics.centeredText(this.font, Component.literal("ALPAKA ADDONS"), centerX + 1, headY + 1, 0xFF000000); // 3D Drop Shadow
        graphics.centeredText(this.font, Component.literal("ALPAKA ADDONS"), centerX, headY, ModernGuiUtils.COLOR_ACCENT);

        // Subtitle IP Badge: "HYPIXEL DASHBOARD"
        int badgeW = 124;
        int badgeX = centerX - badgeW / 2;
        int badgeY = headY + 14;
        ModernGuiUtils.drawRect(graphics, badgeX, badgeY, badgeW, 13, 0x40E5B849);
        ModernGuiUtils.drawOutline(graphics, badgeX, badgeY, badgeW, 13, ModernGuiUtils.COLOR_ACCENT_DIM);
        graphics.centeredText(this.font, Component.literal("HYPIXEL DASHBOARD"), centerX, badgeY + 3, 0xFFF59E0B);
    }

    // Hero Featured Join Button with Premium Gold Gradient, Double Outlines & Corner Ornaments
    private class HeroJoinButton extends AbstractButton {
        private final MenuButtonAction action;
        private float hoverTime = 0.0f;

        public HeroJoinButton(int x, int y, int width, int height, MenuButtonAction action) {
            super(x, y, width, height, Component.literal(""));
            this.action = action;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            CustomSoundFeature.playButtonClickSound();
            if (this.action != null) {
                this.action.onPress(null);
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = mouseX >= this.getX() && mouseX < this.getX() + this.width &&
                              mouseY >= this.getY() && mouseY < this.getY() + this.height && this.active;

            float targetHover = hovered ? 1.0f : 0.0f;
            this.hoverTime += (targetHover - this.hoverTime) * 0.25f;

            int x = this.getX();
            int y = this.getY();
            int w = this.width;
            int h = this.height;

            int drawY = y + (int) (-2.0f * this.hoverTime);

            int bg = hovered ? 0xEE3D2F12 : 0xCC2A200C;
            int border = hovered ? 0xFFF59E0B : ModernGuiUtils.COLOR_ACCENT;
            int mainTextCol = hovered ? 0xFFFFFFFF : ModernGuiUtils.COLOR_ACCENT;

            // 1. Hero Card Base Fill
            ModernGuiUtils.drawRect(graphics, x, drawY, w, h, bg);

            // 2. Double Gold Frame & Hover Glow
            ModernGuiUtils.drawOutline(graphics, x, drawY, w, h, border);
            ModernGuiUtils.drawOutline(graphics, x + 2, drawY + 2, w - 4, h - 4, 0x40E5B849);

            if (hovered) {
                ModernGuiUtils.drawOutline(graphics, x - 1, drawY - 1, w + 2, h + 2, 0x80F59E0B);
            }

            // 3. Ornate Golden Corner Gemstones / Brackets
            ModernGuiUtils.drawRect(graphics, x + 3, drawY + 3, 4, 4, border);
            ModernGuiUtils.drawRect(graphics, x + w - 7, drawY + 3, 4, 4, border);
            ModernGuiUtils.drawRect(graphics, x + 3, drawY + h - 7, 4, 4, border);
            ModernGuiUtils.drawRect(graphics, x + w - 7, drawY + h - 7, 4, 4, border);

            // 4. Hero Main Title & Subtitle Labels
            graphics.centeredText(CustomMainMenuScreen.this.font, Component.literal("▶  JOIN HYPIXEL"), x + w / 2, drawY + 11, mainTextCol);
            graphics.centeredText(CustomMainMenuScreen.this.font, Component.literal("mc.hypixel.net • Click to Join"), x + w / 2, drawY + 27, 0xFFD4AF37);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {}
    }

    // Modern Animated Custom Button Class matching Mod Config & Pause Theme
    private static class CustomMenuButton extends AbstractButton {
        private final boolean isRed;
        private final MenuButtonAction action;
        private float hoverTime = 0.0f;

        public CustomMenuButton(int x, int y, int width, int height, Component message, boolean isRed, MenuButtonAction action) {
            super(x, y, width, height, message);
            this.isRed = isRed;
            this.action = action;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            CustomSoundFeature.playButtonClickSound();
            if (this.action != null) {
                this.action.onPress(this);
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = mouseX >= this.getX() && mouseX < this.getX() + this.width &&
                              mouseY >= this.getY() && mouseY < this.getY() + this.height && this.active;

            float targetHover = hovered ? 1.0f : 0.0f;
            this.hoverTime += (targetHover - this.hoverTime) * 0.25f;

            int x = this.getX();
            int y = this.getY();
            int w = this.width;
            int h = this.height;

            int yOffset = (int) (-2.0f * this.hoverTime);
            int drawY = y + yOffset;

            int bg = hovered ? (this.isRed ? 0x40EF4444 : ModernGuiUtils.COLOR_CARD_BG_HOVER) : ModernGuiUtils.COLOR_CARD_BG;
            int border = hovered ? (this.isRed ? 0xFFEF4444 : ModernGuiUtils.COLOR_ACCENT) : ModernGuiUtils.COLOR_CARD_BORDER;
            int textColor = hovered ? (this.isRed ? 0xFFEF4444 : ModernGuiUtils.COLOR_ACCENT) : ModernGuiUtils.COLOR_TEXT_PRIMARY;

            ModernGuiUtils.drawRect(graphics, x, drawY, w, h, bg);
            ModernGuiUtils.drawOutline(graphics, x, drawY, w, h, border);

            graphics.centeredText(Minecraft.getInstance().font, this.getMessage(), x + w / 2, drawY + (h - 8) / 2, textColor);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {}
    }

    @FunctionalInterface
    public interface MenuButtonAction {
        void onPress(CustomMenuButton button);
    }
}
