package net.alpaka.addons.features.itemsearch;

import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.EditableText;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.compat.MacOSInputFixesCompat;
import net.alpaka.addons.compliance.Outbound;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.chestvalue.ChestValuePanel;
import net.alpaka.addons.features.itemvalue.ItemPrices;
import net.alpaka.addons.features.market.ItemIcons;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.alpaka.addons.mixin.AbstractContainerScreenAccessor;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Item Search, beside every menu, the way item list mods have it: a search bar under the menu and
 * the items that match on the right of the screen, with no panel behind them. Nothing shows until
 * something is typed. "All" searches every SkyBlock item, "Mine" what the player owns as Item
 * Search remembers it, with the amounts; a sum typed into the bar is worked out above it.
 *
 * Clicking an item opens its view in the same place: how it is made (crafting, the Forge, NPC
 * shops, trades, Kat, mob drops, from the NotEnoughUpdates repository) and what it is used in.
 * Nothing here sends anything, except the command a click on one of the player's own items opens
 * its place with, once per click.
 */
public final class ItemListOverlay {
    private ItemListOverlay() {}

    private static final int CELL = 18;
    private static final int EDGE = 4;
    private static final int GAP = 6;
    private static final int TOP_H = 16;
    private static final int BAR_H = 16;
    private static final int BAR_MIN_W = 130;
    private static final int BAR_MAX_W = 200;
    private static final int MIN_COLS = 4;
    private static final int MAX_PLACES = 8;

    /** Which items the list shows. */
    enum Mode {
        ALL("All"), MINE("Mine");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    /** One opened item: which tab, which of its recipes, which page of what it is used in. */
    private static final class View {
        final String id;
        boolean usedTab = false;
        int recipe = 0;
        int usedPage = 0;

        View(String id) {
            this.id = id;
        }
    }

    private static Mode mode = Mode.ALL;
    /** The search, kept from menu to menu as an item list's is. */
    private static final EditableText query = new EditableText(64);
    private static boolean focused = false;
    private static int page = 0;
    private static long caretFrom = 0L;
    private static long copiedAt = 0L;
    private static final Deque<View> views = new ArrayDeque<>();
    /** For the screenshot tour, which is not on SkyBlock. */
    private static boolean devForce = false;

    // The layout of the last frame, for the clicks that follow it.
    private static Screen drawnOn = null;
    private static int regionX, regionY, regionW, regionH;
    private static int barX, barY, barW;
    private static final List<Hit> hits = new ArrayList<>();
    /** Where the mouse wheel steps recipes or pages: the item view's content, or the list. */
    private static Runnable wheelUp, wheelDown;

    /** Something to click: a left click does one thing, a right click (when there is one) another. */
    private record Hit(int x, int y, int w, int h, Runnable left, Runnable right) {}

    // What the list holds, worked out again only when the search or the mode changes.
    private static String builtFor = null;
    private static List<?> items = List.of();
    private static List<ItemSearchResults.Entry> owned = List.of();
    private static final Map<String, Long> ownedById = new HashMap<>();
    private static long ownedAt = 0L;
    private static Double sum = null;
    private static String sumFor = null;

    // ---- State ----------------------------------------------------------------------------------

    /** Whether the bar has the keyboard in this screen, so a key of ours types instead of acting. */
    public static boolean typing(Screen screen) {
        return focused && screen != null && screen == drawnOn;
    }

    static boolean enabled() {
        return AlpakaConfig.instance.itemSearchEnabled && (devForce || SkyblockUtils.isOnSkyblock());
    }

    /** The Item Search key: the bar takes the keyboard, with the item under the mouse searched for among the player's. */
    static void focus(String search) {
        if (search != null) {
            query.setText(search);
            mode = Mode.MINE;
            page = 0;
            views.clear();
        }
        query.selectAll();
        focused = true;
        caretFrom = System.currentTimeMillis();
    }

    static void menuClosed(Screen screen) {
        if (screen == drawnOn) {
            focused = false;
            drawnOn = null;
        }
    }

    /** For the screenshot tour: the list with this search and mode, outside SkyBlock too. */
    public static void devShow(String search, boolean mine, boolean focus) {
        devForce = true;
        query.setText(search == null ? "" : search);
        mode = mine ? Mode.MINE : Mode.ALL;
        page = 0;
        views.clear();
        focused = focus;
        caretFrom = System.currentTimeMillis();
        builtFor = null;
        ownedAt = 0L;
    }

    /** For the screenshot tour: the view of this item, on its recipes or on what it is used in. */
    public static void devOpenItem(String id, boolean usedIn) {
        devForce = true;
        focused = false;
        View view = new View(id);
        view.usedTab = usedIn;
        views.push(view);
    }

    private static void refreshOwned() {
        long now = System.currentTimeMillis();
        if (now - ownedAt < 2_000L) return;
        ownedAt = now;
        owned = ItemSearchResults.collect(null);
        ownedById.clear();
        for (ItemSearchResults.Entry entry : owned) {
            if (entry.id != null) ownedById.merge(entry.id, entry.total, Long::sum);
        }
        builtFor = null;
    }

