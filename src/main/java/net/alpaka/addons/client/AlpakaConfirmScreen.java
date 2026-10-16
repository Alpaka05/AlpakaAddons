package net.alpaka.addons.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.AlpakaStyle.ButtonKind;
import net.alpaka.addons.client.gui.GuiFont;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static net.alpaka.addons.client.gui.AlpakaStyle.inside;

/**
 * A yes-or-no question in the config menu's look, the one way the mod asks before anything that
 * cannot be undone with a click: resetting or disabling settings, saving over a preset, deleting a
 * screenshot, leaving the game, opening a link. Escape and Cancel go back without doing anything;
 * the confirm button and Enter act.
 *
 * The window it was opened from stays in sight behind it, dimmed, so the question reads as part of
 * that window rather than a screen of its own - and nothing flashes away and back on Cancel.
 */
public class AlpakaConfirmScreen extends Screen implements net.alpaka.addons.client.gui.NoticeHost {
    private final Screen parent;
    private final String message;
    private final String confirmLabel;
    private final boolean dangerous;
    private final Runnable onConfirm;
    private long openTimeMs = 0L;
    /**
     * Whether Enter may confirm. Not while the Enter that opened the dialog - on a focused button of
     * the window behind - is still held: its key repeats would otherwise confirm at once.
     */
    private boolean enterArmed = false;
    /**
     * Laid out in init(), which also runs on every resize and after a resource reload; the message
     * does not change while the dialog is up, so it is no longer wrapped and reordered anew every frame.
     */
    private Layout layout;
    private final String titleText;

    private static final int W = 240;
    private static final int MIN_BTN_W = 70;
    /** Room above and below the message. */
    private static final int GAP = 12;
    private static final int LINE_H = 10;
    /**
     * How long the window takes to pop open. Presses before then are ignored: the second click of
     * a double click on the button that opened it must not land on one of its own buttons.
     */
    private static final long CLICK_GUARD_MS = 220L;

    public AlpakaConfirmScreen(Screen parent, String title, String message, String confirmLabel,
                               boolean dangerous, Runnable onConfirm) {
        super(GuiFont.text(title));
        this.titleText = title;
        this.parent = parent;
        this.message = message;
        this.confirmLabel = confirmLabel;
        this.dangerous = dangerous;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        // Once only: coming back from a resize is not a fresh opening.
        if (openTimeMs == 0L) {
            openTimeMs = System.currentTimeMillis();
            enterArmed = this.minecraft == null
                    || !(InputConstants.isKeyDown(this.minecraft.getWindow(), GLFW.GLFW_KEY_ENTER)
                    || InputConstants.isKeyDown(this.minecraft.getWindow(), GLFW.GLFW_KEY_KP_ENTER));
        }
        layout = computeLayout();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        if (parent != null) parent.resize(width, height);
    }

    /** The narrator reads the question with its title, not the title alone. */
    @Override
    public Component getNarrationMessage() {
        return CommonComponents.joinForNarration(this.title, Component.literal(message));
    }

    private record Layout(int x, int y, int w, int h, List<FormattedCharSequence> lines, int btnY, int btnW,
                          int confirmX, int cancelX) {}

