package net.alpaka.addons.features.itemsearch;

import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.EditableText;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.client.gui.PloppAnimation;
import net.alpaka.addons.compat.MacOSInputFixesCompat;
import net.alpaka.addons.compliance.Outbound;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.itemvalue.ItemPrices;
import net.alpaka.addons.features.market.ItemIcons;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The search window, in the manner of a launcher's search: it opens as a single search bar in the
 * upper part of the screen and only grows a result list once something is typed (or a place is
 * picked with Tab). One bar for three things:
 * <ul>
 *   <li>a sum typed in is worked out on the first row, and Enter copies the result;</li>
 *   <li>"Your Items": everything the player owns, as Item Search remembers it; Enter or a click
 *       opens the place with the most of it and outlines the item there;</li>
 *   <li>"All Items": every SkyBlock item there is; Enter shows how it is made, with its recipes
 *       from the NEU repository, and from there its wiki page.</li>
 * </ul>
 * Opened from a menu, the menu stays drawn behind it.
 */
public class ItemSearchScreen extends Screen {

    private static final int MARGIN = 8;
    private static final int WIN_W = 320;
    private static final int MIN_W = 240;
    private static final int BAR_H = 26;
    private static final int ROW_H = 24;
    private static final int HEADER_ROW_H = 14;
    private static final int MAX_ROWS = 9;
    private static final int MIN_ROWS = 5;
    private static final int FOOTER_H = 20;
    private static final int EMPTY_BODY_H = 46;
    /** How many places the tooltip names before it sums up the rest. */
    private static final int MAX_PLACES = 8;
    /** How many of the player's own items show above All Items before a "Show all" row. */
    private static final int OWNED_PREVIEW = 4;
    /** How many of All Items are listed at most; more is a sign the query wants another word. */
    private static final int CATALOG_LIMIT = 60;
    private static final int SLOT = 18;

    /** The place picked last, kept until the game restarts; null for everywhere. */
    private static ItemSearchSource lastCategory = null;

    private final Screen parent;
    /** Opened from the item list for one item's recipe: closes with that item instead of showing a list. */
    private boolean recipeOnly = false;
    private final EditableText input = new EditableText(64);
    private ItemSearchSource category = lastCategory;

    // ---- What the list shows --------------------------------------------------------------------

    private sealed interface Row permits Header, Calc, Owned, CatalogRow, More {}

    private record Header(String title, String note) implements Row {}

    private record Calc(String expression, double value) implements Row {}

    private record Owned(ItemSearchResults.Entry entry) implements Row {}

    private record CatalogRow(ItemCatalog.Item item) implements Row {}

    private record More(int hidden) implements Row {}

    /** An item opened to see how it is made; {@code recipe} is the one of its recipes shown. */
    private static final class Detail {
        final String id;
        final String name;
        final String tier;
        int recipe = 0;

        Detail(String id, String name, String tier) {
            this.id = id;
            this.name = name;
            this.tier = tier;
        }
    }

    private List<ItemSearchResults.Entry> everything = List.of();
    private List<ItemSearchResults.Entry> all = List.of();
    /** How many of each item the player has, by SkyBlock id, for All Items and the item view. */
    private final Map<String, Long> ownedById = new HashMap<>();
    private List<Row> rows = List.of();
    private int[] rowTops = new int[0];
    private int rowsHeight = 0;
    private String rowsFor = null;
    private String rowsText = null;
    private boolean showAllOwned = false;
    private boolean catalogWasLoading = false;
    private final Map<ItemSearchSource, ItemStack> icons = new EnumMap<>(ItemSearchSource.class);

    private final Deque<Detail> details = new ArrayDeque<>();

    private int selected = 0;
    private double scrollY = 0;
    private double targetScrollY = 0;
    /** How far the result area is open, 0 for the bar alone and 1 for the full list. */
    private float expand = 0f;
    private int bodyFromH = 0;
    private long lastFrameMs = System.currentTimeMillis();
    private long openTimeMs = 0L;
    private long caretFrom = System.currentTimeMillis();
    private long copiedAt = 0L;
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;

    /** Where the item view's parts were drawn this frame, for clicks. */
    private final List<SlotHit> slotHits = new ArrayList<>();
    private int[] wikiButton = null;
    private int[] backButton = null;
    private int[] recipeArrows = null;

    private record SlotHit(int x, int y, ItemRecipes.Stack stack) {}

    public ItemSearchScreen(String query, Screen parent) {
        super(GuiFont.text("Item Search"));
        this.parent = parent;
        if (query != null) input.setText(query);
        // Opened with a query (the key over an item), the list is there at once instead of growing.
        if (open()) expand = 1f;
    }

    // ---- Layout ---------------------------------------------------------------------------------

    private record Frame(int x, int y, int w, int barH, int bodyH, int fullBodyH, int rows,
                         int listX, int listW) {
        int bodyY() {
            return y + barH + 1;
        }

        int fullH() {
            return barH + 1 + fullBodyH + FOOTER_H;
        }
    }

    /** Whether the result area shows: once something is typed, a place is picked, or an item opened. */
    private boolean open() {
        return !input.getText().isBlank() || category != null || !details.isEmpty();
    }

    private Frame frame() {
        int w = Math.max(MIN_W, Math.min(WIN_W, this.width - MARGIN * 2));
        int x = (this.width - w) / 2;
        int maxRows = Math.max(MIN_ROWS, Math.min(MAX_ROWS,
                (this.height - MARGIN * 2 - BAR_H - 1 - FOOTER_H - 8) / ROW_H));
        boolean empty = details.isEmpty() && rows().isEmpty();
        int full = maxRows * ROW_H + 8;
        int bodyH = empty ? EMPTY_BODY_H : full;
        int fullH = BAR_H + 1 + full + FOOTER_H;
        // In the upper part of the screen, but high enough for the open list to fit.
        int y = Math.max(MARGIN, Math.min(Math.round(this.height * 0.18f), this.height - MARGIN - fullH));
        return new Frame(x, y, w, BAR_H, bodyH, bodyH, maxRows, x + 4, w - 8);
    }

    @Override
    protected void init() {
        if (openTimeMs == 0L) openTimeMs = System.currentTimeMillis();
        if (AlpakaConfig.instance.allowApiCalls) ItemPrices.refresh();
        reload();
    }

    /** Reads everything again: the list for the chosen place and how many of each item the player has. */
    private void reload() {
        everything = ItemSearchResults.collect(null);
        all = category == null ? everything : ItemSearchResults.collect(category);
        ownedById.clear();
        for (ItemSearchResults.Entry entry : everything) {
            if (entry.id != null) ownedById.merge(entry.id, entry.total, Long::sum);
        }
        rowsFor = null;
    }