    private static List<?> items() {
        List<ItemCatalog.Item> catalog = ItemCatalog.all();
        String key = mode + "\u0000" + query.getText() + "\u0000" + (catalog == null ? 0 : catalog.size());
        if (key.equals(builtFor)) return items;
        builtFor = key;
        String q = query.getText().trim();
        // Nothing typed, nothing shown: the list is a search, not a wall of icons.
        if (q.isEmpty() || sum() != null) items = List.of();
        else if (mode == Mode.MINE) items = ItemSearchResults.filter(owned, q, ItemSearchResults.Sort.NAME);
        else items = catalog == null ? List.of() : ItemCatalog.search(catalog, q, Integer.MAX_VALUE);
        return items;
    }

    private static Double sum() {
        String text = query.getText();
        if (!text.equals(sumFor)) {
            sumFor = text;
            sum = SpotlightMath.evaluate(text, ItemListOverlay::priceOf);
        }
        return sum;
    }

    private static Double priceOf(String name) {
        ItemCatalog.Item item = ItemCatalog.find(name);
        if (item == null) return null;
        ItemPrices.Quote quote = ItemPrices.quote(item.id());
        return quote == null ? null : quote.price();
    }

    private static void textChanged() {
        page = 0;
        views.clear();
    }

    // ---- Drawing --------------------------------------------------------------------------------

    /** Draws the list and the bar beside the menu, after the menu and before its tooltips. */
    public static void render(GuiGraphicsExtractor graphics, Screen in) {
        Minecraft mc = Minecraft.getInstance();
        if (!(in instanceof AbstractContainerScreen<?> container) || mc.gui.screen() != in || !enabled()) {
            if (in == drawnOn) drawnOn = null;
            return;
        }
        if (drawnOn != in) {
            drawnOn = in;
            ownedAt = 0L;
        }
        if (AlpakaConfig.instance.allowApiCalls) ItemPrices.refresh();
        refreshOwned();
        Font font = mc.font;
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        int mx = (int) Math.floor(mc.mouseHandler.getScaledXPos(mc.getWindow()));
        int my = (int) Math.floor(mc.mouseHandler.getScaledYPos(mc.getWindow()));

        AbstractContainerScreenAccessor a = (AbstractContainerScreenAccessor) container;
        int menuLeft = a.alpaka$leftPos();
        int menuRight = menuLeft + a.alpaka$imageWidth();
        int menuBottom = a.alpaka$topPos() + a.alpaka$imageHeight();
        int x0 = Math.max(menuRight, ChestValuePanel.rightEdge(in)) + GAP;
        int x1 = screenW - EDGE;
        int cols = (x1 - x0) / CELL;
        hits.clear();
        wheelUp = wheelDown = null;
        if (cols < MIN_COLS) {
            regionW = 0;
            barW = 0;
            return;
        }

        // The bar under the menu, as wide as the menu; where the menu reaches the bottom, under the list.
        barW = Math.max(BAR_MIN_W, Math.min(BAR_MAX_W, a.alpaka$imageWidth()));
        barX = menuLeft + (a.alpaka$imageWidth() - barW) / 2;
        barY = screenH - EDGE - BAR_H;
        boolean barUnderList = barY < menuBottom + 2;
        if (barUnderList) {
            barW = cols * CELL;
            barX = x1 - barW;
        }
        int gridW = cols * CELL;
        int gx = x1 - gridW;
        int top = EDGE;
        int bottom = barUnderList ? barY - 4 : screenH - EDGE;
        regionX = gx;
        regionY = top;
        regionW = gridW;
        regionH = bottom - top;

        graphics.nextStratum();
        List<Component> tooltip = views.isEmpty()
                ? drawList(graphics, font, in, cols, gx, top, bottom, mx, my)
                : drawView(graphics, font, views.peek(), gx, top, gridW, bottom, mx, my);
        drawBar(graphics, font, mx, my);
        if (tooltip != null) graphics.setComponentTooltipForNextFrame(font, tooltip, mx, my);
    }

    private static List<Component> drawList(GuiGraphicsExtractor graphics, Font font, Screen in, int cols, int gx, int top,
                                            int bottom, int mx, int my) {
        int gridW = cols * CELL;
        List<?> list = items();
        // On a narrow screen the pages go on a line of their own under the two modes.
        int topH = list.isEmpty() || gridW >= topWidth(font) ? TOP_H : TOP_H * 2;
        int gy = top + topH;
        int rows = Math.max(1, (bottom - gy) / CELL);
        int perPage = cols * rows;
        int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(pages - 1, page));
        drawTop(graphics, font, gx, top, gridW, topH, list.isEmpty() ? 0 : pages, mx, my);
        wheelUp = () -> page = Math.max(0, page - 1);
        wheelDown = () -> page = Math.min(pages - 1, page + 1);

