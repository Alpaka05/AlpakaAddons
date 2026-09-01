package net.alpaka.addons.client.gui;

import net.minecraft.client.Minecraft;

/**
 * A one-line editable string with a caret and a selection, for the screens in this mod that draw
 * their own text fields rather than using a vanilla widget.
 *
 * The config screen's search bar started out holding nothing but a {@code String}: typing appended a
 * character, backspace removed the last one, and that was the whole model. With no caret and no
 * selection there was nothing for the usual editing keys to act on, which is why Ctrl+A did nothing
 * at all - not a broken shortcut so much as a missing concept. This is that missing concept, kept
 * separate from any one screen so the same behaviour can be given to the other hand-rolled fields.
 *
 * Positions are indices into {@link #text}, from 0 to its length. {@link #caret} is where typing
 * lands; {@link #anchor} is where the current selection started, and equals the caret when nothing
 * is selected.
 */
public final class EditableText {

    private String text = "";
    private int caret = 0;
    private int anchor = 0;
    private final int maxLength;

    public EditableText(int maxLength) {
        this.maxLength = maxLength;
    }

    public String getText() {
        return text;
    }

    /** Replaces the content outright, putting the caret at the end and dropping any selection. */
    public void setText(String value) {
        this.text = value == null ? "" : trimToMax(value);
        moveCaret(this.text.length(), false);
    }

    public boolean isEmpty() {
        return text.isEmpty();
    }

    public int getCaret() {
        return caret;
    }

    public boolean hasSelection() {
        return caret != anchor;
    }

    public int getSelectionStart() {
        return Math.min(caret, anchor);
    }

    public int getSelectionEnd() {
        return Math.max(caret, anchor);
    }

    /** Puts the caret at the end with nothing selected, for when the field gains focus fresh. */
    public void resetCaretToEnd() {
        moveCaret(text.length(), false);
    }

    /**
     * Handles one typed character. Returns true when the content changed.
     *
     * A selection is replaced rather than added to, which is what makes "select all, then type"
     * behave the way it does everywhere else.
     */
    public boolean insert(char c) {
        if (c < 32 || c == 127) return false;
        return insertString(String.valueOf(c));
    }

    private boolean insertString(String insertion) {
        String before = text.substring(0, getSelectionStart());
        String after = text.substring(getSelectionEnd());

        // Trimmed against what is left after the selection goes, not against the current length:
        // typing over a full field's selection has to be allowed to succeed.
        int room = maxLength - (before.length() + after.length());
        if (room <= 0) return false;
        String toInsert = insertion.length() > room ? insertion.substring(0, room) : insertion;

        text = before + toInsert + after;
        moveCaret(before.length() + toInsert.length(), false);
        return true;
    }

    /**
     * Handles one key press. Returns true when the key belonged to this field, whether or not it
     * changed anything - an arrow key at the end of the line is still this field's key to swallow.
     *
     * @param key   the GLFW key code.
     * @param ctrl  whether the platform's word/command modifier is held (Ctrl, or Cmd on macOS).
     * @param shift whether Shift is held, which extends the selection rather than dropping it.
     */
    public boolean keyPressed(int key, boolean ctrl, boolean shift) {
        switch (key) {
            case KEY_A:
                if (ctrl) {
                    selectAll();
                    return true;
                }
                return false;
            case KEY_C:
                if (ctrl) {
                    copyToClipboard();
                    return true;
                }
                return false;
            case KEY_X:
                if (ctrl) {
                    copyToClipboard();
                    deleteSelection();
                    return true;
                }
                return false;
            case KEY_V:
                if (ctrl) {
                    paste();
                    return true;
                }
                return false;
            case KEY_BACKSPACE:
                if (hasSelection()) {
                    deleteSelection();
                } else if (caret > 0) {
                    int from = ctrl ? previousWord(caret) : caret - 1;
                    text = text.substring(0, from) + text.substring(caret);
                    moveCaret(from, false);
                }
                return true;
            case KEY_DELETE:
                if (hasSelection()) {
                    deleteSelection();
                } else if (caret < text.length()) {
                    int to = ctrl ? nextWord(caret) : caret + 1;
                    text = text.substring(0, caret) + text.substring(to);
                }
                return true;
            case KEY_LEFT:
                // Without Shift a selection collapses to its near edge rather than the caret
                // stepping on from wherever it happened to be, which is the usual behaviour.
                if (hasSelection() && !shift) moveCaret(getSelectionStart(), false);
                else moveCaret(ctrl ? previousWord(caret) : Math.max(0, caret - 1), shift);
                return true;
            case KEY_RIGHT:
                if (hasSelection() && !shift) moveCaret(getSelectionEnd(), false);
                else moveCaret(ctrl ? nextWord(caret) : Math.min(text.length(), caret + 1), shift);
                return true;
            case KEY_HOME:
                moveCaret(0, shift);
                return true;
            case KEY_END:
                moveCaret(text.length(), shift);
                return true;
            default:
                return false;
        }
    }

