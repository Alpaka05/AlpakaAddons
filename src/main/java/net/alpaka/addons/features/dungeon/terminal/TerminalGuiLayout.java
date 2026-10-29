package net.alpaka.addons.features.dungeon.terminal;

import net.alpaka.addons.client.gui.AlpakaStyle;

/**
 * Where the custom terminal window puts things: the panel, its header, and one cell for each play
 * slot of the terminal. Pure arithmetic, so the hit-testing can be checked without a game.
 *
 * The grid is the terminal's play area laid out as the chest lays it out. A cell's place follows
 * from its slot index alone - row {@code slot / 9}, column {@code slot % 9} - and never from the
 * solution, so the cell under the mouse is always the slot drawn there and nothing else
 * (compliance.md C3): there is no layout in which a click lands on a slot other than the one the
 * player aimed at. Gaps between cells and spots of the area that are not play slots (Click the
 * Button on Time's column 6) hit nothing.
 *
 * Cells sit {@code round(18 * scale)} GUI pixels apart, the vanilla slot pitch scaled up, with a
 * small gap between them. When the screen is too small for that, the pitch shrinks until the panel
 * fits, down to {@link #MIN_PITCH}.
 */
public final class TerminalGuiLayout {

    /** The header's height, the same as every other window of the mod. */
    public static final int HEADER_H = AlpakaStyle.HEADER_H;
    /** Space between the panel's edge and its content. */
    public static final int PAD = AlpakaStyle.PAD;
    /** Space kept free around the panel, so it never touches the screen's edge. */
    public static final int MARGIN = 8;
    /** Vanilla's slot pitch, which the GUI Size setting scales. */
    public static final int SLOT_PITCH = 18;
    /** The smallest pitch the window shrinks to on a tiny screen; below this cells are hard to hit. */
    public static final int MIN_PITCH = 10;