        List<Component> tooltip = null;
        int from = page * perPage;
        for (int i = 0; i < perPage && from + i < list.size(); i++) {
            int cx = gx + (i % cols) * CELL;
            int cy = gy + (i / cols) * CELL;
            Object item = list.get(from + i);
            boolean hover = AlpakaStyle.inside(mx, my, cx, cy, CELL, CELL);
            if (hover) ModernGuiUtils.drawRoundedRect(graphics, cx, cy, CELL, CELL, 2, AlpakaStyle.ROW_HOVER);
            if (item instanceof ItemSearchResults.Entry entry) {
                graphics.fakeItem(entry.stack, cx + 1, cy + 1);
                if (entry.total > 1) drawCount(graphics, font, ItemSearchMenus.shortCount(entry.total), cx, cy);
                if (hover) tooltip = ownedTooltip(entry, in);
                hits.add(new Hit(cx, cy, CELL, CELL, () -> goTo(entry, entry.best(), in),
                        entry.id == null ? null : () -> open(entry.id)));
            } else if (item instanceof ItemCatalog.Item catalog) {
                drawIcon(graphics, font, catalog.id(), catalog.name(), cx + 1, cy + 1);
                if (hover) tooltip = itemTooltip(catalog.id(), 1, true);
                hits.add(new Hit(cx, cy, CELL, CELL, () -> open(catalog.id()), () -> openWiki(catalog.id(), in)));
            }
        }
        if (list.isEmpty()) wrapped(graphics, font, emptyMessage(), gx, gy + 2, gridW, AlpakaStyle.TEXT_MUTED, 4);
        return tooltip;
    }

    /** An item's icon by its repository id; coins as a gold nugget, an unknown one as a question mark. */
    private static void drawIcon(GuiGraphicsExtractor graphics, Font font, String id, String name, int x, int y) {
        if (id.equals("SKYBLOCK_COIN") && coin == null) coin = new ItemStack(Items.GOLD_NUGGET);
        ItemStack icon = id.equals("SKYBLOCK_COIN") ? coin : ItemIcons.forItem(id, name);
        if (!icon.isEmpty()) graphics.fakeItem(icon, x, y);
        else ModernGuiUtils.centeredText(graphics, font, GuiFont.text("?"), x + 8, y + 4, AlpakaStyle.TEXT_MUTED);
    }

    /** Made on first use: an ItemStack before the world's components are bound throws, and this class loads on the title screen. */
    private static ItemStack coin = null;