    private Layout computeLayout() {
        int w = Math.min(W, this.width - 16);
        List<FormattedText> wrapped = GuiFont.splitLines(this.font, message, w - 2 * (AlpakaStyle.PAD + 2));
        int h = AlpakaStyle.HEADER_H + GAP + wrapped.size() * LINE_H + GAP + AlpakaStyle.BUTTON_H + AlpakaStyle.PAD + 2;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        int btnY = y + h - AlpakaStyle.PAD - 2 - AlpakaStyle.BUTTON_H;
        // Both buttons as wide as the wider label needs, so they stay a pair.
        int btnW = Math.max(MIN_BTN_W, Math.max(GuiFont.width(this.font, confirmLabel), GuiFont.width(this.font, "Cancel")) + 20);
        int confirmX = x + w - AlpakaStyle.PAD - 2 - btnW;
        int cancelX = confirmX - 6 - btnW;
        List<FormattedCharSequence> lines = Language.getInstance().getVisualOrder(wrapped);
        return new Layout(x, y, w, h, lines, btnY, btnW, confirmX, cancelX);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // The window behind, as it was, without the mouse on it; then the dialog over it.
        if (parent != null && parent.width == this.width && parent.height == this.height) {
            net.alpaka.addons.features.notification.AlpakaNotifications.suppressOverScreen(true);
            try {
                parent.extractBackground(graphics, -1, -1, partialTick);
                parent.extractRenderState(graphics, -1, -1, partialTick);
            } finally {
                net.alpaka.addons.features.notification.AlpakaNotifications.suppressOverScreen(false);
            }
            graphics.nextStratum();
        }
        AlpakaStyle.backdrop(graphics, this.width, this.height);
        boolean pushed = AlpakaStyle.beginOpen(graphics, openTimeMs, this.width, this.height);
        Layout l = layout;
        AlpakaStyle.panel(graphics, l.x(), l.y(), l.w(), l.h());
        AlpakaStyle.header(graphics, this.font, titleText, l.x(), l.y(), l.w());
        int ty = l.y() + AlpakaStyle.HEADER_H + GAP;
        for (FormattedCharSequence line : l.lines()) {
            graphics.text(this.font, line, l.x() + AlpakaStyle.PAD + 2, ty, AlpakaStyle.TEXT_SOFT, false);
            ty += LINE_H;
        }
        boolean hoverCancel = inside(mouseX, mouseY, l.cancelX(), l.btnY(), l.btnW(), AlpakaStyle.BUTTON_H);
        boolean hoverConfirm = inside(mouseX, mouseY, l.confirmX(), l.btnY(), l.btnW(), AlpakaStyle.BUTTON_H);
        AlpakaStyle.button(graphics, this.font, l.cancelX(), l.btnY(), l.btnW(), AlpakaStyle.BUTTON_H, "Cancel", hoverCancel, ButtonKind.PLAIN);
        AlpakaStyle.button(graphics, this.font, l.confirmX(), l.btnY(), l.btnW(), AlpakaStyle.BUTTON_H, confirmLabel, hoverConfirm,
                dangerous ? ButtonKind.DANGER : ButtonKind.PRIMARY);
        AlpakaStyle.endOpen(graphics, pushed);
        // Notices over the dialog, as over the config menu.
        net.alpaka.addons.features.notification.AlpakaNotifications.renderOverScreen(graphics);
    }

    /** For the screenshot tour: the middle of the Cancel button, to click it as a player would. */
    public int[] devCancelPoint() {
        Layout l = layout;
        return new int[] {l.cancelX() + l.btnW() / 2, l.btnY() + AlpakaStyle.BUTTON_H / 2};
    }

    /** For the screenshot tour: the middle of the confirm button. */
    public int[] devConfirmPoint() {
        Layout l = layout;
        return new int[] {l.confirmX() + l.btnW() / 2, l.btnY() + AlpakaStyle.BUTTON_H / 2};
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        if (System.currentTimeMillis() - openTimeMs < CLICK_GUARD_MS) return true;
        Layout l = layout;
        if (inside(event.x(), event.y(), l.confirmX(), l.btnY(), l.btnW(), AlpakaStyle.BUTTON_H)) {
            confirm();
            return true;
        }
        if (inside(event.x(), event.y(), l.cancelX(), l.btnY(), l.btnW(), AlpakaStyle.BUTTON_H)) {
            AlpakaStyle.click();
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (enterArmed) confirm();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) enterArmed = true;
        return super.keyReleased(event);
    }

    private void confirm() {
        AlpakaStyle.click();
        onConfirm.run();
        // An action that moves on by itself - leaving the world, say - is not undone by going back.
        if (this.minecraft != null && this.minecraft.gui.screen() == this) onClose();
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(parent);
    }
}
