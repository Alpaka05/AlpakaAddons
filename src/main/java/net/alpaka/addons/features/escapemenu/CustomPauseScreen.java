package net.alpaka.addons.features.escapemenu;

import net.alpaka.addons.client.AlpakaConfigScreen;
import net.alpaka.addons.client.AlpakaConfirmScreen;
import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.features.mainmenu.MenuStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The pause menu, as a window of the config menu's kind (see {@link MenuStyle}), so the game's own
 * menus and the mod's read as one.
 *
 * The world stays in sight behind it, blurred and dimmed as behind the config menu. The window has
 * the config's header - the logo, "ALPAKA" and the version, which is also the way into the config -
 * with the world or server the player is in on its right. Under it the buttons in the shape
 * vanilla uses: Resume Game across the top, washed in the accent; two rows of two; and, set apart
 * under a hairline, the red one to leave by.
 *
 * Opening plays as the config menu's does: the dim fades in, the window pops open, and the buttons
 * fade in one after another. Back from one of its dialogs the menu is simply there again.
 */
public class CustomPauseScreen extends PauseScreen implements net.alpaka.addons.client.gui.NoticeHost {

    // ------------------------------------------------------------------ layout

    /** The window's width, and its buttons: a full row, or two halves with a gap between them. */
    private static final int PANEL_W = 220;
    private static final int PAD = AlpakaStyle.PAD;
    private static final int INNER_W = PANEL_W - PAD * 2;
    private static final int HALF_GAP = 4;
    private static final int HALF_WIDTH = (INNER_W - HALF_GAP) / 2;
    private static final int RESUME_HEIGHT = 22;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_GAP = 4;
    /** The room either side of the hairline over the leave button. */
    private static final int SEPARATOR_GAP = 6;

    /** Header, the four rows with the hairline between the third and the leave button, and the padding. */
    private static final int PANEL_H = AlpakaStyle.HEADER_H + PAD + RESUME_HEIGHT + ROW_GAP
            + 2 * (BUTTON_HEIGHT + ROW_GAP) - ROW_GAP + SEPARATOR_GAP * 2 + 1 + BUTTON_HEIGHT + PAD;

    // --------------------------------------------------------------- appearance

    /** How long the dim and each element take to appear, and the delay between successive rows. */
    static final float APPEAR_SECONDS = 0.22f;
    static final float STAGGER_SECONDS = 0.03f;
    /** How far the buttons rise as they fade in. */
    private static final float RISE = 4f;

    private static final String WIKI_URL = "https://hypixelskyblock.minecraft.wiki/";

    private long openTime = 0L;
    /** Set while the leave dialog is up, so cancelling it brings the menu back without its entrance. */
    private boolean returningFromDialog = false;

    /** Eased hover amount for the header's logo and name, on the same curve the buttons use for theirs. */
    private float headerHover = 0.0f;

    private PauseButton resumeButton;
    private PauseButton serverListButton;
    private PauseButton modsButton;
    private PauseButton optionsButton;
    private PauseButton wikiButton;
    private PauseButton disconnectButton;

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

    private int panelX() {
        return (this.width - PANEL_W) / 2;
    }

    private int panelY() {
        return (this.height - PANEL_H) / 2;
    }

    private boolean isOverHeader(double mouseX, double mouseY) {
        int[] box = MenuStyle.brandBox(this.font, panelX(), panelY());
        return AlpakaStyle.inside(mouseX, mouseY, box[0], box[1], box[2], box[3]);
    }

    private float elapsed() {
        return (System.currentTimeMillis() - openTime) / 1000.0f;
    }

    /**
     * 0 → 1 progress of the element with this stagger index through its entrance, linear; the dim is
     * index 0, the window 1, the rows 2 to 5.
     */
    static float progressAt(float elapsedSeconds, int index) {
        float t = (elapsedSeconds - index * STAGGER_SECONDS) / APPEAR_SECONDS;
        return Math.max(0.0f, Math.min(1.0f, t));
    }

    /** How far in the element with this stagger index is: its progress, eased out. */
    static float appearAt(float elapsedSeconds, int index) {
        return MenuStyle.easeOutCubic(progressAt(elapsedSeconds, index));
    }

    /** How far below its place an element still is, easing up into it. */
    private static float rise(float progress) {
        return (1.0f - MenuStyle.easeOutCubic(progress)) * RISE;
    }

    /** The color with its alpha scaled by the factor. */
    static int fade(int color, float factor) {
        return MenuStyle.fade(color, factor);
    }