    /**
     * An amount in the cell's corner, smaller than a stack's: cells sit edge to edge with no slot
     * around them, and "145k" at full size runs into the next one.
     */
    private static void drawCount(GuiGraphicsExtractor graphics, Font font, String text, int cx, int cy) {
        float scale = 0.75f;
        int w = font.width(text);
        graphics.pose().pushMatrix();
        graphics.pose().translate(cx + CELL - 1 - w * scale, cy + CELL - 1 - 7 * scale);
        graphics.pose().scale(scale, scale);
        graphics.text(font, Component.literal(text), 0, 0, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
    }

    /** How wide the modes and the pages are on one line, with the widest page label there can be. */
    private static int topWidth(Font font) {
        int w = 0;
        for (Mode m : Mode.values()) w += GuiFont.width(font, m.label) + 8;
        return w + GuiFont.width(font, "99/99") + 2 * 12 + 8;
    }

    /** The two modes on the left, the pages on the right (none for {@code pages} 0). */
    private static void drawTop(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h, int pages, int mx, int my) {
        int ty = y + (TOP_H - 8) / 2 - 1;
        int tx = x + 2;
        for (Mode m : Mode.values()) {
            int tw = GuiFont.width(font, m.label);
            boolean hover = AlpakaStyle.inside(mx, my, tx - 2, y, tw + 4, TOP_H - 2);
            int color = m == mode ? AlpakaStyle.accent() : hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_MUTED;
            graphics.text(font, GuiFont.text(m.label), tx, ty, color, true);
            hits.add(new Hit(tx - 2, y, tw + 4, TOP_H - 2, () -> setMode(m), null));
            tx += tw + 8;
        }
        if (pages <= 0) return;
        if (h > TOP_H) {
            y += TOP_H;
            ty += TOP_H;
        }
        drawPager(graphics, font, x, y, w, ty, page, pages, mx, my, () -> page = Math.max(0, page - 1),
                () -> page = Math.min(pages - 1, page + 1));
    }

    /** "‹ 2/5 ›" at the right end of a line. */
    private static void drawPager(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int ty, int at, int count,
                                  int mx, int my, Runnable back, Runnable forward) {
        String label = (at + 1) + "/" + count;
        int lw = GuiFont.width(font, label);
        int size = 12;
        int right = x + w;
        int[] next = {right - size, y + 1, size, size};
        int lx = right - size - 2 - lw;
        int[] prev = {lx - 2 - size, y + 1, size, size};
        AlpakaStyle.chevronButton(graphics, prev[0], prev[1], size, true, hit(prev, mx, my), at > 0);
        graphics.text(font, GuiFont.text(label), lx, ty, AlpakaStyle.TEXT_SOFT, true);
        AlpakaStyle.chevronButton(graphics, next[0], next[1], size, false, hit(next, mx, my), at < count - 1);
        hits.add(new Hit(prev[0], prev[1], size, size, () -> {
            if (at > 0) {
                AlpakaStyle.click();
                back.run();
            }
        }, null));
        hits.add(new Hit(next[0], next[1], size, size, () -> {
            if (at < count - 1) {
                AlpakaStyle.click();
                forward.run();
            }
        }, null));
    }

    private static void drawBar(GuiGraphicsExtractor graphics, Font font, int mx, int my) {
        boolean hover = AlpakaStyle.inside(mx, my, barX, barY, barW, BAR_H);
        String placeholder = mode == Mode.MINE ? "Search your items" : "Search items, or type a sum";
        AlpakaStyle.textField(graphics, font, barX, barY, barW, BAR_H, query, null, placeholder, focused, hover, caretFrom);
        Double result = sum();
        if (result == null) return;
        // The result just above the bar, as a calculator's display.
        boolean copied = System.currentTimeMillis() - copiedAt < 1500;
        String text = "= " + SpotlightMath.format(result);
        String hint = copied ? "Copied" : "Enter copies";
        int w = GuiFont.width(font, text) + 8 + GuiFont.width(font, hint) + 10;
        int px = barX + barW - w;
        int py = barY - 15;
        ModernGuiUtils.drawRoundedRect(graphics, px, py, w, 13, AlpakaStyle.FIELD_RADIUS, AlpakaStyle.PANEL);
        graphics.text(font, GuiFont.text(text), px + 5, py + 3, AlpakaStyle.TEXT, false);
        graphics.text(font, GuiFont.text(hint), px + 5 + GuiFont.width(font, text) + 8, py + 3, AlpakaStyle.TEXT_MUTED, false);
    }

    private static String emptyMessage() {
        if (query.getText().isBlank() || sum() != null) return "";
        if (mode == Mode.MINE) {
            return owned.isEmpty() ? "Open your Ender Chest, backpacks and sacks once to add them." : "None of your items match.";
        }
        if (ItemCatalog.all() == null) {
            return AlpakaConfig.instance.allowApiCalls ? "Loading the item list…" : "The item list needs Network Features.";
        }
        return "No item matches.";
    }

    /** Text broken to the width, at most {@code maxLines} lines, with a shadow since nothing is behind it. Returns the height drawn. */
    private static int wrapped(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int w, int color, int maxLines) {
        if (text == null || text.isEmpty()) return 0;
        List<FormattedCharSequence> lines = font.split(GuiFont.text(text), w);
        int n = Math.min(maxLines, lines.size());
        for (int i = 0; i < n; i++) graphics.text(font, lines.get(i), x, y + i * 10, color, true);
        return n * 10;
    }

    /** One line of text centred in the width, with a shadow. */
    private static void centered(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int w, int color) {
        String cut = AlpakaStyle.ellipsize(font, text, w);
        graphics.text(font, GuiFont.text(cut), x + (w - GuiFont.width(font, cut)) / 2, y, color, true);
    }

    // ---- The item view --------------------------------------------------------------------------

    /** The opened item where the list was: its name and price, then how it is made or what it is used in. */
    private static List<Component> drawView(GuiGraphicsExtractor graphics, Font font, View v, int x, int y, int w, int bottom,
                                            int mx, int my) {
        Screen in = drawnOn;
        String name = ItemRecipes.displayName(v.id);
        String tier = ItemRecipes.tierOf(v.id);
        List<Component> tooltip = null;

        // The head: back, the item, its name, and the wiki.
        int[] back = {x, y + 2, 12, 14};
        AlpakaStyle.chevronButton(graphics, back[0], back[1] + 1, 12, true, hit(back, mx, my), true);
        hits.add(new Hit(back[0], back[1], back[2], back[3], ItemListOverlay::back, null));
        drawIcon(graphics, font, v.id, name, x + 14, y + 1);
        String wiki = "Wiki ↗";
        int wikiW = GuiFont.width(font, wiki);
        int[] wikiBox = {x + w - wikiW - 2, y, wikiW + 4, 12};
        boolean wikiHover = hit(wikiBox, mx, my);
        int nameX = x + 33;
        graphics.text(font, GuiFont.text(AlpakaStyle.ellipsize(font, name, x + w - nameX)), nameX, y + 1,
                ItemCatalog.tierColor(tier), true);
        StringBuilder sub = new StringBuilder(ItemCatalog.tierLabel(tier));
        String price = priceLabel(v.id);
        if (price != null) sub.append(sub.isEmpty() ? "" : " · ").append(price);
        Long have = ownedById.get(v.id);
        if (have != null && have > 0) sub.append(sub.isEmpty() ? "" : " · ").append("have ").append(ItemSearchMenus.shortCount(have));
        graphics.text(font, GuiFont.text(AlpakaStyle.ellipsize(font, sub.toString(), x + w - nameX)), nameX, y + 11,
                AlpakaStyle.TEXT_MUTED, true);
        y += 24;

        // The tabs, and the wiki at the right end of their line.
        List<ItemRecipes.Recipe> used = ItemRecipes.usedIn(v.id);
        Set<String> usedOutputs = new LinkedHashSet<>();
        if (used != null) for (ItemRecipes.Recipe r : used) usedOutputs.add(r.output().id());
        int tx = x + 2;
        String[] labels = {"Recipe", used == null || usedOutputs.isEmpty() ? "Used in" : "Used in " + usedOutputs.size()};
        for (int i = 0; i < 2; i++) {
            boolean chosen = (i == 1) == v.usedTab;
            int tw = GuiFont.width(font, labels[i]);
            int[] box = {tx - 2, y, tw + 4, 12};
            int color = chosen ? AlpakaStyle.accent() : hit(box, mx, my) ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_MUTED;
            graphics.text(font, GuiFont.text(labels[i]), tx, y + 2, color, true);
            boolean usedTab = i == 1;
            hits.add(new Hit(box[0], box[1], box[2], box[3], () -> {
                if (v.usedTab != usedTab) AlpakaStyle.click();
                v.usedTab = usedTab;
            }, null));
            tx += tw + 8;
        }
        if (tx + 4 <= wikiBox[0]) {
            wikiBox[1] = y;
        } else {
            // No room beside the tabs: on its own line.
            y += 12;
            wikiBox[1] = y;
            wikiBox[0] = x + 2 - 2;
        }
        graphics.text(font, GuiFont.text(wiki), wikiBox[0] + 2, wikiBox[1] + 2, wikiHover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT, true);
        hits.add(new Hit(wikiBox[0], wikiBox[1], wikiBox[2], wikiBox[3], () -> openWiki(v.id, in), null));
        y += 16;

        if (v.usedTab) {
            List<Component> tip = drawUsedIn(graphics, font, v, used, new ArrayList<>(usedOutputs), x, y, w, bottom, mx, my);
            if (tip != null) tooltip = tip;
        } else {
            List<Component> tip = drawRecipe(graphics, font, v, x, y, w, bottom, mx, my);
            if (tip != null) tooltip = tip;
        }
        return tooltip;
    }

    private static List<Component> drawRecipe(GuiGraphicsExtractor graphics, Font font, View v, int x, int y, int w, int bottom,
                                              int mx, int my) {
        ItemRecipes.Info info = ItemRecipes.get(v.id);
        if (info == null) {
            String msg = ItemRecipes.state(v.id) == ItemRecipes.State.FAILED ? "Could not load the recipe; trying again soon."
                    : "Loading the recipe…";
            wrapped(graphics, font, msg, x + 2, y, w - 4, AlpakaStyle.TEXT_MUTED, 3);
            return null;
        }
        List<ItemRecipes.Recipe> recipes = info.recipes();
        if (recipes.isEmpty()) {
            String msg = NeuRepo.state() == NeuRepo.State.LOADING
                    ? "No recipe in the item's own file. Still reading the shops and drops…"
                    : "No recipe known. The wiki may know where it comes from.";
            int h = wrapped(graphics, font, msg, x + 2, y, w - 4, AlpakaStyle.TEXT_MUTED, 4);
            if (!info.craftText().isBlank()) wrapped(graphics, font, info.craftText(), x + 2, y + h + 4, w - 4, AlpakaStyle.TEXT_MUTED, 3);
            return null;
        }
        int count = recipes.size();
        v.recipe = Math.floorMod(v.recipe, count);
        ItemRecipes.Recipe recipe = recipes.get(v.recipe);
        wheelUp = () -> v.recipe--;
        wheelDown = () -> v.recipe++;
        List<Component> tooltip = null;

        if (count > 1) {
            drawPager(graphics, font, x, y, w, y + 3, v.recipe, count, mx, my, () -> v.recipe--, () -> v.recipe++);
        }
        // Which way: the kind, and the NPC or mob, on the pager's line where it fits.
        String kind = recipe.kind().label + (recipe.source() == null ? "" : " · " + recipe.source());
        int pagerW = count > 1 ? GuiFont.width(font, (v.recipe + 1) + "/" + count) + 2 * 12 + 8 : 0;
        if (GuiFont.width(font, kind) <= w - 4 - pagerW) {
            graphics.text(font, GuiFont.text(kind), x + 2, y + 3, AlpakaStyle.TEXT_SOFT, true);
            y += 16;
        } else {
            if (count > 1) y += 15;
            y += wrapped(graphics, font, kind, x + 2, y, w - 4, AlpakaStyle.TEXT_SOFT, 2) + 4;
        }

        int cx = x + w / 2;
        if (recipe.kind() == ItemRecipes.Kind.CRAFTING) {
            int gx = cx - CELL * 3 / 2;
            for (int i = 0; i < 9; i++) {
                List<Component> tip = drawSlot(graphics, font, v, gx + (i % 3) * CELL, y + (i / 3) * CELL, recipe.grid().get(i), mx, my);
                if (tip != null) tooltip = tip;
            }
            y += CELL * 3 + 2;
        } else if (!recipe.inputs().isEmpty()) {
            List<ItemRecipes.Stack> inputs = recipe.inputs();
            int perRow = Math.max(1, Math.min(inputs.size(), w / CELL));
            int rows = (inputs.size() + perRow - 1) / perRow;
            for (int i = 0; i < inputs.size(); i++) {
                int inRow = Math.min(perRow, inputs.size() - (i / perRow) * perRow);
                int rx = cx - inRow * CELL / 2;
                List<Component> tip = drawSlot(graphics, font, v, rx + (i % perRow) * CELL, y + (i / perRow) * CELL, inputs.get(i), mx, my);
                if (tip != null) tooltip = tip;
            }
            y += rows * CELL + 2;
        }
        if (recipe.kind() != ItemRecipes.Kind.DROP) {
            drawDownArrow(graphics, cx, y);
            y += 10;
        }
        List<Component> tip = drawSlot(graphics, font, v, cx - CELL / 2, y, recipe.output(), mx, my);
        if (tip != null) tooltip = tip;
        y += CELL + 3;

        // What else there is to know, as far as there is room.
        List<String> notes = new ArrayList<>();
        if (recipe.kind() == ItemRecipes.Kind.DROP && !recipe.note().isBlank()) notes.add("Chance: " + recipe.note());
        if (recipe.seconds() > 0) notes.add("Takes " + ItemRecipes.duration(recipe.seconds()));
        String cost = costLine(recipe);
        if (cost != null) notes.add(cost);
        for (String note : notes) {
            centered(graphics, font, note, x, y, w, AlpakaStyle.TEXT_SOFT);
            y += 10;
        }
        y += 3;
        List<String> extra = new ArrayList<>(recipe.extra());
        if (recipe.kind() == ItemRecipes.Kind.CRAFTING && !info.craftText().isBlank()) extra.add(info.craftText());
        for (String line : extra) {
            int room = (bottom - y) / 10;
            if (room <= 0) break;
            y += wrapped(graphics, font, line, x + 2, y, w - 4, AlpakaStyle.TEXT_MUTED, room);
        }
        return tooltip;
    }

    /** What the parts cost at today's prices, or null when nothing in it has a price. */
    private static String costLine(ItemRecipes.Recipe recipe) {
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
        if (cost <= 0) return null;
        return (priced ? "Cost: " : "Cost: at least ") + ItemSearchMenus.shortCount(Math.round(cost));
    }

    private static void drawDownArrow(GuiGraphicsExtractor graphics, int cx, int y) {
        ModernGuiUtils.drawRect(graphics, cx - 1, y, 2, 5, AlpakaStyle.TEXT_MUTED);
        for (int i = 0; i < 4; i++) ModernGuiUtils.drawRect(graphics, cx - 1 - i, y + 7 - i, 2 + i * 2, 1, AlpakaStyle.TEXT_MUTED);
    }

    /** One slot of a recipe: a soft square with the item and its amount; a click opens that item. */
    private static List<Component> drawSlot(GuiGraphicsExtractor graphics, Font font, View v, int x, int y, ItemRecipes.Stack stack,
                                            int mx, int my) {
        boolean hover = stack != null && AlpakaStyle.inside(mx, my, x, y, CELL, CELL);
        ModernGuiUtils.drawRoundedRect(graphics, x + 1, y + 1, CELL - 2, CELL - 2, 2, hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.FIELD);
        if (stack == null) return null;
        String id = stack.id();
        drawIcon(graphics, font, id, ItemRecipes.displayName(id), x + 1, y + 1);
        if (stack.count() != 1) {
            String amount = stack.count() == Math.rint(stack.count()) ? ItemSearchMenus.shortCount((long) stack.count())
                    : String.valueOf(stack.count());
            drawCount(graphics, font, amount, x, y);
        }
        boolean coin = id.equals("SKYBLOCK_COIN");
        boolean self = id.equals(v.id);
        if (!coin) {
            Screen in = drawnOn;
            hits.add(new Hit(x, y, CELL, CELL, self ? null : () -> open(id), () -> openWiki(id, in)));
        }
        if (!hover) return null;
        if (coin) return List.of(Component.literal(SpotlightMath.format(stack.count()) + " Coins").withStyle(ChatFormatting.GOLD));
        return itemTooltip(id, stack.count(), !self);
    }

    private static List<Component> drawUsedIn(GuiGraphicsExtractor graphics, Font font, View v, List<ItemRecipes.Recipe> used,
                                              List<String> outputs, int x, int y, int w, int bottom, int mx, int my) {
        if (used == null) {
            String msg = NeuRepo.state() == NeuRepo.State.OFF ? "Needs Network Features."
                    : NeuRepo.state() == NeuRepo.State.FAILED ? "Could not load the recipes; trying again soon."
                    : "Reading every recipe…";
            wrapped(graphics, font, msg, x + 2, y, w - 4, AlpakaStyle.TEXT_MUTED, 3);
            return null;
        }
        if (outputs.isEmpty()) {
            wrapped(graphics, font, "Not used in any recipe.", x + 2, y, w - 4, AlpakaStyle.TEXT_MUTED, 3);
            return null;
        }
        int cols = Math.max(1, w / CELL);
        int rows = Math.max(1, (bottom - y - TOP_H) / CELL);
        int perPage = cols * rows;
        int pages = (outputs.size() + perPage - 1) / perPage;
        v.usedPage = Math.max(0, Math.min(pages - 1, v.usedPage));
        if (pages > 1) {
            drawPager(graphics, font, x, y, w, y + 3, v.usedPage, pages, mx, my, () -> v.usedPage--, () -> v.usedPage++);
            y += TOP_H;
        }
        wheelUp = () -> v.usedPage = Math.max(0, v.usedPage - 1);
        wheelDown = () -> v.usedPage = Math.min(pages - 1, v.usedPage + 1);
        List<Component> tooltip = null;
        int from = v.usedPage * perPage;
        Screen in = drawnOn;
        for (int i = 0; i < perPage && from + i < outputs.size(); i++) {
            String id = outputs.get(from + i);
            int cx = x + (i % cols) * CELL;
            int cy = y + (i / cols) * CELL;
            boolean hover = AlpakaStyle.inside(mx, my, cx, cy, CELL, CELL);
            if (hover) ModernGuiUtils.drawRoundedRect(graphics, cx, cy, CELL, CELL, 2, AlpakaStyle.ROW_HOVER);
            drawIcon(graphics, font, id, ItemRecipes.displayName(id), cx + 1, cy + 1);
            hits.add(new Hit(cx, cy, CELL, CELL, () -> open(id), () -> openWiki(id, in)));
            if (hover) tooltip = itemTooltip(id, 1, true);
        }
        return tooltip;
    }

    private static String priceLabel(String id) {
        ItemPrices.Quote quote = ItemPrices.quote(id);
        if (quote == null || Double.isNaN(quote.price())) return null;
        return ItemSearchMenus.shortCount(Math.round(quote.price())) + " coins";
    }

    // ---- Tooltips -------------------------------------------------------------------------------

    /** An item by its id: name in its rarity's color, rarity, price, how many the player has, and what a click does. */
    private static List<Component> itemTooltip(String id, double count, boolean clickable) {
        List<Component> lines = new ArrayList<>();
        String tier = ItemRecipes.tierOf(id);
        lines.add(Component.literal(ItemRecipes.displayName(id)).withStyle(s -> s.withColor(ItemCatalog.tierColor(tier) & 0xFFFFFF)));
        if (tier != null) lines.add(Component.literal(ItemCatalog.tierLabel(tier)).withStyle(ChatFormatting.DARK_GRAY));
        if (count != 1) {
            lines.add(Component.literal("Amount: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(SpotlightMath.format(count)).withStyle(ChatFormatting.WHITE)));
        }
        ItemPrices.Quote quote = ItemPrices.quote(id);
        if (quote != null && !Double.isNaN(quote.price())) {
            lines.add(Component.literal("Price: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(SpotlightMath.format(Math.round(quote.price())) + " coins" + (count != 1 ? " each" : ""))
                            .withStyle(ChatFormatting.GOLD)));
        }
        Long have = ownedById.get(id);
        if (have != null && have > 0) {
            lines.add(Component.literal("You have: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format(Locale.ROOT, "%,d", have)).withStyle(ChatFormatting.WHITE)));
        }
        lines.add(Component.empty());
        if (clickable) lines.add(Component.literal("Click: Recipe").withStyle(ChatFormatting.YELLOW));
        lines.add(Component.literal("Right-click: Wiki").withStyle(ChatFormatting.YELLOW));
        return lines;
    }

    /** One of the player's items: its own tooltip, then how many there are and where. */
    private static List<Component> ownedTooltip(ItemSearchResults.Entry entry, Screen in) {
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
        lines.add(Component.literal(action(entry.best())).withStyle(ChatFormatting.YELLOW));
        if (entry.id != null) lines.add(Component.literal("Right-click: Recipe").withStyle(ChatFormatting.YELLOW));
        return lines;
    }

    /** What a click on one of the player's items does, in words. */
    private static String action(ItemSearchResults.Place place) {
        return switch (place.source()) {
            case INVENTORY -> "Click: Outline it in your inventory";
            case CHEST -> ItemSearchTracker.onOwnIsland() ? "Click: Outline the chest" : "Click: Outline the chest on your island";
            default -> "Click: Open " + place.name();
        };
    }

    // ---- Actions --------------------------------------------------------------------------------

    private static void setMode(Mode m) {
        if (m == mode) return;
        AlpakaStyle.click();
        mode = m;
        page = 0;
    }

    /** Opens the item's view where the list is; from a view, on top of it, so back returns. */
    private static void open(String id) {
        AlpakaStyle.click();
        views.push(new View(id));
    }

    private static void back() {
        AlpakaStyle.click();
        if (!views.isEmpty()) views.pop();
    }

    /**
     * Opens the item's wiki page in the browser, after the usual question whether to open a link:
     * the page the NEU repository names, else a search on the official wiki for its name. The
     * question comes over the menu and goes back to it.
     */
    private static void openWiki(String id, Screen back) {
        AlpakaStyle.click();
        String name = ItemRecipes.displayName(id);
        String url = ItemRecipes.wikiUrl(ItemRecipes.get(id), name);
        Minecraft.getInstance().gui.setScreen(new net.alpaka.addons.client.AlpakaConfirmScreen(back, "Open Wiki",
                "Opens the wiki page of " + name + " in your browser: " + url, "Open", false,
                () -> net.minecraft.util.Util.getPlatform().openUri(url)));
    }

    /**
     * Goes to where one of the player's items is: the place, opened with Hypixel's command, and the
     * item outlined there. One command per click, the input being the player's. In the menu's own
     * inventory the item is only outlined.
     */
    private static void goTo(ItemSearchResults.Entry entry, ItemSearchResults.Place place, Screen menu) {
        AlpakaStyle.click();
        Minecraft mc = Minecraft.getInstance();
        ItemSearchHighlight.item(entry.key);
        switch (place.source()) {
            case INVENTORY -> {
            }
            case CHEST -> {
                ItemSearchHighlight.chest(place.pos(), place.pos2());
                if (!ItemSearchTracker.onOwnIsland()) {
                    SlayerDropTracker.sendModMessage("§7" + entry.name + " is in the chest at §f"
                            + place.pos().getX() + ", " + place.pos().getY() + ", " + place.pos().getZ()
                            + "§7 on your island. It is outlined when you get there.");
                }
            }
            default -> Outbound.command(place.source().command(place.page()), Outbound.Cause.BUTTON);
        }
    }

    // ---- Input ----------------------------------------------------------------------------------

    private static boolean shown(Screen screen) {
        return screen == drawnOn && regionW > 0 && enabled();
    }

    /**
     * A click on the list or the bar does what it hits, and the menu never gets it: beside the
     * menu, a click would otherwise throw out the item held on the cursor. A click anywhere else
     * takes the keyboard from the bar and goes on to the menu.
     */
    static boolean mouseClicked(Screen screen, double mx, double my, int button) {
        if (!shown(screen)) return false;
        if (AlpakaStyle.inside(mx, my, barX, barY, barW, BAR_H)) {
            if (button == 1) {
                query.setText("");
                textChanged();
            } else {
                AlpakaStyle.clickTextField(Minecraft.getInstance().font, query, null, barX, barW, mx, focused, false);
            }
            focused = true;
            caretFrom = System.currentTimeMillis();
            return true;
        }
        focused = false;
        if (!AlpakaStyle.inside(mx, my, regionX, regionY, regionW, regionH)) return false;
        // The mouse's back button steps out of an item view.
        if (button == GLFW.GLFW_MOUSE_BUTTON_4 && !views.isEmpty()) {
            back();
            return true;
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (!AlpakaStyle.inside(mx, my, hit.x(), hit.y(), hit.w(), hit.h())) continue;
            Runnable action = button == 0 ? hit.left() : button == 1 ? hit.right() : null;
            if (action != null) action.run();
            break;
        }
        return true;
    }

    static boolean mouseScrolled(Screen screen, double mx, double my, double amount) {
        if (!shown(screen) || !AlpakaStyle.inside(mx, my, regionX, regionY, regionW, regionH)) return false;
        Runnable step = amount > 0 ? wheelUp : amount < 0 ? wheelDown : null;
        if (step != null) step.run();
        return true;
    }

    /** Keys while the bar has the keyboard: they all go to the bar, so none of them closes the menu or swaps a slot. */
    static boolean keyPressed(Screen screen, KeyEvent event) {
        if (!focused || screen != drawnOn) return false;
        int key = event.key();
        if (MacOSInputFixesCompat.isDiscardedCopy(key)) return true;
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_TAB -> {
                focused = false;
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                Double result = sum();
                if (result != null) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(SpotlightMath.plain(result));
                    copiedAt = System.currentTimeMillis();
                } else {
                    focused = false;
                }
                return true;
            }
            default -> {
            }
        }
        String before = query.getText();
        query.keyPressed(event);
        if (!before.equals(query.getText())) textChanged();
        caretFrom = System.currentTimeMillis();
        return true;
    }

    static boolean charTyped(Screen screen, int codePoint) {
        if (!focused || screen != drawnOn) return false;
        if (query.insert(codePoint)) textChanged();
        caretFrom = System.currentTimeMillis();
        return true;
    }

    private static boolean hit(int[] box, double mx, double my) {
        return box != null && AlpakaStyle.inside(mx, my, box[0], box[1], box[2], box[3]);
    }
}
