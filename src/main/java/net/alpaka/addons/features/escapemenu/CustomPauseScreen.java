package net.alpaka.addons.features.escapemenu;

import net.alpaka.addons.client.AlpakaConfigScreen;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public class CustomPauseScreen extends Screen {
    private boolean showDisconnectPrompt = false;
    private long openTime = 0L;

    private CustomPauseButton resumeButton;
    private CustomPauseButton configButton;
    private CustomPauseButton modsButton;
    private CustomPauseButton optionsButton;
    private CustomPauseButton wikiButton;
    private CustomPauseButton disconnectButton;

    private CustomPauseButton promptCancelButton;
    private CustomPauseButton promptConfirmButton;

    public CustomPauseScreen() {
        super(Component.literal("Custom Pause Screen"));
    }

    @Override
    protected void init() {
        this.openTime = System.currentTimeMillis();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int cardWidth = 240;
        int cardHeight = 275;
        int startX = centerX - cardWidth / 2;
        int startY = centerY - cardHeight / 2;

        int buttonWidth = 200;
        int buttonHeight = 28;
        int buttonX = centerX - buttonWidth / 2;
        int buttonStartY = startY + 45;
        int spacing = 36;

        // 1. Resume Button
        this.resumeButton = new CustomPauseButton(buttonX, buttonStartY, buttonWidth, buttonHeight,
                Component.literal("▶  Resume Game"), false, btn -> this.onClose());
        this.addRenderableWidget(this.resumeButton);

        // 2. Alpaka Config Button
        this.configButton = new CustomPauseButton(buttonX, buttonStartY + spacing, buttonWidth, buttonHeight,
                Component.literal("⚙  Alpaka Config"), false, btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new AlpakaConfigScreen(this));
            }
        });
        this.addRenderableWidget(this.configButton);

        // 3. Mods Button (Mod Menu mod list)
        this.modsButton = new CustomPauseButton(buttonX, buttonStartY + spacing * 2, buttonWidth, buttonHeight,
                Component.literal("📦  Mods"), false, btn -> {
            if (this.minecraft != null) {
                try {
                    this.minecraft.setScreen(new com.terraformersmc.modmenu.gui.ModsScreen(this));
                } catch (Throwable t) {
                    this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options, false));
                }
            }
        });
        this.addRenderableWidget(this.modsButton);

        // 4. Minecraft Options Button
        this.optionsButton = new CustomPauseButton(buttonX, buttonStartY + spacing * 3, buttonWidth, buttonHeight,
                Component.literal("🛠  Options"), false, btn -> {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options, false));
            }
        });
        this.addRenderableWidget(this.optionsButton);

        // 5. Skyblock Wiki Button
        this.wikiButton = new CustomPauseButton(buttonX, buttonStartY + spacing * 4, buttonWidth, buttonHeight,
                Component.literal("📖  Skyblock Wiki"), false, btn -> {
            if (this.minecraft != null) {
                ConfirmLinkScreen.confirmLinkNow(this, "https://hypixelskyblock.minecraft.wiki/");
            }
        });
        this.addRenderableWidget(this.wikiButton);

        // 6. Disconnect Button (Red Accent)
        boolean isSingleplayer = this.minecraft != null && this.minecraft.isSingleplayer();
        Component disconnectText = isSingleplayer ? Component.literal("🚪  Save & Quit") : Component.literal("🚪  Disconnect");
        this.disconnectButton = new CustomPauseButton(buttonX, buttonStartY + spacing * 5, buttonWidth, buttonHeight,
                disconnectText, true, btn -> {
            this.showDisconnectPrompt = true;
            this.updateWidgetStates();
        });
        this.addRenderableWidget(this.disconnectButton);

        // Disconnect Prompt Buttons
        int promptWidth = 240;
        int promptHeight = 135;
        int promptX = centerX - promptWidth / 2;
        int promptY = centerY - promptHeight / 2;

        int pBtnWidth = 95;
        int pBtnHeight = 24;
        int pBtnY = promptY + 95;

        this.promptCancelButton = new CustomPauseButton(promptX + 20, pBtnY, pBtnWidth, pBtnHeight,
                Component.literal("Cancel"), false, btn -> {
            this.showDisconnectPrompt = false;
            this.updateWidgetStates();
        });

        this.promptConfirmButton = new CustomPauseButton(promptX + promptWidth - 20 - pBtnWidth, pBtnY, pBtnWidth, pBtnHeight,
                Component.literal("Disconnect"), true, btn -> {
            if (this.minecraft != null) {
                if (this.minecraft.level != null) {
                    this.minecraft.level.disconnect(Component.literal("Disconnected"));
                }
                this.minecraft.disconnect(new TitleScreen(), false);
            }
        });

        this.updateWidgetStates();
    }

    private void updateWidgetStates() {
        boolean mainActive = !this.showDisconnectPrompt;
        if (this.resumeButton != null) this.resumeButton.active = mainActive;
        if (this.configButton != null) this.configButton.active = mainActive;
        if (this.modsButton != null) this.modsButton.active = mainActive;
        if (this.optionsButton != null) this.optionsButton.active = mainActive;
        if (this.wikiButton != null) this.wikiButton.active = mainActive;
        if (this.disconnectButton != null) this.disconnectButton.active = mainActive;

        if (this.promptCancelButton != null) this.promptCancelButton.active = this.showDisconnectPrompt;
        if (this.promptConfirmButton != null) this.promptConfirmButton.active = this.showDisconnectPrompt;
    }

    private float getAnimationScale() {
        long elapsed = System.currentTimeMillis() - this.openTime;
        float t = Math.min(1.0f, elapsed / 200.0f);
        // Smooth Ease-Out Back spring curve
        return (float) (0.82 + 0.18 * (1.0 - Math.pow(1.0 - t, 3.0)) + 0.05 * Math.sin(t * Math.PI) * (1.0 - t));
    }

    private static void fillRoundedRect(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int r, int color) {
        if (r <= 0) {
            graphics.fill(x, y, x + w, y + h, color);
            return;
        }
        r = Math.min(r, Math.min(w, h) / 2);
        for (int iy = 0; iy < h; iy++) {
            int dx = 0;
            if (iy < r) {
                int dy = r - 1 - iy;
                dx = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            } else if (iy >= h - r) {
                int dy = iy - (h - r);
                dx = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            }
            graphics.fill(x + dx, y + iy, x + w - dx, y + iy + 1, color);
        }
    }

    private static void drawRoundedOutline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int r, int color) {
        r = Math.min(r, Math.min(w, h) / 2);
        for (int iy = 0; iy < h; iy++) {
            int dx = 0;
            if (iy < r) {
                int dy = r - 1 - iy;
                dx = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            } else if (iy >= h - r) {
                int dy = iy - (h - r);
                dx = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            }
            if (iy == 0 || iy == h - 1) {
                graphics.fill(x + dx, y + iy, x + w - dx, y + iy + 1, color);
            } else {
                graphics.fill(x + dx, y + iy, x + dx + 1, y + iy + 1, color);
                graphics.fill(x + w - 1 - dx, y + iy, x + w - dx, y + iy + 1, color);
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        long elapsed = System.currentTimeMillis() - this.openTime;
        float progress = Math.min(1.0f, elapsed / 200.0f);

        // Dark Backdrop Overlay with fade-in
        int alpha = (int) (0x88 * progress);
        graphics.fill(0, 0, this.width, this.height, (alpha << 24) | 0x0B0F17);

        // Apply smooth scale pop-up animation to main card
        float scale = getAnimationScale();
        graphics.pose().pushMatrix();
        graphics.pose().scaleAround(scale, scale, centerX, centerY);

        int cardHeight = 275;
        int startY = centerY - cardHeight / 2;

        // Subtitle Status
        String status = (this.minecraft != null && this.minecraft.isSingleplayer()) ? "Singleplayer World" : "Multiplayer Server";
        String user = (this.minecraft != null && this.minecraft.getUser() != null) ? this.minecraft.getUser().getName() : "Player";
        graphics.centeredText(this.font, Component.literal(user + " • " + status), centerX, startY + 20, 0xFF94A3B8);

        graphics.pose().popMatrix();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        float scale = getAnimationScale();

        graphics.pose().pushMatrix();
        graphics.pose().scaleAround(scale, scale, centerX, centerY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // If Disconnect Prompt is Active: Draw Dark Overlay, Modal Card Box and Prompt Buttons IN FRONT OF EVERYTHING
        if (this.showDisconnectPrompt) {
            // Full-screen Dim Overlay inside scale
            graphics.fill(0, 0, this.width, this.height, 0xAA000000);

            int promptWidth = 240;
            int promptHeight = 135;
            int promptX = centerX - promptWidth / 2;
            int promptY = centerY - promptHeight / 2;

            // Modal Card Box with Red Border Accent
            fillRoundedRect(graphics, promptX - 1, promptY - 1, promptWidth + 2, promptHeight + 2, 10, 0x30EF4444);
            fillRoundedRect(graphics, promptX, promptY, promptWidth, promptHeight, 9, 0x751A1A1C);

            // Modal Text
            graphics.centeredText(this.font, Component.literal("Leave Server?"), centerX, promptY + 15, 0xFFEF4444);
            graphics.centeredText(this.font, Component.literal("Bist du sicher, dass du bereits"), centerX, promptY + 42, 0xFFF8FAFC);
            graphics.centeredText(this.font, Component.literal("das Spiel verlassen möchtest?"), centerX, promptY + 56, 0xFFCBD5E1);

            // Render Prompt Buttons in Front
            if (this.promptCancelButton != null) {
                this.promptCancelButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }
            if (this.promptConfirmButton != null) {
                this.promptConfirmButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }
        }

        graphics.pose().popMatrix();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.showDisconnectPrompt) {
            if (this.promptCancelButton != null && this.promptCancelButton.mouseClicked(event, doubleClick)) {
                return true;
            }
            if (this.promptConfirmButton != null && this.promptConfirmButton.mouseClicked(event, doubleClick)) {
                return true;
            }
            return true; // Block clicks on underlying elements
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) { // GLFW_KEY_ESCAPE
            if (this.showDisconnectPrompt) {
                this.showDisconnectPrompt = false;
                this.updateWidgetStates();
                return true;
            }
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    // Modern Animated Custom Button Class
    private static class CustomPauseButton extends AbstractButton {
        private final boolean isRed;
        private final ButtonAction action;
        private float hoverTime = 0.0f;

        public CustomPauseButton(int x, int y, int width, int height, Component message, boolean isRed, ButtonAction action) {
            super(x, y, width, height, message);
            this.isRed = isRed;
            this.action = action;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            if (CustomSoundFeature.BUTTON_CLICK_SOUND != null && Minecraft.getInstance().getSoundManager() != null) {
                Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(CustomSoundFeature.BUTTON_CLICK_SOUND, 1.0f)
                );
            }
            if (this.action != null) {
                this.action.onPress(this);
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = mouseX >= this.getX() && mouseX < this.getX() + this.width &&
                              mouseY >= this.getY() && mouseY < this.getY() + this.height && this.active;

            // Interpolate smooth hover animation
            float targetHover = hovered ? 1.0f : 0.0f;
            this.hoverTime += (targetHover - this.hoverTime) * 0.25f;

            int x = this.getX();
            int y = this.getY();
            int w = this.width;
            int h = this.height;

            // Smoothly slide button up 2px on hover
            int yOffset = (int) (-2.0f * this.hoverTime);
            int drawY = y + yOffset;

            // Colors (modern transparent glassmorphism in white/gray, white highlights on hover)
            int bgNormal = this.isRed ? 0x12EF4444 : 0x10FFFFFF;
            int bgHover = this.isRed ? 0x50EF4444 : 0x48FFFFFF;
            int borderNormal = this.isRed ? 0x20EF4444 : 0x15FFFFFF;
            int borderHover = this.isRed ? 0xD0EF4444 : 0xD0FFFFFF;

            int currentBg = interpolateColor(bgNormal, bgHover, this.hoverTime);
            int currentBorder = interpolateColor(borderNormal, borderHover, this.hoverTime);

            // Draw Button Box
            fillRoundedRect(graphics, x, drawY, w, h, 8, currentBg);
            
            // Draw Button 1px Border Outline
            drawRoundedOutline(graphics, x, drawY, w, h, 8, currentBorder);

            // Draw Button Text (Fades to bright white on hover)
            int textNormal = this.active ? 0xCCF1F5F9 : 0x4094A3B8;
            int textHover = this.active ? 0xFFFFFFFF : 0x4094A3B8;
            int textColor = interpolateColor(textNormal, textHover, this.hoverTime);

            graphics.centeredText(Minecraft.getInstance().font, this.getMessage(), x + w / 2, drawY + (h - 8) / 2, textColor);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {}

        private int interpolateColor(int c1, int c2, float factor) {
            int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
            int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;

            int a = (int) (a1 + (a2 - a1) * factor);
            int r = (int) (r1 + (r2 - r1) * factor);
            int g = (int) (g1 + (g2 - g1) * factor);
            int b = (int) (b1 + (b2 - b1) * factor);

            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    @FunctionalInterface
    public interface ButtonAction {
        void onPress(CustomPauseButton button);
    }
}