    /**
     * Puts the caret at the character nearest to {@code x}, measured from the text's left edge.
     *
     * Walked one character at a time rather than measured with a binary search: the fields this
     * serves hold a few dozen characters at most and this runs on a click, so the simple version is
     * the right one. The caret lands on whichever side of a glyph the click was closer to, which is
     * what stops clicking the right half of a letter putting the caret before it.
     */
    public void setCaretFromX(int x, net.minecraft.client.gui.Font font) {
        int best = 0;
        int bestDistance = Math.abs(x);
        for (int i = 1; i <= text.length(); i++) {
            int distance = Math.abs(font.width(text.substring(0, i)) - x);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        moveCaret(best, false);
    }

    public void selectAll() {
        anchor = 0;
        caret = text.length();
    }

    /** Drops the selection without moving the caret, for when the field loses focus. */
    public void clearSelection() {
        anchor = caret;
    }

    private void moveCaret(int position, boolean keepAnchor) {
        caret = Math.max(0, Math.min(text.length(), position));
        if (!keepAnchor) anchor = caret;
    }

    private void deleteSelection() {
        if (!hasSelection()) return;
        int start = getSelectionStart();
        text = text.substring(0, start) + text.substring(getSelectionEnd());
        moveCaret(start, false);
    }

    private void copyToClipboard() {
        if (!hasSelection()) return;
        Minecraft.getInstance().keyboardHandler.setClipboard(text.substring(getSelectionStart(), getSelectionEnd()));
    }

    private void paste() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clip == null || clip.isEmpty()) return;
        // Newlines and tabs would be pasted as glyphs into what is a single-line field, so a
        // multi-line clipboard is flattened to its first line instead.
        int newline = clip.indexOf('\n');
        if (newline >= 0) clip = clip.substring(0, newline);
        clip = clip.replace("\r", "").replace("\t", " ");
        if (!clip.isEmpty()) insertString(clip);
    }

    /** The start of the word to the left of {@code from}, skipping the spaces in between. */
    private int previousWord(int from) {
        int i = from;
        while (i > 0 && text.charAt(i - 1) == ' ') i--;
        while (i > 0 && text.charAt(i - 1) != ' ') i--;
        return i;
    }

    /** The start of the word to the right of {@code from}, past the spaces in between. */
    private int nextWord(int from) {
        int i = from;
        int length = text.length();
        while (i < length && text.charAt(i) != ' ') i++;
        while (i < length && text.charAt(i) == ' ') i++;
        return i;
    }

    private String trimToMax(String value) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    // GLFW key codes, named rather than left as the bare numbers the screens used to compare against.
    private static final int KEY_A = 65;
    private static final int KEY_C = 67;
    private static final int KEY_V = 86;
    private static final int KEY_X = 88;
    private static final int KEY_RIGHT = 262;
    private static final int KEY_LEFT = 263;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;
    private static final int KEY_END = 269;
    private static final int KEY_HOME = 268;
}
