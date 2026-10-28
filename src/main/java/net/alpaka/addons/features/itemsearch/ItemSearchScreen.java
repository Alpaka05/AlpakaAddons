package net.alpaka.addons.features.itemsearch;

import net.alpaka.addons.client.gui.EditableText;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.compliance.Outbound;
import net.alpaka.addons.config.AlpakaConfig;
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
    private static final int DONE_W = 50;
    /** How many places the tooltip names before it sums up the rest. */
    private static final int MAX_PLACES = 8;

    /** The category picked last, kept until the game restarts; null for everything. */
    private static ItemSearchSource lastCategory = null;
    private static ItemSearchResults.Sort lastSort = ItemSearchResults.Sort.AMOUNT;

    private final Screen parent;
    private final EditableText input = new EditableText(64);
    private ItemSearchSource category = lastCategory;
    private ItemSearchResults.Sort sort = lastSort;

    private List<ItemSearchResults.Entry> all = List.of();
    private List<ItemSearchResults.Entry> shown = List.of();
    private final Map<ItemSearchSource, Integer> counts = new EnumMap<>(ItemSearchSource.class);
    private int allCount = 0;
    private String shownFor = null;

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
        int pad = ItemSearchStyle.PAD;
        int sideX = winX + pad;
        int bodyY = winY + ItemSearchStyle.HEADER_H + 6;
        int footerY = winY + winH - FOOTER_H;
        int sideH = footerY - 4 - bodyY;
        int gridX = sideX + SIDE_W + pad;
        int gridW = winX + winW - pad - gridX;
        int columns = Math.max(1, (gridW + GAP) / (CELL + GAP));
        // The grid is centred in the room it has, so the columns line up with no ragged gap at the right.
        int used = columns * (CELL + GAP) - GAP;
        gridX += (gridW - used) / 2;
        int buttonY = ItemSearchStyle.headerButtonY(winY);
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
        if (!key.equals(shownFor)) {
            shown = ItemSearchResults.filter(all, input.getText(), sort);
            shownFor = key;
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

        ItemSearchStyle.backdrop(graphics, this.width, this.height);
        boolean pushed = ItemSearchStyle.beginOpen(graphics, openTimeMs, this.width, this.height);
        Frame f = frame();
        ItemSearchStyle.panel(graphics, f.winX, f.winY, f.winW, f.winH);
        ItemSearchStyle.header(graphics, this.font, "Item Search", f.winX, f.winY, f.winW);

        drawField(graphics, f, mouseX, mouseY);
        boolean hoverSort = inside(mouseX, mouseY, f.sortX, f.buttonY, SORT_W, ItemSearchStyle.BUTTON_H);
        ItemSearchStyle.button(graphics, this.font, f.sortX, f.buttonY, SORT_W, ItemSearchStyle.BUTTON_H,
                "Sort: " + sort.label, hoverSort, ItemSearchStyle.ButtonKind.PLAIN);
        boolean hoverDone = inside(mouseX, mouseY, f.doneX, f.buttonY, DONE_W, ItemSearchStyle.BUTTON_H);
        ItemSearchStyle.button(graphics, this.font, f.doneX, f.buttonY, DONE_W, ItemSearchStyle.BUTTON_H, "Done", hoverDone,
                ItemSearchStyle.ButtonKind.PRIMARY);

        drawSidebar(graphics, f, mouseX, mouseY);
        ItemSearchResults.Entry hovered = drawGrid(graphics, f, mouseX, mouseY, deltaSec);
        drawFooter(graphics, f, hovered);

        ItemSearchStyle.endOpen(graphics, pushed);
        if (hovered != null) graphics.setComponentTooltipForNextFrame(this.font, tooltip(hovered), mouseX, mouseY);
        else if (hoverSort) ItemSearchStyle.tooltip(graphics, this.font, this.width, this.height, mouseX, mouseY,
                "Click to change the order.");
    }

    private void drawField(GuiGraphicsExtractor graphics, Frame f, int mouseX, int mouseY) {
        int x = f.fieldX;
        int y = f.buttonY;
        int w = f.fieldW;
        int h = ItemSearchStyle.BUTTON_H;
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, ItemSearchStyle.FIELD_RADIUS, ItemSearchStyle.FIELD);
        ModernGuiUtils.drawRect(graphics, x + 3, y + h - 1, w - 6, 1, ItemSearchStyle.accent());
        int textY = y + (h - 8) / 2;
        String text = input.getText();
        if (text.isEmpty()) {
            graphics.text(this.font, GuiFont.text("Search your items..."), x + 7, textY, ItemSearchStyle.TEXT_MUTED, false);
        }
        int start = visibleStart(text, w - 10);
        graphics.enableScissor(x + 2, y, x + w - 2, y + h);
        if (input.hasSelection()) {
            int sx = x + 5 + GuiFont.width(this.font, text.substring(start, Math.max(input.getSelectionStart(), start)));
            int ex = x + 5 + GuiFont.width(this.font, text.substring(start, Math.max(input.getSelectionEnd(), start)));
            ModernGuiUtils.drawRect(graphics, sx, textY - 1, ex - sx, 10, ItemSearchStyle.accentDim());
        }
        graphics.text(this.font, GuiFont.text(text.substring(start)), x + 5, textY, ItemSearchStyle.TEXT, false);
        if ((System.currentTimeMillis() - caretFrom) / 500 % 2 == 0) {
            int caretX = x + 5 + GuiFont.width(this.font, text.substring(start, Math.max(start, input.getCaret())));
            ModernGuiUtils.drawRect(graphics, caretX, textY - 1, 1, 10, ItemSearchStyle.TEXT);
        }
        graphics.disableScissor();
    }

    /** The first character shown, so the caret stays inside the field. */
    private int visibleStart(String text, int maxWidth) {
        int start = 0;
        int caret = input.getCaret();
        while (start < caret && GuiFont.width(this.font, text.substring(start, caret)) > maxWidth) start++;
        return start;
    }

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
        ModernGuiUtils.drawRect(graphics, f.sideX + SIDE_W + ItemSearchStyle.PAD / 2, f.sideY, 1, f.sideH, ItemSearchStyle.LINE);
    }

    private void drawSideRow(GuiGraphicsExtractor graphics, int x, int y, int rowH, ItemSearchSource source, String label, int count,
                             int mouseX, int mouseY) {
        boolean selected = source == category;
        boolean hover = inside(mouseX, mouseY, x, y, SIDE_W, rowH - 2);
        if (selected || hover) {
            ModernGuiUtils.drawRoundedRect(graphics, x, y, SIDE_W, rowH - 2, ItemSearchStyle.ROW_RADIUS,
                    selected ? ItemSearchStyle.ROW_SELECTED : ItemSearchStyle.ROW);
        }
        int textX = x + 6;
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
        graphics.text(this.font, GuiFont.text(ItemSearchStyle.ellipsize(this.font, label, x + SIDE_W - 8 - countW - textX)),
                textX, textY, selected ? ItemSearchStyle.accent() : hover ? ItemSearchStyle.TEXT : ItemSearchStyle.TEXT_SOFT, false);
        graphics.text(this.font, GuiFont.text(countText), x + SIDE_W - 5 - countW, textY, ItemSearchStyle.TEXT_MUTED, false);
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
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text(message[0]), cx, cy - 10, ItemSearchStyle.TEXT_SOFT);
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text(ItemSearchStyle.ellipsize(this.font, message[1], f.gridW)),
                    cx, cy + 2, ItemSearchStyle.TEXT_MUTED);
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
            ModernGuiUtils.drawRoundedRect(graphics, x, y, CELL, CELL, ItemSearchStyle.FIELD_RADIUS,
                    hover ? ItemSearchStyle.ROW_SELECTED : ItemSearchStyle.ROW);
            graphics.item(entry.stack, x + 2, y + 2);
            if (entry.total > 1) drawCount(graphics, ItemSearchMenus.shortCount(entry.total), x, y);
        }
        ItemSearchStyle.scrollFades(graphics, f.gridX - 1, f.gridY, f.gridW + 2, f.gridH, scrollY > 0.5, scrollY < maxScrollY - 0.5);
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
        ModernGuiUtils.drawRect(graphics, f.winX + ItemSearchStyle.PAD, y - 2, f.winW - ItemSearchStyle.PAD * 2, 1, ItemSearchStyle.LINE);
        int textY = y + (FOOTER_H - 8) / 2 - 1;
        String left;
        if (hovered != null) {
            left = action(hovered);
        } else {
            int n = shown().size();
            left = n + (n == 1 ? " item" : " items") + (input.isEmpty() ? "" : " match")
                    + " · Hover an item to see where it is, click it to go there.";
        }
        graphics.text(this.font, GuiFont.text(ItemSearchStyle.ellipsize(this.font, left, f.winW - ItemSearchStyle.PAD * 2 - 4)),
                f.winX + ItemSearchStyle.PAD + 2, textY, ItemSearchStyle.TEXT_MUTED, false);
    }

    /** The item's own tooltip, then how many there are and where. */
    private List<Component> tooltip(ItemSearchResults.Entry entry) {
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), entry.stack));
        lines.add(Component.empty());
        lines.add(Component.literal("Total: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format(Locale.ROOT, "%,d", entry.total)).withStyle(ChatFormatting.WHITE)));
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

        if (inside(mx, my, f.doneX, f.buttonY, DONE_W, ItemSearchStyle.BUTTON_H)) {
            ItemSearchStyle.click();
            onClose();
            return true;
        }
        if (inside(mx, my, f.sortX, f.buttonY, SORT_W, ItemSearchStyle.BUTTON_H)) {
            ItemSearchStyle.click();
            sort = sort.next();
            targetScrollY = 0;
            return true;
        }
        if (inside(mx, my, f.fieldX, f.buttonY, f.fieldW, ItemSearchStyle.BUTTON_H)) {
            caretFrom = System.currentTimeMillis();
            int offset = GuiFont.width(this.font, input.getText().substring(0, visibleStart(input.getText(), f.fieldW - 10)));
            input.setCaretFromX(mx - f.fieldX - 5 + offset, s -> GuiFont.width(this.font, s));
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
                ItemSearchStyle.click();
                go(list.get(index));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void pick(ItemSearchSource source) {
        if (source == category) return;
        ItemSearchStyle.click();
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
        return ItemSearchStyle.inside(mx, my, x, y, w, h);
    }
}
