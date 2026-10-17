package net.alpaka.addons.features.chat;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * What the chat's two drawing accesses gain through their mixin: a clip for the text.
 *
 * The chat draws its text through an {@code ActiveTextCollector} whose clip rectangle lives in its
 * own parameters, not in the graphics' scissor stack - so a scissor on the graphics cuts the line
 * backgrounds and nothing else. To keep scrolling lines inside the chat box, both have to be set:
 * the graphics' scissor for fills, this one for the glyphs. Coordinates are the chat's own, under
 * the pose in effect when the clip is set.
 */
public interface AlpakaChatGraphicsClip {

    void alpaka$setTextClip(int x0, int x1, int y0, int y1);

    void alpaka$clearTextClip();

    GuiGraphicsExtractor alpaka$graphics();
}
