package net.alpaka.addons.features.escapemenu;

import net.alpaka.addons.client.AlpakaConfigScreen;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.features.mainmenu.MenuStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The pause menu: vanilla's arrangement on the blurred world, without a panel, drawn in the main
 * menu's design language (see {@link MenuStyle}).
 *
 * There is deliberately no card. The world stays visible through the menu blur and a light dim,
 * and on it sit the logo (which is also the way into the Alpaka config) and the buttons in the
 * shape vanilla uses - one full-width row, two rows of two, one full-width row. The buttons are
 * glass, a faint white fill and hairline that brighten on hover, with the main menu's line icons;
 * Resume Game is the main menu's hero tile, with its washed panel and circling edge, and the leaving
 * one is tinted red like the main menu's Quit. All of them share one corner radius.
 *
 * Everything appears in a short stagger: the dim fades in, then the logo and the rows follow one
 * another, each springing up a few pixels into place as it fades in.
 */
public class CustomPauseScreen extends PauseScreen {

    // ------------------------------------------------------------------ layout

    /** Vanilla's widths: a full row, or two halves with a gap between them that add up to it. */
    private static final int BUTTON_WIDTH = 204;
    private static final int HALF_WIDTH = 98;
    private static final int HALF_GAP = BUTTON_WIDTH - 2 * HALF_WIDTH;
    private static final int BUTTON_HEIGHT = 22;
    private static final int ROW_PITCH = 26;
    private static final int BUTTON_RADIUS = 6;

    /** Side length of the logo, and the gap between it and the first row. */
    private static final int LOGO_SIZE = 44;
    private static final int LOGO_GAP = 14;

    /** Logo, gap, three row pitches and the last row: the whole stack, centred on the screen. */
    private static final int STACK_HEIGHT = LOGO_SIZE + LOGO_GAP + 3 * ROW_PITCH + BUTTON_HEIGHT;

    /** The disconnect prompt: two lines of question, then the buttons; sized once for layout and drawing. */
    private static final int PROMPT_WIDTH = 236;
    private static final int PROMPT_HEIGHT = 96;
    private static final int PROMPT_RADIUS = 14;
    private static final int PROMPT_BUTTON_Y = 58;
    private static final int PROMPT_BUTTON_WIDTH = 92;
    private static final int PROMPT_BUTTON_HEIGHT = 22;

    // --------------------------------------------------------------- appearance

    /** The dim over the blurred world; light, so the world stays part of the picture. */
    private static final int COLOR_DIM = 0x52000000;
    /** The further dim while the disconnect prompt is up. */
    private static final int COLOR_PROMPT_DIM = 0x66000000;

    /** A button's glass: a faint white fill and hairline that brighten under the mouse. */
    private static final int GLASS_FILL = 0x14FFFFFF;
    private static final int GLASS_FILL_HOVER = 0x2EFFFFFF;
    private static final int GLASS_BORDER = 0x30FFFFFF;
    /** The prompt's glass, nearly opaque: it stands over the menu rather than beside it. */
    private static final int PROMPT_FILL = 0xF00D1117;

    /** How long the dim and each element take to appear, and the delay between successive rows. */
    static final float APPEAR_SECONDS = 0.22f;
    static final float STAGGER_SECONDS = 0.03f;
    /** How far the logo and the rows rise as they spring in. */
    private static final float RISE = 8f;

    private boolean showDisconnectPrompt = false;
    private long openTime = 0L;
    private long promptOpenTime = 0L;

    /** Eased hover amount for the logo, on the same curve the buttons use for theirs. */
    private float logoHover = 0.0f;

    private PauseButton resumeButton;
    private PauseButton serverListButton;
    private PauseButton modsButton;
    private PauseButton optionsButton;
    private PauseButton wikiButton;
    private PauseButton disconnectButton;

    private PauseButton promptCancelButton;
    private PauseButton promptConfirmButton;

