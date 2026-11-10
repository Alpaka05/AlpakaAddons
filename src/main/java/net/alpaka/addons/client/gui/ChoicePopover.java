package net.alpaka.addons.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.lwjgl.glfw.GLFW;

/**
 * The list a select field opens when it floats over the window rather than unfolding inside a
 * column: a popover under the field, its entries drawn as {@link AlpakaStyle#choiceEntry}. One per
 * select field; the screen draws it last, so it lies over everything, and asks it first on a click
 * and on a key.
 */
public final class ChoicePopover {
    private static final int ENTRY_H = 16;
    private static final int PAD = 4;
    /** Room a name needs beyond its text: the entry's indent and the popover's padding. */
    private static final int ENTRY_EXTRA = 28;
    private static final int SCREEN_MARGIN = 8;

    private boolean open;
    private int x;
    private int y;
    private int w;
    private int count;

    public boolean isOpen() {
        return open;
    }

    /**
     * Opens the list under the field, or closes it if it was open. It is at least as wide as the
     * field and as wide as its longest name needs, right-aligned with the field and kept on screen,
     * so no name in it is ever cut.
     */
    public void toggle(Font font, String[] names, int fieldX, int fieldY, int fieldW, int fieldH, int screenW, int screenH) {
        if (open) {
            open = false;
            return;
        }
        int widest = 0;
        for (String name : names) widest = Math.max(widest, GuiFont.width(font, name));
        open = true;
        w = Math.max(fieldW, widest + ENTRY_EXTRA);
        x = Math.max(SCREEN_MARGIN, Math.min(fieldX + fieldW - w, screenW - SCREEN_MARGIN - w));
        count = names.length;
        // Under the field, or over it where the screen ends first (a field in a footer).
        int h = count * ENTRY_H + PAD * 2;
        y = fieldY + fieldH + 2 + h <= screenH - SCREEN_MARGIN ? fieldY + fieldH + 2 : fieldY - 2 - h;
    }

    public void close() {
        open = false;
    }

    /** Draws the open list over everything drawn before it; nothing while closed. */
    public void draw(GuiGraphicsExtractor graphics, Font font, String[] names, int current, int mouseX, int mouseY) {
        if (!open) return;
        graphics.nextStratum();
        int h = count * ENTRY_H + PAD * 2;
        AlpakaStyle.popover(graphics, x, y, w, h);
        for (int i = 0; i < count && i < names.length; i++) {
            int ey = y + PAD + i * ENTRY_H;
            boolean hover = AlpakaStyle.inside(mouseX, mouseY, x + PAD, ey, w - PAD * 2, ENTRY_H);
            AlpakaStyle.choiceEntry(graphics, font, x + PAD, ey, w - PAD * 2, ENTRY_H, names[i], i == current, hover);
        }
    }

    /**
     * A click while the list is open: the index of the entry clicked, or -1 for a click anywhere
     * else, which closes the list. Either way the click is the list's. Only call it while open.
     */
    public int click(double mouseX, double mouseY) {
        open = false;
        for (int i = 0; i < count; i++) {
            int ey = y + PAD + i * ENTRY_H;
            if (AlpakaStyle.inside(mouseX, mouseY, x + PAD, ey, w - PAD * 2, ENTRY_H)) return i;
        }
        return -1;
    }

    /**
     * A key while the list is open, which no control beneath it may see: Up and Down step the
     * choice (the index returned, applied at once, as the config's lists do), Enter and Escape close
     * the list. Returns the new index, or -1 when the choice stays. Only call it while open.
     */
    public int keyPressed(int key, int current) {
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) {
            int next = Math.max(0, Math.min(count - 1, current + (key == GLFW.GLFW_KEY_UP ? -1 : 1)));
            return next == current ? -1 : next;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_ESCAPE) open = false;
        return -1;
    }

    /** Whether the mouse is over the open list, so what lies beneath it shows no hover. */
    public boolean covers(double mouseX, double mouseY) {
        return open && AlpakaStyle.inside(mouseX, mouseY, x, y, w, count * ENTRY_H + PAD * 2);
    }
}
