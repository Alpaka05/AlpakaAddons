package net.alpaka.addons.features.itemsearch;

import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.EditableText;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.compliance.Outbound;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.itemvalue.ItemPrices;
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
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Item Search window, in the config menu's design: the search bar and the order in the header,
 * the places down the side with how many items each holds, and every matching item as a slot in a
 * grid. Hovering an item shows its own tooltip with where it lies; clicking it opens the place with
 * the most of it and outlines it there.
 */
public class ItemSearchScreen extends Screen {

    private static final int MARGIN = 8;
    private static final int WIN_W = 560;
    private static final int WIN_H = 340;
    private static final int MIN_W = 340;
    private static final int MIN_H = 220;
    private static final int SIDE_W = 118;
    private static final int SIDE_ROW_H = 20;
    private static final int CELL = 20;
    private static final int GAP = 2;
    private static final int FOOTER_H = 20;
    private static final int SORT_W = 86;
    /** The close cross, as the other windows that only show things have it. */
    private static final int DONE_W = 16;
    /** How many places the tooltip names before it sums up the rest. */
    private static final int MAX_PLACES = 8;

    /** The category picked last, kept until the game restarts; null for everything. */
    private static ItemSearchSource lastCategory = null;
    private static ItemSearchResults.Sort lastSort = ItemSearchResults.Sort.AMOUNT;

    private final Screen parent;
    private final EditableText input = new EditableText(64);
    private ItemSearchSource category = lastCategory;
    private ItemSearchResults.Sort sort = lastSort;
    private final net.alpaka.addons.client.gui.ChoicePopover sortList = new net.alpaka.addons.client.gui.ChoicePopover();
    private static final String[] SORT_NAMES = java.util.Arrays.stream(ItemSearchResults.Sort.values())
            .map(s -> s.label).toArray(String[]::new);

    private List<ItemSearchResults.Entry> all = List.of();
    private List<ItemSearchResults.Entry> shown = List.of();
    private final Map<ItemSearchSource, Integer> counts = new EnumMap<>(ItemSearchSource.class);
    private int allCount = 0;
    private String shownFor = null;
    private int pricesVersion = -1;

    private double scrollY = 0;
    private double targetScrollY = 0;
    private double maxScrollY = 0;
    private long lastFrameMs = System.currentTimeMillis();
    private long openTimeMs = 0L;
    private long caretFrom = System.currentTimeMillis();

    public ItemSearchScreen(String query, Screen parent) {
        super(GuiFont.text("Item Search"));
        this.parent = parent;
        if (query != null) input.setText(query);
    }

    // ---- Layout ---------------------------------------------------------------------------------

    private record Frame(int winX, int winY, int winW, int winH,
                         int sideX, int sideY, int sideH,
                         int gridX, int gridY, int gridW, int gridH, int columns,
                         int fieldX, int fieldW, int sortX, int doneX, int buttonY, int footerY) {}

    private Frame frame() {
        int winW = Math.max(MIN_W, Math.min(WIN_W, this.width - MARGIN * 2));
        int winH = Math.max(MIN_H, Math.min(WIN_H, this.height - MARGIN * 2));
        int winX = (this.width - winW) / 2;
        int winY = Math.max(MARGIN / 2, (this.height - winH) / 2);
        int pad = AlpakaStyle.PAD;
        int sideX = winX + pad;
        int bodyY = winY + AlpakaStyle.HEADER_H + 6;
        int footerY = winY + winH - FOOTER_H;
        int sideH = footerY - 4 - bodyY;
        int gridX = sideX + SIDE_W + pad;
        int gridW = winX + winW - pad - gridX;
        int columns = Math.max(1, (gridW + GAP) / (CELL + GAP));
        // The grid is centred in the room it has, so the columns line up with no ragged gap at the right.
        int used = columns * (CELL + GAP) - GAP;
        gridX += (gridW - used) / 2;
        int buttonY = AlpakaStyle.headerButtonY(winY);
        int doneX = winX + winW - pad - DONE_W;
        int sortX = doneX - 6 - SORT_W;
        int titleW = GuiFont.width(this.font, "Item Search");
        int fieldX = winX + pad + titleW + 14;
        int fieldW = Math.max(60, sortX - 6 - fieldX);
        return new Frame(winX, winY, winW, winH, sideX, bodyY, sideH, gridX, bodyY, used, sideH, columns,
                fieldX, fieldW, sortX, doneX, buttonY, footerY);
    }

    @Override
    protected void init() {
        if (openTimeMs == 0L) openTimeMs = System.currentTimeMillis();
        if (AlpakaConfig.instance.allowApiCalls) ItemPrices.refresh();
        reload();
    }

