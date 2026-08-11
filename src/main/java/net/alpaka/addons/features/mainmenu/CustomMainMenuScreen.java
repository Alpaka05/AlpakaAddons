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
        int sidebarW = 220;
        int sidebarH = 310;
        int sidebarY = (this.height - sidebarH) / 2;

        int innerX = sidebarX + 14;
        int innerW = sidebarW - 28;

        // 1. Hero Featured "PLAY HYPIXEL" Button (Taller 44px Card)
        int heroY = sidebarY + 58;
        this.addRenderableWidget(new HeroJoinButton(innerX, heroY, innerW, 44, btn -> joinServer("mc.hypixel.net")));

        // 2. Secondary Server / Game Mode Buttons
        int listY = sidebarY + 114;
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
        int bottomY = sidebarY + 264;
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
        int sidebarW = 220;
        int sidebarH = 310;
        int sidebarY = (this.height - sidebarH) / 2;

        // Multi-Layer Drop Shadow for Sidebar Panel
        for (int i = 1; i <= 6; i++) {
            int alpha = (int) (0x20 * (1.0f - (float) i / 6.0f));
            ModernGuiUtils.drawRect(graphics, sidebarX - i, sidebarY - i, sidebarW + i * 2, sidebarH + i * 2, (alpha << 24));
        }

        // Sidebar Panel Base & Frame
        ModernGuiUtils.drawRect(graphics, sidebarX, sidebarY, sidebarW, sidebarH, ModernGuiUtils.COLOR_PANEL_BG);
        ModernGuiUtils.drawOutline(graphics, sidebarX, sidebarY, sidebarW, sidebarH, ModernGuiUtils.COLOR_CARD_BORDER);

        // Top Accent Line
        ModernGuiUtils.drawRect(graphics, sidebarX, sidebarY, sidebarW, 3, ModernGuiUtils.COLOR_ACCENT);

        // Header Title (Single occurrence of Hypixel Network)
        int logoY = sidebarY + 16;
        String titleText = "HYPIXEL NETWORK";
        graphics.centeredText(this.font, Component.literal(titleText), sidebarX + sidebarW / 2, logoY, ModernGuiUtils.COLOR_ACCENT);
        graphics.centeredText(this.font, Component.literal("mc.hypixel.net"), sidebarX + sidebarW / 2, logoY + 12, ModernGuiUtils.COLOR_TEXT_MUTED);

        // Section Dividers
        ModernGuiUtils.drawRect(graphics, sidebarX + 14, sidebarY + 252, sidebarW - 28, 1, ModernGuiUtils.COLOR_CARD_BORDER);

        // Footer Version Label
        graphics.text(this.font, Component.literal("AlpakaAddons v1.0.29"), 12, this.height - 20, ModernGuiUtils.COLOR_TEXT_PRIMARY);
        graphics.text(this.font, Component.literal("Minecraft 1.21.1 • Fabric"), 12, this.height - 10, ModernGuiUtils.COLOR_TEXT_MUTED);
    }

    // Hero Featured Join Button with Premium Gold Gradient & Glow
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

            int bg = hovered ? 0xEE443515 : 0xCC2A200C;
            int border = hovered ? 0xFFF59E0B : ModernGuiUtils.COLOR_ACCENT;
            int mainTextCol = hovered ? 0xFFFFFFFF : ModernGuiUtils.COLOR_ACCENT;

            // Hero Card Base & Outline
            ModernGuiUtils.drawRect(graphics, x, drawY, w, h, bg);
            ModernGuiUtils.drawOutline(graphics, x, drawY, w, h, border);
            if (hovered) {
                ModernGuiUtils.drawOutline(graphics, x - 1, drawY - 1, w + 2, h + 2, 0x60F59E0B);
            }

            // Hero Main Title & Subtitle Text
            graphics.centeredText(CustomMainMenuScreen.this.font, Component.literal("JOIN HYPIXEL"), x + w / 2, drawY + 9, mainTextCol);
            graphics.centeredText(CustomMainMenuScreen.this.font, Component.literal("mc.hypixel.net"), x + w / 2, drawY + 23, 0xFFD4AF37);
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