    /**
     * A {@link PauseScreen} by inheritance, with vanilla's own title, so that other mods recognise
     * it as the pause menu. Essential in particular draws its player preview, wardrobe and friends
     * next to any PauseScreen whose title is not the "Game paused" one, and hooks the base class's
     * rendering to do so; the vanilla {@code init} that would add the vanilla buttons is not run,
     * so the menu itself stays this one.
     */
    public CustomPauseScreen() {
        super(true);
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

    private float elapsed() {
        return (System.currentTimeMillis() - openTime) / 1000.0f;
    }

    /**
     * 0 → 1 progress of the element with this stagger index through its entrance, linear; the dim is
     * index 0, the logo 1, the rows 2 to 5. Index -1 is always fully there (the prompt's buttons,
     * which arrive with the prompt instead).
     */
    static float progressAt(float elapsedSeconds, int index) {
        if (index < 0) return 1.0f;
        float t = (elapsedSeconds - index * STAGGER_SECONDS) / APPEAR_SECONDS;
        return Math.max(0.0f, Math.min(1.0f, t));
    }

    /** How far in the element with this stagger index is: its progress, eased out. */
    static float appearAt(float elapsedSeconds, int index) {
        return MenuStyle.easeOutCubic(progressAt(elapsedSeconds, index));
    }

    /** How far below its place an element still is, springing up with a little overshoot. */
    private static float rise(float progress) {
        return (1.0f - MenuStyle.easeOutBack(progress)) * RISE;
    }

    /** 0 → 1 how far in the disconnect prompt is, eased out; 0 while it is not up. */
    private float promptShown() {
        if (!this.showDisconnectPrompt) return 0.0f;
        return MenuStyle.easeOutCubic((System.currentTimeMillis() - this.promptOpenTime) / 1000.0f / APPEAR_SECONDS);
    }

    /** The colour with its alpha scaled by the factor. */
    static int fade(int color, float factor) {
        return MenuStyle.fade(color, factor);
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
        this.resumeButton = new PauseButton(Kind.HERO, 2, fullX, rowY, BUTTON_WIDTH, BUTTON_HEIGHT,
                MenuStyle.ICON_PLAY, "Resume Game", btn -> this.onClose());
        this.addRenderableWidget(this.resumeButton);

        // Row 2: the server list and the mods.
        //
        // The server list opens with this screen as its parent, which is what makes it a detour
        // rather than an exit: JoinMultiplayerScreen's own Escape hands control back to whatever it
        // was opened from, so the player returns here and then to the game, still connected.
        this.serverListButton = new PauseButton(Kind.GLASS, 3, fullX, rowY + ROW_PITCH, HALF_WIDTH, BUTTON_HEIGHT,
                MenuStyle.ICON_SERVER, "Server List", btn -> {
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(new JoinMultiplayerScreen(this));
            }
        });
        this.addRenderableWidget(this.serverListButton);

        this.modsButton = new PauseButton(Kind.GLASS, 3, rightX, rowY + ROW_PITCH, HALF_WIDTH, BUTTON_HEIGHT,
                MenuStyle.ICON_PUZZLE, "Mods", btn -> {
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
        this.optionsButton = new PauseButton(Kind.GLASS, 4, fullX, rowY + ROW_PITCH * 2, HALF_WIDTH, BUTTON_HEIGHT,
                MenuStyle.ICON_SLIDERS, "Options", btn -> {
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, false));
            }
        });
        this.addRenderableWidget(this.optionsButton);

        this.wikiButton = new PauseButton(Kind.GLASS, 4, rightX, rowY + ROW_PITCH * 2, HALF_WIDTH, BUTTON_HEIGHT,
                MenuStyle.ICON_BOOK, "Wiki", btn -> {
            if (this.minecraft != null) {
                ConfirmLinkScreen.confirmLinkNow(this, "https://hypixelskyblock.minecraft.wiki/");
            }
        });
        this.addRenderableWidget(this.wikiButton);

        // Row 4: leaving, full width, tinted red.
        String leaveText = isSingleplayerWorld() ? "Save & Quit" : "Disconnect";
        this.disconnectButton = new PauseButton(Kind.QUIT, 5, fullX, rowY + ROW_PITCH * 3, BUTTON_WIDTH, BUTTON_HEIGHT,
                MenuStyle.ICON_DOOR, leaveText, btn -> {
            this.showDisconnectPrompt = true;
            this.promptOpenTime = System.currentTimeMillis();
            this.updateWidgetStates();
        });
        this.addRenderableWidget(this.disconnectButton);