    /** A rectangle in GUI pixels; {@link #contains} takes the left and top edge in, the right and bottom out. */
    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }

        public boolean overlaps(Rect o) {
            return x < o.x + o.w && o.x < x + w && y < o.y + o.h && o.y < y + h;
        }

        /** This rectangle grown by {@code by} on every side. */
        public Rect inflate(int by) {
            return new Rect(x - by, y - by, w + 2 * by, h + 2 * by);
        }
    }

    private final TerminalKind kind;
    private final int firstRow;
    private final int firstColumn;
    private final int rows;
    private final int columns;
    private final int pitch;
    private final int gap;
    private final int panelX;
    private final int panelY;
    private final int panelW;
    private final int panelH;
    private final int gridX;
    private final int gridY;

    private TerminalGuiLayout(TerminalKind kind, int firstRow, int firstColumn, int rows, int columns, int pitch,
                              int gap, int panelX, int panelY, int panelW, int panelH, int gridX, int gridY) {
        this.kind = kind;
        this.firstRow = firstRow;
        this.firstColumn = firstColumn;
        this.rows = rows;
        this.columns = columns;
        this.pitch = pitch;
        this.gap = gap;
        this.panelX = panelX;
        this.panelY = panelY;
        this.panelW = panelW;
        this.panelH = panelH;
        this.gridX = gridX;
        this.gridY = gridY;
    }

    /**
     * The layout for {@code kind} on a screen of {@code screenW} by {@code screenH} GUI pixels.
     *
     * @param scale     the GUI Size setting; clamped to 1..3 like the setting itself
     * @param minPanelW how wide the header's content needs the panel to be; the grid is centred in
     *                  a wider panel. Never wider than the screen allows.
     */
    public static TerminalGuiLayout of(TerminalKind kind, float scale, int screenW, int screenH, int minPanelW) {
        int[] area = kind.playArea();
        int firstRow = area[0];
        int rows = area[1] - area[0] + 1;
        int firstColumn = area[2];
        int columns = area[3] - area[2] + 1;

        int availW = screenW - 2 * MARGIN;
        int availH = screenH - 2 * MARGIN;
        int pitch = pitchFor(scale);
        while (pitch > MIN_PITCH && (gridSize(columns, pitch) + 2 * PAD > availW
                || HEADER_H + 2 * PAD + gridSize(rows, pitch) > availH)) {
            pitch--;
        }
        int gap = gapFor(pitch);
        int gridW = gridSize(columns, pitch);
        int gridH = gridSize(rows, pitch);
        int panelW = Math.max(gridW + 2 * PAD, Math.min(minPanelW, availW));
        int panelH = HEADER_H + PAD + gridH + PAD;
        int panelX = (screenW - panelW) / 2;
        int panelY = (screenH - panelH) / 2;
        int gridX = panelX + (panelW - gridW) / 2;
        int gridY = panelY + HEADER_H + PAD;
        return new TerminalGuiLayout(kind, firstRow, firstColumn, rows, columns, pitch, gap, panelX, panelY,
                panelW, panelH, gridX, gridY);
    }

    /** The pitch the setting asks for: vanilla's 18 pixels times the scale, rounded. */
    public static int pitchFor(float scale) {
        return Math.round(SLOT_PITCH * TerminalSettings.clampGuiScale(scale));
    }

    /** The gap between two cells at this pitch: two pixels as in vanilla, growing with the cells. */
    public static int gapFor(int pitch) {
        return Math.max(2, Math.round(pitch / 9.0f));
    }

    private static int gridSize(int cells, int pitch) {
        return cells * pitch - gapFor(pitch);
    }

    // ---- What a frame draws ---------------------------------------------------------------------

    public TerminalKind kind() {
        return kind;
    }

    /** Distance from one cell to the next. */
    public int pitch() {
        return pitch;
    }

    /** A cell's width and height. */
    public int cell() {
        return pitch - gap;
    }

    public int gap() {
        return gap;
    }

    public Rect panel() {
        return new Rect(panelX, panelY, panelW, panelH);
    }

    /** The header strip at the top of the panel. */
    public Rect header() {
        return new Rect(panelX, panelY, panelW, HEADER_H);
    }

    /** The whole grid, gaps included. */
    public Rect grid() {
        return new Rect(gridX, gridY, gridSize(columns, pitch), gridSize(rows, pitch));
    }

    /** The cell of a play slot, or null for a slot that is not one. */
    public Rect cell(int slot) {
        return kind.isPlaySlot(slot) ? rectOf(slot) : null;
    }

    /**
     * The rectangle spanning the cells of two slots and everything between them, for a band
     * behind a row or a column. Both slots have to lie in the play area's rows and columns, play
     * slots or not.
     */
    public Rect span(int fromSlot, int toSlot) {
        Rect a = rectOf(fromSlot);
        Rect b = rectOf(toSlot);
        int x0 = Math.min(a.x, b.x);
        int y0 = Math.min(a.y, b.y);
        int x1 = Math.max(a.x + a.w, b.x + b.w);
        int y1 = Math.max(a.y + a.h, b.y + b.h);
        return new Rect(x0, y0, x1 - x0, y1 - y0);
    }

    /** Where a slot's cell would sit by its row and column; the play-slot check is the caller's. */
    private Rect rectOf(int slot) {
        int row = slot / 9 - firstRow;
        int column = slot % 9 - firstColumn;
        return new Rect(gridX + column * pitch, gridY + row * pitch, cell(), cell());
    }

    /**
     * The play slot whose cell holds the point, or -1: in a gap, outside the grid, or on a spot of
     * the area that is not a play slot.
     */
    public int slotAt(double mx, double my) {
        double dx = mx - gridX;
        double dy = my - gridY;
        if (dx < 0 || dy < 0) return -1;
        int column = (int) Math.floor(dx / pitch);
        int row = (int) Math.floor(dy / pitch);
        if (column >= columns || row >= rows) return -1;
        if (dx - column * (double) pitch >= cell() || dy - row * (double) pitch >= cell()) return -1;
        int slot = (firstRow + row) * 9 + firstColumn + column;
        return kind.isPlaySlot(slot) ? slot : -1;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TerminalGuiLayout l)) return false;
        return kind == l.kind && pitch == l.pitch && gap == l.gap && panelX == l.panelX && panelY == l.panelY
                && panelW == l.panelW && panelH == l.panelH && gridX == l.gridX && gridY == l.gridY;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(kind, pitch, gap, panelX, panelY, panelW, panelH, gridX, gridY);
    }
}
