package net.alpaka.addons.features.escapemenu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pause menu's pure parts: the alpha fade, the open stagger, and the icon codepoints.
 *
 * The screen itself cannot be built here - Screen's constructor reads the running game's font - so
 * what is tested is what the drawing is made of rather than the drawing.
 */
class CustomPauseScreenTest {
    private static final float EPSILON = 1e-6f;

    // ----------------------------------------------------------------- fade

    /** Colour, factor, and the colour with its alpha scaled: RGB is never touched. */
    @ParameterizedTest(name = "fade({0}, {1}) = {2}")
    @CsvSource({
            "0xFFEF4444, 1.0,  0xFFEF4444",
            "0xFFEF4444, 0.0,  0x00EF4444",
            "0xFFFFFFFF, 0.5,  0x80FFFFFF",
            "0x52000000, 0.5,  0x29000000",
            "0x14FFFFFF, 1.0,  0x14FFFFFF",
            // Out-of-range factors clamp rather than wrap into the colour channels.
            "0xFFEF4444, 2.0,  0xFFEF4444",
            "0xFFEF4444, -1.0, 0x00EF4444",
    })
    void fadeScalesOnlyTheAlpha(String color, float factor, String expected) {
        assertEquals(hex(expected), CustomPauseScreen.fade(hex(color), factor));
    }

    private static int hex(String s) {
        return Integer.parseUnsignedInt(s.substring(2), 16);
    }

    // --------------------------------------------------------------- appear

    @Test
    void indexMinusOneIsAlwaysFullyThere() {
        assertEquals(1.0f, CustomPauseScreen.appearAt(0.0f, -1));
        assertEquals(1.0f, CustomPauseScreen.appearAt(-5.0f, -1));
    }

    @Test
    void eachElementStartsAtZeroAndEndsAtOne() {
        for (int index = 0; index <= 5; index++) {
            float start = index * CustomPauseScreen.STAGGER_SECONDS;
            float end = start + CustomPauseScreen.APPEAR_SECONDS;
            assertEquals(0.0f, CustomPauseScreen.appearAt(start, index), "index " + index + " at its start");
            assertEquals(0.0f, CustomPauseScreen.appearAt(start - 0.01f, index), "index " + index + " before its start");
            assertEquals(1.0f, CustomPauseScreen.appearAt(end, index), "index " + index + " at its end");
            assertEquals(1.0f, CustomPauseScreen.appearAt(end + 1.0f, index), "index " + index + " long after");
        }
    }

    @Test
    void appearanceRisesSteadilyAndEasesOut() {
        float previous = 0.0f;
        for (int ms = 0; ms <= 200; ms++) {
            float value = CustomPauseScreen.appearAt(ms / 1000.0f, 0);
            assertTrue(value >= previous, "dropped at " + ms + " ms");
            previous = value;
        }
        // Ease-out: halfway through the time it is well past halfway there (cubic: 1 - 0.5^3).
        float half = CustomPauseScreen.appearAt(CustomPauseScreen.APPEAR_SECONDS / 2, 0);
        assertEquals(0.875f, half, 1e-4f);
    }

    @Test
    void laterRowsLagEarlierOnes() {
        // Once the last row has started and before the dim has finished, everything is mid-fade:
        // the dim is ahead of the logo, which is ahead of each row in turn.
        float t = (5 * CustomPauseScreen.STAGGER_SECONDS + CustomPauseScreen.APPEAR_SECONDS) / 2;
        for (int index = 1; index <= 5; index++) {
            assertTrue(CustomPauseScreen.appearAt(t, index) < CustomPauseScreen.appearAt(t, index - 1),
                    "index " + index + " should trail index " + (index - 1));
        }
        // ...and each is exactly the previous one, shifted by one stagger step.
        assertEquals(CustomPauseScreen.appearAt(t, 2),
                CustomPauseScreen.appearAt(t + CustomPauseScreen.STAGGER_SECONDS, 3), EPSILON);
    }

    // ---------------------------------------------------------------- icons

    /**
     * Each icon constant is the codepoint of its cell in the sprite sheet. The order is set by
     * tools/GenPauseIcons.java (play, server, box, sliders, book, door, puzzle, potion from U+E000),
     * and a constant one cell off draws the neighbouring icon without any error.
     */
    @Test
    void iconsPointAtTheirSpriteSheetCells() {
        assertEquals("", CustomPauseScreen.ICON_PLAY);
        assertEquals("", CustomPauseScreen.ICON_SERVER);
        assertEquals("", CustomPauseScreen.ICON_SLIDERS);
        assertEquals("", CustomPauseScreen.ICON_BOOK);
        assertEquals("", CustomPauseScreen.ICON_DOOR);
        assertEquals("", CustomPauseScreen.ICON_PUZZLE);
    }

    /** A codepoint the font json does not list is a missing glyph, drawn as a box. */
    @Test
    void everyIconIsListedInTheFont() throws IOException {
        String json = Files.readString(Path.of("src/main/resources/assets/alpaka/font/pause_icons.json"));
        Matcher chars = Pattern.compile("\"chars\"\\s*:\\s*\\[\\s*\"([^\"]*)\"").matcher(json);
        assertTrue(chars.find(), "pause_icons.json has no chars row");
        String listed = unescape(chars.group(1));

        for (String icon : List.of(CustomPauseScreen.ICON_PLAY, CustomPauseScreen.ICON_SERVER,
                CustomPauseScreen.ICON_SLIDERS, CustomPauseScreen.ICON_BOOK,
                CustomPauseScreen.ICON_DOOR, CustomPauseScreen.ICON_PUZZLE)) {
            assertTrue(listed.contains(icon),
                    String.format("U+%04X is not in pause_icons.json", icon.codePointAt(0)));
        }
    }

    /** The json writes the codepoints as \\uXXXX escapes. */
    private static String unescape(String s) {
        return Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(s)
                .replaceAll(m -> String.valueOf((char) Integer.parseInt(m.group(1), 16)));
    }
}