        // The disconnect prompt's buttons, drawn and clicked by hand while the prompt is up.
        int promptX = centerX - PROMPT_WIDTH / 2;
        int promptY = centerY - PROMPT_HEIGHT / 2;
        int pBtnY = promptY + PROMPT_BUTTON_Y;

        this.promptCancelButton = new PauseButton(Kind.GLASS, -1, promptX + 16, pBtnY,
                PROMPT_BUTTON_WIDTH, PROMPT_BUTTON_HEIGHT, null, "Cancel", btn -> {
            this.showDisconnectPrompt = false;
            this.updateWidgetStates();
        });

        this.promptConfirmButton = new PauseButton(Kind.QUIT, -1, promptX + PROMPT_WIDTH - 16 - PROMPT_BUTTON_WIDTH, pBtnY,
                PROMPT_BUTTON_WIDTH, PROMPT_BUTTON_HEIGHT, MenuStyle.ICON_DOOR, leaveText, btn -> {
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
        graphics.fill(0, 0, this.width, this.height, fade(COLOR_DIM, appearAt(elapsed(), 0)));
        graphics.pose().popMatrix();

        // The logo, which is also the way into the Alpaka config. It floats gently; under the mouse
        // it grows and gives a little wiggle, like the main menu's copy of it.
        float logoProgress = progressAt(elapsed(), 1);
        if (logoProgress > 0.0f) {
            boolean logoHovered = !this.showDisconnectPrompt && isOverLogo(mouseX, mouseY);
            this.logoHover += ((logoHovered ? 1.0f : 0.0f) - this.logoHover) * MenuStyle.HOVER_EASE;
            float logoAppear = MenuStyle.easeOutCubic(logoProgress) * (1.0f - promptShown());
            graphics.pose().pushMatrix();
            graphics.pose().translate(0.0f, rise(logoProgress));
            MenuStyle.logo(graphics, logoLeft(), logoTop(), LOGO_SIZE, this.logoHover, logoAppear, elapsed());
            graphics.pose().popMatrix();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (!this.showDisconnectPrompt) return;

        float promptAlpha = promptShown();

        // The prompt's full-screen dim, against an identity matrix for the same reason as the
        // backdrop: a full-screen darkening must never move with an inherited transform.
        graphics.pose().pushMatrix();
        graphics.pose().identity();
        graphics.fill(0, 0, this.width, this.height, fade(COLOR_PROMPT_DIM, promptAlpha));
        graphics.pose().popMatrix();

        // The prompt is a small card of the main menu's glass: the question, and the two buttons.
        int centerX = this.width / 2;
        int promptX = centerX - PROMPT_WIDTH / 2;
        int promptY = this.height / 2 - PROMPT_HEIGHT / 2;
        graphics.pose().pushMatrix();
        graphics.pose().translate(0.0f, (1.0f - promptAlpha) * RISE);
        MenuStyle.cardShadow(graphics, promptX, promptY, PROMPT_WIDTH, PROMPT_HEIGHT, PROMPT_RADIUS, promptAlpha);
        ModernGuiUtils.drawRoundedRect(graphics, promptX, promptY, PROMPT_WIDTH, PROMPT_HEIGHT, PROMPT_RADIUS,
                fade(PROMPT_FILL, promptAlpha));
        MenuStyle.cardEdge(graphics, promptX, promptY, PROMPT_WIDTH, PROMPT_HEIGHT, PROMPT_RADIUS, promptAlpha);

        ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text("Are you sure you want to"),
                centerX, promptY + 18, fade(MenuStyle.TEXT, promptAlpha));
        ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text("leave the game session?"),
                centerX, promptY + 32, fade(MenuStyle.MUTED, promptAlpha));

        if (this.promptCancelButton != null) {
            this.promptCancelButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
        }
        if (this.promptConfirmButton != null) {
            this.promptConfirmButton.extractRenderState(graphics, mouseX, mouseY, partialTick);
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
            // Vanilla's click, which the custom-sound swap replaces when the player opted in.
            net.minecraft.client.gui.components.AbstractWidget.playButtonClickSound(this.minecraft.getSoundManager());
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

    /** What a button is drawn as: the main menu's hero tile, plain glass, or tinted red like its Quit. */
    private enum Kind { HERO, GLASS, QUIT }

    /**
     * A button of the menu in the main menu's style: its icon and its label, centred together, on
     * glass whose edge takes the accent under the mouse. Each button springs up into place at its
     * own moment of the open stagger, and fades out while the disconnect prompt is up.
     */
    private final class PauseButton extends AbstractButton {
        private final Kind kind;
        private final int appearIndex;
        private final String glyph;
        private final ButtonAction action;
        private float hover = 0.0f;

        PauseButton(Kind kind, int appearIndex, int x, int y, int width, int height, String glyph,
                    String label, ButtonAction action) {
            super(x, y, width, height, GuiFont.text(label));
            this.kind = kind;
            this.appearIndex = appearIndex;
            this.glyph = glyph;
            this.action = action;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            // No sound here: the widget already played vanilla's click on the press.
            if (this.action != null) {
                this.action.onPress(this);
            }
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            float progress = progressAt(elapsed(), this.appearIndex);
            if (progress <= 0.0f) return;
            // The menu's own buttons fade out as the prompt comes up - its glass would otherwise
            // show them through it behind the question - and the prompt's buttons arrive with it.
            float prompt = promptShown();
            float appear = MenuStyle.easeOutCubic(progress) * (this.appearIndex >= 0 ? 1.0f - prompt : prompt);
            if (appear <= 0.01f) return;

            boolean hovered = mouseX >= this.getX() && mouseX < this.getX() + this.width
                    && mouseY >= this.getY() && mouseY < this.getY() + this.height && this.active;
            this.hover += ((hovered ? 1.0f : 0.0f) - this.hover) * MenuStyle.HOVER_EASE;

            Font font = Minecraft.getInstance().font;
            int x = this.getX(), y = this.getY(), w = this.width, h = this.height;
            graphics.pose().pushMatrix();
            graphics.pose().translate(0.0f, rise(progress));

            int textColor;
            switch (this.kind) {
                case HERO -> {
                    MenuStyle.heroPanel(graphics, x, y, w, h, BUTTON_RADIUS, this.hover, appear);
                    textColor = MenuStyle.TEXT;
                }
                case QUIT -> {
                    MenuStyle.quitBox(graphics, font, x, y, w, h, BUTTON_RADIUS, Component.empty(), this.hover, appear);
                    textColor = 0xFFFCA5A5;
                }
                default -> {
                    int fill = ModernGuiUtils.lerpColor(GLASS_FILL, GLASS_FILL_HOVER, this.hover);
                    int edge = ModernGuiUtils.lerpColor(GLASS_BORDER, MenuStyle.accent(), this.hover);
                    ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, BUTTON_RADIUS, fade(fill, appear));
                    ModernGuiUtils.drawRoundedOutline(graphics, x, y, w, h, BUTTON_RADIUS, fade(edge, appear));
                    textColor = ModernGuiUtils.lerpColor(MenuStyle.TEXT_SOFT, MenuStyle.TEXT, this.hover);
                }
            }

            // The icon and the label, centred together; the hero's icon takes the accent.
            int labelWidth = font.width(this.getMessage());
            if (this.glyph == null) {
                ModernGuiUtils.centeredText(graphics, font, this.getMessage(), x + w / 2, y + (h - 8) / 2, fade(textColor, appear));
            } else {
                Component icon = MenuStyle.icon(this.glyph);
                int iconWidth = font.width(icon);
                int left = x + (w - iconWidth - 6 - labelWidth) / 2;
                int iconColor = this.kind == Kind.HERO ? MenuStyle.accent() : textColor;
                graphics.text(font, icon, left, y + (h - 8) / 2, fade(iconColor, appear), false);
                graphics.text(font, this.getMessage(), left + iconWidth + 6, y + (h - 8) / 2, fade(textColor, appear), false);
            }
            graphics.pose().popMatrix();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {}
    }

    @FunctionalInterface
    private interface ButtonAction {
        void onPress(PauseButton button);
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
