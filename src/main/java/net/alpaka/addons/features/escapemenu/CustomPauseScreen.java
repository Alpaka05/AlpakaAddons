package net.alpaka.addons.features.escapemenu;

import net.alpaka.addons.client.AlpakaConfigScreen;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.client.gui.PloppAnimation;
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
        super(Component.literal("Alpaka Escape Menu"));
    }

    @Override
    protected void init() {
        this.openTime = System.currentTimeMillis();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        int cardWidth = 200;
        int cardHeight = 250;
        int startX = centerX - cardWidth / 2;
        int startY = centerY - cardHeight / 2;

        int buttonWidth = 168;
        int buttonHeight = 26;
        int buttonX = centerX - buttonWidth / 2;
        int buttonStartY = startY + 46;
        int spacing = 32;

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
        int promptWidth = 220;
        int promptHeight = 130;
        int promptX = centerX - promptWidth / 2;
        int promptY = centerY - promptHeight / 2;

        int pBtnWidth = 88;
        int pBtnHeight = 24;
        int pBtnY = promptY + 88;

        this.promptCancelButton = new CustomPauseButton(promptX + 16, pBtnY, pBtnWidth, pBtnHeight,
                Component.literal("Cancel"), false, btn -> {
            this.showDisconnectPrompt = false;
            this.updateWidgetStates();
        });

        this.promptConfirmButton = new CustomPauseButton(promptX + promptWidth - 16 - pBtnWidth, pBtnY, pBtnWidth, pBtnHeight,
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

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        float elapsedSec = (System.currentTimeMillis() - openTime) / 1000.0f;
        float animProgress = Math.min(1.0f, elapsedSec / 0.15f); // 150ms fast snappy animation
        float anim = 1.0f - (float) Math.pow(1.0f - animProgress, 3); // Smooth cubic ease-out

        float slideOffsetY = (1.0f - anim) * 24.0f; // Subtle 24px slide up from slightly below

        // Dark translucent backdrop. Deliberately not animated: only the panel should slide and
        // fade, while the darkening behind it stays put from the first frame. Drawn against an
        // identity matrix rather than whatever is already on the pose stack - other installed GUI
        // mods (SmoothGui and friends) apply their own open-transition transform around Screen's
        // render calls, and without this the fill inherited that transform and slid along with it.
        graphics.pose().pushMatrix();
        graphics.pose().identity();
        graphics.fill(0, 0, this.width, this.height, 0x70000000);
        graphics.pose().popMatrix();

        int cardWidth = 200;
        int cardHeight = 250;
        int startX = centerX - cardWidth / 2;
        int startY = centerY - cardHeight / 2;

        // Smooth Slide Up Animation
        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0f, slideOffsetY);

        // Soft Multi-Layer Drop Shadow
        for (int i = 1; i <= 6; i++) {
            int shadowAlpha = (int) ((0x24 * anim) * (1.0f - (float) i / 6.0f));
            ModernGuiUtils.drawRect(graphics, startX - i, startY - i, cardWidth + i * 2, cardHeight + i * 2, (shadowAlpha << 24));
        }
        ModernGuiUtils.drawOutline(graphics, startX - 1, startY - 1, cardWidth + 2, cardHeight + 2, 0x60000000);

        // Outer Panel Base & Border
        ModernGuiUtils.drawRect(graphics, startX, startY, cardWidth, cardHeight, ModernGuiUtils.COLOR_PANEL_BG);
        ModernGuiUtils.drawOutline(graphics, startX, startY, cardWidth, cardHeight, ModernGuiUtils.COLOR_CARD_BORDER);

        // Top Header Bar
        int headerH = 38;
        ModernGuiUtils.drawRect(graphics, startX, startY, cardWidth, headerH, ModernGuiUtils.COLOR_SIDEBAR_BG);
        ModernGuiUtils.drawRect(graphics, startX, startY + headerH - 1, cardWidth, 1, ModernGuiUtils.getAccentColor());

        // Header Title & Version
        graphics.centeredText(this.font, Component.literal("ALPAKA ADDONS"), centerX, startY + 8, ModernGuiUtils.COLOR_TEXT_PRIMARY);

        String status = (this.minecraft != null && this.minecraft.isSingleplayer()) ? "Singleplayer" : "Multiplayer";
        String user = (this.minecraft != null && this.minecraft.getUser() != null) ? this.minecraft.getUser().getName() : "Player";
        graphics.centeredText(this.font, Component.literal(user + " • " + status), centerX, startY + 22, ModernGuiUtils.COLOR_TEXT_MUTED);

        graphics.pose().popMatrix();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        float elapsedSec = (System.currentTimeMillis() - openTime) / 1000.0f;
        float animProgress = Math.min(1.0f, elapsedSec / 0.15f);
        float anim = 1.0f - (float) Math.pow(1.0f - animProgress, 3);
        float slideOffsetY = (1.0f - anim) * 24.0f;

        // The modal's full-screen dim is drawn outside the slide transform below, for the same
        // reason the backdrop is: a full-screen darkening must never move with the panel. Unlike
        // the backdrop, this runs inside extractRenderState, which is exactly the call other GUI
        // mods animate - so the pose stack can already carry an inherited transform before this
        // method even starts, and it needs the same identity reset to stay put.
        if (this.showDisconnectPrompt) {
            graphics.pose().pushMatrix();
            graphics.pose().identity();
            graphics.fill(0, 0, this.width, this.height, 0x90000000);
            graphics.pose().popMatrix();
        }

        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0f, slideOffsetY);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // Disconnect Modal Prompt Overlay
        if (this.showDisconnectPrompt) {
            int promptWidth = 250;
            int promptHeight = 140;
            int promptX = centerX - promptWidth / 2;
            int promptY = centerY - promptHeight / 2;

            // Modal Drop Shadow & Card Frame
            for (int i = 1; i <= 5; i++) {
                int shadowAlpha = (int) (0x20 * (1.0f - (float) i / 5.0f));
                ModernGuiUtils.drawRect(graphics, promptX - i, promptY - i, promptWidth + i * 2, promptHeight + i * 2, (shadowAlpha << 24));
            }
            ModernGuiUtils.drawRect(graphics, promptX, promptY, promptWidth, promptHeight, ModernGuiUtils.COLOR_PANEL_BG);
            ModernGuiUtils.drawOutline(graphics, promptX, promptY, promptWidth, promptHeight, 0xFFEF4444);

            // Header Line
            ModernGuiUtils.drawRect(graphics, promptX, promptY, promptWidth, 32, ModernGuiUtils.COLOR_SIDEBAR_BG);
            ModernGuiUtils.drawRect(graphics, promptX, promptY + 31, promptWidth, 1, 0xFFEF4444);

            // Modal Text
            graphics.centeredText(this.font, Component.literal("Leave World / Server?"), centerX, promptY + 10, 0xFFEF4444);
            graphics.centeredText(this.font, Component.literal("Are you sure you want to"), centerX, promptY + 44, ModernGuiUtils.COLOR_TEXT_PRIMARY);
            graphics.centeredText(this.font, Component.literal("leave the game session?"), centerX, promptY + 58, ModernGuiUtils.COLOR_TEXT_MUTED);

            // Render Modal Buttons
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
            return true;
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

    // Modern Animated Custom Button Class matching Config Theme
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
            int border = hovered ? (this.isRed ? 0xFFEF4444 : ModernGuiUtils.getAccentColor()) : ModernGuiUtils.COLOR_CARD_BORDER;
            int textColor = hovered ? (this.isRed ? 0xFFEF4444 : ModernGuiUtils.getAccentColor()) : ModernGuiUtils.COLOR_TEXT_PRIMARY;

            ModernGuiUtils.drawRect(graphics, x, drawY, w, h, bg);
            ModernGuiUtils.drawOutline(graphics, x, drawY, w, h, border);

            graphics.centeredText(Minecraft.getInstance().font, this.getMessage(), x + w / 2, drawY + (h - 8) / 2, textColor);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {}
    }

    @FunctionalInterface
    public interface ButtonAction {
        void onPress(CustomPauseButton button);
    }
}
