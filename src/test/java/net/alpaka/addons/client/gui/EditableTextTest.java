package net.alpaka.addons.client.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

class EditableTextTest {
    private static final int LEFT = InputConstants.KEY_LEFT;
    private static final int RIGHT = InputConstants.KEY_RIGHT;
    private static final int HOME = InputConstants.KEY_HOME;
    private static final int END = InputConstants.KEY_END;
    private static final int BACKSPACE = InputConstants.KEY_BACKSPACE;
    private static final int A = InputConstants.KEY_A;
    private static final int Z = InputConstants.KEY_Z;

    private static EditableText field(String text) {
        EditableText edit = new EditableText(32);
        edit.setText(text);
        return edit;
    }

    private static void type(EditableText edit, String text) {
        text.codePoints().forEach(edit::insert);
    }

    @Test
    void typesAtTheCaretNotAtTheEnd() {
        EditableText edit = field("CultureGround");
        edit.keyPressed(HOME, false, false);
        type(edit, "x");
        assertEquals("xCultureGround", edit.getText());
        edit.keyPressed(RIGHT, false, false);
        edit.keyPressed(BACKSPACE, false, false);
        assertEquals("xultureGround", edit.getText());
        assertEquals(1, edit.getCaret());
    }

    @Test
    void selectAllThenTypeReplaces() {
        EditableText edit = field("old name");
        assertTrue(edit.keyPressed(A, true, false));
        type(edit, "new");
        assertEquals("new", edit.getText());
    }

    @Test
    void shiftArrowsSelectAndArrowsCollapse() {
        EditableText edit = field("abcdef");
        edit.keyPressed(LEFT, false, true);
        edit.keyPressed(LEFT, false, true);
        assertEquals(4, edit.getSelectionStart());
        assertEquals(6, edit.getSelectionEnd());
        edit.keyPressed(LEFT, false, false);
        assertFalse(edit.hasSelection());
        assertEquals(4, edit.getCaret());
        edit.keyPressed(END, false, false);
        assertEquals(6, edit.getCaret());
    }

    @Test
    void wordJumps() {
        EditableText edit = field("warp crimson isle");
        edit.keyPressed(LEFT, true, false);
        assertEquals(13, edit.getCaret());
        edit.keyPressed(LEFT, true, false);
        assertEquals(5, edit.getCaret());
        edit.keyPressed(BACKSPACE, true, false);
        assertEquals("crimson isle", edit.getText());
    }

    @Test
    void undoTakesBackARunOfTypingAtOnce() {
        EditableText edit = field("ab");
        type(edit, "cd");
        edit.keyPressed(BACKSPACE, false, false);
        assertEquals("abc", edit.getText());
        edit.keyPressed(Z, true, false);
        assertEquals("abcd", edit.getText());
        edit.keyPressed(Z, true, false);
        assertEquals("ab", edit.getText());
        edit.keyPressed(Z, true, true);
        assertEquals("abcd", edit.getText());
    }

    @Test
    void filterAndMaxLength() {
        EditableText edit = new EditableText(4).filter(c -> Character.digit(c, 16) >= 0);
        type(edit, "fgz09a");
        assertEquals("f09a", edit.getText());
        edit.keyPressed(A, true, false);
        type(edit, "1");
        assertEquals("1", edit.getText());
    }

    @Test
    void caretFromClickUsesTheMeasure() {
        EditableText edit = field("abcd");
        // Every character 6 px wide: a click at 13 px is nearest the boundary after "ab".
        edit.setCaretFromX(13, s -> s.length() * 6);
        assertEquals(2, edit.getCaret());
        edit.setCaretFromX(24, s -> s.length() * 6, true);
        assertEquals(2, edit.getSelectionStart());
        assertEquals(4, edit.getSelectionEnd());
    }
}