    @Override
    protected void init() {
        // Back from a dialog the menu is simply there again, its clock running on; otherwise it pops
        // open.
        if (!this.returningFromDialog || this.openTime == 0L) this.openTime = System.currentTimeMillis();
        this.returningFromDialog = false;

        int fullX = panelX() + PAD;
        int rightX = fullX + HALF_WIDTH + HALF_GAP;
        int rowY = panelY() + AlpakaStyle.HEADER_H + PAD;
        int row2 = rowY + RESUME_HEIGHT + ROW_GAP;
        int row3 = row2 + BUTTON_HEIGHT + ROW_GAP;
        int row4 = row3 + BUTTON_HEIGHT + SEPARATOR_GAP * 2 + 1;

        // Row 1: back to the game, full width.
        this.resumeButton = new PauseButton(MenuStyle.Kind.PRIMARY, 2, fullX, rowY, INNER_W, RESUME_HEIGHT,
                MenuStyle.ICON_PLAY, "Resume Game", btn -> this.onClose());
        this.addRenderableWidget(this.resumeButton);

        // Row 2: the server list and the mods.
        //
        // The server list opens with this screen as its parent, which is what makes it a detour
        // rather than an exit: JoinMultiplayerScreen's own Escape hands control back to whatever it
        // was opened from, so the player returns here and then to the game, still connected.
        this.serverListButton = new PauseButton(MenuStyle.Kind.PLAIN, 3, fullX, row2, HALF_WIDTH, BUTTON_HEIGHT,
                MenuStyle.ICON_SERVER, "Server List", btn -> {
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(new JoinMultiplayerScreen(this));
            }
        });
        this.addRenderableWidget(this.serverListButton);

        this.modsButton = new PauseButton(MenuStyle.Kind.PLAIN, 3, rightX, row2, HALF_WIDTH, BUTTON_HEIGHT,
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
        this.optionsButton = new PauseButton(MenuStyle.Kind.PLAIN, 4, fullX, row3, HALF_WIDTH, BUTTON_HEIGHT,
                MenuStyle.ICON_SLIDERS, "Options", btn -> {
            if (this.minecraft != null) {
                this.minecraft.gui.setScreen(new OptionsScreen(this, this.minecraft.options, false));
            }
        });
        this.addRenderableWidget(this.optionsButton);

        this.wikiButton = new PauseButton(MenuStyle.Kind.PLAIN, 4, rightX, row3, HALF_WIDTH, BUTTON_HEIGHT,
                MenuStyle.ICON_BOOK, "Wiki", btn -> {
            if (this.minecraft != null) {
                // A link out of the game asks first, in the dialog every such question in the mod uses.
                this.returningFromDialog = true;
                this.minecraft.gui.setScreen(new AlpakaConfirmScreen(this, "Open Wiki",
                        "Opens the Hypixel SkyBlock wiki in your browser: " + WIKI_URL, "Open", false,
                        () -> net.minecraft.util.Util.getPlatform().openUri(WIKI_URL)));
            }
        });
        this.addRenderableWidget(this.wikiButton);

        // Row 4: leaving, full width, red, set apart under a hairline.
        String leaveText = isSingleplayerWorld() ? "Save & Quit" : "Disconnect";
        this.disconnectButton = new PauseButton(MenuStyle.Kind.DANGER, 5, fullX, row4, INNER_W, BUTTON_HEIGHT,
                MenuStyle.ICON_DOOR, leaveText, btn -> {
            // Leaving asks first, in the dialog every such question in the mod uses.
            if (this.minecraft == null) return;
            this.returningFromDialog = true;
            this.minecraft.gui.setScreen(new AlpakaConfirmScreen(this, leaveText, leaveMessage(), leaveText, true,
                    this::leave));
        });
        this.addRenderableWidget(this.disconnectButton);
    }

    /** For the screenshot tour: the middle of the Wiki button. */
    public int[] devWikiPoint() {
        return new int[] {this.wikiButton.getX() + this.wikiButton.getWidth() / 2,
                this.wikiButton.getY() + this.wikiButton.getHeight() / 2};
    }

    /** For the screenshot tour: the middle of the leave button, to click it as a player would. */
    public int[] devLeavePoint() {
        return new int[] {this.disconnectButton.getX() + this.disconnectButton.getWidth() / 2,
                this.disconnectButton.getY() + this.disconnectButton.getHeight() / 2};
    }

    /** What leaving does here, for the dialog that asks first. */
    private String leaveMessage() {
        if (isSingleplayerWorld()) return "Saves the world and returns to the main menu.";
        if (this.minecraft != null && this.minecraft.hasSingleplayerServer()) {
            return "Saves the world and closes it, for everyone you opened it to over LAN as well.";
        }
        return "Leaves the server.";
    }