    /** Reads everything again: the places, the counts beside them, and the list. */
    private void reload() {
        List<ItemSearchResults.Entry> everything = ItemSearchResults.collect(null);
        counts.clear();
        for (ItemSearchResults.Entry entry : everything) {
            for (ItemSearchResults.Place place : entry.places) counts.merge(place.source(), 1, Integer::sum);
        }
        allCount = everything.size();
        all = category == null ? everything : ItemSearchResults.collect(category);
        shownFor = null;
    }

    /** The list for the query and order as they are now, worked out again only when one of them changed. */
    private List<ItemSearchResults.Entry> shown() {
        String key = input.getText() + "\u0000" + sort;
        int version = sort == ItemSearchResults.Sort.VALUE ? ItemPrices.version() : 0;
        if (!key.equals(shownFor) || version != pricesVersion) {
            shown = ItemSearchResults.filter(all, input.getText(), sort);
            shownFor = key;
            pricesVersion = version;
        }
        return shown;
    }

    @Override
    public void onClose() {
        lastCategory = category;
        lastSort = sort;
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

        AlpakaStyle.backdrop(graphics, this.width, this.height);
        boolean pushed = AlpakaStyle.beginOpen(graphics, openTimeMs, this.width, this.height);
        Frame f = frame();
        AlpakaStyle.panel(graphics, f.winX, f.winY, f.winW, f.winH);
        AlpakaStyle.header(graphics, this.font, "Item Search", f.winX, f.winY, f.winW);

        drawField(graphics, f, mouseX, mouseY);
        boolean hoverSort = inside(mouseX, mouseY, f.sortX, f.buttonY, SORT_W, AlpakaStyle.BUTTON_H);
        // A select field like every other choice of the mod, its list floating under it.
        AlpakaStyle.selectField(graphics, this.font, f.sortX, f.buttonY, SORT_W, AlpakaStyle.BUTTON_H,
                "Sort: " + sort.label, sortList.isOpen(), hoverSort && !sortList.isOpen());
        boolean hoverDone = inside(mouseX, mouseY, f.doneX, f.buttonY, DONE_W, AlpakaStyle.BUTTON_H);
        AlpakaStyle.closeButton(graphics, f.doneX, f.buttonY + (AlpakaStyle.BUTTON_H - DONE_W) / 2, DONE_W, hoverDone);

        drawSidebar(graphics, f, mouseX, mouseY);
        ItemSearchResults.Entry hovered = drawGrid(graphics, f, mouseX, mouseY, deltaSec);
        drawFooter(graphics, f, hovered);

        sortList.draw(graphics, this.font, SORT_NAMES, sort.ordinal(), mouseX, mouseY);
        AlpakaStyle.endOpen(graphics, pushed);
        if (hovered != null && !sortList.covers(mouseX, mouseY)) graphics.setComponentTooltipForNextFrame(this.font, tooltip(hovered), mouseX, mouseY);
        else if (hoverSort && !sortList.isOpen()) AlpakaStyle.tooltip(graphics, this.font, this.width, this.height, mouseX, mouseY,
                sort == ItemSearchResults.Sort.VALUE && !AlpakaConfig.instance.allowApiCalls
                        ? "Value needs Network Features, which are off." : "How the items are ordered.");
    }

    private void drawField(GuiGraphicsExtractor graphics, Frame f, int mouseX, int mouseY) {
        // The search field every window has; here it always has the focus.
        int[] clear = AlpakaStyle.searchClearBox(f.fieldX, f.buttonY, f.fieldW, AlpakaStyle.BUTTON_H);
        boolean hoverClear = !input.isEmpty() && inside(mouseX, mouseY, clear[0], clear[1], clear[2], clear[2]);
        fieldScroll = AlpakaStyle.searchField(graphics, this.font, f.fieldX, f.buttonY, f.fieldW, AlpakaStyle.BUTTON_H, input,
                "Search your items…", true, false, hoverClear, caretFrom);
    }

    /** How far the field's text is scrolled, for placing the caret on a click. */
    private int fieldScroll = 0;


    /** The height of a row down the side: roomy where the window is tall, tighter where it is not, so all of them fit. */
    private static int sideRowH(Frame f) {
        return Math.max(14, Math.min(SIDE_ROW_H, (f.sideH - 4) / (ItemSearchSource.values().length + 1)));
    }

