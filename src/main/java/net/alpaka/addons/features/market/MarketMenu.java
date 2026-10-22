package net.alpaka.addons.features.market;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A market menu taken apart by what its items are for, so the screen can lay them out by purpose
 * instead of by Hypixel's chest grid: the categories down the side, the controls in the header and
 * footer, the auctions or products as cards.
 *
 * Items are recognised by their names, not their slots, so a menu Hypixel rearranges still comes
 * out right. Nothing is ever dropped: an item that is neither a filler pane nor recognised is either
 * content (inside the chest's border) or an extra button (on the border).
 */
public final class MarketMenu {

    /** One slot of the chest as plain data: what the parser needs to know about its item. */
    public record Slot(int index, String itemId, String name, List<String> lore, boolean foil) {
        /** Hypixel's background panes: a glass pane, or anything without a name. */
        public boolean isFiller() {
            if (itemId == null || itemId.isEmpty() || itemId.equals("air")) return true;
            boolean blankName = name == null || name.isBlank();
            if (blankName && itemId.endsWith("glass_pane")) return true;
            return blankName && (lore == null || lore.stream().allMatch(String::isBlank));
        }
    }

    /** The controls that get a place of their own rather than a card. */
    public enum Control {
        SEARCH, SORT, TIER, BIN_FILTER, MANAGE_ORDERS, SELL_INVENTORY, SELL_SACKS, BACK, CLOSE, NEXT, PREVIOUS
    }

    /** A category of the left column, with the icon the screen draws for it. */
    public enum CategoryIcon {
        WEAPONS("Weapons", 0xFFFFB347),
        ARMOR("Armor", 0xFF5B9DFF),
        ACCESSORIES("Accessories", 0xFF4FD38A),
        CONSUMABLES("Consumables", 0xFFFF6B6B),
        BLOCKS("Blocks", 0xFFC9935A),
        TOOLS("Tools & Misc", 0xFFB487FF),
        FARMING("Farming", 0xFFF2C94C),
        MINING("Mining", 0xFF56CCF2),
        COMBAT("Combat", 0xFFFF6B6B),
        WOODS_FISHES("Woods & Fishes", 0xFFE8A055),
        ODDITIES("Oddities", 0xFFE07BFF);

        public final String hypixelName;
        /** The category's own color: Hypixel tints each category's border, this follows it. */
        public final int color;

        CategoryIcon(String hypixelName, int color) {
            this.hypixelName = hypixelName;
            this.color = color;
        }

        static CategoryIcon byName(String name) {
            String n = name.trim();
            if (n.endsWith(".")) n = n.substring(0, n.length() - 1);
            for (CategoryIcon icon : values()) {
                if (icon.hypixelName.equalsIgnoreCase(n)) return icon;
            }
            return null;
        }
    }

    public record Category(int slot, String name, CategoryIcon icon, boolean selected) {}

    /** How the content is best shown: many similar things as a grid, a few actions as large tiles. */
    public enum Layout { GRID, TILES }

    public final MarketKind kind;
    public final String title;
    public final int rows;
    public final List<Category> categories = new ArrayList<>();
    public final Map<Control, Integer> controls = new EnumMap<>(Control.class);
    /** The auctions, products or actions, in slot order. */
    public final List<Integer> content = new ArrayList<>();
    /** Unrecognised items on the chest's border: shown as buttons, so nothing is out of reach. */
    public final List<Integer> extras = new ArrayList<>();
    /** Parsed lore of every content slot, by slot. */
    public final Map<Integer, LoreInfo> info = new java.util.HashMap<>();
    public final Map<Control, String> controlValues = new EnumMap<>(Control.class);
    public Layout layout = Layout.GRID;
    /** The one content item worth a large card of its own: the auction being looked at, say. */
    public Integer hero;
    public String query;
    public int page = -1;
    public int pages = -1;
    /** Where the back button leads, from its lore ("To Auction House"). */
    public String backTarget;
    /** Whether the chest holds anything but filler yet; right after opening its items are on their way. */
    public boolean empty = true;

    private MarketMenu(MarketKind kind, String title, int rows) {
        this.kind = kind;
        this.title = title;
        this.rows = rows;
    }

    private static final Pattern PAGE = Pattern.compile("Page (\\d+)", Pattern.CASE_INSENSITIVE);
    /** "(2/14)": the first line of the Auction House's page arrows. */
    private static final Pattern PAGE_OF = Pattern.compile("\\(([\\d,]+)/([\\d,]+)\\)");

    public static MarketMenu parse(MarketKind kind, String title, int rows, List<Slot> slots) {
        MarketMenu menu = new MarketMenu(kind, title, rows);
        menu.query = MarketTitles.query(title);
        List<String> crumbs = MarketTitles.breadcrumb(title, kind);
        String titleCategory = crumbs.isEmpty() ? null : crumbs.get(0);

        for (Slot slot : slots) {
            if (slot.index() >= rows * 9) continue;
            if (slot.isFiller()) continue;
            menu.empty = false;
            String name = slot.name().trim();

            Control control = control(name);
            if (control != null && !menu.controls.containsKey(control)) {
                menu.controls.put(control, slot.index());
                menu.readControl(control, slot);
                continue;
            }

            CategoryIcon icon = slot.index() % 9 == 0 ? CategoryIcon.byName(name) : null;
            if (icon != null) {
                boolean selected = slot.foil() || mentionsCurrent(slot.lore())
                        || (titleCategory != null && titleCategory.equalsIgnoreCase(icon.hypixelName));
                menu.categories.add(new Category(slot.index(), name, icon, selected));
                continue;
            }

            if (onBorder(slot.index(), rows)) {
                menu.extras.add(slot.index());
            } else {
                menu.content.add(slot.index());
                menu.info.put(slot.index(), LoreInfo.parse(slot.lore()));
            }
        }

        // Only one selected category: the glint wins over the title, which can name a sub page.
        long selected = menu.categories.stream().filter(Category::selected).count();
        if (selected > 1) {
            List<Category> fixed = new ArrayList<>();
            boolean taken = false;
            for (Category c : menu.categories) {
                boolean keep = !taken && c.selected() && slotFoil(slots, c.slot());
                taken |= keep;
                fixed.add(new Category(c.slot(), c.name(), c.icon(), keep));
            }
            if (!taken) {
                fixed.clear();
                for (Category c : menu.categories) {
                    boolean keep = !taken && c.selected();
                    taken |= keep;
                    fixed.add(new Category(c.slot(), c.name(), c.icon(), keep));
                }
            }
            menu.categories.clear();
            menu.categories.addAll(fixed);
        }

        menu.readProductPage(slots);
        menu.chooseLayout();
        if (menu.pages < 0 && title != null) {
            Matcher m = PAGE_OF.matcher(title);
            if (m.find()) {
                menu.page = number(m.group(1), -1);
                menu.pages = number(m.group(2), -1);
            }
        }
        return menu;
    }

    /** A page number, or the fallback for one that is not a sensible number. */
    private static int number(String digits, int fallback) {
        try {
            return Integer.parseInt(digits.replace(",", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean slotFoil(List<Slot> slots, int index) {
        for (Slot s : slots) if (s.index() == index) return s.foil();
        return false;
    }

    private static boolean mentionsCurrent(List<String> lore) {
        for (String line : lore) {
            String l = line.toLowerCase(Locale.ROOT);
            if (l.contains("currently viewing") || l.contains("currently browsing") || l.contains("currently selected")) {
                return true;
            }
        }
        return false;
    }

    /** The outer ring of a chest of three rows or more; smaller chests have no border to speak of. */
    static boolean onBorder(int index, int rows) {
        if (rows < 3) return false;
        int row = index / 9;
        int col = index % 9;
        return row == 0 || row == rows - 1 || col == 0 || col == 8;
    }

    static Control control(String name) {
        String n = name.toLowerCase(Locale.ROOT).trim();
        return switch (n) {
            case "search", "search bazaar", "search auctions" -> Control.SEARCH;
            case "sort" -> Control.SORT;
            case "item tier" -> Control.TIER;
            case "bin filter" -> Control.BIN_FILTER;
            case "manage orders" -> Control.MANAGE_ORDERS;
            case "sell inventory now" -> Control.SELL_INVENTORY;
            case "sell sacks now" -> Control.SELL_SACKS;
            case "go back" -> Control.BACK;
            case "close" -> Control.CLOSE;
            case "next page" -> Control.NEXT;
            case "previous page", "prev page" -> Control.PREVIOUS;
            default -> null;
        };
    }

    /** Reads what a control's lore says about the current state: a filter's value, a page number. */
    private void readControl(Control control, Slot slot) {
        switch (control) {
            case SORT, TIER, BIN_FILTER -> {
                // Hypixel lists the choices and marks the current one with an arrow.
                for (String line : slot.lore()) {
                    String l = line.trim();
                    if (l.startsWith("▶")) {
                        controlValues.put(control, l.substring(1).trim());
                        break;
                    }
                }
            }
            case NEXT, PREVIOUS -> {
                for (String line : slot.lore()) {
                    Matcher of = PAGE_OF.matcher(line);
                    if (of.find()) {
                        page = number(of.group(1), page);
                        pages = number(of.group(2), pages);
                        continue;
                    }
                    Matcher m = PAGE.matcher(line);
                    if (m.find() && page < 0) {
                        int target = number(m.group(1), -1);
                        if (target > 0) page = control == Control.NEXT ? target - 1 : target + 1;
                    }
                }
            }
            case BACK -> {
                for (String line : slot.lore()) {
                    String l = line.trim();
                    if (l.startsWith("To ")) {
                        backTarget = l.substring(3).trim();
                        break;
                    }
                }
            }
            case SEARCH -> {
                // Some search buttons name the active filter in their lore.
                if (query == null) {
                    for (String line : slot.lore()) {
                        String l = line.trim();
                        if (l.startsWith("Filtered: ")) query = l.substring("Filtered: ".length()).trim();
                    }
                }
            }
            default -> {
            }
        }
    }

    /** The actions on a Bazaar product's page, by Hypixel's names in lower case. */
    private static final List<String> PRODUCT_ACTIONS = List.of("buy instantly", "sell instantly", "create buy order", "create sell offer");

    /**
     * A Bazaar product's own page. Its product item only says what the item is ("Collection Item"),
     * not what it costs, so the page is known by its Buy Instantly button, the way SkyHanni knows it,
     * and the product takes the prices the two instant buttons quote. That makes it the page's hero,
     * which gets the price history.
     */
    private void readProductPage(List<Slot> slots) {
        if (kind != MarketKind.BAZAAR) return;
        Integer buy = null;
        Integer sell = null;
        List<Integer> others = new ArrayList<>();
        for (int index : content) {
            String name = nameAt(slots, index).toLowerCase(Locale.ROOT);
            if (name.equals("buy instantly")) buy = index;
            else if (name.equals("sell instantly")) sell = index;
            else if (!PRODUCT_ACTIONS.contains(name)) others.add(index);
        }
        if (buy == null) return;
        // Hypixel puts the product in the middle of the second row; should it move, it is the one
        // item that is not an action.
        Integer product = others.contains(13) ? Integer.valueOf(13) : others.size() == 1 ? others.get(0) : null;
        if (product == null) return;
        LoreInfo productInfo = info.get(product);
        if (productInfo.type != LoreInfo.Type.GENERIC && productInfo.type != LoreInfo.Type.PRODUCT) return;
        productInfo.type = LoreInfo.Type.PRODUCT;
        if (productInfo.buyPrice == null) productInfo.buyPrice = info.get(buy).unitPrice;
        if (productInfo.sellPrice == null && sell != null) productInfo.sellPrice = info.get(sell).unitPrice;
    }

    private static String nameAt(List<Slot> slots, int index) {
        for (Slot s : slots) if (s.index() == index) return s.name() == null ? "" : s.name().trim();
        return "";
    }

    /**
     * A grid when the content is a list of auctions, products or orders; large tiles for a handful
     * of actions, with the one item that has auction or product data, if any, as the hero beside them.
     */
    private void chooseLayout() {
        int rich = 0;
        Integer firstRich = null;
        for (int slot : content) {
            if (info.get(slot).isRich()) {
                rich++;
                if (firstRich == null) firstRich = slot;
            }
        }
        if (rich >= 2 || content.size() > 10) {
            layout = Layout.GRID;
            return;
        }
        layout = Layout.TILES;
        hero = rich == 1 && content.size() > 1 ? firstRich : null;
    }

    /** Whether the sidebar has categories, filters or actions to show. */
    public boolean hasSidebarControls() {
        return !categories.isEmpty() || controls.containsKey(Control.SORT) || controls.containsKey(Control.TIER)
                || controls.containsKey(Control.BIN_FILTER) || controls.containsKey(Control.MANAGE_ORDERS)
                || controls.containsKey(Control.SELL_INVENTORY) || controls.containsKey(Control.SELL_SACKS);
    }

    /** A page's one or two extra buttons go in the footer rather than opening a sidebar of their own. */
    public boolean extrasInFooter() {
        return !hasSidebarControls() && extras.size() <= 2;
    }

    /** Whether the menu has a sidebar to show: categories, filters, actions or extras. */
    public boolean hasSidebar() {
        return hasSidebarControls() || (!extras.isEmpty() && !extrasInFooter());
    }
}