    /**
     * Leaves the world or the server the way vanilla's pause menu does: saving a local world, then
     * on to the main menu, the server list or Realms, whichever the game came from - and asking
     * first about an unsent chat report, as vanilla does.
     */
    private void leave() {
        if (this.minecraft == null) return;
        this.minecraft.disconnectFromWorld(net.minecraft.client.multiplayer.ClientLevel.DEFAULT_QUIT_MESSAGE);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Vanilla's menu blur, and the config menu's dim over it, fading in. The dim is drawn
        // against an identity pose, so the transitions other GUI mods (SmoothGui and friends) put
        // around a screen's rendering do not slide it along.
        this.extractBlurredBackground(graphics);
        AlpakaStyle.backdrop(graphics, this.width, this.height, appearAt(elapsed(), 0));

        boolean pushed = MenuStyle.beginOpen(graphics, this.openTime, this.width / 2.0f, this.height / 2.0f);
        int x = panelX();
        int y = panelY();
        AlpakaStyle.panel(graphics, x, y, PANEL_W, PANEL_H);

        // The header: the way into the Alpaka config on the left, where the player is on the right.
        this.headerHover += ((isOverHeader(mouseX, mouseY) ? 1.0f : 0.0f) - this.headerHover) * MenuStyle.HOVER_EASE;
        MenuStyle.header(graphics, this.font, x, y, PANEL_W, this.headerHover, elapsed());
        int[] brand = MenuStyle.brandBox(this.font, x, y);
        int placeRight = x + PANEL_W - PAD - 2;
        Component placeIcon = MenuStyle.icon(isSingleplayerWorld() ? MenuStyle.ICON_PERSON : MenuStyle.ICON_SERVER);
        int iconW = this.font.width(placeIcon);
        int room = placeRight - (brand[0] + brand[2] + 8) - iconW - 5;
        String place = AlpakaStyle.ellipsize(this.font, placeLabel(), room);
        if (!place.isEmpty()) {
            int placeX = placeRight - GuiFont.width(this.font, place);
            int textY = y + (AlpakaStyle.HEADER_H - 8) / 2;
            graphics.text(this.font, placeIcon, placeX - 5 - iconW, textY, AlpakaStyle.TEXT_MUTED, false);
            graphics.text(this.font, GuiFont.text(place), placeX, textY, AlpakaStyle.TEXT_MUTED, false);
        }

        // The hairline that sets the leave button apart.
        float leaveAppear = appearAt(elapsed(), 5);
        int lineY = this.disconnectButton.getY() - SEPARATOR_GAP - 1;
        ModernGuiUtils.drawRect(graphics, x + PAD, lineY, INNER_W, 1, fade(AlpakaStyle.LINE, leaveAppear));
        MenuStyle.endOpen(graphics, pushed);
    }

    /** Where the player is: the world's name, the same opened to LAN, or the server's address. */
    private String placeLabel() {
        if (this.minecraft == null) return "";
        net.minecraft.client.server.IntegratedServer server = this.minecraft.getSingleplayerServer();
        if (this.minecraft.hasSingleplayerServer() && server != null) {
            String name = server.getWorldData().getLevelName();
            return server.isPublished() ? name + " · LAN" : name;
        }
        net.minecraft.client.multiplayer.ServerData data = this.minecraft.getCurrentServer();
        return data == null ? "" : data.ip;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The buttons pop open with the window they sit in.
        boolean pushed = MenuStyle.beginOpen(graphics, this.openTime, this.width / 2.0f, this.height / 2.0f);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        MenuStyle.endOpen(graphics, pushed);
        // Notices crisp over the menu, not blurred away under it with the HUD.
        net.alpaka.addons.features.notification.AlpakaNotifications.renderOverScreen(graphics);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && isOverHeader(event.x(), event.y()) && this.minecraft != null) {
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
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    /**
     * A button of the menu, drawn as the config draws its buttons (see {@link MenuStyle#button}): its
     * icon and label centred together. It shows as hovered under the mouse or, while the player
     * steps through the menu with the keyboard, when focused; and it rises into place as it fades in
     * at its own moment of the open stagger.
     */
    private final class PauseButton extends AbstractButton {
        private final MenuStyle.Kind kind;
        private final int appearIndex;
        private final String glyph;
        private final String label;
        private final ButtonAction action;
        private float hover = 0.0f;

        PauseButton(MenuStyle.Kind kind, int appearIndex, int x, int y, int width, int height, String glyph,
                    String label, ButtonAction action) {
            super(x, y, width, height, GuiFont.text(label));
            this.kind = kind;
            this.appearIndex = appearIndex;
            this.glyph = glyph;
            this.label = label;
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
            float appear = MenuStyle.easeOutCubic(progress);
            if (appear <= 0.01f) return;

            boolean mouseOver = mouseX >= this.getX() && mouseX < this.getX() + this.width
                    && mouseY >= this.getY() && mouseY < this.getY() + this.height && this.active;
            boolean hovered = MenuStyle.highlighted(mouseOver, this.isFocused());
            this.hover += ((hovered ? 1.0f : 0.0f) - this.hover) * MenuStyle.HOVER_EASE;

            Font font = Minecraft.getInstance().font;
            graphics.pose().pushMatrix();
            graphics.pose().translate(0.0f, rise(progress));
            MenuStyle.button(graphics, font, this.getX(), this.getY(), this.width, this.height, this.glyph,
                    this.label, this.kind, this.hover, appear);
            graphics.pose().popMatrix();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {
            this.defaultButtonNarrationText(narration);
        }
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
