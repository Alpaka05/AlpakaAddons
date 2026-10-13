package net.alpaka.addons.client;

import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.AlpakaStyle.ButtonKind;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static net.alpaka.addons.client.gui.AlpakaStyle.inside;

/**
 * A yes-or-no question in the config menu's look, for the few settings that cannot be undone with
 * a click. Escape and Cancel go back without doing anything; only the confirm button acts.
 */
public class AlpakaConfirmScreen extends Screen {
    private final Screen parent;
    private final String message;
    private final String confirmLabel;
    private final boolean dangerous;
    private final Runnable onConfirm;
    private long openTimeMs = 0L;
    /**
     * Laid out in init(), which also runs on every resize and after a resource reload; the message
     * does not change while the dialog is up, so it is no longer wrapped and reordered anew every frame.
     */
    private Layout layout;
    private final String titleText;

    private static final int W = 240;
    private static final int BTN_W = 70;

    public AlpakaConfirmScreen(Screen parent, String title, String message, String confirmLabel,
                               boolean dangerous, Runnable onConfirm) {
        super(GuiFont.text(title));
        this.parent = parent;
        this.message = message;
        this.confirmLabel = confirmLabel;
        this.dangerous = dangerous;
        this.onConfirm = onConfirm;
        this.titleText = this.title.getString();
    }

    @Override
    protected void init() {
        openTimeMs = System.currentTimeMillis();
        layout = computeLayout();
    }

    private record Layout(int x, int y, int w, int h, List<FormattedCharSequence> lines, int btnY, int confirmX, int cancelX) {}

    private Layout computeLayout() {
        int w = Math.min(W, this.width - 16);
        List<FormattedText> wrapped = GuiFont.splitLines(this.font, message, w - 2 * (AlpakaStyle.PAD + 2));
        int h = AlpakaStyle.HEADER_H + 10 + wrapped.size() * 10 + 12 + AlpakaStyle.BUTTON_H + AlpakaStyle.PAD;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        int btnY = y + h - AlpakaStyle.PAD - AlpakaStyle.BUTTON_H;
        int confirmX = x + w - AlpakaStyle.PAD - BTN_W;
        int cancelX = confirmX - 6 - BTN_W;
        List<FormattedCharSequence> lines = Language.getInstance().getVisualOrder(wrapped);
        return new Layout(x, y, w, h, lines, btnY, confirmX, cancelX);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        AlpakaStyle.backdrop(graphics, this.width, this.height);
        boolean pushed = AlpakaStyle.beginOpen(graphics, openTimeMs, this.width, this.height);
        Layout l = layout;
        AlpakaStyle.panel(graphics, l.x(), l.y(), l.w(), l.h());
        AlpakaStyle.header(graphics, this.font, titleText, l.x(), l.y(), l.w());
        int ty = l.y() + AlpakaStyle.HEADER_H + 10;
        for (FormattedCharSequence line : l.lines()) {
            graphics.text(this.font, line, l.x() + AlpakaStyle.PAD + 2, ty, AlpakaStyle.TEXT_SOFT, false);
            ty += 10;
        }
        boolean hoverCancel = inside(mouseX, mouseY, l.cancelX(), l.btnY(), BTN_W, AlpakaStyle.BUTTON_H);
        boolean hoverConfirm = inside(mouseX, mouseY, l.confirmX(), l.btnY(), BTN_W, AlpakaStyle.BUTTON_H);
        AlpakaStyle.button(graphics, this.font, l.cancelX(), l.btnY(), BTN_W, AlpakaStyle.BUTTON_H, "Cancel", hoverCancel, ButtonKind.PLAIN);
        AlpakaStyle.button(graphics, this.font, l.confirmX(), l.btnY(), BTN_W, AlpakaStyle.BUTTON_H, confirmLabel, hoverConfirm,
                dangerous ? ButtonKind.DANGER : ButtonKind.PRIMARY);
        AlpakaStyle.endOpen(graphics, pushed);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        Layout l = layout;
        if (inside(event.x(), event.y(), l.confirmX(), l.btnY(), BTN_W, AlpakaStyle.BUTTON_H)) {
            click();
            onConfirm.run();
            onClose();
            return true;
        }
        if (inside(event.x(), event.y(), l.cancelX(), l.btnY(), BTN_W, AlpakaStyle.BUTTON_H)) {
            click();
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
        return super.keyPressed(event);
    }

    private static void click() {
        try {
            CustomSoundFeature.playButtonClickSound();
        } catch (Throwable ignored) {}
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(parent);
    }
}