    /** The rows for the query as it is now, worked out again only when it (or what is loaded) changed. */
    private List<Row> rows() {
        String key = input.getText() + "\u0000" + showAllOwned;
        boolean catalogLoading = category == null && ItemCatalog.all() == null;
        if (key.equals(rowsFor) && catalogLoading == catalogWasLoading) return rows;
        boolean sameQuery = input.getText().equals(rowsText);
        rowsText = input.getText();
        catalogWasLoading = catalogLoading;
        rowsFor = key;
        rows = buildRows(input.getText());
        rowTops = new int[rows.size()];
        int y = 0;
        for (int i = 0; i < rows.size(); i++) {
            rowTops[i] = y;
            y += rows.get(i) instanceof Header ? HEADER_ROW_H : ROW_H;
        }
        rowsHeight = y;
        if (!sameQuery) {
            selected = firstSelectable(0, 1);
            targetScrollY = 0;
        } else {
            selected = Math.max(0, Math.min(selected, rows.size() - 1));
        }
        return rows;
    }

    private List<Row> buildRows(String query) {
        List<Row> out = new ArrayList<>();
        String q = query.trim();

        Double sum = SpotlightMath.evaluate(q, ItemSearchScreen::priceOf);
        if (sum != null) out.add(new Calc(q, sum));

        // Always by name: a list that keeps its order while typing is easier to read than one that
        // reshuffles by amount or value.
        List<ItemSearchResults.Entry> owned = sum != null ? List.of()
                : ItemSearchResults.filter(all, q, ItemSearchResults.Sort.NAME);
        if (category != null) {
            for (ItemSearchResults.Entry entry : owned) out.add(new Owned(entry));
            return out;
        }
        if (q.isEmpty()) return out;

        if (!owned.isEmpty()) {
            out.add(new Header("Your Items", countLabel(owned.size(), "match")));
            int shown = showAllOwned ? owned.size() : Math.min(OWNED_PREVIEW, owned.size());
            for (int i = 0; i < shown; i++) out.add(new Owned(owned.get(i)));
            if (shown < owned.size()) out.add(new More(owned.size() - shown));
        }

        if (sum == null) {
            List<ItemCatalog.Item> catalog = ItemCatalog.all();
            if (catalog == null) {
                out.add(new Header("All Items", AlpakaConfig.instance.allowApiCalls ? "loading…" : "needs Network Features"));
            } else {
                List<ItemCatalog.Item> hits = ItemCatalog.search(catalog, q, CATALOG_LIMIT);
                if (!hits.isEmpty()) {
                    out.add(new Header("All Items", hits.size() >= CATALOG_LIMIT ? CATALOG_LIMIT + "+" : countLabel(hits.size(), "item")));
                    for (ItemCatalog.Item item : hits) out.add(new CatalogRow(item));
                }
            }
        }
        return out;
    }

    /** What an item costs now, by its name or id, for the calculator's bz(...) and ah(...). */
    private static Double priceOf(String name) {
        ItemCatalog.Item item = ItemCatalog.find(name);
        if (item == null) return null;
        ItemPrices.Quote quote = ItemPrices.quote(item.id());
        return quote == null ? null : quote.price();
    }

    private int firstSelectable(int from, int step) {
        for (int i = from; i >= 0 && i < rows.size(); i += step) {
            if (!(rows.get(i) instanceof Header)) return i;
        }
        return Math.max(0, Math.min(rows.size() - 1, from));
    }

    private Row selectedRow() {
        List<Row> list = rows();
        if (list.isEmpty() || selected < 0 || selected >= list.size()) return null;
        Row row = list.get(selected);
        return row instanceof Header ? null : row;
    }

    @Override
    public void onClose() {
        lastCategory = category;
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- Drawing --------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = System.currentTimeMillis();
        float deltaSec = Math.min(0.1f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;

        // Opened from a menu, the menu stays in sight behind the bar, as it was, without the mouse on it.
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

        Frame f = frame();
        float target = open() ? 1f : 0f;
        expand = PloppAnimation.interpolate(expand, target, deltaSec, 16f);
        float eased = 1f - (1f - expand) * (1f - expand);
        // While the area closes the body keeps the height it had, so it does not jump as the list empties.
        if (target > 0f) bodyFromH = f.bodyH;
        int bodyH = target > 0f ? f.bodyH : bodyFromH;
        int extra = Math.round((1 + bodyH + FOOTER_H) * eased);
        int panelH = f.barH + extra;

        // A lighter backdrop than the other windows: the bar floats over the game.
        AlpakaStyle.backdrop(graphics, this.width, this.height, 0.6f + 0.4f * eased);
        boolean pushed = beginOpen(graphics, f);
        AlpakaStyle.popover(graphics, f.x, f.y, f.w, panelH);

        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            if (details.isEmpty()) hoverSelect(f, mouseX, mouseY);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }

        drawBar(graphics, f);
        List<Component> tooltip = null;
        if (extra > 1) {
            graphics.enableScissor(f.x, f.y + f.barH, f.x + f.w, f.y + panelH);
            ModernGuiUtils.drawRect(graphics, f.x + AlpakaStyle.PAD, f.y + f.barH, f.w - AlpakaStyle.PAD * 2, 1, AlpakaStyle.LINE);
            tooltip = details.isEmpty()
                    ? drawList(graphics, f, bodyH, mouseX, mouseY, deltaSec)
                    : drawDetail(graphics, f, bodyH, mouseX, mouseY);
            drawFooter(graphics, f, f.bodyY() + bodyH);
            graphics.disableScissor();
        }

        if (pushed) graphics.pose().popMatrix();
        if (tooltip != null) graphics.setComponentTooltipForNextFrame(this.font, tooltip, mouseX, mouseY);
    }

    /** The opening pop, around the bar rather than the screen's centre, since the bar sits high up. */
    private boolean beginOpen(GuiGraphicsExtractor graphics, Frame f) {
        float scale = PloppAnimation.getOpenScale(openTimeMs);
        if (scale >= 0.999f) return false;
        graphics.pose().pushMatrix();
        graphics.pose().scaleAround(scale, scale, f.x + f.w / 2.0f, f.y + f.barH / 2.0f);
        return true;
    }

