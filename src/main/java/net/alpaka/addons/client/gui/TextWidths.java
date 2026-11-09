package net.alpaka.addons.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Text widths measured once and kept: HUDs draw the same labels every frame (room names, the
 * scoreboard footer, timers, tab bars), and measuring walks every glyph each time. A width only
 * changes with the font or its glyphs, so everything is dropped when the font object or {@link
 * AlpakaStyle#fontGeneration} (bumped after each resource reload and font option change) changes.
 *
 * Components are kept by object, so only pass ones that are themselves kept and never changed
 * (cached components); strings are kept by value. Off the render thread nothing is kept.
 */
public final class TextWidths {
    private TextWidths() {}

    private static final int LIMIT = 512;
    private static final Map<Component, Integer> COMPONENTS = new IdentityHashMap<>();
    private static final Map<String, Integer> STRINGS = new HashMap<>();
    private static Font measuredWith;
    private static int generation = -1;

    /** {@code font.width(text)} for a kept component. */
    public static int of(Font font, Component text) {
        if (!fresh(font)) return font.width(text);
        Integer known = COMPONENTS.get(text);
        if (known != null) return known;
        int width = font.width(text);
        if (COMPONENTS.size() >= LIMIT) COMPONENTS.clear();
        COMPONENTS.put(text, width);
        return width;
    }

    /** {@code font.width(text)} for a plain string. */
    public static int of(Font font, String text) {
        if (!fresh(font)) return font.width(text);
        Integer known = STRINGS.get(text);
        if (known != null) return known;
        int width = font.width(text);
        if (STRINGS.size() >= LIMIT) STRINGS.clear();
        STRINGS.put(text, width);
        return width;
    }

    /** Whether the kept widths may be used: on the render thread, for the font they were measured with. */
    private static boolean fresh(Font font) {
        if (!Minecraft.getInstance().isSameThread()) return false;
        if (font != measuredWith || generation != AlpakaStyle.fontGeneration) {
            COMPONENTS.clear();
            STRINGS.clear();
            measuredWith = font;
            generation = AlpakaStyle.fontGeneration;
        }
        return true;
    }
}
