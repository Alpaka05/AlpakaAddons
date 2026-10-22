package net.alpaka.addons.features.tablist;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Where everything on the Custom Tab goes, in the tab's own pixels with (0, 0) at the panel's
 * top-left corner.
 *
 * Each category is a group: its title centered over its columns. Side by Side puts the groups
 * next to each other, each a tall column that holds whole sections with a small gap between them.
 * Stacked puts them under each other, each section a short column of its own, so a category reads
 * as one row of widgets. Only a section longer than a column (the player list) is cut across
 * columns, and anything wider than the screen wraps onto the next row.
 */
public final class CustomTabLayout {

    public static final int LINE_H = 9;
    public static final int SECTION_GAP = 4;
    public static final int TITLE_GAP = 3;
    public static final int COLUMN_GAP = 10;
    public static final int GROUP_GAP = 16;
    public static final int ROW_GAP = 8;
    public static final int INDENT = 4;
    public static final int FACE = 8;
    public static final int FACE_GAP = 2;
    public static final int PAD_X = 8;
    public static final int PAD_Y = 6;
    /** Rows per column in Stacked, which keeps each category a short band. */
    public static final int STACKED_ROWS = 10;
    /** Rows per column in Side by Side at most; vanilla's own column height. */
    public static final int SIDE_BY_SIDE_ROWS = 20;
    /** A progress bar: its length, its thickness, its offset from the row's top and its gap to the text. */
    public static final int BAR_W = 40;
    public static final int BAR_H = 4;
    public static final int BAR_TOP = 2;
    public static final int BAR_GAP = 4;

    /**
     * A piece of text to draw at (x, y); {@code face} names the player whose head goes before it,
     * and {@code line} is the tab row it shows - with its bar and key - or null for a title.
     */
    public record Text(Component text, int x, int y, String face, TabListParser.Line line) {}

    public final List<Text> texts = new ArrayList<>();
    public int width;
    public int height;

    private record Column(List<Object> rows, int width, int height) {}

    private record Group(Component title, int titleWidth, List<Column> columns) {}

    private static final Object GAP = new Object();

    /**
     * @param stacked     categories under each other rather than next to each other
     * @param maxWidth    how wide the content may get before it wraps, in the tab's pixels
     * @param maxHeight   how tall a Side by Side column may get, in the tab's pixels
     * @param withFaces   whether player rows leave room for a head
     */
    public static CustomTabLayout build(List<Component> header, List<TabListParser.Category> categories,
                                        List<Component> footer, boolean stacked, int maxWidth, int maxHeight,
                                        boolean withFaces, ToIntFunction<Component> widthOf) {
        CustomTabLayout layout = new CustomTabLayout();
        int maxRows = stacked ? STACKED_ROWS
                : Math.max(6, Math.min(SIDE_BY_SIDE_ROWS, (maxHeight - LINE_H - TITLE_GAP) / LINE_H));

        List<Group> groups = new ArrayList<>();
        for (TabListParser.Category category : categories) {
            groups.add(new Group(category.title(), widthOf.applyAsInt(category.title()),
                    columns(category, maxRows, stacked, withFaces, widthOf)));
        }

        // Rows of groups: one group each when stacked, as many as fit next to each other otherwise.
        // A group wider than the screen is cut between its columns onto rows of its own.
        List<List<Group>> rows = new ArrayList<>();
        List<Group> row = new ArrayList<>();
        int rowWidth = 0;
        for (Group group : groups) {
            for (Group piece : split(group, maxWidth)) {
                int w = groupWidth(piece);
                if (!row.isEmpty() && (stacked || rowWidth + GROUP_GAP + w > maxWidth)) {
                    rows.add(row);
                    row = new ArrayList<>();
                    rowWidth = 0;
                }
                rowWidth += (row.isEmpty() ? 0 : GROUP_GAP) + w;
                row.add(piece);
            }
        }
        if (!row.isEmpty()) rows.add(row);

        int contentWidth = 0;
        for (List<Group> r : rows) contentWidth = Math.max(contentWidth, rowWidth(r));
        for (Component line : header) contentWidth = Math.max(contentWidth, widthOf.applyAsInt(line));
        for (Component line : footer) contentWidth = Math.max(contentWidth, widthOf.applyAsInt(line));
        layout.width = contentWidth + PAD_X * 2;

        int y = PAD_Y;
        for (Component line : header) {
            layout.centered(line, y, widthOf);
            y += LINE_H;
        }
        if (!header.isEmpty() && !rows.isEmpty()) y += ROW_GAP;

        for (int r = 0; r < rows.size(); r++) {
            if (r > 0) y += ROW_GAP;
            List<Group> groupRow = rows.get(r);
            int x = PAD_X + (contentWidth - rowWidth(groupRow)) / 2;
            int tallest = 0;
            for (Group group : groupRow) {
                int w = groupWidth(group);
                int top = y;
                if (group.title() != null) {
                    layout.texts.add(new Text(group.title(), x + (w - group.titleWidth()) / 2, top, null, null));
                    top += LINE_H + TITLE_GAP;
                }
                int columnsWidth = columnsWidth(group.columns());
                int cx = x + (w - columnsWidth) / 2;
                int deepest = 0;
                for (Column column : group.columns()) {
                    int cy = top;
                    for (Object entry : column.rows()) {
                        if (entry == GAP) {
                            cy += SECTION_GAP;
                            continue;
                        }
                        TabListParser.Line line = (TabListParser.Line) entry;
                        int lx = cx + (line.indented() ? INDENT : 0);
                        String face = withFaces ? line.player() : null;
                        layout.texts.add(new Text(line.text(), lx, cy, face, line));
                        cy += LINE_H;
                    }
                    deepest = Math.max(deepest, cy - top);
                    cx += column.width() + COLUMN_GAP;
                }
                tallest = Math.max(tallest, top - y + deepest);
                x += w + GROUP_GAP;
            }
            y += tallest;
        }

        if (!footer.isEmpty()) {
            if (!rows.isEmpty() || !header.isEmpty()) y += ROW_GAP;
            for (Component line : footer) {
                layout.centered(line, y, widthOf);
                y += LINE_H;
            }
        }
        layout.height = y + PAD_Y;
        return layout;
    }