    private void drawBar(GuiGraphicsExtractor graphics, Frame f) {
        int cy = f.y + f.barH / 2;
        if (recipeOnly) {
            // Opened from the item list for one item: a title instead of a search.
            graphics.text(this.font, GuiFont.text("Recipe"), f.x + 12, cy - 4, AlpakaStyle.TEXT, false);
            String hint = "Esc to go back";
            graphics.text(this.font, GuiFont.text(hint), f.x + f.w - 12 - GuiFont.width(this.font, hint), cy - 4, AlpakaStyle.TEXT_MUTED, false);
            return;
        }
        drawMagnifier(graphics, f.x + 12, cy, AlpakaStyle.TEXT_MUTED);
        int textX = f.x + 28;

        if (category != null) {
            String label = category.label;
            int pillW = GuiFont.width(this.font, label) + 24;
            int pillH = 16;
            int pillY = cy - pillH / 2;
            ModernGuiUtils.drawRoundedRect(graphics, textX, pillY, pillW, pillH, pillH / 2, AlpakaStyle.ROW_SELECTED);
            drawIcon(graphics, icon(category), textX + 3, pillY + 2, 0.75f);
            graphics.text(this.font, GuiFont.text(label), textX + 18, cy - 4, AlpakaStyle.accent(), false);
            textX += pillW + 6;
        }

        String countText = countText();
        int countW = GuiFont.width(this.font, countText);
        int countX = f.x + f.w - 12 - countW;
        graphics.text(this.font, GuiFont.text(countText), countX, cy - 4, AlpakaStyle.TEXT_MUTED, false);

        int fieldRight = countX - 10;
        int maxW = Math.max(10, fieldRight - textX);
        String text = input.getText();
        int start = visibleStart(text, maxW);
        int textY = cy - 4;

        graphics.enableScissor(textX - 1, f.y, fieldRight, f.y + f.barH);
        if (text.isEmpty()) {
            // With a place picked its pill already names it, so the hint stays short.
            String hint = category == null ? "Search items, recipes or do math" : "Search";
            graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, hint, maxW)), textX, textY, AlpakaStyle.TEXT_MUTED, false);
        }
        if (input.hasSelection()) {
            int sx = GuiFont.width(this.font, text.substring(start, Math.max(input.getSelectionStart(), start)));
            int ex = GuiFont.width(this.font, text.substring(start, Math.max(input.getSelectionEnd(), start)));
            ModernGuiUtils.drawRect(graphics, textX + sx, textY - 1, ex - sx, 10, AlpakaStyle.accentDim());
        }
        graphics.text(this.font, GuiFont.text(text.substring(start)), textX, textY, AlpakaStyle.TEXT, false);
        if ((System.currentTimeMillis() - caretFrom) / 500 % 2 == 0 && details.isEmpty()) {
            int caretX = textX + GuiFont.width(this.font, text.substring(start, Math.max(start, input.getCaret())));
            ModernGuiUtils.drawRect(graphics, caretX, textY - 1, 1, 10, AlpakaStyle.accent());
        }
        graphics.disableScissor();
    }

    /** The count at the bar's right: results while searching, else how many items are known. */
    private String countText() {
        if (!open()) return countLabel(everything.size(), "item");
        if (!details.isEmpty()) return "";
        int n = 0;
        boolean sum = false;
        for (Row row : rows()) {
            if (row instanceof Owned || row instanceof CatalogRow) n++;
            sum |= row instanceof Calc;
        }
        // A sum is its own answer; "0 results" beside it would read as if it found nothing.
        return sum && n == 0 ? "" : countLabel(n, "result");
    }

    private static String countLabel(int n, String word) {
        String plural = word.endsWith("ch") ? word + "es" : word + "s";
        return String.format(Locale.ROOT, "%,d", n) + " " + (n == 1 ? word : plural);
    }

    /** A small magnifying glass: a ring and a handle. */
    private static void drawMagnifier(GuiGraphicsExtractor graphics, int cx, int cy, int color) {
        ModernGuiUtils.drawRoundedOutline(graphics, cx - 5, cy - 5, 8, 8, 4, color);
        for (int i = 0; i < 3; i++) ModernGuiUtils.drawRect(graphics, cx + 2 + i, cy + 2 + i, 2, 2, color);
    }

    /** The first character shown, so the caret stays inside the field. */
    private int visibleStart(String text, int maxWidth) {
        int start = 0;
        int caret = input.getCaret();
        while (start < caret && GuiFont.width(this.font, text.substring(start, caret)) > maxWidth) start++;
        return start;
    }

    // ---- The list -------------------------------------------------------------------------------

    /** The result list; returns the tooltip to show, if the mouse is on an item. */
    private List<Component> drawList(GuiGraphicsExtractor graphics, Frame f, int bodyH, int mouseX, int mouseY,
                                     float deltaSec) {
        List<Row> list = rows();
        int top = f.bodyY() + 4;
        int listH = bodyH - 8;
        if (list.isEmpty()) {
            String[] message = emptyMessage();
            int cx = f.x + f.w / 2;
            int cy = f.bodyY() + bodyH / 2;
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text(message[0]), cx, cy - 10, AlpakaStyle.TEXT_SOFT);
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, message[1], f.w - 20)),
                    cx, cy + 2, AlpakaStyle.TEXT_MUTED);
            return null;
        }

        double maxScroll = Math.max(0, rowsHeight - listH);
        targetScrollY = Math.max(0, Math.min(maxScroll, targetScrollY));
        scrollY += (targetScrollY - scrollY) * Math.min(1.0, deltaSec * 16.0);

        List<Component> tooltip = null;
        boolean mouseInList = inside(mouseX, mouseY, f.listX, top, f.listW, listH);
        graphics.enableScissor(f.listX, top, f.listX + f.listW, top + listH);
        for (int i = 0; i < list.size(); i++) {
            int y = top + rowTops[i] - (int) Math.round(scrollY);
            int h = list.get(i) instanceof Header ? HEADER_ROW_H : ROW_H;
            if (y + h < top) continue;
            if (y > top + listH) break;
            Row row = list.get(i);
            boolean isSelected = i == selected;
            if (isSelected && !(row instanceof Header)) {
                ModernGuiUtils.drawRoundedRect(graphics, f.listX, y, f.listW, h, AlpakaStyle.ROW_RADIUS + 1, AlpakaStyle.ROW_HOVER);
            }
            boolean iconHover = mouseInList && inside(mouseX, mouseY, f.listX + 6, y + 4, 16, 16);
            switch (row) {
                case Header header -> drawHeader(graphics, f, y, header);
                case Calc calc -> drawCalc(graphics, f, y, calc, isSelected);
                case Owned owned -> {
                    drawOwned(graphics, f, y, owned.entry(), isSelected);
                    if (iconHover) tooltip = tooltip(owned.entry(), parent, false);
                }
                case CatalogRow catalog -> {
                    drawCatalog(graphics, f, y, catalog.item(), isSelected);
                    if (iconHover) tooltip = tooltip(catalog.item());
                }
                case More more -> {
                    String label = "Show " + more.hidden() + " more of your items";
                    graphics.text(this.font, GuiFont.text(label), f.listX + 28, y + 8,
                            isSelected ? AlpakaStyle.accent() : AlpakaStyle.TEXT_SOFT, false);
                    AlpakaStyle.chevron(graphics, f.listX + 14, y + ROW_H / 2, false, AlpakaStyle.TEXT_MUTED);
                }
            }
        }
        AlpakaStyle.scrollFades(graphics, f.listX, top, f.listW, listH, scrollY > 0.5, scrollY < maxScroll - 0.5);
        graphics.disableScissor();
        return tooltip;
    }

    private void drawHeader(GuiGraphicsExtractor graphics, Frame f, int y, Header header) {
        int x = f.listX + 6;
        graphics.text(this.font, GuiFont.text(header.title()), x, y + 4, AlpakaStyle.TEXT_MUTED, false);
        if (!header.note().isEmpty()) {
            int noteW = GuiFont.width(this.font, header.note());
            graphics.text(this.font, GuiFont.text(header.note()), f.listX + f.listW - 8 - noteW, y + 4, AlpakaStyle.TEXT_MUTED, false);
        }
    }

    private void drawCalc(GuiGraphicsExtractor graphics, Frame f, int y, Calc calc, boolean isSelected) {
        int boxX = f.listX + 5;
        int boxY = y + 3;
        ModernGuiUtils.drawRoundedRect(graphics, boxX, boxY, 18, 18, AlpakaStyle.FIELD_RADIUS + 1, AlpakaStyle.FIELD);
        ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text("="), boxX + 9, boxY + 5, AlpakaStyle.accent());
        int textX = f.listX + 28;
        String result = SpotlightMath.format(calc.value());
        boolean copied = System.currentTimeMillis() - copiedAt < 1500;
        String right = copied ? "Copied" : isSelected ? "Enter to copy" : "";
        int rightW = GuiFont.width(this.font, right);
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, result, f.listW - 44 - rightW)),
                textX, y + 4, AlpakaStyle.TEXT, false);
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, calc.expression(), f.listX + f.listW - 8 - textX)),
                textX, y + 14, AlpakaStyle.TEXT_MUTED, false);
        if (!right.isEmpty()) {
            graphics.text(this.font, GuiFont.text(right), f.listX + f.listW - 8 - rightW, y + 4,
                    copied ? AlpakaStyle.accent() : AlpakaStyle.TEXT_MUTED, false);
        }
    }

    private void drawOwned(GuiGraphicsExtractor graphics, Frame f, int y, ItemSearchResults.Entry entry, boolean isSelected) {
        graphics.item(entry.stack, f.listX + 6, y + (ROW_H - 16) / 2);
        int textX = f.listX + 28;
        String total = "×" + String.format(Locale.ROOT, "%,d", entry.total);
        int totalW = GuiFont.width(this.font, total);
        int nameRight = f.listX + f.listW - 8 - totalW - 6;
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, entry.name, nameRight - textX)),
                textX, y + 4, nameColor(entry.stack), false);
        graphics.text(this.font, GuiFont.text(total), f.listX + f.listW - 8 - totalW, y + 4,
                isSelected ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT, false);
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, placesLine(entry), f.listX + f.listW - 8 - textX)),
                textX, y + 14, AlpakaStyle.TEXT_MUTED, false);
    }

    private void drawCatalog(GuiGraphicsExtractor graphics, Frame f, int y, ItemCatalog.Item item, boolean isSelected) {
        ItemStack icon = ItemIcons.forItem(item.id(), item.name());
        if (!icon.isEmpty()) graphics.fakeItem(icon, f.listX + 6, y + (ROW_H - 16) / 2);
        int textX = f.listX + 28;
        String price = priceLabel(item.id());
        int priceW = price == null ? 0 : GuiFont.width(this.font, price);
        int nameRight = f.listX + f.listW - 8 - priceW - 6;
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, item.name(), nameRight - textX)),
                textX, y + 4, ItemCatalog.tierColor(item.tier()), false);
        if (price != null) {
            graphics.text(this.font, GuiFont.text(price), f.listX + f.listW - 8 - priceW, y + 4,
                    isSelected ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT, false);
        }
        StringBuilder sub = new StringBuilder(ItemCatalog.tierLabel(item.tier()));
        Long have = ownedById.get(item.id());
        if (have != null && have > 0) {
            if (!sub.isEmpty()) sub.append(" · ");
            sub.append("You have ").append(String.format(Locale.ROOT, "%,d", have));
        }
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, sub.toString(), f.listX + f.listW - 8 - textX)),
                textX, y + 14, AlpakaStyle.TEXT_MUTED, false);
    }

    /** What one of the item costs now, short: "1.2m". Null without a price. */
    private static String priceLabel(String id) {
        ItemPrices.Quote quote = ItemPrices.quote(id);
        if (quote == null || Double.isNaN(quote.price())) return null;
        return ItemSearchMenus.shortCount(Math.round(quote.price()));
    }

    /** Where an entry lies, in a few words: "Mining Sack", "Ender Chest 1 and 2 more". */
    private static String placesLine(ItemSearchResults.Entry entry) {
        String best = entry.best().name();
        int more = entry.places.size() - 1;
        return more <= 0 ? best : best + " and " + more + (more == 1 ? " more" : " more places");
    }

    /** The color the item's name starts in, which on SkyBlock is its rarity's. */
    private static int nameColor(ItemStack stack) {
        int[] color = {AlpakaStyle.TEXT};
        stack.getHoverName().visit((style, text) -> {
            if (text.isEmpty()) return Optional.empty();
            if (style.getColor() != null) {
                color[0] = 0xFF000000 | style.getColor().getValue();
                return Optional.of(Boolean.TRUE);
            }
            // Hypixel's names often carry the old § codes in the text itself.
            for (int i = 0; i + 1 < text.length() && text.charAt(i) == '§'; i += 2) {
                ChatFormatting format = ChatFormatting.getByCode(text.charAt(i + 1));
                TextColor legacy = format == null ? null : TextColor.fromLegacyFormat(format);
                if (legacy != null) {
                    color[0] = 0xFF000000 | legacy.getValue();
                    return Optional.of(Boolean.TRUE);
                }
            }
            return Optional.of(Boolean.TRUE);
        }, Style.EMPTY);
        return color[0];
    }

    private ItemStack icon(ItemSearchSource source) {
        return icons.computeIfAbsent(source, s -> new ItemStack(s.icon()));
    }

    private static void drawIcon(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y, float scale) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.fakeItem(stack, 0, 0);
        graphics.pose().popMatrix();
    }

    // ---- The item view --------------------------------------------------------------------------

    /** How the opened item is made: its recipes one at a time, its price and how many the player has. */
    private List<Component> drawDetail(GuiGraphicsExtractor graphics, Frame f, int bodyH, int mouseX, int mouseY) {
        Detail d = details.peek();
        slotHits.clear();
        recipeArrows = null;
        List<Component> tooltip = null;
        int x0 = f.x + AlpakaStyle.PAD + 2;
        int right = f.x + f.w - AlpakaStyle.PAD - 2;
        int y = f.bodyY() + 6;

        // The head: back, the item, its name and rarity, and the wiki button.
        backButton = new int[]{x0 - 2, y, 14, 20};
        boolean backHover = inside(mouseX, mouseY, backButton[0], backButton[1], backButton[2], backButton[3]);
        AlpakaStyle.sideChevron(graphics, x0 + 4, y + 10, true, backHover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_MUTED);
        ItemStack icon = ItemIcons.forItem(d.id, d.name);
        if (!icon.isEmpty()) graphics.fakeItem(icon, x0 + 14, y + 2);
        String wikiLabel = "Wiki ↗";
        int wikiW = GuiFont.width(this.font, wikiLabel) + 14;
        wikiButton = new int[]{right - wikiW, y + 1, wikiW, AlpakaStyle.BUTTON_H};
        boolean wikiHover = inside(mouseX, mouseY, wikiButton[0], wikiButton[1], wikiButton[2], wikiButton[3]);
        AlpakaStyle.button(graphics, this.font, wikiButton[0], wikiButton[1], wikiButton[2], wikiButton[3], wikiLabel, wikiHover,
                AlpakaStyle.ButtonKind.PLAIN);
        int nameX = x0 + 36;
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, d.name, wikiButton[0] - 8 - nameX)),
                nameX, y + 2, ItemCatalog.tierColor(d.tier), false);
        StringBuilder sub = new StringBuilder(ItemCatalog.tierLabel(d.tier));
        String price = priceLabel(d.id);
        if (price != null) sub.append(sub.isEmpty() ? "" : " · ").append(price).append(" coins");
        Long have = ownedById.get(d.id);
        if (have != null && have > 0) sub.append(sub.isEmpty() ? "" : " · ").append("You have ").append(String.format(Locale.ROOT, "%,d", have));
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, sub.toString(), wikiButton[0] - 8 - nameX)),
                nameX, y + 12, AlpakaStyle.TEXT_MUTED, false);

        y += 26;
        ModernGuiUtils.drawRect(graphics, f.x + AlpakaStyle.PAD, y, f.w - AlpakaStyle.PAD * 2, 1, AlpakaStyle.LINE);
        y += 8;

        ItemRecipes.Info info = ItemRecipes.get(d.id);
        int cx = f.x + f.w / 2;
        if (info == null) {
            String msg = ItemRecipes.state(d.id) == ItemRecipes.State.FAILED ? "Could not load the recipe; trying again soon." : "Loading the recipe…";
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text(msg), cx, y + 30, AlpakaStyle.TEXT_MUTED);
            return null;
        }
        if (info.recipes().isEmpty()) {
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text("No recipe known for this item."), cx, y + 24, AlpakaStyle.TEXT_SOFT);
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text("It may drop, be sold or come from elsewhere; the wiki knows."),
                    cx, y + 36, AlpakaStyle.TEXT_MUTED);
            return null;
        }

        int count = info.recipes().size();
        d.recipe = Math.floorMod(d.recipe, count);
        ItemRecipes.Recipe recipe = info.recipes().get(d.recipe);

        // Which recipe: its kind, and arrows to step through them when there are more.
        String kind = recipe.kind().label + (recipe.kind() == ItemRecipes.Kind.FORGE && recipe.seconds() > 0
                ? " · " + ItemRecipes.duration(recipe.seconds()) : "");
        graphics.text(this.font, GuiFont.text(kind), x0, y, AlpakaStyle.TEXT_SOFT, false);
        if (count > 1) {
            String pos = (d.recipe + 1) + " / " + count;
            int posW = GuiFont.width(this.font, pos);
            int px = right - 12 - posW;
            AlpakaStyle.sideChevron(graphics, px - 8, y + 4, true, AlpakaStyle.TEXT_MUTED);
            graphics.text(this.font, GuiFont.text(pos), px, y, AlpakaStyle.TEXT_MUTED, false);
            AlpakaStyle.sideChevron(graphics, right - 4, y + 4, false, AlpakaStyle.TEXT_MUTED);
            recipeArrows = new int[]{px - 14, y - 3, right + 2 - (px - 14), 14, px + posW / 2};
        }
        y += 14;

        if (recipe.kind() == ItemRecipes.Kind.CRAFTING) {
            int gridW = SLOT * 3;
            int total = gridW + 28 + SLOT;
            int gx = cx - total / 2;
            for (int i = 0; i < 9; i++) {
                int sx = gx + (i % 3) * SLOT;
                int sy = y + (i / 3) * SLOT;
                List<Component> tip = drawSlot(graphics, sx, sy, recipe.grid().get(i), mouseX, mouseY);
                if (tip != null) tooltip = tip;
            }
            drawArrow(graphics, gx + gridW + 6, y + SLOT + SLOT / 2);
            List<Component> tip = drawSlot(graphics, gx + gridW + 28, y + SLOT, recipe.output(), mouseX, mouseY);
            if (tip != null) tooltip = tip;
            y += SLOT * 3 + 8;
        } else {
            // Forge and shop: what goes in, in rows, then the arrow and what comes out.
            List<ItemRecipes.Stack> inputs = recipe.inputs();
            int perRow = Math.min(6, Math.max(1, inputs.size()));
            int rowsN = (inputs.size() + perRow - 1) / perRow;
            int total = perRow * SLOT + 28 + SLOT;
            int gx = cx - total / 2;
            for (int i = 0; i < inputs.size(); i++) {
                int sx = gx + (i % perRow) * SLOT;
                int sy = y + (i / perRow) * SLOT;
                List<Component> tip = drawSlot(graphics, sx, sy, inputs.get(i), mouseX, mouseY);
                if (tip != null) tooltip = tip;
            }
            int midY = y + Math.max(1, rowsN) * SLOT / 2;
            drawArrow(graphics, gx + perRow * SLOT + 6, midY);
            List<Component> tip = drawSlot(graphics, gx + perRow * SLOT + 28, midY - SLOT / 2, recipe.output(), mouseX, mouseY);
            if (tip != null) tooltip = tip;
            y += Math.max(1, rowsN) * SLOT + 8;
        }

        // What the recipe costs, priced ingredient by ingredient where the markets know them.
        double cost = 0;
        boolean priced = true;
        List<ItemRecipes.Stack> parts = recipe.kind() == ItemRecipes.Kind.CRAFTING ? recipe.grid() : recipe.inputs();
        for (ItemRecipes.Stack part : parts) {
            if (part == null) continue;
            if (part.id().equals("SKYBLOCK_COIN")) {
                cost += part.count();
                continue;
            }
            ItemPrices.Quote quote = ItemPrices.quote(part.id());
            if (quote == null || Double.isNaN(quote.price())) {
                priced = false;
                continue;
            }
            cost += quote.price() * part.count();
        }
        String costLine = cost <= 0 ? "" : (priced ? "Ingredients: " : "Ingredients (some unpriced): ")
                + ItemSearchMenus.shortCount(Math.round(cost)) + " coins";
        if (!costLine.isEmpty()) ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text(costLine), cx, y, AlpakaStyle.TEXT_MUTED);
        if (!info.craftText().isBlank()) {
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, info.craftText(), f.w - 20)),
                    cx, y + (costLine.isEmpty() ? 0 : 11), AlpakaStyle.TEXT_MUTED);
        }
        return tooltip;
    }

    /** One slot of a recipe with its item and amount; returns the tooltip when the mouse is on it. */
    private List<Component> drawSlot(GuiGraphicsExtractor graphics, int x, int y, ItemRecipes.Stack stack, int mouseX, int mouseY) {
        boolean hover = stack != null && inside(mouseX, mouseY, x, y, SLOT, SLOT);
        ModernGuiUtils.drawRoundedRect(graphics, x + 1, y + 1, SLOT - 2, SLOT - 2, 2, hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.FIELD);
        if (stack == null) return null;
        slotHits.add(new SlotHit(x, y, stack));
        String name = nameOf(stack.id());
        ItemStack icon = stack.id().equals("SKYBLOCK_COIN") ? new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET)
                : ItemIcons.forItem(stack.id(), name);
        String amount = stack.count() == 1 ? null : stack.count() == Math.rint(stack.count())
                ? ItemSearchMenus.shortCount((long) stack.count()) : String.valueOf(stack.count());
        if (!icon.isEmpty()) {
            graphics.fakeItem(icon, x + 1, y + 1);
            // The amount as the game draws a stack's, over the item.
            if (amount != null) graphics.itemDecorations(this.font, icon, x + 1, y + 1, amount);
        } else {
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text("?"), x + SLOT / 2, y + 5, AlpakaStyle.TEXT_MUTED);
        }
        if (!hover) return null;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(name).withStyle(s -> s.withColor(ItemCatalog.tierColor(ItemIcons.tier(stack.id())) & 0xFFFFFF)));
        if (stack.count() != 1) {
            lines.add(Component.literal("Amount: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(SpotlightMath.format(stack.count())).withStyle(ChatFormatting.WHITE)));
        }
        String price = stack.id().equals("SKYBLOCK_COIN") ? null : priceLabel(stack.id());
        if (price != null) {
            lines.add(Component.literal("Price: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(price + " coins each").withStyle(ChatFormatting.GOLD)));
        }
        Long have = ownedById.get(stack.id());
        if (have != null && have > 0) {
            lines.add(Component.literal("You have: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format(Locale.ROOT, "%,d", have)).withStyle(ChatFormatting.WHITE)));
        }
        if (details.isEmpty() || !stack.id().equals(details.peek().id)) {
            lines.add(Component.literal("Click to see how it is made.").withStyle(ChatFormatting.YELLOW));
        }
        return lines;
    }

    /** The crafting arrow between what goes in and what comes out. */
    private static void drawArrow(GuiGraphicsExtractor graphics, int x, int cy) {
        ModernGuiUtils.drawRect(graphics, x, cy - 1, 12, 2, AlpakaStyle.TEXT_MUTED);
        for (int i = 0; i < 4; i++) ModernGuiUtils.drawRect(graphics, x + 11 - i, cy - 1 - i, 1, 2 + i * 2, AlpakaStyle.TEXT_MUTED);
    }

    /** An item's name: Hypixel's, else made from its id. */
    private static String nameOf(String id) {
        if (id.equals("SKYBLOCK_COIN")) return "Coins";
        String name = ItemIcons.name(id);
        return name != null ? name : ItemRecipes.prettyId(id);
    }

    // ---- Footer, messages, tooltips -------------------------------------------------------------

    /** The key hints along the bottom, for what the picked row does. */
    private void drawFooter(GuiGraphicsExtractor graphics, Frame f, int y) {
        ModernGuiUtils.drawRect(graphics, f.x + AlpakaStyle.PAD, y, f.w - AlpakaStyle.PAD * 2, 1, AlpakaStyle.LINE);
        int cy = y + 1 + (FOOTER_H - 1) / 2;
        int x = f.x + 10;
        if (!details.isEmpty()) {
            x = hint(graphics, x, cy, "Enter", "Wiki");
            ItemRecipes.Info info = ItemRecipes.get(details.peek().id);
            if (info != null && info.recipes().size() > 1) x = hint(graphics, x, cy, "Tab", "Next Recipe");
            hint(graphics, x, cy, "Esc", "Back");
            return;
        }
        Row row = selectedRow();
        switch (row) {
            case Calc ignored -> x = hint(graphics, x, cy, "Enter", "Copy");
            case Owned owned -> {
                x = hint(graphics, x, cy, "Enter", "Open");
                if (owned.entry().id != null) {
                    x = hint(graphics, x, cy, "→", "Recipe");
                    x = hint(graphics, x, cy, "⇧Enter", "Wiki");
                }
            }
            case CatalogRow ignored -> {
                x = hint(graphics, x, cy, "Enter", "Recipe");
                x = hint(graphics, x, cy, "⇧Enter", "Wiki");
            }
            case More ignored -> x = hint(graphics, x, cy, "Enter", "Show All");
            case null, default -> x = hint(graphics, x, cy, "↑↓", "Select");
        }
        hint(graphics, x, cy, "Tab", "Place");
    }

    /** A key in a small cap with what it does beside it; returns where the next one starts. */
    private int hint(GuiGraphicsExtractor graphics, int x, int cy, String key, String action) {
        int keyW = GuiFont.width(this.font, key) + 6;
        ModernGuiUtils.drawRoundedRect(graphics, x, cy - 6, keyW, 12, AlpakaStyle.FIELD_RADIUS, AlpakaStyle.ROW_SELECTED);
        graphics.text(this.font, GuiFont.text(key), x + 3, cy - 4, AlpakaStyle.TEXT_SOFT, false);
        x += keyW + 4;
        graphics.text(this.font, GuiFont.text(action), x, cy - 4, AlpakaStyle.TEXT_MUTED, false);
        return x + GuiFont.width(this.font, action) + 10;
    }

    /** What the empty list says: why there is nothing, and what to do about it. */
    private String[] emptyMessage() {
        if (!all.isEmpty() || category == null && !input.getText().isBlank()) {
            return new String[]{"Nothing matches \"" + input.getText().trim() + "\".", "Try fewer or other words."};
        }
        if (category == null) return new String[]{"Nothing remembered yet.", "Open your Ender Chest, backpacks and sacks once to add them."};
        return new String[]{"Nothing in " + category.label + " yet.", switch (category) {
            case INVENTORY -> "Your inventory is empty.";
            case ENDER_CHEST -> "Open each Ender Chest page once to add it.";
            case BACKPACK -> "Open each backpack once to add it.";
            case SACKS -> "Open each sack once to add it; the [Sacks] messages keep it up to date.";
            case CHEST -> "Open the chests on your island once to add them.";
            case WARDROBE -> "Open your wardrobe once to add it.";
            case ACCESSORY_BAG -> "Open every page of your Accessory Bag once to add it.";
            case VAULT -> "Open your Personal Vault once to add it.";
        }};
    }

    /** The item's own tooltip, then how many there are and where. */
    static List<Component> tooltip(ItemSearchResults.Entry entry, Screen parent, boolean inList) {
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), entry.stack));
        lines.add(Component.empty());
        lines.add(Component.literal("Total: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format(Locale.ROOT, "%,d", entry.total)).withStyle(ChatFormatting.WHITE)));
        double value = ItemSearchResults.value(entry);
        if (!Double.isNaN(value)) {
            lines.add(Component.literal("Value: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(ItemSearchMenus.shortCount(Math.round(value)) + " coins").withStyle(ChatFormatting.GOLD)));
        }
        int listed = Math.min(MAX_PLACES, entry.places.size());
        for (int i = 0; i < listed; i++) {
            ItemSearchResults.Place place = entry.places.get(i);
            lines.add(Component.literal(" " + place.name()).withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(" × " + String.format(Locale.ROOT, "%,d", place.count())).withStyle(ChatFormatting.DARK_GRAY)));
        }
        if (entry.places.size() > listed) {
            lines.add(Component.literal(" and " + (entry.places.size() - listed) + " more places").withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.literal(action(entry.best(), parent)).withStyle(ChatFormatting.YELLOW));
        if (inList && entry.id != null) lines.add(Component.literal("Right-click: Recipe").withStyle(ChatFormatting.YELLOW));
        return lines;
    }

    /** A catalog item's tooltip: its name, rarity and price. */
    private List<Component> tooltip(ItemCatalog.Item item) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(item.name()).withStyle(s -> s.withColor(ItemCatalog.tierColor(item.tier()) & 0xFFFFFF)));
        if (item.tier() != null) lines.add(Component.literal(ItemCatalog.tierLabel(item.tier())).withStyle(ChatFormatting.DARK_GRAY));
        ItemPrices.Quote quote = ItemPrices.quote(item.id());
        if (quote != null && !Double.isNaN(quote.price())) {
            lines.add(Component.literal("Price: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(SpotlightMath.format(Math.round(quote.price())) + " coins").withStyle(ChatFormatting.GOLD)));
        }
        lines.add(Component.literal("Click to see how it is made.").withStyle(ChatFormatting.YELLOW));
        return lines;
    }

    /** What opening the place does, in words. */
    private static String action(ItemSearchResults.Place place, Screen parent) {
        return switch (place.source()) {
            case INVENTORY -> parent instanceof AbstractContainerScreen<?> ? "Click to go back and see it outlined."
                    : "Click to open your inventory with it outlined.";
            case CHEST -> ItemSearchTracker.onOwnIsland() ? "Click to outline the chest in the world."
                    : "The chest is on your island; click to outline it there.";
            default -> "Click to open " + place.name() + ".";
        };
    }

    // ---- Input ----------------------------------------------------------------------------------

    /** The list row under the mouse, or -1. */
    private int rowAt(Frame f, double mx, double my) {
        if (!open() || !details.isEmpty() || rows().isEmpty()) return -1;
        int top = f.bodyY() + 4;
        int listH = f.bodyH - 8;
        if (!inside(mx, my, f.listX, top, f.listW, listH)) return -1;
        double at = my - top + scrollY;
        for (int i = 0; i < rows.size(); i++) {
            int h = rows.get(i) instanceof Header ? HEADER_ROW_H : ROW_H;
            if (at >= rowTops[i] && at < rowTops[i] + h) return rows.get(i) instanceof Header ? -1 : i;
        }
        return -1;
    }

    /** The mouse picks the row it moves onto, as the arrow keys do. */
    private void hoverSelect(Frame f, int mouseX, int mouseY) {
        if (lastMouseX == Integer.MIN_VALUE) return;
        int row = rowAt(f, mouseX, mouseY);
        if (row >= 0) selected = row;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        double mx = event.x();
        double my = event.y();
        Frame f = frame();

        if (inside(mx, my, f.x, f.y, f.w, f.barH)) {
            if (recipeOnly) return true;
            caretFrom = System.currentTimeMillis();
            int textX = f.x + 28 + (category == null ? 0 : GuiFont.width(this.font, category.label) + 30);
            if (category != null && mx < textX) {
                // The place pill: clicking it goes back to searching everywhere.
                pick(null);
                return true;
            }
            details.clear();
            int offset = GuiFont.width(this.font, input.getText().substring(0, visibleStart(input.getText(), f.w - 28 - (textX - f.x))));
            input.setCaretFromX((int) (mx - textX) + offset, s -> GuiFont.width(this.font, s));
            return true;
        }
        if (!open()) {
            // A click beside the bar closes it, as a click outside a launcher's search does.
            onClose();
            return true;
        }

        if (!details.isEmpty()) {
            if (hit(backButton, mx, my)) {
                AlpakaStyle.click();
                back();
                return true;
            }
            if (hit(wikiButton, mx, my)) {
                AlpakaStyle.click();
                openWiki(details.peek().id, details.peek().name);
                return true;
            }
            if (recipeArrows != null && inside(mx, my, recipeArrows[0], recipeArrows[1], recipeArrows[2], recipeArrows[3])) {
                AlpakaStyle.click();
                details.peek().recipe += mx < recipeArrows[4] ? -1 : 1;
                return true;
            }
            for (SlotHit slot : slotHits) {
                if (inside(mx, my, slot.x(), slot.y(), SLOT, SLOT) && !slot.stack().id().equals("SKYBLOCK_COIN")
                        && !slot.stack().id().equals(details.peek().id)) {
                    AlpakaStyle.click();
                    String id = slot.stack().id();
                    details.push(new Detail(id, nameOf(id), ItemIcons.tier(id)));
                    return true;
                }
            }
        } else {
            int row = rowAt(f, mx, my);
            if (row >= 0) {
                AlpakaStyle.click();
                selected = row;
                activate(rows.get(row), false);
                return true;
            }
        }

        if (!inside(mx, my, f.x, f.y, f.w, f.fullH())) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static boolean hit(int[] box, double mx, double my) {
        return box != null && inside(mx, my, box[0], box[1], box[2], box[3]);
    }

    /** What Enter (or a click) does on a row; with {@code wiki}, Shift+Enter: the item's wiki page. */
    private void activate(Row row, boolean wiki) {
        switch (row) {
            case Calc calc -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(SpotlightMath.plain(calc.value()));
                copiedAt = System.currentTimeMillis();
            }
            case Owned owned -> {
                ItemSearchResults.Entry entry = owned.entry();
                if (wiki && entry.id != null) openWiki(entry.id, entry.name);
                else go(entry, entry.best());
            }
            case CatalogRow catalog -> {
                ItemCatalog.Item item = catalog.item();
                if (wiki) openWiki(item.id(), item.name());
                else details.push(new Detail(item.id(), item.name(), item.tier()));
            }
            case More ignored -> showAllOwned = true;
            case Header ignored -> {
            }
        }
    }

    /** Opens the item view for the picked row, which → does. False when the row has no item to show. */
    private boolean openDetail(Row row) {
        if (row instanceof CatalogRow catalog) {
            details.push(new Detail(catalog.item().id(), catalog.item().name(), catalog.item().tier()));
            return true;
        }
        if (row instanceof Owned owned && owned.entry().id != null) {
            String id = owned.entry().id;
            ItemCatalog.Item known = ItemCatalog.find(id);
            details.push(new Detail(id, known != null ? known.name() : owned.entry().name,
                    known != null ? known.tier() : ItemIcons.tier(id)));
            return true;
        }
        return false;
    }

    /**
     * Opens the item's wiki page in the browser, after the usual question whether to open a link:
     * the page the NEU repository names, else a search on the official wiki for its name.
     */
    private void openWiki(String id, String name) {
        openWiki(id, name, this);
    }

    /** The same from anywhere: the question comes over {@code back}, and goes back to it. */
    static void openWiki(String id, String name, Screen back) {
        ItemRecipes.Info info = ItemRecipes.get(id);
        String url = ItemRecipes.wikiUrl(info, name);
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(new net.alpaka.addons.client.AlpakaConfirmScreen(back, "Open Wiki",
                "Opens the wiki page of " + name + " in your browser: " + url, "Open", false,
                () -> net.minecraft.util.Util.getPlatform().openUri(url)));
    }

    private void pick(ItemSearchSource source) {
        if (source == category) return;
        AlpakaStyle.click();
        category = source;
        details.clear();
        targetScrollY = 0;
        scrollY = 0;
        selected = 0;
        reload();
    }

    /** Tab and Shift+Tab: the next or the previous place to search, everywhere coming between the last and the first. */
    private void cyclePlace(boolean back) {
        ItemSearchSource[] values = ItemSearchSource.values();
        int index = category == null ? -1 : category.ordinal();
        index += back ? -1 : 1;
        if (index >= values.length) index = -1;
        if (index < -1) index = values.length - 1;
        pick(index == -1 ? null : values[index]);
    }

    /**
     * Goes to where the item is: the place given, opened with Hypixel's command, and the item
     * outlined there. One command per click or Enter, the input being the player's.
     */
    private void go(ItemSearchResults.Entry entry, ItemSearchResults.Place place) {
        lastCategory = category;
        goTo(entry, place, parent);
    }

    /**
     * The same from the item list beside a menu, {@code parent} being that menu, or whatever
     * screen to go back to. In the menu's own inventory the item is only outlined.
     */
    static void goTo(ItemSearchResults.Entry entry, ItemSearchResults.Place place, Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        ItemSearchHighlight.item(entry.key);
        switch (place.source()) {
            case INVENTORY -> {
                Screen target = parent instanceof AbstractContainerScreen<?> ? parent
                        : mc.player == null ? null : new InventoryScreen(mc.player);
                if (mc.gui.screen() != target) mc.gui.setScreen(target);
            }
            case CHEST -> {
                ItemSearchHighlight.chest(place.pos(), place.pos2());
                if (mc.gui.screen() != parent) mc.gui.setScreen(parent);
                if (!ItemSearchTracker.onOwnIsland()) {
                    SlayerDropTracker.sendModMessage("§7" + entry.name + " is in the chest at §f"
                            + place.pos().getX() + ", " + place.pos().getY() + ", " + place.pos().getZ()
                            + "§7 on your island. It is outlined when you get there.");
                }
            }
            default -> {
                if (mc.gui.screen() != parent) mc.gui.setScreen(parent);
                Outbound.command(place.source().command(place.page()), Outbound.Cause.BUTTON);
            }
        }
    }

    private void moveSelection(int by) {
        List<Row> list = rows();
        if (list.isEmpty()) return;
        int step = by < 0 ? -1 : 1;
        int target = Math.max(0, Math.min(list.size() - 1, selected + by));
        // Headers are skipped; at either end the nearest row that can be picked.
        int next = firstSelectable(target, step);
        if (list.get(next) instanceof Header) next = firstSelectable(target, -step);
        selected = next;
        // Keeps the picked row in view, with the header above it when it is the first of its part.
        Frame f = frame();
        int listH = f.bodyH - 8;
        double rowTop = rowTops[selected];
        if (selected > 0 && list.get(selected - 1) instanceof Header) rowTop = rowTops[selected - 1];
        if (rowTop < targetScrollY) targetScrollY = rowTop;
        else if (rowTops[selected] + ROW_H > targetScrollY + listH) targetScrollY = rowTops[selected] + ROW_H - listH;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollYAmount) {
        if (scrollYAmount != 0 && open() && details.isEmpty()) {
            targetScrollY = Math.max(0, targetScrollY - scrollYAmount * ROW_H * 2);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollYAmount);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int codePoint = event.codepoint();
        if (recipeOnly) return true;
        if (Character.isBmpCodePoint(codePoint) && input.insert((char) codePoint)) {
            // Typing goes back to the list, to search for what is typed.
            details.clear();
            showAllOwned = false;
            caretFrom = System.currentTimeMillis();
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        // On a Mac, MacOS Input Fixes hands Tab and Escape over twice; acting on both would step two
        // places a press, or empty the search and close the window with one Escape.
        if (MacOSInputFixesCompat.isDiscardedCopy(key)) return true;
        if (!details.isEmpty() && detailKey(event)) return true;
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                // Escape first empties the search, as a launcher's does, and closes the window after that.
                if (!input.isEmpty() || category != null) {
                    input.setText("");
                    showAllOwned = false;
                    pick(null);
                    caretFrom = System.currentTimeMillis();
                } else {
                    onClose();
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                Row row = open() ? selectedRow() : null;
                if (row != null) activate(row, event.hasShiftDown());
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                // At the end of the text, → opens the picked item, as a launcher's preview does.
                if (input.getCaret() == input.getText().length() && !input.hasSelection() && openDetail(selectedRow())) {
                    AlpakaStyle.click();
                    return true;
                }
            }
            case GLFW.GLFW_KEY_UP -> {
                moveSelection(-1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                moveSelection(1);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                moveSelection(-frame().rows);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                moveSelection(frame().rows);
                return true;
            }
            case GLFW.GLFW_KEY_TAB -> {
                cyclePlace(event.hasShiftDown());
                return true;
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                // Backspace in an empty field takes the place off, as with a search token.
                if (input.isEmpty() && category != null) {
                    pick(null);
                    return true;
                }
            }
            default -> {
            }
        }
        String before = input.getText();
        if (!recipeOnly && input.keyPressed(event)) {
            if (!before.equals(input.getText())) {
                showAllOwned = false;
                details.clear();
            }
            caretFrom = System.currentTimeMillis();
            return true;
        }
        return super.keyPressed(event);
    }

    /** The keys of the item view: Escape, ← and Backspace go back, Enter opens the wiki, Tab steps through the recipes. */
    private boolean detailKey(KeyEvent event) {
        Detail d = details.peek();
        switch (event.key()) {
            case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_BACKSPACE -> {
                back();
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                openWiki(d.id, d.name);
                return true;
            }
            case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_RIGHT -> {
                d.recipe += event.hasShiftDown() ? -1 : 1;
                return true;
            }
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_PAGE_DOWN -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /** Back from the item view; the recipe window of the item list closes with its last item. */
    private void back() {
        details.pop();
        if (recipeOnly && details.isEmpty()) onClose();
    }

    /**
     * How an item is made, as the item list beside a menu opens it: the item view on its own, over
     * the menu, which it goes back to when closed.
     */
    public static void openRecipe(String id, Screen parent) {
        ItemSearchScreen screen = new ItemSearchScreen(null, parent);
        screen.recipeOnly = true;
        Minecraft.getInstance().gui.setScreen(screen.devOpenItem(id));
    }

    /** For the screenshot tour: the item view of the item with this id, as Enter on its row opens it. */
    public ItemSearchScreen devOpenItem(String id) {
        ItemCatalog.Item item = ItemCatalog.find(id);
        details.push(new Detail(id, item != null ? item.name() : ItemRecipes.prettyId(id),
                item != null ? item.tier() : ItemIcons.tier(id)));
        return this;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return AlpakaStyle.inside(mx, my, x, y, w, h);
    }
}