    private void drawSidebar(GuiGraphicsExtractor graphics, Frame f, int mouseX, int mouseY) {
        int rowH = sideRowH(f);
        int y = f.sideY;
        drawSideRow(graphics, f.sideX, y, rowH, null, "All", allCount, mouseX, mouseY);
        y += rowH + 4;
        for (ItemSearchSource source : ItemSearchSource.values()) {
            drawSideRow(graphics, f.sideX, y, rowH, source, source.label, counts.getOrDefault(source, 0), mouseX, mouseY);
            y += rowH;
        }
        ModernGuiUtils.drawRect(graphics, f.sideX + SIDE_W + AlpakaStyle.PAD / 2, f.sideY, 1, f.sideH, AlpakaStyle.LINE);
    }

    private void drawSideRow(GuiGraphicsExtractor graphics, int x, int y, int rowH, ItemSearchSource source, String label, int count,
                             int mouseX, int mouseY) {
        boolean selected = source == category;
        boolean hover = inside(mouseX, mouseY, x, y, SIDE_W, rowH - 2);
        if (hover) ModernGuiUtils.drawRoundedRect(graphics, x, y, SIDE_W, rowH - 2, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW);
        // The picked place is named in the accent color, as the config's sidebar names its section.
        int color = selected ? AlpakaStyle.accentTextOn(hover ? AlpakaStyle.ROW : AlpakaStyle.PANEL)
                : hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT;
        // Every name starts in one column, "All" too, which has a dot where the others have an icon.
        int textX = x + 21;
        if (source == null) AlpakaStyle.listDot(graphics, x + 10, y + (rowH - 2 - 8) / 2 + 3, color);
        if (source != null) {
            float iconScale = (rowH - 4) / 16f;
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + 3, y + (rowH - 2 - 16 * iconScale) / 2f);
            graphics.pose().scale(iconScale, iconScale);
            graphics.fakeItem(new ItemStack(source.icon()), 0, 0);
            graphics.pose().popMatrix();
            textX = x + 21;
        }
        String countText = count == 0 ? "–" : Integer.toString(count);
        int countW = GuiFont.width(this.font, countText);
        int textY = y + (rowH - 2 - 8) / 2;
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, label, x + SIDE_W - 8 - countW - textX)),
                textX, textY, color, false);
        graphics.text(this.font, GuiFont.text(countText), x + SIDE_W - 5 - countW, textY, AlpakaStyle.TEXT_MUTED, false);
    }

    /** The grid of items; returns the one under the mouse. */
    private ItemSearchResults.Entry drawGrid(GuiGraphicsExtractor graphics, Frame f, int mouseX, int mouseY, float deltaSec) {
        List<ItemSearchResults.Entry> list = shown();
        int rows = (list.size() + f.columns - 1) / f.columns;
        int content = rows * (CELL + GAP) - GAP;
        maxScrollY = Math.max(0, content - f.gridH);
        targetScrollY = Math.max(0, Math.min(maxScrollY, targetScrollY));
        scrollY += (targetScrollY - scrollY) * Math.min(1.0, deltaSec * 14.0);

        if (list.isEmpty()) {
            int cx = f.gridX + f.gridW / 2;
            int cy = f.gridY + f.gridH / 2;
            String[] message = emptyMessage();
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text(message[0]), cx, cy - 10, AlpakaStyle.TEXT_SOFT);
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, message[1], f.gridW)),
                    cx, cy + 2, AlpakaStyle.TEXT_MUTED);
            return null;
        }

        boolean inGrid = inside(mouseX, mouseY, f.gridX, f.gridY, f.gridW, f.gridH);
        ItemSearchResults.Entry hovered = null;
        graphics.enableScissor(f.gridX - 1, f.gridY, f.gridX + f.gridW + 1, f.gridY + f.gridH);
        int first = Math.max(0, (int) (scrollY / (CELL + GAP)) * f.columns);
        for (int i = first; i < list.size(); i++) {
            int col = i % f.columns;
            int row = i / f.columns;
            int x = f.gridX + col * (CELL + GAP);
            int y = f.gridY + row * (CELL + GAP) - (int) scrollY;
            if (y > f.gridY + f.gridH) break;
            ItemSearchResults.Entry entry = list.get(i);
            boolean hover = inGrid && inside(mouseX, mouseY, x, y, CELL, CELL);
            if (hover) hovered = entry;
            ModernGuiUtils.drawRoundedRect(graphics, x, y, CELL, CELL, AlpakaStyle.FIELD_RADIUS,
                    hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.ROW);
            graphics.item(entry.stack, x + 2, y + 2);
            if (entry.total > 1) drawCount(graphics, ItemSearchMenus.shortCount(entry.total), x, y);
        }
        AlpakaStyle.scrollFades(graphics, f.gridX - 1, f.gridY, f.gridW + 2, f.gridH, scrollY > 0.5, scrollY < maxScrollY - 0.5);
        graphics.disableScissor();
        return hovered;
    }

    /**
     * The amount in the cell's corner, as a slot shows it, but smaller once it is longer than two
     * digits: "145k" at full size would run out of the cell.
     */
    private void drawCount(GuiGraphicsExtractor graphics, String text, int x, int y) {
        float scale = text.length() <= 2 ? 1.0f : 0.75f;
        int w = this.font.width(text);
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + CELL - 1 - w * scale, y + CELL - 1 - 8 * scale);
        graphics.pose().scale(scale, scale);
        graphics.text(this.font, text, 0, 0, 0xFFFFFFFF, true);
        graphics.pose().popMatrix();
    }

    /** What the empty grid says: why there is nothing, and what to do about it. */
    private String[] emptyMessage() {
        if (!all.isEmpty()) return new String[]{"Nothing matches \"" + input.getText().trim() + "\".", "Try fewer or other words."};
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

    private void drawFooter(GuiGraphicsExtractor graphics, Frame f, ItemSearchResults.Entry hovered) {
        int y = f.footerY;
        ModernGuiUtils.drawRect(graphics, f.winX + AlpakaStyle.PAD, y - 2, f.winW - AlpakaStyle.PAD * 2, 1, AlpakaStyle.LINE);
        int textY = y + (FOOTER_H - 8) / 2 - 1;
        String left;
        if (hovered != null) {
            left = action(hovered);
        } else {
            int n = shown().size();
            left = n + (n == 1 ? " item" : " items") + (input.isEmpty() ? "" : " match")
                    + " · Hover an item to see where it is, click it to go there.";
        }
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, left, f.winW - AlpakaStyle.PAD * 2 - 4)),
                f.winX + AlpakaStyle.PAD + 2, textY, AlpakaStyle.TEXT_MUTED, false);
    }

    /** The item's own tooltip, then how many there are and where. */
    private List<Component> tooltip(ItemSearchResults.Entry entry) {
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), entry.stack));
        lines.add(Component.empty());
        lines.add(Component.literal("Total: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format(Locale.ROOT, "%,d", entry.total)).withStyle(ChatFormatting.WHITE)));
        if (sort == ItemSearchResults.Sort.VALUE && !Double.isNaN(entry.value)) {
            lines.add(Component.literal("Value: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(String.format(Locale.ROOT, "%,.0f coins", entry.value)).withStyle(ChatFormatting.GOLD)));
        }
        int shownPlaces = Math.min(MAX_PLACES, entry.places.size());
        for (int i = 0; i < shownPlaces; i++) {
            ItemSearchResults.Place place = entry.places.get(i);
            lines.add(Component.literal(" " + place.name()).withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(" × " + String.format(Locale.ROOT, "%,d", place.count())).withStyle(ChatFormatting.DARK_GRAY)));
        }
        if (entry.places.size() > shownPlaces) {
            lines.add(Component.literal(" and " + (entry.places.size() - shownPlaces) + " more places").withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.literal(action(entry)).withStyle(ChatFormatting.YELLOW));
        return lines;
    }

    /** What clicking the item does, in words. */
    private String action(ItemSearchResults.Entry entry) {
        ItemSearchResults.Place place = entry.best();
        return switch (place.source()) {
            case INVENTORY -> parent instanceof AbstractContainerScreen<?> ? "Click to go back and see it outlined."
                    : "Click to open your inventory with it outlined.";
            case CHEST -> ItemSearchTracker.onOwnIsland() ? "Click to outline the chest in the world."
                    : "The chest is on your island; click to outline it there.";
            default -> "Click to open " + place.name() + ".";
        };
    }

    // ---- Input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        int mx = (int) event.x();
        int my = (int) event.y();
        Frame f = frame();

        if (sortList.isOpen()) {
            int picked = sortList.click(mx, my);
            if (picked >= 0) {
                AlpakaStyle.click();
                sort = ItemSearchResults.Sort.values()[picked];
                targetScrollY = 0;
                return true;
            }
            // A click on the field itself only closes the list again.
            if (inside(mx, my, f.sortX, f.buttonY, SORT_W, AlpakaStyle.BUTTON_H)) return true;
        }
        if (inside(mx, my, f.doneX, f.buttonY, DONE_W, AlpakaStyle.BUTTON_H)) {
            AlpakaStyle.click();
            onClose();
            return true;
        }
        if (inside(mx, my, f.sortX, f.buttonY, SORT_W, AlpakaStyle.BUTTON_H)) {
            AlpakaStyle.click();
            sortList.toggle(this.font, SORT_NAMES, f.sortX, f.buttonY, SORT_W, AlpakaStyle.BUTTON_H, this.width, this.height);
            return true;
        }
        int[] clear = AlpakaStyle.searchClearBox(f.fieldX, f.buttonY, f.fieldW, AlpakaStyle.BUTTON_H);
        if (!input.isEmpty() && inside(mx, my, clear[0], clear[1], clear[2], clear[2])) {
            AlpakaStyle.click();
            input.setText("");
            caretFrom = System.currentTimeMillis();
            targetScrollY = 0;
            return true;
        }
        if (inside(mx, my, f.fieldX, f.buttonY, f.fieldW, AlpakaStyle.BUTTON_H)) {
            caretFrom = System.currentTimeMillis();
            input.setCaretFromX(mx - f.fieldX - AlpakaStyle.SEARCH_TEXT_INSET + fieldScroll, s -> GuiFont.width(this.font, s));
            return true;
        }

        int rowH = sideRowH(f);
        int y = f.sideY;
        if (inside(mx, my, f.sideX, y, SIDE_W, rowH - 2)) {
            pick(null);
            return true;
        }
        y += rowH + 4;
        for (ItemSearchSource source : ItemSearchSource.values()) {
            if (inside(mx, my, f.sideX, y, SIDE_W, rowH - 2)) {
                pick(source);
                return true;
            }
            y += rowH;
        }

        if (inside(mx, my, f.gridX, f.gridY, f.gridW, f.gridH)) {
            List<ItemSearchResults.Entry> list = shown();
            int col = (mx - f.gridX) / (CELL + GAP);
            int row = (my - f.gridY + (int) scrollY) / (CELL + GAP);
            int index = row * f.columns + col;
            if (col < f.columns && index >= 0 && index < list.size()) {
                AlpakaStyle.click();
                go(list.get(index));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void pick(ItemSearchSource source) {
        if (source == category) return;
        AlpakaStyle.click();
        category = source;
        targetScrollY = 0;
        scrollY = 0;
        reload();
    }

    /**
     * Goes to where the item is: the place with the most of it, opened with Hypixel's command, and
     * the item outlined there. One command per click, the click being the player's.
     */
    private void go(ItemSearchResults.Entry entry) {
        Minecraft mc = Minecraft.getInstance();
        ItemSearchResults.Place place = entry.best();
        ItemSearchHighlight.item(entry.key);
        lastCategory = category;
        lastSort = sort;
        switch (place.source()) {
            case INVENTORY -> mc.gui.setScreen(parent instanceof AbstractContainerScreen<?> ? parent
                    : mc.player == null ? null : new InventoryScreen(mc.player));
            case CHEST -> {
                ItemSearchHighlight.chest(place.pos(), place.pos2());
                mc.gui.setScreen(parent);
                if (!ItemSearchTracker.onOwnIsland()) {
                    SlayerDropTracker.sendModMessage("§7" + entry.name + " is in the chest at §f"
                            + place.pos().getX() + ", " + place.pos().getY() + ", " + place.pos().getZ()
                            + "§7 on your island. It is outlined when you get there.");
                }
            }
            default -> {
                mc.gui.setScreen(parent);
                Outbound.command(place.source().command(place.page()), Outbound.Cause.BUTTON);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollYAmount) {
        if (scrollYAmount != 0) {
            targetScrollY = Math.max(0, Math.min(maxScrollY, targetScrollY - scrollYAmount * (CELL + GAP) * 2));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollYAmount);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        // Typing goes nowhere while the Sort list is open; the list has the keyboard.
        if (sortList.isOpen()) return true;
        int codePoint = event.codepoint();
        if (Character.isBmpCodePoint(codePoint) && input.insert((char) codePoint)) {
            caretFrom = System.currentTimeMillis();
            targetScrollY = 0;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        // The open Sort list has the keyboard first: nothing beneath it opens or closes.
        if (sortList.isOpen()) {
            int next = sortList.keyPressed(key, sort.ordinal());
            if (next >= 0) {
                sort = ItemSearchResults.Sort.values()[next];
                targetScrollY = 0;
            }
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            // Enter goes to the first result, as clicking it would.
            List<ItemSearchResults.Entry> list = shown();
            if (!list.isEmpty()) go(list.get(0));
            return true;
        }
        if (input.keyPressed(key, event.hasControlDownWithQuirk(), event.hasShiftDown())) {
            caretFrom = System.currentTimeMillis();
            targetScrollY = 0;
            return true;
        }
        return super.keyPressed(event);
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return AlpakaStyle.inside(mx, my, x, y, w, h);
    }
}
