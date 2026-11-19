package net.alpaka.addons.features.itemsearch;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.client.gui.PloppAnimation;
import net.alpaka.addons.compat.MacOSInputFixesCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A wiki page read in the game ({@link WikiPage}), over the menu it was opened from, which it goes
 * back to when closed. Links to other pages open them here too, with a way back; the page can
 * still be opened in the browser, after the usual question.
 */
public class WikiScreen extends Screen {
    private static final int MARGIN = 16;
    private static final int MAX_W = 460;
    private static final int LINE_H = 10;
    private static final int SCROLLBAR_W = 3;

    private final Screen parent;
    private final Deque<String> history = new ArrayDeque<>();
    private String title;
    private long openTimeMs = 0L;

    private double scroll = 0;
    private double scrollTarget = 0;
    private int contentH = 0;
    private long lastFrameMs = System.currentTimeMillis();

    // The laid-out page, for the width it was laid out at.
    private WikiPage.Page laidOutFor = null;
    private int laidOutW = -1;
    private final List<Piece> pieces = new ArrayList<>();
    private final List<Rect> rects = new ArrayList<>();

    private record Piece(int x, int y, FormattedCharSequence text, int color) {}

    private record Rect(int x, int y, int w, int h, int color) {}

    private int[] backButton, browserButton, closeButton;

    public WikiScreen(String title, Screen parent) {
        super(GuiFont.text("Wiki"));
        this.title = title;
        this.parent = parent;
    }

    // ---- Layout ---------------------------------------------------------------------------------

    private int panelW() {
        return Math.min(MAX_W, this.width - 2 * MARGIN);
    }

    private int panelX() {
        return (this.width - panelW()) / 2;
    }

    private int panelY() {
        return MARGIN;
    }

    private int panelH() {
        return this.height - 2 * MARGIN;
    }

    private int bodyY() {
        return panelY() + AlpakaStyle.HEADER_H;
    }

    private int bodyH() {
        return panelH() - AlpakaStyle.HEADER_H - 4;
    }

    private int textX() {
        return panelX() + AlpakaStyle.PAD + 4;
    }

    private int textW() {
        return panelW() - 2 * (AlpakaStyle.PAD + 4) - SCROLLBAR_W;
    }

    /** Lays the page out at the width given, line by line, once per page and width. */
    private void layout(WikiPage.Page page, int w) {
        if (page == laidOutFor && w == laidOutW) return;
        laidOutFor = page;
        laidOutW = w;
        pieces.clear();
        rects.clear();
        int y = 4;
        List<WikiPage.Block> blocks = page.blocks();
        for (int i = 0; i < blocks.size(); i++) {
            WikiPage.Block b = blocks.get(i);
            switch (b.kind()) {
                case HEADING -> {
                    y += 8;
                    // In the larger title size where the menu font has one.
                    Component big = GuiFont.title(b.text().getString());
                    y = big == null ? lines(b.text(), 0, y, w, AlpakaStyle.TEXT) : lines(big, 0, y, w, AlpakaStyle.TEXT, 13, false);
                    rects.add(new Rect(0, y + 1, w, 1, AlpakaStyle.LINE));
                    y += 6;
                }
                case SUBHEADING -> {
                    y += 4;
                    y = lines(b.text(), 0, y, w, AlpakaStyle.TEXT);
                    y += 3;
                }
                case PARAGRAPH -> {
                    y = lines(b.text(), 0, y, w, AlpakaStyle.TEXT_SOFT);
                    y += 4;
                }
                case BULLET -> {
                    int indent = 8 + b.depth() * 8;
                    pieces.add(new Piece(indent - 7, y, Component.literal("•").getVisualOrderText(), AlpakaStyle.TEXT_MUTED));
                    y = lines(b.text(), indent, y, w - indent, AlpakaStyle.TEXT_SOFT);
                    y += 1;
                    if (i + 1 >= blocks.size() || blocks.get(i + 1).kind() != WikiPage.Kind.BULLET) y += 3;
                }
                case FACT -> {
                    int labelW = Math.max(60, w * 2 / 5);
                    int ly = lines(b.text(), 4, y + 2, labelW - 8, AlpakaStyle.TEXT_MUTED);
                    int vy = lines(b.value(), labelW, y + 2, w - labelW - 4, AlpakaStyle.TEXT);
                    int bottom = Math.max(ly, vy) + 2;
                    rects.add(0, new Rect(0, y, w, bottom - y, AlpakaStyle.ROW));
                    y = bottom + 1;
                    if (i + 1 >= blocks.size() || blocks.get(i + 1).kind() != WikiPage.Kind.FACT) y += 6;
                }
                case ROW, HEADER_ROW -> {
                    // A table: every row of it at once, the columns as wide as the widest row has cells.
                    int table = b.table();
                    int end = i;
                    while (end < blocks.size() && blocks.get(end).table() == table
                            && (blocks.get(end).kind() == WikiPage.Kind.ROW || blocks.get(end).kind() == WikiPage.Kind.HEADER_ROW)) {
                        end++;
                    }
                    y = table(dropEmptyColumns(blocks.subList(i, end)), y, w);
                    y += 6;
                    i = end - 1;
                }
            }
        }
        contentH = y + 8;
    }

