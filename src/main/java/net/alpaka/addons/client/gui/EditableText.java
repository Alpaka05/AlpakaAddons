package net.alpaka.addons.client.gui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.IntPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.client.input.KeyEvent;

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
    /** Which characters typing and pasting may add; anything else is dropped. Null takes every printable one. */
    private IntPredicate filter = null;

    /** Earlier states for Ctrl/Cmd+Z, newest first, and the ones undone since, for redo. */
    private final Deque<State> undo = new ArrayDeque<>();
    private final Deque<State> redo = new ArrayDeque<>();
    /**
     * What the last change was. A run of typed letters is undone as one step, as in any text
     * field, so a new undo step only starts when the kind of change switches or a word ends.
     */
    private Edit lastEdit = Edit.NONE;

    private record State(String text, int caret, int anchor) {}

    private enum Edit { NONE, TYPE, DELETE, OTHER }

    private static final int MAX_UNDO = 50;

    public EditableText(int maxLength) {
        this.maxLength = maxLength;
    }

    /** Limits what typing and pasting add, such as only hex digits in a color field. */
    public EditableText filter(IntPredicate allowed) {
        this.filter = allowed;
        return this;
    }

    public String getText() {
        return text;
    }

    /** Replaces the content outright, putting the caret at the end and dropping any selection. */
    public void setText(String value) {
        this.text = value == null ? "" : trimToMax(value);
        moveCaret(this.text.length(), false);
        undo.clear();
        redo.clear();
        lastEdit = Edit.NONE;
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

    /**
     * Handles one typed character. Returns true when the content changed.
     *
     * A selection is replaced rather than added to, which is what makes "select all, then type"
     * behave the way it does everywhere else.
     */
    public boolean insert(char c) {
        if (c < 32 || c == 127) return false;
        return insertString(String.valueOf(c), c == ' ' ? Edit.OTHER : Edit.TYPE);
    }

    /** Handles one typed code point, so a character outside the basic plane is not cut in half. */
    public boolean insert(int codePoint) {
        if (codePoint < 32 || codePoint == 127) return false;
        return insertString(new String(Character.toChars(codePoint)), codePoint == ' ' ? Edit.OTHER : Edit.TYPE);
    }

    private boolean insertString(String insertion, Edit kind) {
        if (filter != null) {
            StringBuilder kept = new StringBuilder();
            insertion.codePoints().filter(filter).forEach(kept::appendCodePoint);
            insertion = kept.toString();
        }
        if (insertion.isEmpty()) return false;
        String before = text.substring(0, getSelectionStart());
        String after = text.substring(getSelectionEnd());

        // Trimmed against what is left after the selection goes, not against the current length:
        // typing over a full field's selection has to be allowed to succeed.
        int room = maxLength - (before.length() + after.length());
        if (room <= 0) return false;
        String toInsert = insertion.length() > room ? insertion.substring(0, room) : insertion;

        remember(kind);
        text = before + toInsert + after;
        moveCaret(before.length() + toInsert.length(), false);
        return true;
    }

    /**
     * Handles one key press with the platform's usual shortcuts. Returns true when the key belonged
     * to this field, whether or not it changed anything - an arrow key at the end of the line is
     * still this field's key to swallow.
     *
     * On macOS Cmd takes Ctrl's place for select all, copy, cut, paste and undo, Option jumps or
     * deletes a word and Cmd with an arrow or Backspace goes to the start or end of the line, as in
     * every Mac text field. Elsewhere Ctrl jumps or deletes a word.
     */
    public boolean keyPressed(KeyEvent event) {
        boolean shortcut = event.hasControlDownWithQuirk();
        boolean mac = InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY;
        boolean word = mac ? event.hasAltDown() : event.hasControlDown();
        return keyPressed(event.key(), shortcut, word, mac && shortcut, event.hasShiftDown());
    }

    /**
     * As above, from the bare key: {@code ctrl} is the shortcut modifier and also jumps by word.
     *
     * @param key   the GLFW key code.
     * @param ctrl  whether the platform's word/command modifier is held (Ctrl, or Cmd on macOS).
     * @param shift whether Shift is held, which extends the selection rather than dropping it.
     */
    public boolean keyPressed(int key, boolean ctrl, boolean shift) {
        return keyPressed(key, ctrl, ctrl, false, shift);
    }

    private boolean keyPressed(int key, boolean shortcut, boolean word, boolean line, boolean shift) {
        switch (key) {
            case KEY_A:
                if (shortcut) {
                    selectAll();
                    return true;
                }
                return false;
            case KEY_C:
                if (shortcut) {
                    copyToClipboard();
                    return true;
                }
                return false;
            case KEY_X:
                if (shortcut) {
                    copyToClipboard();
                    if (hasSelection()) {
                        remember(Edit.OTHER);
                        deleteSelection();
                    }
                    return true;
                }
                return false;
            case KEY_V:
                if (shortcut) {
                    paste();
                    return true;
                }
                return false;
            case KEY_Z:
                if (shortcut) {
                    if (shift) redo();
                    else undo();
                    return true;
                }
                return false;
            case KEY_Y:
                // Ctrl+Y redoes on Windows and Linux; on a Mac it is Cmd+Shift+Z alone.
                if (shortcut && !InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY) {
                    redo();
                    return true;
                }
                return false;
            case KEY_BACKSPACE:
                if (hasSelection()) {
                    remember(Edit.DELETE);
                    deleteSelection();
                } else if (caret > 0) {
                    int from = line ? 0 : word ? previousWord(caret) : caret - 1;
                    remember(Edit.DELETE);
                    text = text.substring(0, from) + text.substring(caret);
                    moveCaret(from, false);
                }
                return true;
            case KEY_DELETE:
                if (hasSelection()) {
                    remember(Edit.DELETE);
                    deleteSelection();
                } else if (caret < text.length()) {
                    int to = line ? text.length() : word ? nextWord(caret) : caret + 1;
                    remember(Edit.DELETE);
                    text = text.substring(0, caret) + text.substring(to);
                }
                return true;
            case KEY_LEFT:
                // Moving the caret by hand ends a run of typing, so the next letters are an undo
                // step of their own; the same for Right, Home and End.
                lastEdit = Edit.NONE;
                // Without Shift a selection collapses to its near edge rather than the caret
                // stepping on from wherever it happened to be, which is the usual behaviour.
                if (line) moveCaret(0, shift);
                else if (hasSelection() && !shift && !word) moveCaret(getSelectionStart(), false);
                else moveCaret(word ? previousWord(caret) : Math.max(0, caret - 1), shift);
                return true;
            case KEY_RIGHT:
                lastEdit = Edit.NONE;
                if (line) moveCaret(text.length(), shift);
                else if (hasSelection() && !shift && !word) moveCaret(getSelectionEnd(), false);
                else moveCaret(word ? nextWord(caret) : Math.min(text.length(), caret + 1), shift);
                return true;
            case KEY_HOME:
                lastEdit = Edit.NONE;
                moveCaret(0, shift);
                return true;
            case KEY_END:
                lastEdit = Edit.NONE;
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
        setCaretFromX(x, font::width);
    }

    /** As above, with the measure supplied - the config screen measures in its own typeface. */
    public void setCaretFromX(int x, java.util.function.ToIntFunction<String> measure) {
        setCaretFromX(x, measure, false);
    }

    /** As above; with {@code extend} the selection runs from where it started to the click, as Shift+click does. */
    public void setCaretFromX(int x, java.util.function.ToIntFunction<String> measure, boolean extend) {
        int best = 0;
        int bestDistance = Math.abs(x);
        for (int i = 1; i <= text.length(); i++) {
            int distance = Math.abs(measure.applyAsInt(text.substring(0, i)) - x);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        moveCaret(best, extend);
        lastEdit = Edit.NONE;
    }

    /** Puts the caret at this position, dropping any selection; for putting back a refused edit. */
    public void setCaret(int position) {
        moveCaret(position, false);
    }

    public void selectAll() {
        lastEdit = Edit.NONE;
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

    /** Saves the state before a change, unless the change only carries on the last one. */
    private void remember(Edit kind) {
        if (kind == Edit.OTHER || kind != lastEdit) {
            undo.push(new State(text, caret, anchor));
            while (undo.size() > MAX_UNDO) undo.removeLast();
        }
        redo.clear();
        lastEdit = kind;
    }

    /** Puts back the state before the last change. Returns true when there was one. */
    public boolean undo() {
        if (undo.isEmpty()) return false;
        redo.push(new State(text, caret, anchor));
        restore(undo.pop());
        return true;
    }

    /** Applies again the change undone last. Returns true when there was one. */
    public boolean redo() {
        if (redo.isEmpty()) return false;
        undo.push(new State(text, caret, anchor));
        restore(redo.pop());
        return true;
    }

    private void restore(State state) {
        text = state.text();
        caret = Math.min(state.caret(), text.length());
        anchor = Math.min(state.anchor(), text.length());
        lastEdit = Edit.NONE;
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
        // Trimmed too: a key or a name copied from a web page or Discord often brings a space along.
        clip = clip.replace("\r", "").replace("\t", " ").strip();
        if (!clip.isEmpty()) insertString(clip, Edit.OTHER);
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
    private static final int KEY_Y = 89;
    private static final int KEY_Z = 90;
    private static final int KEY_RIGHT = 262;
    private static final int KEY_LEFT = 263;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;
    private static final int KEY_END = 269;
    private static final int KEY_HOME = 268;
}