    private void centered(Component line, int y, ToIntFunction<Component> widthOf) {
        texts.add(new Text(line, (width - widthOf.applyAsInt(line)) / 2, y, null, null));
    }

    /** A category's sections in columns of at most {@code maxRows} rows; one section each if asked. */
    private static List<Column> columns(TabListParser.Category category, int maxRows, boolean sectionEach,
                                        boolean withFaces, ToIntFunction<Component> widthOf) {
        List<Column> columns = new ArrayList<>();
        List<Object> rows = new ArrayList<>();
        int used = 0;
        for (List<TabListParser.Line> section : category.sections()) {
            if (used > 0 && (sectionEach || used + section.size() > maxRows)) {
                columns.add(column(rows, withFaces, widthOf));
                rows = new ArrayList<>();
                used = 0;
            } else if (used > 0) {
                rows.add(GAP);
            }
            for (TabListParser.Line line : section) {
                if (used >= maxRows) {
                    columns.add(column(rows, withFaces, widthOf));
                    rows = new ArrayList<>();
                    used = 0;
                }
                rows.add(line);
                used++;
            }
        }
        if (!rows.isEmpty()) columns.add(column(rows, withFaces, widthOf));
        return columns;
    }

    private static Column column(List<Object> rows, boolean withFaces, ToIntFunction<Component> widthOf) {
        int width = 0;
        int height = 0;
        for (Object entry : rows) {
            if (entry == GAP) {
                height += SECTION_GAP;
                continue;
            }
            TabListParser.Line line = (TabListParser.Line) entry;
            int w = lineWidth(line, widthOf) + (line.indented() ? INDENT : 0);
            if (withFaces && line.player() != null) w += FACE + FACE_GAP;
            width = Math.max(width, w);
            height += LINE_H;
        }
        return new Column(rows, width, height);
    }

    /** A row's width: its text, and its bar and the text after it when it has one. */
    public static int lineWidth(TabListParser.Line line, ToIntFunction<Component> widthOf) {
        int width = widthOf.applyAsInt(line.text());
        if (line.bar() == null) return width;
        width = barX(width) + BAR_W;
        int after = widthOf.applyAsInt(line.after());
        return after > 0 ? width + BAR_GAP + after : width;
    }

    /** Where a row's bar starts, after text this wide: right at the start when there is none. */
    public static int barX(int textWidth) {
        return textWidth > 0 ? textWidth + BAR_GAP : 0;
    }

    /** The group cut between its columns so no piece is wider than {@code maxWidth}; only the first keeps the title. */
    private static List<Group> split(Group group, int maxWidth) {
        List<Group> pieces = new ArrayList<>();
        List<Column> current = new ArrayList<>();
        for (Column column : group.columns()) {
            if (!current.isEmpty() && columnsWidth(current) + COLUMN_GAP + column.width() > maxWidth) {
                pieces.add(new Group(pieces.isEmpty() ? group.title() : null, pieces.isEmpty() ? group.titleWidth() : 0, current));
                current = new ArrayList<>();
            }
            current.add(column);
        }
        pieces.add(new Group(pieces.isEmpty() ? group.title() : null, pieces.isEmpty() ? group.titleWidth() : 0, current));
        return pieces;
    }

    private static int columnsWidth(List<Column> columns) {
        int w = 0;
        for (int i = 0; i < columns.size(); i++) w += (i > 0 ? COLUMN_GAP : 0) + columns.get(i).width();
        return w;
    }

    private static int groupWidth(Group group) {
        return Math.max(group.titleWidth(), columnsWidth(group.columns()));
    }

    private static int rowWidth(List<Group> row) {
        int w = 0;
        for (int i = 0; i < row.size(); i++) w += (i > 0 ? GROUP_GAP : 0) + groupWidth(row.get(i));
        return w;
    }
}