    /** The rows without the columns no row has anything in, such as the wiki's crafting grids, which are pictures. */
    private static List<WikiPage.Block> dropEmptyColumns(List<WikiPage.Block> rows) {
        int columns = 0;
        for (WikiPage.Block r : rows) columns = Math.max(columns, r.cells().size());
        boolean[] used = new boolean[columns];
        for (WikiPage.Block r : rows) {
            if (r.kind() == WikiPage.Kind.HEADER_ROW) continue;
            for (int c = 0; c < r.cells().size(); c++) used[c] |= !r.cells().get(c).getString().isBlank();
        }
        boolean anyBody = false;
        for (WikiPage.Block r : rows) anyBody |= r.kind() == WikiPage.Kind.ROW;
        if (!anyBody) return rows;
        List<WikiPage.Block> out = new ArrayList<>();
        for (WikiPage.Block r : rows) {
            List<Component> cells = new ArrayList<>();
            for (int c = 0; c < r.cells().size(); c++) if (used[c]) cells.add(r.cells().get(c));
            if (!cells.isEmpty()) out.add(new WikiPage.Block(r.kind(), r.text(), r.value(), cells, r.depth(), r.table()));
        }
        return out;
    }

    private int table(List<WikiPage.Block> rows, int y, int w) {
        int columns = 1;
        for (WikiPage.Block r : rows) columns = Math.max(columns, r.cells().size());
        boolean narrow = w / columns < 48;
        int odd = 0;
        for (WikiPage.Block row : rows) {
            boolean header = row.kind() == WikiPage.Kind.HEADER_ROW;
            int top = y;
            int bottom;
            if (narrow) {
                // Too many columns for the width: the cells one after the other, with dots between.
                var joined = Component.empty();
                for (int c = 0; c < row.cells().size(); c++) {
                    if (c > 0) joined.append(Component.literal("  ·  ").withStyle(s -> s.withColor(AlpakaStyle.TEXT_MUTED & 0xFFFFFF)));
                    joined.append(row.cells().get(c));
                }
                bottom = lines(joined, 4, y + 2, w - 8, header ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT) + 2;
            } else {
                int colW = w / columns;
                bottom = y + 2;
                for (int c = 0; c < row.cells().size(); c++) {
                    // A row with fewer cells lets its last one run to the end.
                    int cw = c == row.cells().size() - 1 ? w - c * colW : colW;
                    bottom = Math.max(bottom, lines(row.cells().get(c), c * colW + 4, y + 2, cw - 8,
                            header ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT));
                }
                bottom += 2;
            }
            int color = header ? AlpakaStyle.ROW_SELECTED : (odd++ % 2 == 0 ? AlpakaStyle.ROW : AlpakaStyle.FIELD);
            rects.add(0, new Rect(0, top, w, bottom - top, color));
            y = bottom + 1;
        }
        return y;
    }

    /** The text broken into lines at {@code x}, from {@code y}; returns the y under the last line. */
    private int lines(Component text, int x, int y, int w, int color) {
        return lines(text, x, y, w, color, LINE_H, true);
    }

    private int lines(Component text, int x, int y, int w, int color, int lineH, boolean restyle) {
        for (FormattedCharSequence line : this.font.split(restyle ? GuiFont.restyle(text) : text, Math.max(20, w))) {
            pieces.add(new Piece(x, y, line, color));
            y += lineH;
        }
        return y;
    }

    // ---- Drawing --------------------------------------------------------------------------------

