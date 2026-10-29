package net.alpaka.addons.features.dungeon.terminal;

import net.alpaka.addons.features.dungeon.terminal.TerminalSolution.Mark;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which color each cell of the custom terminal window gets, and the words around the grid. */
class TerminalGuiPaletteTest {

    private static final TerminalGuiPalette PALETTE = new TerminalGuiPalette(
            0xFF00FF00, 0xFF0000FF, 0xFF8800FF, 0xFF11FF11, 0xFFFF7711, 0xFFFF00FF, 0xE0101014);

    @Test
    void clickInOrderByItsMarks() {
        TerminalSolution s = TerminalSolution.builder(TerminalKind.NUMBERS)
                .mark(11, Mark.CLICK).mark(12, Mark.SECOND).mark(13, Mark.THIRD).mark(14, Mark.DONE)
                .loaded(true).build();
        assertEquals(PALETTE.solution(), PALETTE.fill(s, 11));
        assertEquals(PALETTE.next(), PALETTE.fill(s, 12));
        assertEquals(PALETTE.third(), PALETTE.fill(s, 13));
        assertEquals(0, PALETTE.fill(s, 14), "done");
        assertEquals(0, PALETTE.fill(s, 15), "other");
    }

    @Test
    void changeAllToSameColorByDirection() {
        TerminalSolution s = TerminalSolution.builder(TerminalKind.RUBIX)
                .mark(12, Mark.CLICK).clicks(12, 2)
                .mark(13, Mark.CLICK).clicks(13, -1)
                .mark(14, Mark.DONE)
                .loaded(true).build();
        assertEquals(PALETTE.rubixLeft(), PALETTE.fill(s, 12));
        assertEquals(PALETTE.rubixRight(), PALETTE.fill(s, 13));
        assertEquals(0, PALETTE.fill(s, 14));
    }

    @Test
    void clickTheButtonOnTimeIsSteady() {
        // Target column 3, lime pane in row 2 at column 1, the button of row 2.
        TerminalSolution apart = TerminalSolution.builder(TerminalKind.MELODY).melody(3, 1, 2, 25).loaded(true).build();
        // The same a moment later, with the pane lined up under the target.
        TerminalSolution lined = TerminalSolution.builder(TerminalKind.MELODY).melody(3, 3, 2, 25).loaded(true).build();
        assertTrue(lined.aligned());
        assertFalse(apart.aligned());

        assertEquals(PALETTE.melody(), PALETTE.fill(apart, 3), "top of the target column");
        assertEquals(PALETTE.melody(), PALETTE.fill(apart, 39), "bottom of the target column");
        assertEquals(PALETTE.melody(), PALETTE.fill(apart, 19), "the moving pane");
        assertEquals(PALETTE.solution(), PALETTE.fill(apart, 25), "the button");
        assertEquals(0, PALETTE.fill(apart, 21), "a red pane of the active row");
        assertEquals(0, PALETTE.fill(apart, 16), "another row's button");

        // Nothing changes color at the moment the pane lines up: no "click now" cue.
        assertEquals(PALETTE.fill(apart, 25), PALETTE.fill(lined, 25));
        assertEquals(PALETTE.fill(apart, 3), PALETTE.fill(lined, 3));
        assertEquals(PALETTE.fill(apart, 39), PALETTE.fill(lined, 39));
        assertEquals(PALETTE.fill(apart, 19), PALETTE.fill(lined, 21), "the moving pane wherever it is");
    }

    @Test
    void clickTheButtonOnTimeUnknownDrawsNothing() {
        TerminalSolution unknown = TerminalSolution.empty(TerminalKind.MELODY);
        for (int slot = 0; slot < TerminalSolution.MAX_SLOTS; slot++) assertEquals(0, PALETTE.fill(unknown, slot));
    }

    @Test
    void textStaysReadableOnTheBackground() {
        assertTrue(TerminalGuiPalette.dark(0xE0101014), "the default");
        assertTrue(TerminalGuiPalette.dark(0xFF000000));
        assertFalse(TerminalGuiPalette.dark(0xFFF2F2F2));
        assertTrue(TerminalGuiPalette.dark(0x10FFFFFF), "almost see-through: the dimmed game shows");
        assertEquals(TerminalGuiPalette.TEXT_ON_DARK, PALETTE.text());
        assertEquals(TerminalGuiPalette.TEXT_ON_LIGHT,
                new TerminalGuiPalette(0, 0, 0, 0, 0, 0, 0xFFF2F2F2).text());
    }

    @Test
    void alphaHelpers() {
        assertEquals(0x80123456, TerminalGuiPalette.withAlpha(0xFF123456, 0x80));
        assertEquals(0x00123456, TerminalGuiPalette.withAlpha(0xFF123456, -5));
        assertEquals(0xFF123456, TerminalGuiPalette.withAlpha(0x00123456, 300));
        assertEquals(0x80123456, TerminalGuiPalette.scaleAlpha(0xFF123456, 128 / 255.0f));
        assertEquals(0x00123456, TerminalGuiPalette.scaleAlpha(0xFF123456, -1.0f));
    }

    @Test
    void whatTheTerminalAsksFor() {
        assertEquals("'S'", TerminalGuiText.argument(TerminalKind.STARTS_WITH, "What starts with: 'S'?"));
        assertEquals("Red", TerminalGuiText.argument(TerminalKind.SELECT_ALL, "Select all the RED items!"));
        assertEquals("Light Blue", TerminalGuiText.argument(TerminalKind.SELECT_ALL, "Select all the LIGHT BLUE items!"));
        assertEquals("Silver (Light Gray)", TerminalGuiText.argument(TerminalKind.SELECT_ALL, "Select all the SILVER items!"));
        assertNull(TerminalGuiText.argument(TerminalKind.PANES, "Correct all the panes!"));
        assertNull(TerminalGuiText.argument(TerminalKind.MELODY, "Click the button on time!"));
        assertNull(TerminalGuiText.argument(TerminalKind.SELECT_ALL, "Click in order!"));
    }

    @Test
    void everyRefusalExplainsItself() {
        for (TerminalClicks.Verdict verdict : TerminalClicks.Verdict.values()) {
            if (verdict == TerminalClicks.Verdict.ALLOW) assertNull(TerminalGuiText.refusal(verdict));
            else assertNotNull(TerminalGuiText.refusal(verdict));
        }
        assertNull(TerminalGuiText.refusal(null));
    }
}
