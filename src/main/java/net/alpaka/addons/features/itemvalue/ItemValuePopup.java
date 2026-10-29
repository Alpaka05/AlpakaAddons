package net.alpaka.addons.features.itemvalue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.serialization.JsonOps;
import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.client.gui.PloppAnimation;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.itemvalue.ItemValue.Group;
import net.alpaka.addons.features.itemvalue.ItemValue.Part;
import net.alpaka.addons.features.market.Coins;
import net.alpaka.addons.features.market.ItemIcons;
import net.alpaka.addons.features.market.ItemTags;
import net.alpaka.addons.features.market.MarketIcons;
import net.alpaka.addons.features.market.MarketKind;
import net.alpaka.addons.features.market.MarketScreen;
import net.alpaka.addons.features.market.Markets;
import net.alpaka.addons.features.market.PriceGraph;
import net.alpaka.addons.features.wheel.WheelMesh;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Item Value popup: a small window beside the item it was opened on, over the menu, with what
 * the item is worth and what that is made of - a bar split by share, then the parts grouped as the
 * bar is - and, opened under the Price History row at the bottom, the price history of the item or
 * of whichever part is clicked.
 *
 * Its header is a handle: held there, the popup can be moved like a window, and it stays where it
 * was put until it is opened on another item.
 *
 * It belongs to the menu it was opened in and goes with it. While it is open it takes the clicks
 * meant for it and every click beside it, which closes it, so nothing reaches a slot of the menu
 * by accident; the mouse over it hides the slots underneath from the menu, so they neither light
 * up nor show their tooltips through it.
 */
public final class ItemValuePopup {
    private ItemValuePopup() {}

    // ---- Layout, in GUI pixels ------------------------------------------------------------------

    private static final int WIDTH = 228;
    private static final int PAD = AlpakaStyle.PAD;
    private static final int HEADER_H = AlpakaStyle.HEADER_H;
    private static final int GROUP_H = 15;
    private static final int PART_H = 12;
    private static final int BAR_H = 4;
    /** The price history's row: its switch, and where the prices come from. */
    private static final int HISTORY_ROW_H = 22;
    private static final int GRAPH_H = 112;
    private static final int GRAPH_MIN_H = 76;
    private static final int MESSAGE_H = 44;
    /** Kept between the popup and the screen's edge. */
    private static final int EDGE = 4;

    private static final int COIN = 0xFFFFAA00;

    /** Each group's color, in the bar and beside its rows. */
    static int color(Group group) {
        return switch (group) {
            case BASE -> 0xFFD8D8D8;
            case STARS -> 0xFFFFB347;
            case ENCHANTMENTS -> 0xFFC264F2;
            case GEMSTONES -> 0xFF5CE1F0;
            case UPGRADES -> 0xFF5BE38A;
            case REFORGE -> 0xFF6E8BFF;
            case COSMETICS -> 0xFFFF73E3;
        };
    }

    // ---- State ----------------------------------------------------------------------------------

    /** The menu it was opened in; null while closed. */
    private static Screen screen = null;
    private static ItemStack stack = ItemStack.EMPTY;
    /** The item's SkyBlock data as JSON, or null for an item without any. */
    private static JsonObject data = null;
    private static ItemValue.Breakdown breakdown = null;
    private static long computedFor = Long.MIN_VALUE;
    private static List<Row> rows = List.of();
    private static final Set<Group> collapsed = EnumSet.noneOf(Group.class);
    /** Whether the groups were folded to fit yet; decided once, with the first breakdown. */
    private static boolean folded = false;

    private static int anchorX, anchorY;
    private static long openedAt = 0L;
    private static long lastFrameMs = 0L;

    private static boolean graphOpen = false;
    /** How far the graph has slid open, 0 to 1. */
    private static float graphShown = 0f;
    /** The part whose history the graph shows; null for the item itself. */
    private static Part graphPart = null;

    private static double scroll = 0;
    private static double scrollTarget = 0;
    private static int maxScroll = 0;

    // The last frame's layout, for the mouse.
    private static int panelX, panelY, panelW, panelH;
    private static int listX, listY, listW;
    private static final List<Hit> hits = new ArrayList<>();
    private static Object tipKey = null;
    private static long tipSince = 0L;
    /** A mouse button whose press the popup took, so its release and drags are not the menu's either. */
    private static int heldButton = -1;