    @Override
    protected void init() {
        if (openTimeMs == 0L) openTimeMs = System.currentTimeMillis();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        float delta = Math.min(0.1f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
        // The menu stays in sight behind the page, as it was, without the mouse on it.
        if (parent != null && parent.width == this.width && parent.height == this.height) {
            net.alpaka.addons.features.notification.AlpakaNotifications.suppressOverScreen(true);
            try {
                parent.extractBackground(graphics, -1, -1, partialTick);
                parent.extractRenderState(graphics, -1, -1, partialTick);
            } finally {
                net.alpaka.addons.features.notification.AlpakaNotifications.suppressOverScreen(false);
            }
            graphics.nextStratum();
        }
        AlpakaStyle.backdrop(graphics, this.width, this.height);
        boolean pushed = AlpakaStyle.beginOpen(graphics, openTimeMs, this.width, this.height);
        int px = panelX(), py = panelY(), pw = panelW(), ph = panelH();
        AlpakaStyle.panel(graphics, px, py, pw, ph);

        WikiPage.Page page = WikiPage.get(title);
        drawHeader(graphics, page, px, py, pw, mouseX, mouseY);

        int by = bodyY(), bh = bodyH();
        if (page == null) {
            WikiPage.State state = WikiPage.state(title);
            String msg = switch (state) {
                case MISSING -> "The wiki has no page for " + title + ".";
                case FAILED -> "Could not load the page. Trying again soon…";
                default -> net.alpaka.addons.config.AlpakaConfig.instance.allowApiCalls ? "Loading…" : "The wiki needs Network Features.";
            };
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text(msg), px + pw / 2, by + bh / 2 - 4, AlpakaStyle.TEXT_MUTED);
            AlpakaStyle.endOpen(graphics, pushed);
            return;
        }
        int tx = textX(), tw = textW();
        layout(page, tw);
        double max = Math.max(0, contentH - bh);
        scrollTarget = Math.max(0, Math.min(max, scrollTarget));
        scroll += (scrollTarget - scroll) * Math.min(1.0, delta * 18.0);
        if (Math.abs(scrollTarget - scroll) < 0.3) scroll = scrollTarget;
        int top = by - (int) Math.round(scroll);

        graphics.enableScissor(px, by, px + pw, by + bh);
        for (Rect r : rects) {
            if (top + r.y() + r.h() < by || top + r.y() > by + bh) continue;
            ModernGuiUtils.drawRoundedRect(graphics, tx - 4 + r.x(), top + r.y(), r.w() + 8, r.h(), 2, r.color());
        }
        Style hovered = null;
        for (Piece p : pieces) {
            int y = top + p.y();
            if (y + LINE_H < by || y > by + bh) continue;
            graphics.text(this.font, p.text(), tx + p.x(), y, p.color(), false);
            if (mouseY >= y - 1 && mouseY < y + LINE_H - 1 && AlpakaStyle.inside(mouseX, mouseY, px, by, pw, bh)) {
                Style s = styleAt(p.text(), mouseX - (tx + p.x()));
                if (s != null && s.getInsertion() != null) hovered = s;
            }
        }
        graphics.disableScissor();
        if (hovered != null) graphics.requestCursor(CursorTypes.POINTING_HAND);

        // The scrollbar, when the page is longer than the window.
        if (max > 0) {
            int trackX = px + pw - AlpakaStyle.PAD + 1 - SCROLLBAR_W;
            int thumbH = Math.max(16, bh * bh / contentH);
            int thumbY = by + (int) Math.round((bh - thumbH) * (scroll / max));
            ModernGuiUtils.drawRoundedRect(graphics, trackX, thumbY, SCROLLBAR_W, thumbH, 1, AlpakaStyle.DIVIDER);
        }
        AlpakaStyle.scrollFades(graphics, px, by, pw, bh, scroll > 0.5, scroll < max - 0.5);
        AlpakaStyle.endOpen(graphics, pushed);
    }

