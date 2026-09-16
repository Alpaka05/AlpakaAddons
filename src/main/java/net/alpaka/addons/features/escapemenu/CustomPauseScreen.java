package net.alpaka.addons.features.escapemenu;

import net.alpaka.addons.client.AlpakaConfigScreen;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
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
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/**
 * The pause menu: vanilla's arrangement on the blurred world, without a panel.
 *
 * There is deliberately no card. The world stays visible through the menu blur and a light dim,
 * and on it sit the logo (which is also the way into the Alpaka config) and the buttons in the
 * shape vanilla uses - one full-width row, two rows of two, one full-width row. The buttons are
 * glass: a faint white fill and hairline that brighten on hover, the leaving one tinted red. That
 * keeps it clearly apart from the config screen, which is built from opaque panels.
 *
 * Everything appears in a short stagger: the dim fades in, then the logo and the rows follow one
 * another 25 ms apart, each fading in as it drifts up a few pixels.
 */
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
    private static final String ICON_PLAY = "";
    private static final String ICON_SERVER = "";
    private static final String ICON_BOX = "";
    private static final String ICON_SLIDERS = "";
    private static final String ICON_BOOK = "";
    private static final String ICON_DOOR = "";
    private static final String ICON_PUZZLE = "";

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
                .append(GuiFont.text("  " + text));
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

    // ------------------------------------------------------------------ layout

    /** Vanilla's widths: a full row, or two halves with a gap between them that add up to it. */
    private static final int BUTTON_WIDTH = 204;
    private static final int HALF_WIDTH = 98;
    private static final int HALF_GAP = BUTTON_WIDTH - 2 * HALF_WIDTH;
    private static final int BUTTON_HEIGHT = 22;
    private static final int ROW_PITCH = 26;

    /** Side length of the logo, and the gap between it and the first row. */
    private static final int LOGO_SIZE = 44;
    private static final int LOGO_GAP = 14;

    /** Logo, gap, three row pitches and the last row: the whole stack, centred on the screen. */
    private static final int STACK_HEIGHT = LOGO_SIZE + LOGO_GAP + 3 * ROW_PITCH + BUTTON_HEIGHT;

    /**
     * How far the logo grows on each side when hovered, as a fraction of its own size.
     *
     * The same fraction the main menu's larger copy of this logo uses, so the two swell by the same
     * proportion rather than by the same number of pixels.
     */
    private static final float LOGO_HOVER_GROWTH = 0.07f;

    /** The disconnect prompt: two lines of question, then the buttons; sized once for layout and drawing. */
    private static final int PROMPT_WIDTH = 236;
    private static final int PROMPT_HEIGHT = 96;
    private static final int PROMPT_BUTTON_Y = 56;

    // --------------------------------------------------------------- appearance

    /** The dim over the blurred world; light, so the world stays part of the picture. */
    private static final int COLOR_DIM = 0x52000000;

    private static final int GLASS_FILL = 0x14FFFFFF;
    private static final int GLASS_FILL_HOVER = 0x2EFFFFFF;
    private static final int GLASS_BORDER = 0x30FFFFFF;
    private static final int GLASS_TEXT = 0xE8FFFFFF;
    private static final int GLASS_TEXT_HOVER = 0xFFFFFFFF;

    private static final int RED = 0xFFEF4444;
    private static final int RED_BORDER = 0x66EF4444;
    private static final int RED_FILL_HOVER = 0x38EF4444;
    private static final int RED_TEXT = 0xFFF0B4B4;
    private static final int RED_TEXT_HOVER = 0xFFF87171;

    private static final int PROMPT_BG = 0xD0121419;

    /** How long the dim and each element take to appear, and the delay between successive rows. */
    private static final float APPEAR_SECONDS = 0.16f;
    private static final float STAGGER_SECONDS = 0.025f;

    private boolean showDisconnectPrompt = false;
    private long openTime = 0L;

    /** Eased hover amount for the logo, on the same curve the buttons use for theirs. */
    private float logoHover = 0.0f;

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

    private int stackTop() {
        return (this.height - STACK_HEIGHT) / 2;
    }

    private int logoLeft() {
        return this.width / 2 - LOGO_SIZE / 2;
    }

    private int logoTop() {
        return stackTop();
    }

    private boolean isOverLogo(double mouseX, double mouseY) {
        int x = logoLeft(), y = logoTop();
        return mouseX >= x && mouseX < x + LOGO_SIZE && mouseY >= y && mouseY < y + LOGO_SIZE;
    }

    /**
     * 0 → 1 appearance of the element with this stagger index, eased out; the dim is index 0, the
     * logo 1, the rows 2 to 5. Index -1 is always fully there (the prompt's buttons).
     */
    private float appear(int index) {
        if (index < 0) return 1.0f;
        float t = ((System.currentTimeMillis() - openTime) / 1000.0f - index * STAGGER_SECONDS) / APPEAR_SECONDS;
        if (t <= 0.0f) return 0.0f;
        if (t >= 1.0f) return 1.0f;
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    /** The colour with its alpha scaled by the factor. */
    private static int fade(int color, float factor) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, factor)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    @Override
    protected void init() {
        this.openTime = System.currentTimeMillis();

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int fullX = centerX - BUTTON_WIDTH / 2;
        int rightX = fullX + HALF_WIDTH + HALF_GAP;
        int rowY = stackTop() + LOGO_SIZE + LOGO_GAP;

        // Row 1: back to the game, full width.
        this.resumeButton = new CustomPauseButton(this, 2, fullX, rowY, BUTTON_WIDTH, BUTTON_HEIGHT,
                iconLabel(ICON_PLAY, "Resume Game"), false, btn -> this.onClose());
        this.addRenderableWidget(this.resumeButton);

        // Row 2: the server list and the mods.
        //
        // The server list opens with this screen as its parent, which is what makes it a detour
        // rather than an exit: JoinMultiplayerScreen's own Escape hands control back to whatever it
        // was opened from, so the player returns here and then to the game, still connected.
        this.serverListButton = new CustomPauseButton(this, 3, fullX, rowY + ROW_PITCH, HALF_WIDTH, BUTTON_HEIGHT,
                iconLabel(ICON_SERVER, "Server List"), false, btn -> {
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(new JoinMultiplayerScreen(this));
            }
        });
        this.addRenderableWidget(this.serverListButton);

        this.modsButton = new CustomPauseButton(this, 3, rightX, rowY + ROW_PITCH, HALF_WIDTH, BUTTON_HEIGHT,
                iconLabel(ICON_PUZZLE, "Mods"), false, btn -> {
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

        // Row 3: options and the wiki.
        this.optionsButton = new CustomPauseButton(this, 4, fullX, rowY + ROW_PITCH * 2, HALF_WIDTH, BUTTON_HEIGHT,
                iconLabel(ICON_SLIDERS, "Options"), false, btn -> {
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, false));
            }
        });
        this.addRenderableWidget(this.optionsButton);

        this.wikiButton = new CustomPauseButton(this, 4, rightX, rowY + ROW_PITCH * 2, HALF_WIDTH, BUTTON_HEIGHT,
                iconLabel(ICON_BOOK, "Wiki"), false, btn -> {
            if (this.minecraft != null) {
                ConfirmLinkScreen.confirmLinkNow(this, "https://hypixelskyblock.minecraft.wiki/");
            }
        });
        this.addRenderableWidget(this.wikiButton);

        // Row 4: leaving, full width, tinted red.
        Component disconnectText = isSingleplayerWorld() ? iconLabel(ICON_DOOR, "Save & Quit") : iconLabel(ICON_DOOR, "Disconnect");
        this.disconnectButton = new CustomPauseButton(this, 5, fullX, rowY + ROW_PITCH * 3, BUTTON_WIDTH, BUTTON_HEIGHT,
                disconnectText, true, btn -> {
            this.showDisconnectPrompt = true;
            this.updateWidgetStates();
        });
        this.addRenderableWidget(this.disconnectButton);

        // The disconnect prompt's buttons, drawn and clicked by hand while the prompt is up.
        int promptX = centerX - PROMPT_WIDTH / 2;
        int promptY = centerY - PROMPT_HEIGHT / 2;
        int pBtnWidth = 88;
        int pBtnHeight = 24;
        int pBtnY = promptY + PROMPT_BUTTON_Y;

        this.promptCancelButton = new CustomPauseButton(this, -1, promptX + 16, pBtnY, pBtnWidth, pBtnHeight,
                GuiFont.text("Cancel"), false, btn -> {
            this.showDisconnectPrompt = false;
            this.updateWidgetStates();
        });

        this.promptConfirmButton = new CustomPauseButton(this, -1, promptX + PROMPT_WIDTH - 16 - pBtnWidth, pBtnY, pBtnWidth, pBtnHeight,
                GuiFont.text("Disconnect"), true, btn -> {
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
        // Vanilla's menu blur, but not vanilla's heavy in-world darkening: the dim here is lighter
        // and fades in, so the world stays visible behind the menu.
        this.extractBlurredBackground(graphics);

        // Drawn against an identity matrix rather than whatever is already on the pose stack -
        // other installed GUI mods (SmoothGui and friends) apply their own open-transition
        // transform around Screen's render calls, and without this the fill inherited that
        // transform and slid along with it instead of staying still.
        graphics.pose().pushMatrix();
        graphics.pose().identity();
        graphics.fill(0, 0, this.width, this.height, fade(COLOR_DIM, appear(0)));
        graphics.pose().popMatrix();

        // The logo, which is also the way into the Alpaka config. Hovering grows it in place and
        // does nothing else - no card behind it, no border - like the main menu's copy of it.
        ensureModIconRegistered();
        float logoAppear = appear(1);
        int iconX = logoLeft();
        int iconY = logoTop() + Math.round((1.0f - logoAppear) * 6.0f);

        boolean logoHovered = !this.showDisconnectPrompt && isOverLogo(mouseX, mouseY);
        this.logoHover += ((logoHovered ? 1.0f : 0.0f) - this.logoHover) * 0.25f;

        int grow = (int) (LOGO_SIZE * LOGO_HOVER_GROWTH * this.logoHover);
        if (logoAppear > 0.01f) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, MOD_ICON_ID, iconX - grow, iconY - grow, 0.0f, 0.0f,
                    LOGO_SIZE + grow * 2, LOGO_SIZE + grow * 2, 128, 128, 128, 128, fade(0xFFFFFFFF, logoAppear));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // The prompt's full-screen dim, against an identity matrix for the same reason as the
        // backdrop: a full-screen darkening must never move with an inherited transform.
        if (this.showDisconnectPrompt) {
            graphics.pose().pushMatrix();
            graphics.pose().identity();
            graphics.fill(0, 0, this.width, this.height, 0x80000000);
            graphics.pose().popMatrix();
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        if (this.showDisconnectPrompt) {
            int promptX = centerX - PROMPT_WIDTH / 2;
            int promptY = centerY - PROMPT_HEIGHT / 2;

            // The prompt is glass too, with a red hairline: the question and the two buttons.
            int radius = ModernGuiUtils.PANEL_RADIUS;
            ModernGuiUtils.drawPanelShadow(graphics, promptX, promptY, PROMPT_WIDTH, PROMPT_HEIGHT, radius, 1.0f);
            ModernGuiUtils.drawRoundedPanel(graphics, promptX, promptY, PROMPT_WIDTH, PROMPT_HEIGHT, radius, PROMPT_BG, RED);

            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text("Are you sure you want to"), centerX, promptY + 18, ModernGuiUtils.COLOR_TEXT_PRIMARY);
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text("leave the game session?"), centerX, promptY + 32, ModernGuiUtils.COLOR_TEXT_MUTED);

            if (this.promptCancelButton != null) {
                this.promptCancelButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }
            if (this.promptConfirmButton != null) {
                this.promptConfirmButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
            }
        }
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

    /**
     * A glass button: faint white fill and hairline over the blurred world, brightening on hover
     * with the border taking the accent; the leaving button is tinted red instead. Each button
     * fades in and drifts up a few pixels at its own moment of the open stagger.
     */
    private static class CustomPauseButton extends AbstractButton {
        private final CustomPauseScreen owner;
        private final int appearIndex;
        private final boolean isRed;
        private final ButtonAction action;
        private float hoverTime = 0.0f;

        public CustomPauseButton(CustomPauseScreen owner, int appearIndex, int x, int y, int width, int height,
                                 Component message, boolean isRed, ButtonAction action) {
            super(x, y, width, height, message);
            this.owner = owner;
            this.appearIndex = appearIndex;
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
            // The menu's own buttons step aside while the prompt is up: the prompt is glass, and
            // they would otherwise show through it behind the question.
            if (this.appearIndex >= 0 && this.owner.showDisconnectPrompt) return;
            float appear = this.owner.appear(this.appearIndex);
            if (appear <= 0.01f) return;

            boolean hovered = mouseX >= this.getX() && mouseX < this.getX() + this.width &&
                              mouseY >= this.getY() && mouseY < this.getY() + this.height && this.active;
            this.hoverTime += ((hovered ? 1.0f : 0.0f) - this.hoverTime) * 0.25f;
            float hover = this.hoverTime;

            int x = this.getX();
            int y = this.getY() + Math.round((1.0f - appear) * 6.0f) - Math.round(hover);
            int w = this.width;
            int h = this.height;

            int fill, border, text;
            if (this.isRed) {
                fill = ModernGuiUtils.lerpColor(GLASS_FILL, RED_FILL_HOVER, hover);
                border = ModernGuiUtils.lerpColor(RED_BORDER, RED, hover);
                text = ModernGuiUtils.lerpColor(RED_TEXT, RED_TEXT_HOVER, hover);
            } else {
                fill = ModernGuiUtils.lerpColor(GLASS_FILL, GLASS_FILL_HOVER, hover);
                border = ModernGuiUtils.lerpColor(GLASS_BORDER, ModernGuiUtils.getAccentColor(), hover);
                text = ModernGuiUtils.lerpColor(GLASS_TEXT, GLASS_TEXT_HOVER, hover);
            }

            ModernGuiUtils.drawRoundedPanel(graphics, x, y, w, h, ModernGuiUtils.WIDGET_RADIUS + 2,
                    fade(fill, appear), fade(border, appear));
            ModernGuiUtils.centeredText(graphics, Minecraft.getInstance().font, this.getMessage(),
                    x + w / 2, y + (h - 8) / 2, fade(text, appear));
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