    /** Whether the header is held and the popup follows the mouse, like a window by its title bar. */
    private static boolean dragging = false;
    /** Where the mouse took hold, from the popup's corner. */
    private static int grabX, grabY;
    /** Whether it was moved; then it stays where it was put rather than beside the item. */
    private static boolean moved = false;
    private static int movedX, movedY;

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return AlpakaStyle.inside(mx, my, x, y, w, h);
        }
    }

    /**
     * One line of the list: a group's heading with its subtotal, or one part. A group of a single
     * part is one line, under the part's own name.
     */
    private record Row(Group group, Part part, String label, double amount, boolean estimated, boolean priced,
                       boolean heading, boolean child, int parts) {
        int height() {
            return child ? PART_H : GROUP_H;
        }
    }

    // ---- Opening and closing --------------------------------------------------------------------

    public static boolean isOpen() {
        return screen != null;
    }

    static boolean isOpenIn(Screen in) {
        return screen != null && screen == in;
    }

    /** Whether the popup is open in this menu and shows this item. */
    static boolean shows(Screen in, ItemStack item) {
        return screen == in && ItemStack.isSameItemSameComponents(stack, item);
    }

    /**
     * Whether the item's own tooltip is to stay hidden: the popup is open on it, beside the mouse
     * that is still on the item, and the tooltip would cover it.
     */
    public static boolean hidesTooltipOf(Screen in, ItemStack item) {
        return screen != null && !item.isEmpty() && shows(in, item);
    }

    /** Opens the popup on an item of a menu, beside the mouse. */
    public static void open(Screen in, ItemStack item, double mouseX, double mouseY) {
        screen = in;
        stack = item.copy();
        data = skyblockData(item);
        breakdown = null;
        computedFor = Long.MIN_VALUE;
        rows = List.of();
        collapsed.clear();
        folded = false;
        anchorX = (int) Math.round(mouseX);
        anchorY = (int) Math.round(mouseY);
        openedAt = System.currentTimeMillis();
        lastFrameMs = openedAt;
        // A popup opened on another item keeps its graph open, showing that item.
        graphPart = null;
        graphShown = graphOpen ? 1f : 0f;
        scroll = scrollTarget = 0;
        maxScroll = 0;
        tipKey = null;
        panelW = 0;
        dragging = false;
        moved = false;
    }

    public static void close() {
        screen = null;
        stack = ItemStack.EMPTY;
        data = null;
        breakdown = null;
        rows = List.of();
        graphOpen = false;
        graphShown = 0f;
        graphPart = null;
        hits.clear();
        heldButton = -1;
        panelW = 0;
        dragging = false;
        moved = false;
    }

    /** The menu closed or was replaced: the popup goes with it. */
    static void screenRemoved(Screen removed) {
        if (removed == screen) close();
    }

    /**
     * The item's SkyBlock data, as JSON; null for one without, such as a menu's display item - the
     * Bazaar's products above all - whose id is then found by its name ({@link #idByName}).
     */
    private static JsonObject skyblockData(ItemStack item) {
        CustomData custom = item.get(DataComponents.CUSTOM_DATA);
        if (custom != null) {
            JsonElement json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, custom.copyTag());
            if (json.isJsonObject() && json.getAsJsonObject().has("id")) return json.getAsJsonObject();
        }
        return null;
    }

    /** The item's id found by its name, for one that carries no data; null while unknown. */
    private static String idByName() {
        MarketKind kind = Markets.context(screen);
        String name = SkyblockUtils.cleanColor(stack.getHoverName().getString());
        ItemTags.Tag tag = ItemTags.of(stack, name, kind == null ? MarketKind.BAZAAR : kind);
        return tag.tag();
    }

    // ---- The breakdown --------------------------------------------------------------------------

    private static final ItemValue.Prices PRICES = ItemPrices::quote;
    private static final ItemValue.Catalog CATALOG = new ItemValue.Catalog() {
        @Override
        public String name(String id) {
            return ItemIcons.name(id);
        }

        @Override
        public ItemIcons.Upgrades upgrades(String id) {
            return ItemIcons.upgrades(id);
        }
    };

    /** Works the breakdown out again when prices, the item list or the settings changed. */
    private static void update() {
        if (!AlpakaConfig.instance.allowApiCalls) return;
        ItemPrices.refresh();
        JsonObject source = data;
        if (source == null) {
            String id = idByName();
            if (id == null) return;
            source = new JsonObject();
            source.addProperty("id", id);
        }
        long key = ((long) ItemPrices.version() << 8) | (AlpakaConfig.instance.itemValueBazaarPrice << 1) | (ItemIcons.ready() ? 1 : 0);
        if (key == computedFor && breakdown != null) return;
        computedFor = key;
        breakdown = ItemValue.of(source, PRICES, CATALOG);
        rows = rows(breakdown);
    }

    /** The list's lines: the item first, then each group by what it adds, its parts by theirs. */
    private static List<Row> rows(ItemValue.Breakdown b) {
        Map<Group, List<Part>> byGroup = new EnumMap<>(Group.class);
        for (Part part : b.parts()) byGroup.computeIfAbsent(part.group(), g -> new ArrayList<>()).add(part);
        List<Group> order = new ArrayList<>(byGroup.keySet());
        order.sort(Comparator.comparing((Group g) -> g != Group.BASE)
                .thenComparing(g -> -b.total(g))
                .thenComparing(Enum::ordinal));
        Comparator<Part> byWorth = Comparator.comparing((Part p) -> !p.priced())
                .thenComparing(p -> p.priced() ? -p.total() : 0)
                .thenComparing(Part::label);
        List<Row> out = new ArrayList<>();
        for (Group group : order) {
            List<Part> parts = byGroup.get(group);
            parts.sort(byWorth);
            if (parts.size() == 1) {
                Part p = parts.get(0);
                out.add(new Row(group, p, label(p), p.priced() ? p.total() : Double.NaN, p.estimated(), p.priced(), true, false, 1));
                continue;
            }
            boolean estimated = false;
            boolean anyPriced = false;
            for (Part p : parts) {
                // As in the total: an enchantment without a price adds next to nothing.
                estimated |= p.estimated() || (!p.priced() && p.group() != Group.ENCHANTMENTS);
                anyPriced |= p.priced();
            }
            out.add(new Row(group, null, group.title, anyPriced ? b.total(group) : Double.NaN, estimated, anyPriced, true, false, parts.size()));
            for (Part p : parts) {
                out.add(new Row(group, p, label(p), p.priced() ? p.total() : Double.NaN, p.estimated(), p.priced(), false, true, 1));
            }
        }
        return out;
    }

    private static String label(Part part) {
        if (part.count() <= 1) return part.label();
        return part.label() + " ×" + Coins.full(part.count());
    }

    /** The rows as shown: a folded group's parts left out. */
    private static List<Row> visibleRows() {
        if (collapsed.isEmpty()) return rows;
        List<Row> out = new ArrayList<>();
        for (Row row : rows) if (!row.child() || !collapsed.contains(row.group())) out.add(row);
        return out;
    }

    // ---- Drawing --------------------------------------------------------------------------------

    /** Draws the popup over its menu, after the menu and before the tooltips. */
    public static void render(GuiGraphicsExtractor graphics) {
        if (screen == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != screen) {
            close();
            return;
        }
        long now = System.currentTimeMillis();
        float deltaSec = Math.min(0.1f, (now - lastFrameMs) / 1000.0f);
        lastFrameMs = now;
        update();

        Font font = mc.font;
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        double[] cursor = cursor();
        int mx = (int) Math.floor(cursor[0]);
        int my = (int) Math.floor(cursor[1]);
        hits.clear();

        graphShown = approach(graphShown, graphOpen ? 1f : 0f, deltaSec * 7f);
        float graphEase = 1f - (1f - graphShown) * (1f - graphShown);
        scroll += (scrollTarget - scroll) * Math.min(1.0, deltaSec * 16.0);
        if (Math.abs(scrollTarget - scroll) < 0.05) scroll = scrollTarget;

        // Height: the header, the bar, the list, the price history's row and its graph; the list gives way first.
        int w = Math.min(WIDTH, screenW - 2 * EDGE);
        // Until the prices and Hypixel's item list are there, a line saying so rather than a list of dashes.
        boolean waiting = ItemPrices.loading() || ItemIcons.loading();
        boolean hasList = breakdown != null && !breakdown.parts().isEmpty() && !waiting;
        boolean showBar = hasList && groupsWithValue() >= 2;
        int barArea = showBar ? BAR_H + 12 : 4;
        // Without anything priced there is nothing to show the history of, nor a source to name.
        int historyRow = hasList ? HISTORY_ROW_H : 0;
        int maxH = screenH - 2 * EDGE;
        int fixed = HEADER_H + barArea + historyRow;
        int graphFull = Math.max(GRAPH_MIN_H, Math.min(GRAPH_H, maxH - fixed - 3 * PART_H - PAD));
        int graphArea = hasList ? Math.round((graphFull + PAD) * graphEase) : 0;
        if (hasList && !folded) foldToFit(maxH - fixed - (graphOpen ? graphFull + PAD : 0));
        List<Row> shown = hasList ? visibleRows() : List.of();
        int content = hasList ? contentHeight(shown) : MESSAGE_H;
        int listArea = Math.max(Math.min(content, 3 * PART_H), Math.min(content, maxH - fixed - graphArea));
        int h = Math.min(maxH, fixed + listArea + graphArea);

        int x = anchorX + 12;
        if (x + w > screenW - EDGE) x = anchorX - 12 - w;
        x = Math.max(EDGE, Math.min(screenW - EDGE - w, x));
        int y = Math.max(EDGE, Math.min(screenH - EDGE - h, anchorY - 16));
        if (dragging) {
            moved = true;
            movedX = mx - grabX;
            movedY = my - grabY;
            graphics.requestCursor(CursorTypes.RESIZE_ALL);
        }
        if (moved) {
            // Kept on the screen, also when the graph opening makes it taller.
            x = Math.max(EDGE, Math.min(screenW - EDGE - w, movedX));
            y = Math.max(EDGE, Math.min(screenH - EDGE - h, movedY));
        }
        panelX = x;
        panelY = y;
        panelW = w;
        panelH = h;

        graphics.nextStratum();
        float scale = PloppAnimation.getOpenScale(openedAt);
        boolean scaling = scale < 0.999f;
        if (scaling) {
            graphics.pose().pushMatrix();
            graphics.pose().scaleAround(scale, scale, Math.max(x, Math.min(x + w, anchorX)), Math.max(y, Math.min(y + h, anchorY)));
        }
        AlpakaStyle.popover(graphics, x, y, w, h);
        String tip = drawHeader(graphics, font, x, y, w, mx, my);
        int cy = y + HEADER_H;
        if (showBar) {
            String barTip = drawBar(graphics, x + PAD, cy + 6, w - 2 * PAD, mx, my);
            if (barTip != null) tip = barTip;
        }
        cy += barArea;

        listX = x;
        listY = cy;
        listW = w;
        if (hasList) {
            maxScroll = Math.max(0, content - listArea);
            scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
            String rowTip = drawList(graphics, font, shown, x, cy, w, listArea, mx, my);
            if (rowTip != null) tip = rowTip;
        } else {
            maxScroll = 0;
            drawMessage(graphics, font, x, cy, w, listArea);
        }
        cy += listArea;

        // The switch heads the graph, as a group's line heads its parts: the graph opens below it.
        if (hasList) {
            String rowTip = drawHistoryRow(graphics, font, x, cy, w, mx, my);
            if (rowTip != null) tip = rowTip;
            cy += HISTORY_ROW_H;
        }
        if (graphArea > 0) {
            graphics.enableScissor(x, cy, x + w, cy + graphArea);
            String graphTip = drawGraph(graphics, font, x + PAD, cy, w - 2 * PAD, graphFull, mx, my, graphShown >= 0.999f);
            graphics.disableScissor();
            if (graphTip != null) tip = graphTip;
        }
        if (scaling) graphics.pose().popMatrix();

        drawTooltip(graphics, font, screenW, screenH, mx, my, tip, now);
    }

    /** How many groups add anything, for whether the bar says more than the total does. */
    private static int groupsWithValue() {
        Set<Group> groups = EnumSet.noneOf(Group.class);
        for (Part part : breakdown.parts()) if (part.priced() && part.total() > 0) groups.add(part.group());
        return groups.size();
    }

    /**
     * Folds the groups with the most parts, one after another, until the list fits the screen; on a
     * small one that is all of them. Decided once, when the breakdown first comes in.
     */
    private static void foldToFit(int room) {
        if (rows.isEmpty()) return;
        folded = true;
        List<Row> groups = new ArrayList<>();
        for (Row row : rows) if (row.heading() && row.parts() > 1) groups.add(row);
        groups.sort(Comparator.comparingInt(Row::parts).reversed());
        for (Row group : groups) {
            if (contentHeight(visibleRows()) <= room) return;
            collapsed.add(group.group());
        }
    }

    private static int contentHeight(List<Row> list) {
        int height = 4;
        for (Row row : list) height += row.height();
        return height;
    }

    /** The item, its name and its total, and the close button. Returns a tooltip for what is under the mouse. */
    private static String drawHeader(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int mx, int my) {
        int rarity = MarketScreen.vivid(MarketScreen.nameColor(stack.getHoverName()));
        int well = 22;
        int wx = x + PAD;
        int wy = y + (HEADER_H - well) / 2;
        ModernGuiUtils.drawRoundedRect(graphics, wx, wy, well, well, 5, MarketIcons.fade(rarity, 0.14f));
        graphics.item(stack, wx + 3, wy + 3);
        graphics.itemDecorations(font, stack, wx + 3, wy + 3);

        int close = 14;
        int closeX = x + w - PAD - close;
        int closeY = y + (HEADER_H - close) / 2;
        boolean hoverClose = AlpakaStyle.inside(mx, my, closeX, closeY, close, close);
        if (hoverClose) ModernGuiUtils.drawRoundedRect(graphics, closeX, closeY, close, close, AlpakaStyle.FIELD_RADIUS, AlpakaStyle.ROW_SELECTED);
        MarketIcons.draw(graphics, MarketIcons.Glyph.CLOSE, closeX + 3, closeY + 3, 8, hoverClose ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_MUTED);
        hits.add(new Hit(closeX, closeY, close, close, () -> {
            AlpakaStyle.click();
            close();
        }));

        int tx = wx + well + 6;
        int room = closeX - 4 - tx;
        styled(graphics, font, stack.getHoverName(), tx, y + 7, room);

        int ly = y + 19;
        String tip = null;
        if (!AlpakaConfig.instance.allowApiCalls || breakdown == null) {
            graphics.text(font, GuiFont.text(AlpakaStyle.ellipsize(font, "Item Value", room)), tx, ly, AlpakaStyle.TEXT_MUTED, false);
            return null;
        }
        double each = breakdown.total();
        int count = Math.max(1, stack.getCount());
        if (each <= 0 && (ItemPrices.loading() || ItemIcons.loading())) {
            graphics.text(font, GuiFont.text("Loading prices…"), tx, ly, AlpakaStyle.TEXT_MUTED, false);
            return null;
        }
        if (each <= 0) {
            graphics.text(font, GuiFont.text("No price"), tx, ly, AlpakaStyle.TEXT_MUTED, false);
            return null;
        }
        double total = each * count;
        String compact = Coins.compact(total);
        graphics.text(font, GuiFont.text(compact), tx, ly, COIN, false);
        int after = tx + GuiFont.width(font, compact) + 6;
        String detail = count > 1 ? count + " × " + Coins.compact(each) : whole(total) + " coins";
        if (breakdown.incomplete()) detail = "at least " + detail;
        graphics.text(font, GuiFont.text(AlpakaStyle.ellipsize(font, detail, closeX - 4 - after)), after, ly, AlpakaStyle.TEXT_MUTED, false);
        if (AlpakaStyle.inside(mx, my, tx, ly - 2, closeX - tx, 12)) {
            tip = (count > 1 ? count + " × " + Coins.full(each) + " = " : "") + whole(total) + " coins"
                    + (breakdown.incomplete() ? ". Some parts have no price right now, so it is worth at least this." : ".");
            hoverTip("total");
        }
        return tip;
    }

    /**
     * The bar split into the groups by their share of the total. Returns a tooltip naming the group
     * under the mouse, so the colors can be read without the list beside them.
     */
    private static String drawBar(GuiGraphicsExtractor graphics, int x, int y, int w, int mx, int my) {
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, BAR_H, BAR_H / 2, AlpakaStyle.LINE);
        double total = breakdown.total();
        if (total <= 0) return null;
        List<int[]> segments = new ArrayList<>();
        List<Row> owners = new ArrayList<>();
        int at = x;
        double sum = 0;
        for (Row row : rows) {
            if (!row.heading() || !row.priced() || row.amount() <= 0) continue;
            sum += row.amount();
            int end = x + (int) Math.round(w * Math.min(1.0, sum / total));
            if (end - at >= 1) {
                segments.add(new int[] {at, end, color(row.group())});
                owners.add(row);
            }
            at = end;
        }
        String tip = null;
        // A little taller than the bar, which is only a few pixels high.
        if (AlpakaStyle.inside(mx, my, x, y - 3, w, BAR_H + 6)) {
            for (int i = 0; i < segments.size(); i++) {
                int[] s = segments.get(i);
                if (mx >= s[0] && mx < s[1]) {
                    Row row = owners.get(i);
                    double share = row.amount() / total;
                    tip = row.label() + ": " + (row.estimated() ? "~" : "") + Coins.compact(row.amount()) + ", "
                            + (share < 0.005 ? "<1%" : Math.round(share * 100) + "%") + " of the item's value.";
                    hoverTip("bar:" + row.group());
                    break;
                }
            }
        }
        for (int i = 0; i < segments.size(); i++) {
            int[] s = segments.get(i);
            int sx = s[0] + (i > 0 ? 1 : 0);
            int sw = s[1] - sx;
            if (sw <= 0) continue;
            boolean first = i == 0;
            boolean last = i == segments.size() - 1;
            if ((first || last) && sw >= BAR_H) {
                ModernGuiUtils.drawRoundedRect(graphics, sx, y, sw, BAR_H, BAR_H / 2, s[2]);
                // Square on the side that meets the next segment.
                if (!last) ModernGuiUtils.drawRect(graphics, sx + sw - BAR_H / 2, y, BAR_H / 2, BAR_H, s[2]);
                if (!first) ModernGuiUtils.drawRect(graphics, sx, y, BAR_H / 2, BAR_H, s[2]);
            } else {
                ModernGuiUtils.drawRect(graphics, sx, y, sw, BAR_H, s[2]);
            }
        }
        return tip;
    }

    /** The list of parts, scrolled. Returns a tooltip for the row under the mouse. */
    private static String drawList(GuiGraphicsExtractor graphics, Font font, List<Row> shown, int x, int y, int w, int h,
                                   int mx, int my) {
        int offset = (int) Math.round(scroll);
        boolean overList = AlpakaStyle.inside(mx, my, x, y, w, h);
        double total = breakdown.total();
        boolean shares = groupsWithValue() >= 2 && total > 0;
        String tip = null;
        graphics.enableScissor(x, y, x + w, y + h);
        int ry = y + 2 - offset;
        WheelMesh dots = new WheelMesh(1.0f / guiScale());
        for (Row row : shown) {
            int rh = row.height();
            if (ry + rh > y && ry < y + h) {
                boolean hover = overList && AlpakaStyle.inside(mx, my, x + 4, ry, w - 8, rh);
                Runnable action = action(row);
                drawRow(graphics, font, dots, row, x, ry, w, rh, hover && action != null, shares ? total : 0);
                if (hover) {
                    tip = tip(row);
                    if (tip != null) hoverTip(row);
                }
                int top = Math.max(ry, y);
                int bottom = Math.min(ry + rh, y + h);
                if (action != null) hits.add(new Hit(x + 4, top, w - 8, bottom - top, action));
            }
            ry += rh;
        }
        dots.submit(graphics);
        graphics.disableScissor();
        AlpakaStyle.scrollFades(graphics, x + 1, y, w - 2, h, offset > 0, offset < maxScroll);
        return tip;
    }

    /** What clicking the row does: a group folds or unfolds, a part with a market shows its history. */
    private static Runnable action(Row row) {
        if (row.heading() && row.parts() > 1) {
            return () -> {
                AlpakaStyle.click();
                if (!collapsed.remove(row.group())) collapsed.add(row.group());
            };
        }
        if (row.part() != null && graphable(row.part())) {
            return () -> {
                AlpakaStyle.click();
                graphPart = row.part().group() == Group.BASE ? null : row.part();
                graphOpen = true;
            };
        }
        return null;
    }

    private static boolean graphable(Part part) {
        return part.group() == Group.BASE || (part.id() != null && part.market() != null);
    }

    private static void drawRow(GuiGraphicsExtractor graphics, Font font, WheelMesh dots, Row row, int x, int y, int w, int h,
                                boolean hover, double total) {
        boolean selected = row.part() != null && graphOpen && (graphPart == null ? row.group() == Group.BASE && row.heading()
                : row.part() == graphPart);
        if (hover) ModernGuiUtils.drawRoundedRect(graphics, x + 4, y, w - 8, h, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
        if (selected) ModernGuiUtils.drawRoundedRect(graphics, x + 4, y + 2, 2, h - 4, 1, AlpakaStyle.accent());
        int ty = y + (h - 8) / 2;
        int color = color(row.group());

        int labelX;
        if (row.child()) {
            labelX = x + PAD + 16;
        } else if (row.parts() > 1) {
            AlpakaStyle.chevron(graphics, x + PAD + 4, y + h / 2, !collapsed.contains(row.group()), color);
            labelX = x + PAD + 12;
        } else {
            dots.disc(x + PAD + 4, y + h / 2f, 2.5f, color);
            labelX = x + PAD + 12;
        }

        // The value on the right; a share of the total before it on a group's line.
        int right = x + w - PAD - 2;
        String value = !row.priced() ? "—" : (row.estimated() ? "~" : "") + Coins.compact(row.amount());
        int valueColor = !row.priced() ? AlpakaStyle.TEXT_MUTED : row.child() ? AlpakaStyle.TEXT_SOFT : COIN;
        int vw = GuiFont.width(font, value);
        graphics.text(font, GuiFont.text(value), right - vw, ty, valueColor, false);
        right -= vw + 6;
        if (!row.child() && total > 0 && row.priced()) {
            String share = Math.round(row.amount() / total * 100) + "%";
            if (row.amount() / total < 0.005) share = "<1%";
            int sw = GuiFont.width(font, share);
            graphics.text(font, GuiFont.text(share), right - sw, ty, AlpakaStyle.TEXT_MUTED, false);
            right -= 24;
        }

        int labelColor = selected ? AlpakaStyle.accent() : !row.priced() ? AlpakaStyle.TEXT_MUTED
                : row.child() ? AlpakaStyle.TEXT_SOFT : AlpakaStyle.TEXT;
        String label = row.parts() > 1 ? row.label() + " (" + row.parts() + ")" : row.label();
        graphics.text(font, GuiFont.text(AlpakaStyle.ellipsize(font, label, right - labelX)), labelX, ty, labelColor, false);
    }

    /** What a row's tooltip says: the arithmetic, where the price comes from, and how it was worked out. */
    private static String tip(Row row) {
        if (row.part() == null) {
            return row.priced() ? row.label() + ": " + whole(row.amount()) + " coins over " + row.parts() + " parts. Click to "
                    + (collapsed.contains(row.group()) ? "show" : "hide") + " them." : null;
        }
        Part p = row.part();
        StringBuilder tip = new StringBuilder();
        if (!p.priced()) {
            tip.append(p.label()).append(": no price. ");
            tip.append(p.note() != null ? p.note() : "Neither the Bazaar nor the Auction House has it right now.");
            return tip.toString();
        }
        if (p.count() > 1) {
            tip.append(Coins.full(p.count())).append(" × ").append(Coins.full(p.unit())).append(" = ");
        }
        tip.append(whole(p.total())).append(" coins");
        if (p.market() == MarketKind.BAZAAR) {
            tip.append(AlpakaConfig.instance.itemValueBazaarPrice == 1 ? ", sold instantly on the Bazaar." : ", bought instantly on the Bazaar.");
        } else if (p.market() == MarketKind.AUCTION_HOUSE) {
            tip.append(", the lowest BIN on the Auction House.");
        } else {
            tip.append('.');
        }
        if (p.note() != null) tip.append(' ').append(p.note());
        if (p.estimated()) tip.append(" An estimate.");
        if (graphable(p)) tip.append(" Click for its price history.");
        return tip.toString();
    }

    /** Why there is nothing to list, in the list's place. */
    private static void drawMessage(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h) {
        String text;
        if (!AlpakaConfig.instance.allowApiCalls) {
            text = "Turn on Network Features (General › Main) to see prices.";
        } else if (data == null && idByName() == null) {
            text = ItemTags.loading() ? "Looking this item up…" : "This item has no SkyBlock id, so it has no price.";
        } else if (breakdown != null && !ItemPrices.loading()) {
            text = "Nothing on this item has a price.";
        } else if (ItemPrices.failed()) {
            text = "Couldn't load prices. Click to try again.";
            hits.add(new Hit(x, y, w, h, () -> {
                AlpakaStyle.click();
                ItemPrices.retry();
            }));
        } else {
            text = "Loading prices…";
        }
        List<FormattedText> lines = GuiFont.splitLines(font, text, w - 2 * PAD - 8);
        int ly = y + (h - lines.size() * 10) / 2;
        for (FormattedText line : lines) {
            int lw = font.width(line);
            graphics.text(font, Language.getInstance().getVisualOrder(line), x + (w - lw) / 2, ly, AlpakaStyle.TEXT_MUTED, false);
            ly += 10;
        }
    }

    /** The price history of the item, or of the part picked in the list. */
    private static String drawGraph(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h, int mx, int my,
                                    boolean interactive) {
        MarketKind kind;
        ItemTags.Tag tag;
        String title;
        if (graphPart == null) {
            Part base = basePart();
            kind = base != null && base.market() != null ? base.market() : MarketKind.AUCTION_HOUSE;
            tag = AlpakaConfig.instance.allowApiCalls
                    ? ItemTags.of(stack, SkyblockUtils.cleanColor(stack.getHoverName().getString()), kind)
                    : new ItemTags.Tag(ItemTags.Lookup.UNKNOWN, null);
            title = baseTitle();
        } else {
            kind = graphPart.market();
            tag = new ItemTags.Tag(ItemTags.Lookup.FOUND, graphPart.id());
            // What is graphed may be what the part was priced by: Smite VII by the Severed Hand that raised it.
            title = ItemValue.nameOf(graphPart.id(), CATALOG);
        }
        String[] tip = {null};
        PriceGraph.draw(graphics, font, kind, tag, title, x, y, w, h, interactive ? mx : -10000, interactive ? my : -10000,
                (hx, hy, hw, hh, action, tipText) -> {
                    if (!interactive) return;
                    hits.add(new Hit(hx, hy, hw, hh, () -> {
                        AlpakaStyle.click();
                        action.run();
                    }));
                    if (tipText != null && AlpakaStyle.inside(mx, my, hx, hy, hw, hh)) {
                        tip[0] = tipText;
                        hoverTip("graph:" + hx + ":" + hy);
                    }
                });
        return tip[0];
    }

    /** The item's own name for its graph: Hypixel's, without the reforge, stars or pet level the item's name carries. */
    private static String baseTitle() {
        String id = data != null && data.has("id") ? data.get("id").getAsString() : idByName();
        String listed = id == null || id.equals("PET") ? null : ItemIcons.name(id);
        if (listed != null) return listed;
        return SkyblockUtils.cleanColor(stack.getHoverName().getString())
                .replaceAll("^\\[Lvl \\d+\\]\\s*", "").replaceAll("[✪➊➋➌➍➎]", "").trim();
    }

    private static Part basePart() {
        if (breakdown == null) return null;
        for (Part part : breakdown.parts()) if (part.group() == Group.BASE) return part;
        return null;
    }

    /** The price history's row: its switch on the left, where the prices come from on the right. */
    private static String drawHistoryRow(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int mx, int my) {
        ModernGuiUtils.drawRect(graphics, x + PAD, y, w - 2 * PAD, 1, AlpakaStyle.LINE);
        String tip = null;
        String label = "Price History";
        int bw = GuiFont.width(font, label) + 20;
        int bh = 14;
        int bx = x + PAD - 2;
        int by = y + (HISTORY_ROW_H - bh) / 2 + 1;
        boolean hover = AlpakaStyle.inside(mx, my, bx, by, bw, bh);
        if (hover) ModernGuiUtils.drawRoundedRect(graphics, bx, by, bw, bh, AlpakaStyle.FIELD_RADIUS, AlpakaStyle.ROW_SELECTED);
        int color = graphOpen ? AlpakaStyle.accent() : hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT;
        AlpakaStyle.chevron(graphics, bx + 7, by + bh / 2, graphOpen, color);
        graphics.text(font, GuiFont.text(label), bx + 14, by + 3, color, false);
        hits.add(new Hit(bx, by, bw, bh, () -> {
            AlpakaStyle.click();
            graphOpen = !graphOpen;
            if (!graphOpen) graphPart = null;
        }));
        if (hover) {
            tip = graphOpen ? "Hide the price history." : "Show the price history of the item, or of the part you click in the list.";
            hoverTip("graph-toggle");
        }

        String source = sourceText();
        int room = x + w - PAD - (bx + bw + 8);
        String shown = AlpakaStyle.ellipsize(font, source, room);
        int sw = GuiFont.width(font, shown);
        int sx = x + w - PAD - sw;
        graphics.text(font, GuiFont.text(shown), sx, by + 3, AlpakaStyle.DIVIDER, false);
        if (AlpakaStyle.inside(mx, my, sx - 2, by, sw + 4, bh)) {
            tip = "Bazaar prices from Hypixel, lowest BINs from SkyCofl. Asked for again every few minutes while this is open.";
            hoverTip("source");
        }
        return tip;
    }

    private static String sourceText() {
        long at = ItemPrices.updatedAt();
        if (at <= 0) return "Hypixel · SkyCofl";
        long minutes = (System.currentTimeMillis() - at) / 60_000L;
        return "Hypixel · SkyCofl · " + (minutes < 1 ? "now" : minutes + "m ago");
    }

    // ---- Tooltips -------------------------------------------------------------------------------

    private static Object hovered = null;

    private static void hoverTip(Object key) {
        hovered = key;
    }

    private static void drawTooltip(GuiGraphicsExtractor graphics, Font font, int screenW, int screenH, int mx, int my,
                                    String tip, long now) {
        Object key = tip == null ? null : hovered;
        hovered = null;
        if (key == null || !key.equals(tipKey)) {
            tipKey = key;
            tipSince = now;
        }
        if (tip != null && now - tipSince >= AlpakaStyle.TOOLTIP_DELAY_MS) {
            AlpakaStyle.tooltip(graphics, font, screenW, screenH, mx, my, tip);
        }
    }

    // ---- Input ----------------------------------------------------------------------------------

    /** Whether the mouse is over the open popup in this menu, which then hides what is under it. */
    public static boolean coversMouse(Screen in) {
        if (screen == null || in != screen || panelW <= 0) return false;
        double[] cursor = cursor();
        return AlpakaStyle.inside(cursor[0], cursor[1], panelX, panelY, panelW, panelH);
    }

    /**
     * A click in the menu while the popup is open: on the popup it does what it hits; beside it, it
     * closes the popup. Either way the menu does not get it. False when the popup is not open there.
     */
    static boolean mouseClicked(Screen in, double mx, double my, int button) {
        if (screen == null || in != screen) return false;
        heldButton = button;
        if (!AlpakaStyle.inside(mx, my, panelX, panelY, panelW, panelH)) {
            close();
            heldButton = button;
            return true;
        }
        if (button != 0) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(mx, my)) {
                hit.action().run();
                return true;
            }
        }
        // The header, where nothing else answers, is the handle to move the popup by.
        if (my < panelY + HEADER_H) {
            dragging = true;
            grabX = (int) Math.floor(mx) - panelX;
            grabY = (int) Math.floor(my) - panelY;
        }
        return true;
    }

    /** The release of a press the popup took. */
    static boolean mouseReleased(int button) {
        if (heldButton < 0 || button != heldButton) return false;
        heldButton = -1;
        dragging = false;
        return true;
    }

    /** A drag that began on the popup, or beside it while it was open. */
    static boolean mouseDragged(int button) {
        return heldButton >= 0 && button == heldButton;
    }

    /** The wheel over the popup scrolls its list. */
    static boolean mouseScrolled(Screen in, double mx, double my, double amount) {
        if (screen == null || in != screen || !AlpakaStyle.inside(mx, my, panelX, panelY, panelW, panelH)) return false;
        if (amount != 0 && maxScroll > 0) scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - amount * 3 * PART_H));
        return true;
    }

    // ---- Helpers --------------------------------------------------------------------------------

    /** A sum of coins as a whole number: "1,416,677,593". A single price keeps its decimal. */
    private static String whole(double coins) {
        return Coins.full(Math.rint(coins));
    }

    /** The cursor in GUI pixels. */
    private static double[] cursor() {
        Minecraft mc = Minecraft.getInstance();
        return new double[] {mc.mouseHandler.getScaledXPos(mc.getWindow()), mc.mouseHandler.getScaledYPos(mc.getWindow())};
    }

    private static float approach(float current, float target, float step) {
        if (current < target) return Math.min(target, current + step);
        return Math.max(target, current - step);
    }

    /** Text in its own colors and the menu font, cut with an ellipsis to fit. */
    private static void styled(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int maxW) {
        Component restyled = GuiFont.restyle(text);
        if (font.width(restyled) <= maxW) {
            graphics.text(font, restyled, x, y, 0xFFFFFFFF, false);
            return;
        }
        Component dots = GuiFont.text("…");
        FormattedText cut = font.substrByWidth(restyled, Math.max(0, maxW - font.width(dots)));
        graphics.text(font, Language.getInstance().getVisualOrder(cut), x, y, 0xFFFFFFFF, false);
        graphics.text(font, dots, x + font.width(cut), y, AlpakaStyle.TEXT_MUTED, false);
    }

    private static int guiScale() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? 2 : (int) Math.max(1, mc.getWindow().getGuiScale());
    }

    // ---- For the screenshot tour ----------------------------------------------------------------

    /** Shows the item's own price history, as the Price History switch does, for the screenshot tour. */
    public static void devGraph() {
        graphOpen = true;
        graphShown = 1f;
        graphPart = null;
    }

    /** The middle of the first line of the list that starts with this text, as last drawn, for the screenshot tour. */
    public static int[] devRowPoint(String label) {
        int y = listY + 2 - (int) Math.round(scroll);
        for (Row row : visibleRows()) {
            if (row.label().startsWith(label)) return new int[] {listX + listW / 2, y + row.height() / 2};
            y += row.height();
        }
        return new int[] {listX + listW / 2, listY};
    }

    /** A left click at this point, as the menu would hand it over, for the screenshot tour. */
    public static void devClick(int[] point) {
        if (screen != null) mouseClicked(screen, point[0], point[1], 0);
        mouseReleased(0);
    }

    /** Folds or unfolds every group, for the screenshot tour. */
    public static void devFold(boolean fold) {
        folded = true;
        collapsed.clear();
        if (fold) for (Group group : Group.values()) collapsed.add(group);
    }
}
