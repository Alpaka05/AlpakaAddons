package net.alpaka.addons.features.chestvalue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.serialization.JsonOps;
import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.client.gui.PloppAnimation;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.chestvalue.ChestValue.Entry;
import net.alpaka.addons.features.chestvalue.ChestValue.Sort;
import net.alpaka.addons.features.chestvalue.ChestValue.Stack;
import net.alpaka.addons.features.chestvalue.ChestValue.Summary;
import net.alpaka.addons.features.itemvalue.ItemPrices;
import net.alpaka.addons.features.itemvalue.ItemValue;
import net.alpaka.addons.features.itemvalue.ItemValuePopup;
import net.alpaka.addons.features.market.Coins;
import net.alpaka.addons.features.market.ItemIcons;
import net.alpaka.addons.features.market.MarketIcons;
import net.alpaka.addons.features.market.MarketKind;
import net.alpaka.addons.features.market.MarketScreen;
import net.alpaka.addons.mixin.AbstractContainerScreenAccessor;
import net.alpaka.addons.mixin.AbstractRecipeBookScreenAccessor;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.locale.Language;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Chest Value panel: a column beside the open menu - a chest on the island, a backpack, an
 * Ender Chest page, the inventory - with what everything in it is worth. The total and how it moved
 * since the menu opened, a bar split by the most valuable items, and every item as one line with
 * its amount, value and share; the items without a price folded away at the bottom.
 *
 * It sits to the left of the menu by default, beside its top edge, since the item list mods (NEU,
 * REI, Skyblocker's) take the right; never over the menu's own slots, and away from the recipe book
 * when that is open. With little room it narrows to icons and values, and with none it stays away.
 *
 * Its header is a handle: held there, the panel moves like a window and stays where it was put, in
 * every menu and after a restart; a double-click on the header puts it back beside the menu.
 *
 * Pointing at a line lights up the slots that item is in; pointing at a slot lights up its line.
 * Clicking a line opens the Item Value breakdown of that item. It only reads the menu's slots and
 * the price lists Item Value already keeps; nothing is sent to the server.
 */
public final class ChestValuePanel {
    private ChestValuePanel() {}

    // ---- Layout, in GUI pixels ------------------------------------------------------------------

    private static final int MAX_W = 204;
    /** Below this the panel drops the names and the toolbar and shows icons and values only. */
    private static final int FULL_W = 128;
    /** Below this there is no room for it at all. */
    private static final int MIN_W = 62;
    /** Between the panel and the menu, and the panel and the screen's edge. */
    private static final int GAP = 6;
    private static final int EDGE = 4;
    private static final int PAD = AlpakaStyle.PAD;
    private static final int HEADER_H = AlpakaStyle.HEADER_H;
    private static final int NARROW_HEADER_H = 24;
    private static final int TOOLBAR_H = 20;
    private static final int ROW_H = 18;
    private static final int GROUP_H = 15;
    private static final int BAR_H = 4;
    private static final int FOOTER_H = 17;
    private static final int MESSAGE_H = 40;
    private static final int CHIP_H = 14;
    /** Where the recipe book sits beside the inventory, as vanilla places it. */
    private static final int RECIPE_BOOK_OFFSET = 86;

    private static final int COIN = 0xFFFFAA00;
    private static final int GAIN = 0xFF5BE38A;
    /** The colors of the most valuable items, in the bar and beside their lines; the rest share gray. */
    private static final int[] PALETTE = {0xFFFFB347, 0xFFC264F2, 0xFF5CE1F0, 0xFF5BE38A, 0xFF6E8BFF, 0xFFFF73E3};
    private static final int OTHER = 0xFF6A6A6A;

    /** How often the slots are read again: often enough to follow items moved in and out. */
    private static final long REBUILD_MS = 250;
    /** Items that are part of every inventory and worth nothing to anyone. */
    private static final Set<String> IGNORED = Set.of("SKYBLOCK_MENU");

    // ---- State ----------------------------------------------------------------------------------

    /** The menu everything below belongs to; null before the first frame of a menu. */
    private static Screen screen = null;
    private static Summary summary = Summary.EMPTY;
    /** Whether the counted slots hold anything at all, SkyBlock item or menu button. */
    private static boolean anyItems = false;
    private static long builtAt = 0L;
    private static boolean dirty = true;
    /** What the menu held when its prices first came in, valued again at today's prices; null before. */
    private static List<Held> baseline = null;
    private static boolean unpricedOpen = false;

    private static long shownAt = 0L;
    private static long lastFrameMs = 0L;
    private static long copiedAt = 0L;
    private static double scroll = 0;
    private static double scrollTarget = 0;
    private static int maxScroll = 0;

    // The last frame's layout, for the mouse.
    private static int panelX, panelY, panelW, panelH;
    private static int listY;
    private static boolean leftSide = true;
    private static final List<Hit> hits = new ArrayList<>();
    /** The line under the mouse, in the list or the bar, whose slots are lit. */
    private static Entry pointed = null;
    private static Object tipKey = null;
    private static long tipSince = 0L;
    private static Object hoveredTip = null;
    private static int heldButton = -1;

    /** Whether the header is held and the panel follows the mouse, like a window by its title bar. */
    private static boolean dragging = false;
    private static int grabX, grabY;
    private static int headerBottom;
    private static long lastHeaderPress = 0L;
    /** Two presses on the header this close together put the panel back beside the menu. */
    private static final long DOUBLE_CLICK_MS = 350;

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return AlpakaStyle.inside(mx, my, x, y, w, h);
        }
    }

    /** A counted slot's data and amount, for what the menu held when it opened. */
    private record Held(CustomData data, int count) {}

    /** One line of the list: an item, or the heading of the items without a price. */
    private record Row(Entry entry, boolean heading) {
        int height() {
            return heading ? GROUP_H : ROW_H;
        }
    }

    // ---- What things are worth ------------------------------------------------------------------

    /** One kind of item at today's prices: NaN for none. Null id for an item that is no SkyBlock item. */
    private record Valuation(String id, double each, boolean estimated, MarketKind market) {}

    private static final Valuation NONE = new Valuation(null, Double.NaN, false, null);
    /** Valuations by the item's data, worked out once per price list: a chest is mostly the same items. */
    private static final Map<CustomData, Valuation> VALUES = new HashMap<>();
    private static long valuesKey = Long.MIN_VALUE;

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

    private static Valuation value(CustomData custom) {
        Valuation cached = VALUES.get(custom);
        if (cached != null) return cached;
        Valuation v = NONE;
        JsonElement json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, custom.copyTag());
        if (json.isJsonObject() && json.getAsJsonObject().has("id")) {
            JsonObject data = json.getAsJsonObject();
            String id = data.get("id").getAsString();
            if (!IGNORED.contains(id)) {
                ItemValue.Breakdown b = ItemValue.of(data, PRICES, CATALOG);
                double each = b.total();
                // An item priced as a whole says which market; one added up from its parts does not.
                MarketKind market = b.parts().size() == 1 ? b.parts().get(0).market() : null;
                v = new Valuation(id, each > 0 ? each : Double.NaN, b.incomplete(), market);
            }
        }
        // A menu full of changing buttons must not grow this without end.
        if (VALUES.size() > 4096) VALUES.clear();
        VALUES.put(custom, v);
        return v;
    }

    /** Throws the valuations away when prices, the item list or the Bazaar side changed. */
    private static void checkPrices() {
        long key = ((long) ItemPrices.version() << 8) | (AlpakaConfig.instance.itemValueBazaarPrice << 1) | (ItemIcons.ready() ? 1 : 0);
        if (key == valuesKey) return;
        valuesKey = key;
        VALUES.clear();
        dirty = true;
    }

    // ---- Reading the menu -----------------------------------------------------------------------

    /**
     * Whether the panel goes with this menu at all: not with the market screens or the creative
     * inventory, and not with Hypixel's Storage overview, whose slots are buttons for the backpacks
     * and ender chest pages (one of them a Hunting Toolkit). A single backpack or page has it as usual.
     */
    private static boolean applies(Screen s) {
        return s instanceof AbstractContainerScreen<?> && !(s instanceof MarketScreen) && !(s instanceof CreativeModeInventoryScreen)
                && !"Storage".equals(SkyblockUtils.cleanColor(s.getTitle().getString()).trim());
    }

    private static boolean includesInventory(AbstractContainerScreen<?> c) {
        return c instanceof InventoryScreen || AlpakaConfig.instance.chestValueIncludeInventory;
    }

    /**
     * Reads the slots again. A chest counts its own slots, the inventory its player's, and a chest
     * with Include Inventory both; the crafting grid never.
     */
    private static void rebuild(AbstractContainerScreen<?> c, long now) {
        checkPrices();
        if (!dirty && now - builtAt < REBUILD_MS) return;
        builtAt = now;
        dirty = false;
        boolean inventoryScreen = c instanceof InventoryScreen;
        boolean withInventory = includesInventory(c);
        List<Stack> stacks = new ArrayList<>();
        List<Held> heldNow = new ArrayList<>();
        boolean any = false;
        List<Slot> slots = c.getMenu().slots;
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            boolean player = slot.container instanceof Inventory;
            if (player ? !withInventory : inventoryScreen) continue;
            ItemStack item = slot.getItem();
            if (item.isEmpty()) continue;
            any = true;
            CustomData custom = item.get(DataComponents.CUSTOM_DATA);
            if (custom == null) continue;
            Valuation v = value(custom);
            if (v.id() == null) continue;
            int count = count(item, player);
            if (count <= 0) continue;
            String name = SkyblockUtils.cleanColor(item.getHoverName().getString());
            stacks.add(new Stack(i, v.id(), name, count, v.each(), v.estimated(), v.market(), item.copy()));
            heldNow.add(new Held(custom, count));
        }
        anyItems = any;
        summary = ChestValue.of(stacks, Sort.of(AlpakaConfig.instance.chestValueSort));
        if (baseline == null && !waiting()) baseline = List.copyOf(heldNow);
    }

    /**
     * How many of the item a slot stands for: the stack's own count, or in a sack what its lore says
     * is stored there. An empty sack line counts nothing.
     */
    private static int count(ItemStack item, boolean player) {
        if (!player) {
            ItemLore lore = item.get(DataComponents.LORE);
            if (lore != null) {
                List<String> lines = new ArrayList<>(lore.lines().size());
                for (Component line : lore.lines()) lines.add(SkyblockUtils.cleanColor(line.getString()));
                long stored = ChestValue.sackCount(lines);
                if (stored >= 0) return (int) Math.min(stored, Integer.MAX_VALUE);
            }
        }
        return item.getCount();
    }

    /** What the menu held when it opened, at today's prices. */
    private static double baselineTotal() {
        double sum = 0;
        for (Held h : baseline) {
            Valuation v = value(h.data());
            if (!Double.isNaN(v.each())) sum += v.each() * h.count();
        }
        return sum;
    }

    /** Whether there is nothing to show yet: prices off, still on their way, or failed. */
    private static boolean waiting() {
        return !AlpakaConfig.instance.allowApiCalls || ItemPrices.loading() || ItemIcons.loading() || ItemPrices.failed();
    }

    private static void reset(Screen s) {
        screen = s;
        summary = Summary.EMPTY;
        anyItems = false;
        builtAt = 0L;
        dirty = true;
        baseline = null;
        scroll = scrollTarget = 0;
        maxScroll = 0;
        pointed = null;
        tipKey = null;
        heldButton = -1;
        dragging = false;
        panelW = 0;
    }

    // ---- Showing and hiding ---------------------------------------------------------------------

    /** The key switched the panel: it pops in when shown. */
    static void toggled(boolean shown) {
        shownAt = shown ? System.currentTimeMillis() : 0L;
        if (!shown) {
            panelW = 0;
            hits.clear();
            heldButton = -1;
        }
    }

    /** The menu closed or was replaced: what was read from it goes with it. */
    static void screenRemoved(Screen removed) {
        if (removed == screen) {
            screen = null;
            panelW = 0;
            hits.clear();
            heldButton = -1;
        }
    }

    // ---- Drawing --------------------------------------------------------------------------------

    /** Draws the panel beside its menu, after the menu and before the tooltips. */
    public static void render(GuiGraphicsExtractor graphics, Screen in) {
        Minecraft mc = Minecraft.getInstance();
        if (!AlpakaConfig.instance.chestValueEnabled || !AlpakaConfig.instance.chestValueShown || !applies(in)
                || mc.gui.screen() != in) {
            if (in == screen) panelW = 0;
            return;
        }
        AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) in;
        if (in != screen) reset(in);
        long now = System.currentTimeMillis();
        float deltaSec = Math.min(0.1f, (now - lastFrameMs) / 1000.0f);
        lastFrameMs = now;
        if (AlpakaConfig.instance.allowApiCalls) ItemPrices.refresh();
        rebuild(container, now);

        // A menu of buttons, with no SkyBlock item in it, is no chest: the panel stays away. An empty
        // chest or inventory still shows it, so it does not seem to have gone.
        if (summary.isEmpty() && anyItems && !(in instanceof InventoryScreen)) {
            panelW = 0;
            return;
        }

        Font font = mc.font;
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        double[] cursor = cursor();
        int mx = (int) Math.floor(cursor[0]);
        int my = (int) Math.floor(cursor[1]);
        boolean beside = place(container, screenW);
        boolean moved = moved();
        if (!beside && !moved) {
            panelW = 0;
            return;
        }
        // Moved by the player, it keeps the width it had beside the menu, or the full one where there was no room.
        if (!beside) panelW = Math.min(MAX_W, screenW - 2 * EDGE);
        boolean narrow = panelW < FULL_W;
        int w = panelW;
        hits.clear();
        pointed = null;
        String tip = null;

        scroll += (scrollTarget - scroll) * Math.min(1.0, deltaSec * 16.0);
        if (Math.abs(scrollTarget - scroll) < 0.05) scroll = scrollTarget;

        boolean waiting = waiting();
        boolean hasList = !waiting && !summary.isEmpty();
        Map<Entry, Integer> colors = colors();
        boolean showBar = hasList && summary.priced().size() >= 2 && summary.total() > 0;
        int headerH = narrow ? NARROW_HEADER_H : HEADER_H;
        int toolbarH = narrow ? 0 : TOOLBAR_H;
        int barArea = showBar ? BAR_H + 10 : 2;
        int fixed = headerH + toolbarH + barArea + FOOTER_H;
        List<Row> rows = hasList ? rows() : List.of();
        int content = hasList ? contentHeight(rows) : MESSAGE_H;
        int maxH = screenH - 2 * EDGE;
        int listArea = Math.max(Math.min(content, 2 * ROW_H), Math.min(content, maxH - fixed));
        int h = Math.min(maxH, fixed + listArea);
        int x = panelX;
        int y = ((AbstractContainerScreenAccessor) container).alpaka$topPos();
        if (dragging) {
            AlpakaConfig.instance.chestValueX = (float) (mx - grabX) / screenW;
            AlpakaConfig.instance.chestValueY = (float) (my - grabY) / screenH;
            moved = true;
            graphics.requestCursor(CursorTypes.RESIZE_ALL);
        }
        if (moved) {
            // Kept as a share of the screen, so it lands in the same spot at another size or GUI scale.
            x = Math.round(AlpakaConfig.instance.chestValueX * screenW);
            y = Math.round(AlpakaConfig.instance.chestValueY * screenH);
        }
        x = Math.max(EDGE, Math.min(screenW - EDGE - w, x));
        y = Math.max(EDGE, Math.min(screenH - EDGE - h, y));
        panelX = x;
        panelY = y;
        panelH = h;
        headerBottom = y + headerH;

        graphics.nextStratum();
        float scale = shownAt > 0 ? PloppAnimation.getOpenScale(shownAt) : 1f;
        boolean scaling = scale < 0.999f;
        if (scaling) {
            graphics.pose().pushMatrix();
            graphics.pose().scaleAround(scale, scale, leftSide ? x + w : x, y);
        }
        AlpakaStyle.popover(graphics, x, y, w, h);

        int cy = y;
        String t = narrow ? drawNarrowHeader(graphics, font, x, cy, w, mx, my) : drawHeader(graphics, font, container, x, cy, w, mx, my);
        if (t != null) tip = t;
        if (tip == null && !dragging && AlpakaStyle.inside(mx, my, x, y, w, headerH) && !overHit(mx, my)) {
            tip = moved ? "Drag to move. Double-click to put it back beside the menu." : "Drag to move.";
            hoverTip("move");
        }
        cy += headerH;
        if (!narrow) {
            t = drawToolbar(graphics, font, container, x, cy, w, mx, my, now);
            if (t != null) tip = t;
            cy += toolbarH;
        }
        if (showBar) {
            t = drawBar(graphics, x + PAD, cy + 5, w - 2 * PAD, colors, mx, my);
            if (t != null) tip = t;
        }
        cy += barArea;

        listY = cy;
        if (hasList) {
            maxScroll = Math.max(0, content - listArea);
            scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget));
            t = drawList(graphics, font, container, rows, colors, x, cy, w, listArea, mx, my, narrow);
            if (t != null) tip = t;
        } else {
            maxScroll = 0;
            drawMessage(graphics, font, x, cy, w, listArea);
        }
        cy += listArea;
        t = drawFooter(graphics, font, x, cy, w, mx, my, narrow);
        if (t != null) tip = t;
        if (scaling) graphics.pose().popMatrix();

        drawSlotHighlights(graphics, container, colors);
        drawTooltip(graphics, font, screenW, screenH, mx, my, tip, now);
    }

    /**
     * Finds the panel's place beside the menu: on the side the settings pick, up against the menu,
     * as wide as the room there allows. False when there is too little room.
     */
    private static boolean place(AbstractContainerScreen<?> c, int screenW) {
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) c;
        int left = a.alpaka$leftPos();
        int right = left + a.alpaka$imageWidth();
        // The recipe book opens to the left of the inventory and pushes it right.
        if (c instanceof AbstractRecipeBookScreen<?> && c instanceof AbstractRecipeBookScreenAccessor book
                && book.alpaka$recipeBook().isVisible() && !book.alpaka$widthTooNarrow()) {
            left = Math.min(left, (screenW - RecipeBookComponent.IMAGE_WIDTH) / 2 - RECIPE_BOOK_OFFSET);
        }
        leftSide = AlpakaConfig.instance.chestValueSide == 0;
        int room = leftSide ? left - GAP - EDGE : screenW - EDGE - (right + GAP);
        int w = Math.min(MAX_W, room);
        if (w < MIN_W) return false;
        panelW = w;
        panelX = leftSide ? left - GAP - w : right + GAP;
        return true;
    }

    /** The colors of the most valuable items, by value whatever the list's order. */
    private static Map<Entry, Integer> colors() {
        Map<Entry, Integer> out = new IdentityHashMap<>();
        List<Entry> byValue = new ArrayList<>(summary.priced());
        byValue.sort(ChestValue.comparator(Sort.VALUE));
        for (int i = 0; i < byValue.size(); i++) out.put(byValue.get(i), i < PALETTE.length ? PALETTE[i] : OTHER);
        return out;
    }

    private static List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (Entry e : summary.priced()) out.add(new Row(e, false));
        if (!summary.unpriced().isEmpty()) {
            out.add(new Row(null, true));
            if (unpricedOpen) for (Entry e : summary.unpriced()) out.add(new Row(e, false));
        }
        return out;
    }

    private static int contentHeight(List<Row> rows) {
        int height = 4;
        for (Row row : rows) height += row.height();
        return height;
    }

    /** The menu's name and its total, with how it moved since the menu opened, and the button that hides the panel. */
    private static String drawHeader(GuiGraphicsExtractor graphics, Font font, AbstractContainerScreen<?> c,
                                     int x, int y, int w, int mx, int my) {
        // No icon before the name: the title and the total say what this is, and the name keeps the room.
        int closeX = drawClose(graphics, x, y, w, HEADER_H, mx, my);
        int tx = x + PAD + 2;
        int room = closeX - 4 - tx;
        Component title = title(c);
        String extra = !(c instanceof InventoryScreen) && AlpakaConfig.instance.chestValueIncludeInventory ? " + Inventory" : "";
        int extraW = extra.isEmpty() ? 0 : GuiFont.width(font, extra);
        int titleW = styled(graphics, font, title, tx, y + 7, room - Math.min(extraW, room / 2));
        if (!extra.isEmpty()) {
            graphics.text(font, GuiFont.text(AlpakaStyle.ellipsize(font, extra, room - titleW)), tx + titleW, y + 7,
                    AlpakaStyle.TEXT_MUTED, false);
        }

        int ly = y + 19;
        return drawTotal(graphics, font, tx, ly, closeX - 4, mx, my, true);
    }

    /** In a narrow panel: the total and the button that hides it. */
    private static String drawNarrowHeader(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int mx, int my) {
        int closeX = drawClose(graphics, x, y, w, NARROW_HEADER_H, mx, my);
        return drawTotal(graphics, font, x + PAD, y + (NARROW_HEADER_H - 8) / 2, closeX - 2, mx, my, false);
    }

    /** The total, then how it moved since the menu opened, then how many items there are. */
    private static String drawTotal(GuiGraphicsExtractor graphics, Font font, int tx, int ly, int end, int mx, int my,
                                    boolean detail) {
        if (!AlpakaConfig.instance.allowApiCalls) {
            graphics.text(font, GuiFont.text(AlpakaStyle.ellipsize(font, "Chest Value", end - tx)), tx, ly, AlpakaStyle.TEXT_MUTED, false);
            return null;
        }
        if (waiting()) {
            String text = ItemPrices.failed() ? "No prices" : "Loading…";
            graphics.text(font, GuiFont.text(AlpakaStyle.ellipsize(font, text, end - tx)), tx, ly, AlpakaStyle.TEXT_MUTED, false);
            return null;
        }
        double total = summary.total();
        String compact = (summary.incomplete() ? "≥ " : "") + Coins.compact(total);
        graphics.text(font, GuiFont.text(compact), tx, ly, COIN, false);
        int after = tx + GuiFont.width(font, compact) + 5;
        String tip = null;
        if (AlpakaStyle.inside(mx, my, tx - 2, ly - 2, after - tx, 12)) {
            tip = whole(total) + " coins over " + summary.items() + (summary.items() == 1 ? " item" : " items") + " in "
                    + summary.stacks() + (summary.stacks() == 1 ? " slot" : " slots") + "."
                    + (summary.incomplete() ? " Some have no price right now, so it is worth at least this." : "")
                    + " Bazaar items count at their " + (AlpakaConfig.instance.itemValueBazaarPrice == 1 ? "instant sell" : "instant buy")
                    + " price, as Item Value's Bazaar Prices setting says.";
            hoverTip("total");
        }

        double delta = baseline == null ? 0 : total - baselineTotal();
        if (Math.abs(delta) >= 0.5 && after < end) {
            String change = (delta > 0 ? "+" : "−") + Coins.compact(Math.abs(delta));
            int cw = GuiFont.width(font, change);
            if (after + cw <= end) {
                graphics.text(font, GuiFont.text(change), after, ly, delta > 0 ? GAIN : AlpakaStyle.DANGER_SOFT, false);
                if (AlpakaStyle.inside(mx, my, after - 2, ly - 2, cw + 4, 12)) {
                    tip = (delta > 0 ? "Up " : "Down ") + whole(Math.abs(delta)) + " coins since this menu opened, from items moved "
                            + (delta > 0 ? "in." : "out.") + " Price changes do not count.";
                    hoverTip("delta");
                }
                after += cw + 5;
            }
        }
        String items = summary.items() == 1 ? "1 item" : Coins.full(summary.items()) + " items";
        // Shown whole or not at all: "169 ite…" says less than nothing.
        if (detail && after + GuiFont.width(font, items) <= end) {
            graphics.text(font, GuiFont.text(items), after, ly, AlpakaStyle.TEXT_MUTED, false);
        }
        return tip;
    }

    /** The close button in the header's top-right corner, which hides the panel as the key does. Returns its left edge. */
    private static int drawClose(GuiGraphicsExtractor graphics, int x, int y, int w, int headerH, int mx, int my) {
        int close = 14;
        int closeX = x + w - PAD - close + 2;
        int closeY = y + (headerH - close) / 2;
        boolean hover = AlpakaStyle.inside(mx, my, closeX, closeY, close, close);
        if (hover) ModernGuiUtils.drawRoundedRect(graphics, closeX, closeY, close, close, AlpakaStyle.FIELD_RADIUS, AlpakaStyle.ROW_SELECTED);
        MarketIcons.draw(graphics, MarketIcons.Glyph.CLOSE, closeX + 3, closeY + 3, 8, hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_MUTED);
        hits.add(new Hit(closeX, closeY, close, close, () -> {
            AlpakaStyle.click();
            ChestValueFeature.toggle();
        }));
        if (hover) hoverTip("close");
        return closeX;
    }

    /** The menu's own name: the inventory's is "Crafting", which says nothing here. */
    private static Component title(AbstractContainerScreen<?> c) {
        return c instanceof InventoryScreen ? GuiFont.text("Inventory") : c.getTitle();
    }

    /** The row of switches under the header: whether the inventory counts, the order, and copying the list. */
    private static String drawToolbar(GuiGraphicsExtractor graphics, Font font, AbstractContainerScreen<?> c,
                                      int x, int y, int w, int mx, int my, long now) {
        String tip = null;
        int cy = y + (TOOLBAR_H - CHIP_H) / 2 - 1;
        int cx = x + PAD - 2;
        int end = x + w - PAD + 2;

        // Copy on the right; the switches fill from the left.
        boolean copied = now - copiedAt < 1500;
        String copyLabel = copied ? "Copied" : "Copy";
        int copyW = GuiFont.width(font, copyLabel) + 10;
        int copyX = end - copyW;
        boolean canCopy = !waiting() && !summary.isEmpty();
        boolean hoverCopy = canCopy && AlpakaStyle.inside(mx, my, copyX, cy, copyW, CHIP_H);
        chip(graphics, font, copyX, cy, copyW, copyLabel, hoverCopy, copied, canCopy);
        if (canCopy) {
            hits.add(new Hit(copyX, cy, copyW, CHIP_H, () -> {
                AlpakaStyle.click();
                String plainTitle = SkyblockUtils.cleanColor(title(c).getString());
                Minecraft.getInstance().keyboardHandler.setClipboard(ChestValue.text(plainTitle, summary));
                copiedAt = System.currentTimeMillis();
            }));
        }
        if (hoverCopy) {
            tip = "Copy the list with its prices, to paste it into a chat or a note.";
            hoverTip("copy");
        }
        int room = copyX - 4;

        if (!(c instanceof InventoryScreen)) {
            boolean on = AlpakaConfig.instance.chestValueIncludeInventory;
            String label = (on ? "✔ " : "+ ") + "Inventory";
            int cw = GuiFont.width(font, label) + 10;
            if (cx + cw <= room) {
                boolean hover = AlpakaStyle.inside(mx, my, cx, cy, cw, CHIP_H);
                chip(graphics, font, cx, cy, cw, label, hover, on, true);
                hits.add(new Hit(cx, cy, cw, CHIP_H, () -> {
                    AlpakaStyle.click();
                    AlpakaConfig.instance.chestValueIncludeInventory = !AlpakaConfig.instance.chestValueIncludeInventory;
                    AlpakaConfig.save();
                    // Another set of slots: the change since opening starts over.
                    dirty = true;
                    baseline = null;
                    scrollTarget = 0;
                }));
                if (hover) {
                    tip = on ? "Counting your inventory too. Click to count only this menu."
                            : "Count your own inventory below the menu too.";
                    hoverTip("inventory");
                }
                cx += cw + 4;
            }
        }

        Sort sort = Sort.of(AlpakaConfig.instance.chestValueSort);
        String label = sort.title;
        int sw = GuiFont.width(font, label) + 20;
        if (cx + sw <= room) {
            boolean hover = AlpakaStyle.inside(mx, my, cx, cy, sw, CHIP_H);
            chip(graphics, font, cx, cy, sw, "", hover, false, true);
            MarketIcons.draw(graphics, MarketIcons.Glyph.SORT, cx + 4, cy + 3, 8, hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_MUTED);
            graphics.text(font, GuiFont.text(label), cx + 15, cy + 3, hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT, false);
            hits.add(new Hit(cx, cy, sw, CHIP_H, () -> {
                AlpakaStyle.click();
                AlpakaConfig.instance.chestValueSort = sort.next().ordinal();
                AlpakaConfig.save();
                dirty = true;
            }));
            if (hover) {
                tip = "Sorted by " + sort.title.toLowerCase() + ". Click to sort by " + sort.next().title.toLowerCase() + ".";
                hoverTip("sort");
            }
        }
        return tip;
    }

    /** A small switch: dark, lighter on hover, in the accent while on. */
    private static void chip(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, String label, boolean hover,
                             boolean on, boolean enabled) {
        int bg = on ? (AlpakaStyle.accent() & 0x00FFFFFF) | (hover ? 0x59000000 : 0x33000000)
                : hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.ROW;
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, CHIP_H, AlpakaStyle.FIELD_RADIUS, bg);
        if (label.isEmpty()) return;
        int color = !enabled ? AlpakaStyle.TEXT_MUTED : on ? (hover ? AlpakaStyle.TEXT : AlpakaStyle.accent())
                : hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT;
        ModernGuiUtils.centeredText(graphics, font, GuiFont.text(label), x + w / 2, y + 3, color);
    }

    /** The bar split by the most valuable items' shares; the rest together in gray. Returns a tooltip for the part under the mouse. */
    private static String drawBar(GuiGraphicsExtractor graphics, int x, int y, int w, Map<Entry, Integer> colors, int mx, int my) {
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, BAR_H, BAR_H / 2, AlpakaStyle.LINE);
        double total = summary.total();
        List<Entry> byValue = new ArrayList<>(summary.priced());
        byValue.sort(ChestValue.comparator(Sort.VALUE));
        // The top items each get a part, the rest one gray part together.
        List<Object[]> parts = new ArrayList<>();
        double rest = 0;
        for (int i = 0; i < byValue.size(); i++) {
            Entry e = byValue.get(i);
            if (i < PALETTE.length) parts.add(new Object[] {e, e.total(), colors.get(e)});
            else rest += e.total();
        }
        if (rest > 0) parts.add(new Object[] {null, rest, OTHER});

        String tip = null;
        boolean overBar = AlpakaStyle.inside(mx, my, x, y - 3, w, BAR_H + 6);
        int at = x;
        double sum = 0;
        for (int i = 0; i < parts.size(); i++) {
            Object[] p = parts.get(i);
            sum += (double) p[1];
            int end = x + (int) Math.round(w * Math.min(1.0, sum / total));
            int sx = at + (i > 0 ? 1 : 0);
            int sw = end - sx;
            at = end;
            if (sw <= 0) continue;
            int color = (int) p[2];
            boolean first = i == 0;
            boolean last = i == parts.size() - 1;
            if ((first || last) && sw >= BAR_H) {
                ModernGuiUtils.drawRoundedRect(graphics, sx, y, sw, BAR_H, BAR_H / 2, color);
                // Square on the side that meets the next part.
                if (!last) ModernGuiUtils.drawRect(graphics, sx + sw - BAR_H / 2, y, BAR_H / 2, BAR_H, color);
                if (!first) ModernGuiUtils.drawRect(graphics, sx, y, BAR_H / 2, BAR_H, color);
            } else {
                ModernGuiUtils.drawRect(graphics, sx, y, sw, BAR_H, color);
            }
            if (overBar && mx >= sx - 1 && mx < sx + sw) {
                Entry e = (Entry) p[0];
                double amount = (double) p[1];
                String name = e == null ? "Everything else" : (e.count > 1 ? e.count + "× " : "") + e.name;
                tip = name + ": " + Coins.compact(amount) + ", " + percent(amount / total) + " of the total.";
                if (e != null) pointed = e;
                hoverTip("bar:" + i);
            }
        }
        return tip;
    }

    /** The list, scrolled. Returns a tooltip for the line under the mouse. */
    private static String drawList(GuiGraphicsExtractor graphics, Font font, AbstractContainerScreen<?> c, List<Row> rows,
                                   Map<Entry, Integer> colors, int x, int y, int w, int h, int mx, int my, boolean narrow) {
        int offset = (int) Math.round(scroll);
        boolean overList = AlpakaStyle.inside(mx, my, x, y, w, h);
        Slot slotUnderMouse = ((AbstractContainerScreenAccessor) c).alpaka$hoveredSlot();
        Entry linked = slotUnderMouse == null ? null : summary.entryOf(c.getMenu().slots.indexOf(slotUnderMouse));
        String tip = null;
        graphics.enableScissor(x, y, x + w, y + h);
        int ry = y + 2 - offset;
        for (Row row : rows) {
            int rh = row.height();
            if (ry + rh > y && ry < y + h) {
                boolean hover = overList && AlpakaStyle.inside(mx, my, x + 4, ry, w - 8, rh);
                if (row.heading()) {
                    drawUnpricedHeading(graphics, font, x, ry, w, rh, hover, narrow);
                    if (hover) {
                        tip = summary.unpriced().size() + (summary.unpriced().size() == 1 ? " item has" : " items have")
                                + " no price on the Bazaar or the Auction House right now. Click to "
                                + (unpricedOpen ? "hide" : "show") + " them.";
                        hoverTip("unpriced");
                    }
                    int top = Math.max(ry, y);
                    hits.add(new Hit(x + 4, top, w - 8, Math.min(ry + rh, y + h) - top, () -> {
                        AlpakaStyle.click();
                        unpricedOpen = !unpricedOpen;
                    }));
                } else {
                    Entry e = row.entry();
                    if (hover) pointed = e;
                    drawEntry(graphics, font, e, colors.get(e), x, ry, w, rh, hover, e == linked, narrow);
                    if (hover) {
                        tip = tip(e);
                        hoverTip(e);
                    }
                    if (e.handle instanceof ItemStack stack && AlpakaConfig.instance.itemValueEnabled) {
                        int top = Math.max(ry, y);
                        int rowY = ry;
                        hits.add(new Hit(x + 4, top, w - 8, Math.min(ry + rh, y + h) - top, () -> {
                            AlpakaStyle.click();
                            openBreakdown(stack, rowY);
                        }));
                    }
                }
            }
            ry += rh;
        }
        graphics.disableScissor();
        AlpakaStyle.scrollFades(graphics, x + 1, y, w - 2, h, offset > 0, offset < maxScroll);
        return tip;
    }

    private static void drawEntry(GuiGraphicsExtractor graphics, Font font, Entry e, Integer color, int x, int y, int w, int h,
                                  boolean hover, boolean linked, boolean narrow) {
        if (hover || linked) ModernGuiUtils.drawRoundedRect(graphics, x + 4, y, w - 8, h, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
        // The line's share of the total as a faint bar behind it, readable even where the percentage has no room.
        double share = e.priced() ? summary.share(e) : 0;
        int shareW = (int) Math.round((w - 8) * Math.min(1.0, share));
        if (shareW >= 2) {
            int tint = color == null || color == OTHER ? AlpakaStyle.TEXT_MUTED : color;
            ModernGuiUtils.drawRoundedRect(graphics, x + 4, y + 1, shareW, h - 2, AlpakaStyle.ROW_RADIUS, MarketIcons.fade(tint, 0.13f));
        }
        if (linked) ModernGuiUtils.drawRoundedRect(graphics, x + 4, y + 3, 2, h - 6, 1, AlpakaStyle.accent());
        else if (color != null && color != OTHER) ModernGuiUtils.drawRoundedRect(graphics, x + 4, y + 5, 2, h - 10, 1, color);

        // Far enough in that a three-digit amount on the icon stays clear of the color mark.
        int ix = x + PAD + 3;
        ItemStack stack = e.handle instanceof ItemStack s ? s : ItemStack.EMPTY;
        if (!stack.isEmpty()) {
            graphics.item(stack, ix, y + 1);
            // The icon carries the amount of the whole line, not of the first stack.
            graphics.itemDecorations(font, stack, ix, y + 1, e.count > 1 ? compactCount(e.count) : "");
        }
        int ty = y + (h - 8) / 2;
        int right = x + w - PAD - 2;
        String value = !e.priced() ? "—" : (e.estimated ? "~" : "") + Coins.compact(e.total());
        int vw = GuiFont.width(font, value);
        graphics.text(font, GuiFont.text(value), right - vw, ty, e.priced() ? COIN : AlpakaStyle.TEXT_MUTED, false);
        right -= vw + 5;
        if (narrow) return;
        int labelX = ix + 21;
        // The share gives way before the name does: the name says what the line is.
        if (e.priced() && summary.total() > 0) {
            String percent = percent(share);
            int sw = GuiFont.width(font, percent);
            if (right - sw - 5 - labelX >= 40) {
                graphics.text(font, GuiFont.text(percent), right - sw, ty, AlpakaStyle.TEXT_MUTED, false);
                right -= sw + 5;
            }
        }
        int labelColor = !e.priced() || stack.isEmpty() ? AlpakaStyle.TEXT_MUTED
                : MarketScreen.vivid(e.id.equals("PET") ? lastColor(stack.getHoverName()) : MarketScreen.nameColor(stack.getHoverName()));
        graphics.text(font, GuiFont.text(AlpakaStyle.ellipsize(font, label(e, stack), right - labelX)), labelX, ty, labelColor, false);
    }

    /** The color of a name's last part: a pet's rarity, after its gray level. */
    private static int lastColor(Component name) {
        String plain = name.getString();
        int legacy = plain.lastIndexOf('§');
        if (legacy >= 0 && legacy + 1 < plain.length()) {
            ChatFormatting format = ChatFormatting.getByCode(plain.charAt(legacy + 1));
            net.minecraft.network.chat.TextColor color = format == null ? null : net.minecraft.network.chat.TextColor.fromLegacyFormat(format);
            if (color != null) return color.getValue();
        }
        int[] found = {MarketScreen.nameColor(name)};
        name.visit((style, text) -> {
            if (!text.isBlank() && style.getColor() != null) found[0] = style.getColor().getValue();
            return Optional.empty();
        }, Style.EMPTY);
        return found[0];
    }

    /**
     * A line's name, short enough for the column: the item's own name from Hypixel's list, without
     * the reforge, stars or pet level its stack shows (the tooltip has the full name), and for a
     * book what is written in it.
     */
    private static String label(Entry e, ItemStack stack) {
        if (e.id.equals("ENCHANTED_BOOK")) {
            ItemLore lore = stack.get(DataComponents.LORE);
            if (lore != null && !lore.lines().isEmpty()) {
                String first = SkyblockUtils.cleanColor(lore.lines().get(0).getString()).trim();
                if (!first.isEmpty()) return first;
            }
        }
        if (!e.id.equals("PET")) {
            String listed = ItemIcons.name(e.id);
            if (listed != null) return listed;
        }
        return e.name.replaceAll("^\\[Lvl \\d+\\]\\s*", "").replaceAll("[✪➊➋➌➍➎]", "").trim();
    }

    private static void drawUnpricedHeading(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h,
                                            boolean hover, boolean narrow) {
        if (hover) ModernGuiUtils.drawRoundedRect(graphics, x + 4, y, w - 8, h, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
        int color = hover ? AlpakaStyle.TEXT_SOFT : AlpakaStyle.TEXT_MUTED;
        AlpakaStyle.chevron(graphics, x + PAD + 4, y + h / 2, unpricedOpen, color);
        String label = (narrow ? "" : "No Price ") + "(" + summary.unpriced().size() + ")";
        graphics.text(font, GuiFont.text(AlpakaStyle.ellipsize(font, label, w - 2 * PAD - 12)), x + PAD + 12, y + (h - 8) / 2, color, false);
    }

    /** What a line's tooltip says: the arithmetic, where the price comes from, and what clicking does. */
    private static String tip(Entry e) {
        StringBuilder tip = new StringBuilder();
        tip.append(e.count > 1 ? Coins.full(e.count) + "× " : "").append(e.name).append(": ");
        if (!e.priced()) {
            tip.append("no price. Neither the Bazaar nor the Auction House has it right now.");
        } else {
            if (e.count > 1) tip.append(Coins.full(e.count)).append(" × ").append(Coins.compact(e.each)).append(" = ");
            tip.append(whole(e.total())).append(" coins");
            if (e.market == MarketKind.BAZAAR) {
                tip.append(AlpakaConfig.instance.itemValueBazaarPrice == 1 ? ", sold instantly on the Bazaar." : ", bought instantly on the Bazaar.");
            } else if (e.market == MarketKind.AUCTION_HOUSE) {
                tip.append(", the lowest BIN on the Auction House.");
            } else {
                tip.append(", the item and everything put on it.");
            }
            tip.append(' ').append(percent(summary.share(e))).append(" of the total.");
            if (e.estimated) tip.append(" Some parts have no price, so it is worth at least this.");
        }
        tip.append(e.slots.size() == 1 ? " In 1 slot." : " In " + e.slots.size() + " slots.");
        if (AlpakaConfig.instance.itemValueEnabled) tip.append(" Click for its Item Value breakdown.");
        return tip.toString();
    }

    /** Opens the Item Value popup on a line's item, beside the panel on the menu's side. */
    private static void openBreakdown(ItemStack stack, int rowY) {
        // The popup is 228 wide and opens 12 to the right of its anchor, or flips to the left when there is no room.
        double anchorX = leftSide ? panelX + panelW - 6 : panelX - 228 - 16;
        ItemValuePopup.open(screen, stack, anchorX, rowY + ROW_H / 2.0);
    }

    /** Why there is no list, in the list's place. */
    private static void drawMessage(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h) {
        String text;
        if (!AlpakaConfig.instance.allowApiCalls) {
            text = "Turn on Network Features (General › Main) to see prices.";
        } else if (ItemPrices.failed()) {
            text = "Couldn't load prices. Click to try again.";
            hits.add(new Hit(x, y, w, h, () -> {
                AlpakaStyle.click();
                ItemPrices.retry();
            }));
        } else if (waiting()) {
            text = "Loading prices…";
        } else {
            text = "Nothing here to value.";
        }
        List<FormattedText> lines = GuiFont.splitLines(font, text, w - 2 * PAD);
        int ly = y + (h - lines.size() * 10) / 2;
        for (FormattedText line : lines) {
            int lw = font.width(line);
            graphics.text(font, Language.getInstance().getVisualOrder(line), x + (w - lw) / 2, ly, AlpakaStyle.TEXT_MUTED, false);
            ly += 10;
        }
    }

    /** The bottom line: how many slots are counted, and where the prices come from, as SkyCofl asks. */
    private static String drawFooter(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int mx, int my, boolean narrow) {
        ModernGuiUtils.drawRect(graphics, x + PAD, y, w - 2 * PAD, 1, AlpakaStyle.LINE);
        int ty = y + (FOOTER_H - 8) / 2 + 1;
        String source = sourceText(narrow);
        int room = w - 2 * PAD;
        String shown = AlpakaStyle.ellipsize(font, source, room);
        int sw = GuiFont.width(font, shown);
        int sx = x + w - PAD - sw;
        graphics.text(font, GuiFont.text(shown), sx, ty, AlpakaStyle.DIVIDER, false);
        if (!narrow && !waiting()) {
            String slots = summary.stacks() == 1 ? "1 slot" : summary.stacks() + " slots";
            if (GuiFont.width(font, slots) + 8 < sx - (x + PAD)) {
                graphics.text(font, GuiFont.text(slots), x + PAD, ty, AlpakaStyle.DIVIDER, false);
            }
        }
        if (AlpakaStyle.inside(mx, my, sx - 2, y + 1, sw + 4, FOOTER_H - 1)) {
            hoverTip("source");
            return "Bazaar prices from Hypixel, lowest BINs from SkyCofl. Asked for again every few minutes while this is open.";
        }
        return null;
    }

    private static String sourceText(boolean narrow) {
        long at = ItemPrices.updatedAt();
        String names = narrow ? "SkyCofl" : "Hypixel · SkyCofl";
        if (at <= 0) return names;
        long minutes = (System.currentTimeMillis() - at) / 60_000L;
        return names + " · " + (minutes < 1 ? "now" : minutes + "m");
    }

    /** The slots of the line under the mouse, lit in its color, so it is clear where that item lies. */
    private static void drawSlotHighlights(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> c, Map<Entry, Integer> colors) {
        if (pointed == null) return;
        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) c;
        int left = a.alpaka$leftPos();
        int top = a.alpaka$topPos();
        Integer own = colors.get(pointed);
        int color = own == null || own == OTHER ? AlpakaStyle.accent() : own;
        List<Slot> slots = c.getMenu().slots;
        for (int index : pointed.slots) {
            if (index < 0 || index >= slots.size()) continue;
            Slot slot = slots.get(index);
            int sx = left + slot.x;
            int sy = top + slot.y;
            graphics.fill(sx, sy, sx + 16, sy + 16, MarketIcons.fade(color, 0.28f));
            graphics.outline(sx - 1, sy - 1, 18, 18, color);
        }
    }

    // ---- Tooltips -------------------------------------------------------------------------------

    private static void hoverTip(Object key) {
        hoveredTip = key;
    }

    private static void drawTooltip(GuiGraphicsExtractor graphics, Font font, int screenW, int screenH, int mx, int my,
                                    String tip, long now) {
        Object key = tip == null ? null : hoveredTip;
        hoveredTip = null;
        if (key == null || !key.equals(tipKey)) {
            tipKey = key;
            tipSince = now;
        }
        if (tip != null && now - tipSince >= AlpakaStyle.TOOLTIP_DELAY_MS) {
            AlpakaStyle.tooltip(graphics, font, screenW, screenH, mx, my, tip);
        }
    }

    // ---- Input ----------------------------------------------------------------------------------

    /** Whether the mouse is over the panel in this menu, which then hides what is under it. */
    public static boolean coversMouse(Screen in) {
        if (screen == null || in != screen || panelW <= 0) return false;
        double[] cursor = cursor();
        return AlpakaStyle.inside(cursor[0], cursor[1], panelX, panelY, panelW, panelH);
    }

    /**
     * A click on the panel does what it hits, and the menu never gets it: beside the menu, a click
     * would otherwise throw out the item held on the cursor. False for a click anywhere else.
     */
    static boolean mouseClicked(Screen in, double mx, double my, int button) {
        if (screen == null || in != screen || panelW <= 0 || !AlpakaStyle.inside(mx, my, panelX, panelY, panelW, panelH)) return false;
        heldButton = button;
        if (button != 0) return true;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (hit.contains(mx, my)) {
                hit.action().run();
                return true;
            }
        }
        // The header, where nothing else answers, is the handle to move the panel by.
        if (my < headerBottom) {
            long now = System.currentTimeMillis();
            if (now - lastHeaderPress < DOUBLE_CLICK_MS && moved()) {
                AlpakaConfig.instance.chestValueX = -1f;
                AlpakaConfig.instance.chestValueY = -1f;
                AlpakaConfig.save();
                lastHeaderPress = 0L;
                return true;
            }
            lastHeaderPress = now;
            dragging = true;
            grabX = (int) Math.floor(mx) - panelX;
            grabY = (int) Math.floor(my) - panelY;
        }
        return true;
    }

    static boolean mouseReleased(int button) {
        if (heldButton < 0 || button != heldButton) return false;
        heldButton = -1;
        if (dragging) {
            dragging = false;
            // Clamped as drawn, so a panel let go half off the screen is remembered where it shows.
            if (moved()) {
                Minecraft mc = Minecraft.getInstance();
                AlpakaConfig.instance.chestValueX = (float) panelX / mc.getWindow().getGuiScaledWidth();
                AlpakaConfig.instance.chestValueY = (float) panelY / mc.getWindow().getGuiScaledHeight();
            }
            AlpakaConfig.save();
        }
        return true;
    }

    /** Whether the player has moved the panel away from its place beside the menu. */
    private static boolean moved() {
        return AlpakaConfig.instance.chestValueX >= 0 && AlpakaConfig.instance.chestValueY >= 0;
    }

    private static boolean overHit(int mx, int my) {
        for (Hit hit : hits) if (hit.contains(mx, my)) return true;
        return false;
    }

    static boolean mouseDragged(int button) {
        return heldButton >= 0 && button == heldButton;
    }

    /** The wheel over the panel scrolls its list. */
    static boolean mouseScrolled(Screen in, double mx, double my, double amount) {
        if (screen == null || in != screen || panelW <= 0 || !AlpakaStyle.inside(mx, my, panelX, panelY, panelW, panelH)) return false;
        if (amount != 0 && maxScroll > 0) scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - amount * 3 * ROW_H));
        return true;
    }

    // ---- Helpers --------------------------------------------------------------------------------

    private static String whole(double coins) {
        return Coins.full(Math.rint(coins));
    }

    private static String percent(double share) {
        if (share < 0.005) return "<1%";
        return Math.round(share * 100) + "%";
    }

    /** An amount short enough for an item's corner: "64", "999", "1.2k". */
    private static String compactCount(int count) {
        return count < 1000 ? Integer.toString(count) : Coins.compact(count);
    }

    private static double[] cursor() {
        Minecraft mc = Minecraft.getInstance();
        return new double[] {mc.mouseHandler.getScaledXPos(mc.getWindow()), mc.mouseHandler.getScaledYPos(mc.getWindow())};
    }

    /** Text in its own colors and the menu font, cut with an ellipsis to fit. Returns the width drawn. */
    private static int styled(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int maxW) {
        Component restyled = GuiFont.restyle(text);
        if (font.width(restyled) <= maxW) {
            graphics.text(font, restyled, x, y, 0xFFFFFFFF, false);
            return font.width(restyled);
        }
        Component dots = GuiFont.text("…");
        FormattedText cut = font.substrByWidth(restyled, Math.max(0, maxW - font.width(dots)));
        graphics.text(font, Language.getInstance().getVisualOrder(cut), x, y, 0xFFFFFFFF, false);
        graphics.text(font, dots, x + font.width(cut), y, AlpakaStyle.TEXT_MUTED, false);
        return font.width(cut) + font.width(dots);
    }

    // ---- For the screenshot tour ----------------------------------------------------------------

    /** The middle of the list's line at this index, as last drawn, for the screenshot tour. */
    public static int[] devRowPoint(int index) {
        int y = listY + 2 - (int) Math.round(scroll) + index * ROW_H + ROW_H / 2;
        return new int[] {panelX + panelW / 2, y};
    }

    /** A left click at this point, as the menu would hand it over, for the screenshot tour. */
    public static void devClick(int[] point) {
        if (screen != null) mouseClicked(screen, point[0], point[1], 0);
        mouseReleased(0);
    }
}
