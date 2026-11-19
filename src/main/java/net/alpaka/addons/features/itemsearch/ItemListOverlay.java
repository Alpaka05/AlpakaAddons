package net.alpaka.addons.features.itemsearch;

import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.EditableText;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.compat.MacOSInputFixesCompat;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.chestvalue.ChestValuePanel;
import net.alpaka.addons.features.itemvalue.ItemPrices;
import net.alpaka.addons.features.market.ItemIcons;
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
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The item list beside every menu, the way item list mods have it: every SkyBlock item as icons
 * on the right of the screen, with no panel behind them, and a search bar under the menu. Unlike
 * those it also searches what the player owns: "Mine" swaps the list for everything Item Search
 * remembers, with the amounts, and a click goes to where it lies. A sum typed into the bar is
 * worked out above it.
 *
 * Only drawn while a menu is open; nothing here sends anything, except the command a click on one
 * of the player's own items opens its place with, once per click.
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
    private static final int MIN_COLS = 3;

    /** Which items the list shows. */
    enum Mode {
        ALL("All"), MINE("Mine");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    private static Mode mode = Mode.ALL;
    /** The search, kept from menu to menu as an item list's is. */
    private static final EditableText query = new EditableText(64);
    private static boolean focused = false;
    private static int page = 0;
    private static long caretFrom = 0L;
    private static long copiedAt = 0L;
    /** For the screenshot tour, which is not on SkyBlock. */
    private static boolean devForce = false;

    // The layout of the last frame, for the clicks that follow it.
    private static Screen drawnOn = null;
    private static int regionX, regionY, regionW, regionH;
    private static int barX, barY, barW;
    private static int[] allTab, mineTab, prevButton, nextButton;
    private static final List<Cell> cells = new ArrayList<>();

    private record Cell(int x, int y, Object item) {}

    // What the list holds, worked out again only when the search or the mode changes.
    private static String builtFor = null;
    private static List<?> items = List.of();
    private static List<ItemSearchResults.Entry> owned = List.of();
    private static final Map<String, Long> ownedById = new HashMap<>();
    private static long ownedAt = 0L;
    private static List<ItemCatalog.Item> sortedCatalog = null;
    private static List<ItemCatalog.Item> sortedFrom = null;
    private static Double sum = null;
    private static String sumFor = null;

    // ---- State ----------------------------------------------------------------------------------

    /** Whether the bar has the keyboard in this screen, so a key of ours types instead of acting. */
    public static boolean typing(Screen screen) {
        return focused && screen != null && screen == drawnOn;
    }

    static boolean enabled() {
        return AlpakaConfig.instance.itemSearchEnabled && AlpakaConfig.instance.itemListEnabled
                && (devForce || SkyblockUtils.isOnSkyblock());
    }

    /** The Item Search key in a menu: the bar takes the keyboard, with the item under the mouse searched for among the player's. */
    static void focus(String search) {
        if (search != null) {
            query.setText(search);
            mode = Mode.MINE;
            page = 0;
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
        focused = focus;
        caretFrom = System.currentTimeMillis();
        builtFor = null;
        ownedAt = 0L;
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
        if (mode == Mode.MINE) {
            items = ItemSearchResults.filter(owned, q, ItemSearchResults.Sort.NAME);
        } else if (catalog == null) {
            items = List.of();
        } else if (q.isEmpty()) {
            if (sortedFrom != catalog) {
                List<ItemCatalog.Item> sorted = new ArrayList<>(catalog);
                sorted.sort(Comparator.comparing((ItemCatalog.Item i) -> i.name().toLowerCase(Locale.ROOT)));
                sortedCatalog = sorted;
                sortedFrom = catalog;
            }
            items = sortedCatalog;
        } else {
            items = ItemCatalog.search(catalog, q, Integer.MAX_VALUE);
        }
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
        cells.clear();
        allTab = mineTab = prevButton = nextButton = null;
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
        // On a narrow screen the pages go on a line of their own under the two modes.
        int topH = gridW >= topWidth(font) ? TOP_H : TOP_H * 2;
        int gy = top + topH;
        int bottom = barUnderList ? barY - 4 : screenH - EDGE;
        int rows = Math.max(1, (bottom - gy) / CELL);
        regionX = gx;
        regionY = top;
        regionW = gridW;
        regionH = gy + rows * CELL - top;

        List<?> list = items();
        int perPage = cols * rows;
        int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(pages - 1, page));

        graphics.nextStratum();
        drawTop(graphics, font, gx, top, gridW, topH, pages, mx, my);

        List<Component> tooltip = null;
        int from = page * perPage;
        for (int i = 0; i < perPage && from + i < list.size(); i++) {
            int cx = gx + (i % cols) * CELL;
            int cy = gy + (i / cols) * CELL;
            Object item = list.get(from + i);
            cells.add(new Cell(cx, cy, item));
            boolean hover = AlpakaStyle.inside(mx, my, cx, cy, CELL, CELL);
            if (hover) ModernGuiUtils.drawRoundedRect(graphics, cx, cy, CELL, CELL, 2, AlpakaStyle.ROW_HOVER);
            if (item instanceof ItemSearchResults.Entry entry) {
                graphics.fakeItem(entry.stack, cx + 1, cy + 1);
                if (entry.total > 1) drawCount(graphics, font, ItemSearchMenus.shortCount(entry.total), cx, cy);
                if (hover) tooltip = ItemSearchScreen.tooltip(entry, in, true);
            } else if (item instanceof ItemCatalog.Item catalog) {
                ItemStack icon = ItemIcons.forItem(catalog.id(), catalog.name());
                if (!icon.isEmpty()) graphics.fakeItem(icon, cx + 1, cy + 1);
                else ModernGuiUtils.centeredText(graphics, font, GuiFont.text("?"), cx + CELL / 2, cy + 5, AlpakaStyle.TEXT_MUTED);
                if (hover) tooltip = catalogTooltip(catalog);
            }
        }
        if (list.isEmpty()) {
            String message = emptyMessage();
            for (var line : font.split(GuiFont.text(message), gridW)) {
                graphics.text(font, line, gx, gy + 2, AlpakaStyle.TEXT_MUTED, true);
                gy += 10;
            }
        }

        drawBar(graphics, font, mx, my);
        if (tooltip != null) graphics.setComponentTooltipForNextFrame(font, tooltip, mx, my);
    }

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

    /** The two modes on the left, the pages on the right. */
    /** How wide the modes and the pages are on one line, with the widest page label there can be. */
    private static int topWidth(Font font) {
        int w = 0;
        for (Mode m : Mode.values()) w += GuiFont.width(font, m.label) + 8;
        return w + GuiFont.width(font, "99/99") + 2 * 12 + 8;
    }

    private static void drawTop(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, int h, int pages, int mx, int my) {
        int ty = y + (TOP_H - 8) / 2 - 1;
        int tx = x + 2;
        for (Mode m : Mode.values()) {
            int tw = GuiFont.width(font, m.label);
            boolean hover = AlpakaStyle.inside(mx, my, tx - 2, y, tw + 4, TOP_H - 2);
            int color = m == mode ? AlpakaStyle.accent() : hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_MUTED;
            graphics.text(font, GuiFont.text(m.label), tx, ty, color, true);
            int[] box = {tx - 2, y, tw + 4, TOP_H - 2};
            if (m == Mode.ALL) allTab = box;
            else mineTab = box;
            tx += tw + 8;
        }
        String label = (page + 1) + "/" + pages;
        int lw = GuiFont.width(font, label);
        int size = 12;
        int right = x + w;
        if (h > TOP_H) {
            y += TOP_H;
            ty += TOP_H;
        }
        nextButton = new int[]{right - size, y + 1, size, size};
        int lx = right - size - 2 - lw;
        prevButton = new int[]{lx - 2 - size, y + 1, size, size};
        AlpakaStyle.chevronButton(graphics, prevButton[0], prevButton[1], size, true, hit(prevButton, mx, my), page > 0);
        graphics.text(font, GuiFont.text(label), lx, ty, AlpakaStyle.TEXT_SOFT, true);
        AlpakaStyle.chevronButton(graphics, nextButton[0], nextButton[1], size, false, hit(nextButton, mx, my), page < pages - 1);
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
        if (mode == Mode.MINE) {
            return query.getText().isBlank() ? "Open your Ender Chest, backpacks and sacks once to add them."
                    : "None of your items match.";
        }
        if (sum() != null) return "";
        if (ItemCatalog.all() == null) {
            return AlpakaConfig.instance.allowApiCalls ? "Loading the item list…" : "The item list needs Network Features.";
        }
        return "No item matches.";
    }

    private static List<Component> catalogTooltip(ItemCatalog.Item item) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(item.name()).withStyle(s -> s.withColor(ItemCatalog.tierColor(item.tier()) & 0xFFFFFF)));
        if (item.tier() != null) lines.add(Component.literal(ItemCatalog.tierLabel(item.tier())).withStyle(ChatFormatting.DARK_GRAY));
        ItemPrices.Quote quote = ItemPrices.quote(item.id());
        if (quote != null && !Double.isNaN(quote.price())) {
            lines.add(Component.literal("Price: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(SpotlightMath.format(Math.round(quote.price())) + " coins").withStyle(ChatFormatting.GOLD)));
        }
        Long have = ownedById.get(item.id());
        if (have != null && have > 0) {
            lines.add(Component.literal("You have: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format(Locale.ROOT, "%,d", have)).withStyle(ChatFormatting.WHITE)));
        }
        lines.add(Component.empty());
        lines.add(Component.literal("Click: Recipe").withStyle(ChatFormatting.YELLOW));
        lines.add(Component.literal("Right-click: Wiki").withStyle(ChatFormatting.YELLOW));
        return lines;
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
                page = 0;
            } else {
                AlpakaStyle.clickTextField(Minecraft.getInstance().font, query, null, barX, barW, mx, focused, false);
            }
            focused = true;
            caretFrom = System.currentTimeMillis();
            return true;
        }
        focused = false;
        if (!AlpakaStyle.inside(mx, my, regionX, regionY, regionW, regionH)) return false;
        if (hit(allTab, mx, my)) {
            setMode(Mode.ALL);
        } else if (hit(mineTab, mx, my)) {
            setMode(Mode.MINE);
        } else if (hit(prevButton, mx, my)) {
            if (page > 0) {
                AlpakaStyle.click();
                page--;
            }
        } else if (hit(nextButton, mx, my)) {
            AlpakaStyle.click();
            page++;
        } else {
            for (Cell cell : cells) {
                if (AlpakaStyle.inside(mx, my, cell.x(), cell.y(), CELL, CELL)) {
                    activate(screen, cell.item(), button);
                    break;
                }
            }
        }
        return true;
    }

    private static void setMode(Mode m) {
        if (m == mode) return;
        AlpakaStyle.click();
        mode = m;
        page = 0;
    }

    /** A catalog item: its recipe, or its wiki page on a right-click. One of the player's: where it lies, or its recipe. */
    private static void activate(Screen screen, Object item, int button) {
        if (button != 0 && button != 1) return;
        AlpakaStyle.click();
        if (item instanceof ItemCatalog.Item catalog) {
            if (button == 0) ItemSearchScreen.openRecipe(catalog.id(), screen);
            else ItemSearchScreen.openWiki(catalog.id(), catalog.name(), screen);
        } else if (item instanceof ItemSearchResults.Entry entry) {
            if (button == 0) ItemSearchScreen.goTo(entry, entry.best(), screen);
            else if (entry.id != null) ItemSearchScreen.openRecipe(entry.id, screen);
        }
    }

    static boolean mouseScrolled(Screen screen, double mx, double my, double amount) {
        if (!shown(screen) || !AlpakaStyle.inside(mx, my, regionX, regionY, regionW, regionH)) return false;
        if (amount > 0 && page > 0) page--;
        else if (amount < 0) page++;
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
        if (!before.equals(query.getText())) page = 0;
        caretFrom = System.currentTimeMillis();
        return true;
    }

    static boolean charTyped(Screen screen, int codePoint) {
        if (!focused || screen != drawnOn) return false;
        if (query.insert(codePoint)) page = 0;
        caretFrom = System.currentTimeMillis();
        return true;
    }

    private static boolean hit(int[] box, double mx, double my) {
        return box != null && AlpakaStyle.inside(mx, my, box[0], box[1], box[2], box[3]);
    }
}
