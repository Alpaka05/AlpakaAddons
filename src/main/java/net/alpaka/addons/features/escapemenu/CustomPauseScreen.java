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
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

public class CustomPauseScreen extends Screen {
    private static final Identifier MOD_ICON_ID = Identifier.parse("alpaka:textures/gui/alpaka_icon.png");
    private static boolean modIconRegistered = false;

    /**
     * The button icons, as a font rather than as blitted textures.
     *
     * They used to be emoji written straight into the labels. Minecraft's font has no glyphs for
     * 📦 🛠 📖 🚪 - they sit outside the Basic Multilingual Plane - so they fell through to the
     * Unifont fallback and were drawn as coarse monochrome bitmaps next to otherwise clean text.
     *
     * Drawing them as glyphs instead of blitting a texture is what keeps them lined up: they sit on
     * the text baseline, scale with the GUI scale, and take the colour the label is drawn in, so
     * they follow the hover and accent colours for free.
     */
    private static final FontDescription ICON_FONT =
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("alpaka", "pause_icons"));

    /** Private-use codepoints, in the order the sprite sheet lays them out. */
    private static final String ICON_PLAY = "\uE000";
    private static final String ICON_SERVER = "\uE001";
    private static final String ICON_BOX = "\uE002";
    private static final String ICON_SLIDERS = "\uE003";
    private static final String ICON_BOOK = "\uE004";
    private static final String ICON_DOOR = "\uE005";

    /**
     * A button label: the icon glyph, then the text.
     *
     * Built on an empty root so the text sibling inherits the root's default font rather than the
     * icon's - a label appended onto the icon component would be drawn in the icon font, where
     * every ordinary letter is a missing glyph.
     */
    private static Component iconLabel(String icon, String text) {
        return Component.empty()
                .append(Component.literal(icon).withStyle(style -> style.withFont(ICON_FONT)))
                .append(Component.literal("  " + text));
    }

    private static void ensureModIconRegistered() {
        if (!modIconRegistered) {
            modIconRegistered = true;
            try {
                SimpleTexture texture = new SimpleTexture(MOD_ICON_ID);
                Minecraft.getInstance().getTextureManager().registerAndLoad(MOD_ICON_ID, texture);
            } catch (Throwable t) {
                System.err.println("[AlpakaAddons] Failed to register SimpleTexture for alpaka_icon.png: " + t.getMessage());
            }
        }
    }

    private boolean showDisconnectPrompt = false;
    private long openTime = 0L;

    /**
     * Eased hover amount for the mod logo, which doubles as the Alpaka config button.
     *
     * Smoothed on the same curve {@link CustomPauseButton} uses for its own hover, so the logo eases
     * up to size rather than snapping to it.
     */
    private float logoHover = 0.0f;

    /**
     * The panel's box. Shared rather than restated per method: the logo's clickable area is derived
     * from it, so a size only changed in one place would leave the hit test pointing at empty space.
     */
    private static final int CARD_WIDTH = 200;
    private static final int CARD_HEIGHT = 254;

    /** Gap between the panel's top edge and the logo. */
    private static final int LOGO_TOP_INSET = 5;

    /** Side length of the logo. */
    private static final int LOGO_SIZE = 34;

    /**
     * How far the logo grows on each side when hovered, as a fraction of its own size.
     *
     * The same fraction the main menu's larger copy of this logo uses, so the two swell by the same
     * proportion rather than by the same number of pixels.
     */
    private static final float LOGO_HOVER_GROWTH = 0.07f;

    /**
     * Where the logo sits, in the settled layout.
     *
     * Derived here rather than inline so drawing and hit-testing cannot drift apart. The open
     * animation slides the panel, and with it the drawn logo, for 150ms; the rectangle returned here
     * is the resting one, which is what a click should be measured against.
     */
    private int logoLeft() {
        return this.width / 2 - LOGO_SIZE / 2;
    }

    private int logoTop() {
        return (this.height - CARD_HEIGHT) / 2 + LOGO_TOP_INSET;
    }

    private boolean isOverLogo(double mouseX, double mouseY) {
        int x = logoLeft(), y = logoTop();
        return mouseX >= x && mouseX < x + LOGO_SIZE && mouseY >= y && mouseY < y + LOGO_SIZE;
    }

    private CustomPauseButton resumeButton;
    private CustomPauseButton serverListButton;
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

        int cardWidth = CARD_WIDTH;
        int cardHeight = CARD_HEIGHT;
        int startX = centerX - cardWidth / 2;
        int startY = centerY - cardHeight / 2;

        int buttonWidth = 168;
        int buttonHeight = 25;
        int buttonX = centerX - buttonWidth / 2;
        int buttonStartY = startY + 64;
        int spacing = 30;

        // 1. Resume Button
        this.resumeButton = new CustomPauseButton(buttonX, buttonStartY, buttonWidth, buttonHeight,
                iconLabel(ICON_PLAY, "Resume Game"), false, btn -> this.onClose());
        this.addRenderableWidget(this.resumeButton);

        // 2. Server List Button
        //
        // Opens the multiplayer list with this screen as its parent, which is what makes it a
        // detour rather than an exit: JoinMultiplayerScreen's own Escape hands control back to
        // whatever it was opened from, so the player returns here and then to the game, still
        // connected. Nothing here disconnects - that is the point, it is for glancing at player
        // counts and ping on other servers mid-session.
        this.serverListButton = new CustomPauseButton(buttonX, buttonStartY + spacing, buttonWidth, buttonHeight,
                iconLabel(ICON_SERVER, "Server List"), false, btn -> {
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(new JoinMultiplayerScreen(this));
            }
        });
        this.addRenderableWidget(this.serverListButton);

        // 3. Mods Button (Mod Menu mod list)
        this.modsButton = new CustomPauseButton(buttonX, buttonStartY + spacing * 2, buttonWidth, buttonHeight,
                iconLabel(ICON_BOX, "Mods"), false, btn -> {
            if (this.minecraft != null) {
                // Falls back to the options screen without Mod Menu; see ModMenuCompat for why the
                // Mod Menu class must not be named here.
                if (net.alpaka.addons.compat.ModMenuCompat.isLoaded()) {
                    net.alpaka.addons.compat.ModMenuCompat.openModsScreen(this);
                } else {
                    this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, false));
                }
            }
        });
        this.addRenderableWidget(this.modsButton);

        // 4. Minecraft Options Button
        this.optionsButton = new CustomPauseButton(buttonX, buttonStartY + spacing * 3, buttonWidth, buttonHeight,
                iconLabel(ICON_SLIDERS, "Options"), false, btn -> {
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, false));
            }
        });
        this.addRenderableWidget(this.optionsButton);

        // 5. Skyblock Wiki Button
        this.wikiButton = new CustomPauseButton(buttonX, buttonStartY + spacing * 4, buttonWidth, buttonHeight,
                iconLabel(ICON_BOOK, "Skyblock Wiki"), false, btn -> {
            if (this.minecraft != null) {
                ConfirmLinkScreen.confirmLinkNow(this, "https://hypixelskyblock.minecraft.wiki/");
            }
        });
        this.addRenderableWidget(this.wikiButton);

        // 6. Disconnect Button (Red Accent)
        boolean isSingleplayer = isSingleplayerWorld();
        Component disconnectText = isSingleplayer ? iconLabel(ICON_DOOR, "Save & Quit") : iconLabel(ICON_DOOR, "Disconnect");
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
        if (this.serverListButton != null) this.serverListButton.active = mainActive;
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

        int cardWidth = CARD_WIDTH;
        int cardHeight = CARD_HEIGHT;
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
        int headerH = 56;
        ModernGuiUtils.drawRect(graphics, startX, startY, cardWidth, headerH, ModernGuiUtils.COLOR_SIDEBAR_BG);
        ModernGuiUtils.drawRect(graphics, startX, startY + headerH - 1, cardWidth, 1, ModernGuiUtils.getAccentColor());

        // Mod Logo, which is also the way into the Alpaka config now that the button is gone.
        ensureModIconRegistered();
        int iconSize = LOGO_SIZE;
        int iconX = centerX - iconSize / 2;
        int iconY = startY + LOGO_TOP_INSET;

        boolean logoHovered = !this.showDisconnectPrompt && isOverLogo(mouseX, mouseY);
        this.logoHover += ((logoHovered ? 1.0f : 0.0f) - this.logoHover) * 0.25f;

        // Hovering grows the logo and does nothing else - no card behind it, no border, no lift. It
        // used to take the same treatment the panel's buttons give themselves, which framed a piece
        // of artwork in a box and made it read as a widget that had been selected rather than as one
        // being pointed at. This matches the main menu's Join Hypixel button: the growth goes on all
        // four sides, so the logo swells in place instead of drifting.
        int grow = (int) (iconSize * LOGO_HOVER_GROWTH * this.logoHover);
        graphics.blit(RenderPipelines.GUI_TEXTURED, MOD_ICON_ID, iconX - grow, iconY - grow, 0.0f, 0.0f,
                iconSize + grow * 2, iconSize + grow * 2, 128, 128, 128, 128);

        // Subtitle: User & Status (IP on server, Singleplayer in local world)
        String status = "Singleplayer";
        if (this.minecraft != null && !isSingleplayerWorld()) {
            ServerData serverData = this.minecraft.getCurrentServer();
            if (serverData != null && serverData.ip != null && !serverData.ip.isBlank()) {
                status = serverData.ip;
            } else {
                status = "Multiplayer";
            }
        }
        String user = (this.minecraft != null && this.minecraft.getUser() != null) ? this.minecraft.getUser().getName() : "Player";
        graphics.centeredText(this.font, Component.literal(user + " • " + status), centerX, startY + 42, ModernGuiUtils.COLOR_TEXT_MUTED);

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

        if (event.button() == 0 && isOverLogo(event.x(), event.y()) && this.minecraft != null) {
            CustomSoundFeature.playButtonClickSound();
            this.minecraft.gui.setScreen(new AlpakaConfigScreen(this));
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

    /**
     * Whether this is a local world that has not been opened to LAN.
     *
     * Minecraft.isSingleplayer() said exactly this until 26.2 removed it; hasSingleplayerServer()
     * alone would also be true for a world shared over LAN, which vanilla treats as multiplayer.
     */
    private boolean isSingleplayerWorld() {
        if (this.minecraft == null || !this.minecraft.hasSingleplayerServer()) return false;
        net.minecraft.client.server.IntegratedServer server = this.minecraft.getSingleplayerServer();
        return server != null && !server.isPublished();
    }
}