    private void drawHeader(GuiGraphicsExtractor graphics, WikiPage.Page page, int px, int py, int pw, int mx, int my) {
        int size = 16;
        int by = py + (AlpakaStyle.HEADER_H - size) / 2;
        int x = px + AlpakaStyle.PAD;
        backButton = null;
        if (!history.isEmpty()) {
            backButton = new int[]{x, by, size, size};
            AlpakaStyle.chevronButton(graphics, x, by, size, true, hit(backButton, mx, my), true);
            x += size + 4;
        } else {
            x += 4;
        }
        closeButton = new int[]{px + pw - AlpakaStyle.PAD - size, by, size, size};
        AlpakaStyle.iconButton(graphics, this.font, closeButton[0], closeButton[1], size, "×", hit(closeButton, mx, my), true, false);
        String browser = "Browser ↗";
        int bw = GuiFont.width(this.font, browser) + 14;
        browserButton = new int[]{closeButton[0] - 6 - bw, AlpakaStyle.headerButtonY(py), bw, AlpakaStyle.BUTTON_H};
        AlpakaStyle.button(graphics, this.font, browserButton[0], browserButton[1], bw, AlpakaStyle.BUTTON_H, browser,
                hit(browserButton, mx, my), AlpakaStyle.ButtonKind.PLAIN);
        String shown = page != null ? page.title() : title;
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, shown, browserButton[0] - 8 - x)),
                x, py + (AlpakaStyle.HEADER_H - 8) / 2, AlpakaStyle.TEXT, false);
        graphics.text(this.font, GuiFont.text("Wiki"), browserButton[0] - 8 - GuiFont.width(this.font, "Wiki"),
                py + (AlpakaStyle.HEADER_H - 8) / 2, AlpakaStyle.TEXT_MUTED, false);
        AlpakaStyle.headerLine(graphics, px, py, pw);
    }

    /** The style of the text at {@code dx} into a drawn line, or null past its end. */
    private Style styleAt(FormattedCharSequence line, int dx) {
        if (dx < 0) return null;
        Style[] found = {null};
        int[] at = {0};
        line.accept((index, style, codepoint) -> {
            int w = this.font.width(FormattedCharSequence.codepoint(codepoint, style));
            if (dx < at[0] + w) {
                found[0] = style;
                return false;
            }
            at[0] += w;
            return true;
        });
        return found[0];
    }

    // ---- Input ----------------------------------------------------------------------------------

    private void go(String next) {
        AlpakaStyle.click();
        history.push(title);
        title = next;
        scroll = scrollTarget = 0;
    }

    private void back() {
        if (history.isEmpty()) {
            onClose();
            return;
        }
        AlpakaStyle.click();
        title = history.pop();
        scroll = scrollTarget = 0;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x(), my = event.y();
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_4) {
            back();
            return true;
        }
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        if (hit(closeButton, mx, my)) {
            AlpakaStyle.click();
            onClose();
            return true;
        }
        if (hit(backButton, mx, my)) {
            back();
            return true;
        }
        if (hit(browserButton, mx, my)) {
            AlpakaStyle.click();
            WikiPage.Page page = WikiPage.get(title);
            String url = WikiPage.url(page != null ? page.title() : title);
            Minecraft.getInstance().gui.setScreen(new net.alpaka.addons.client.AlpakaConfirmScreen(this, "Open Wiki",
                    "Opens " + title + " in your browser: " + url, "Open", false,
                    () -> net.minecraft.util.Util.getPlatform().openUri(url)));
            return true;
        }
        int by = bodyY(), bh = bodyH();
        if (laidOutFor != null && AlpakaStyle.inside(mx, my, panelX(), by, panelW(), bh)) {
            int top = by - (int) Math.round(scroll);
            for (Piece p : pieces) {
                int y = top + p.y();
                if (my < y - 1 || my >= y + LINE_H - 1) continue;
                Style s = styleAt(p.text(), (int) mx - (textX() + p.x()));
                if (s != null && s.getInsertion() != null && s.getInsertion().startsWith(WikiPage.LINK)) {
                    go(s.getInsertion().substring(WikiPage.LINK.length()));
                    return true;
                }
            }
            return true;
        }
        if (!AlpakaStyle.inside(mx, my, panelX(), panelY(), panelW(), panelH())) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollTarget -= scrollY * 30;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (MacOSInputFixesCompat.isDiscardedCopy(key)) return true;
        switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_LEFT -> {
                back();
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> scrollTarget += 30;
            case GLFW.GLFW_KEY_UP -> scrollTarget -= 30;
            case GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_SPACE -> scrollTarget += bodyH() - 20;
            case GLFW.GLFW_KEY_PAGE_UP -> scrollTarget -= bodyH() - 20;
            case GLFW.GLFW_KEY_HOME -> scrollTarget = 0;
            case GLFW.GLFW_KEY_END -> scrollTarget = contentH;
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    /** For the screenshot tour: whether the page has arrived. */
    public boolean devReady() {
        return WikiPage.get(title) != null;
    }

    private static boolean hit(int[] box, double mx, double my) {
        return box != null && AlpakaStyle.inside(mx, my, box[0], box[1], box[2], box[3]);
    }
}
