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
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The Item Search window, in the manner of a launcher's search: it opens as a single search bar in
 * the upper part of the screen and only grows a result list once something is typed (or a place is
 * picked with Tab). The results are rows with the item, its name in its rarity's color, where it
 * lies and how many there are; the row picked with the arrow keys or the mouse is shown on the right
 * with every place it lies, each of which can be clicked to open that one. Enter opens the place with
 * the most of it and outlines the item there.
 */
public class ItemSearchScreen extends Screen {

    private static final int MARGIN = 8;
    private static final int WIN_W = 440;
    private static final int MIN_W = 280;
    private static final int BAR_H = 30;
    private static final int ROW_H = 24;
    private static final int MAX_ROWS = 8;
    private static final int MIN_ROWS = 4;
    private static final int FOOTER_H = 20;
    private static final int DETAIL_W = 150;
    /** Below this window width the detail pane is left out and the list takes the whole width. */
    private static final int DETAIL_MIN_WIN_W = 360;
    private static final int EMPTY_BODY_H = 46;
    private static final int PLACE_ROW_H = 13;

    /** The place picked last and the order, kept until the game restarts; null for everywhere. */
    private static ItemSearchSource lastCategory = null;
    private static ItemSearchResults.Sort lastSort = ItemSearchResults.Sort.AMOUNT;

    private final Screen parent;
    private final EditableText input = new EditableText(64);
    private ItemSearchSource category = lastCategory;
    private ItemSearchResults.Sort sort = lastSort;

    private List<ItemSearchResults.Entry> all = List.of();
    private List<ItemSearchResults.Entry> shown = List.of();
    private int allCount = 0;
    private String shownFor = null;
    private int pricesVersion = -1;
    private final Map<ItemSearchSource, ItemStack> icons = new EnumMap<>(ItemSearchSource.class);

    private int selected = 0;
    private double scrollY = 0;
    private double targetScrollY = 0;
    /** How far the result area is open, 0 for the bar alone and 1 for the full list. */
    private float expand = 0f;
    private float expandFrom = 0f;
    private int bodyFromH = 0;
    private long lastFrameMs = System.currentTimeMillis();
    private long openTimeMs = 0L;
    private long caretFrom = System.currentTimeMillis();
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;

    public ItemSearchScreen(String query, Screen parent) {
        super(GuiFont.text("Item Search"));
        this.parent = parent;
        if (query != null) input.setText(query);
        // Opened with a query (the key over an item), the list is there at once instead of growing.
        if (open()) expand = 1f;
    }

    // ---- Layout ---------------------------------------------------------------------------------

    private record Frame(int x, int y, int w, int barH, int bodyH, int fullBodyH, int rows,
                         int listX, int listW, int detailX, boolean detail, float textScale) {
        int bodyY() {
            return y + barH + 1;
        }

        int footerY() {
            return bodyY() + fullBodyH;
        }

        int fullH() {
            return barH + 1 + fullBodyH + FOOTER_H;
        }
    }

    /** Whether the result area shows: once something is typed, or a place is picked. */
    private boolean open() {
        return !input.getText().isBlank() || category != null;
    }

    private Frame frame() {
        int w = Math.max(MIN_W, Math.min(WIN_W, this.width - MARGIN * 2));
        int x = (this.width - w) / 2;
        int maxRows = Math.max(MIN_ROWS, Math.min(MAX_ROWS,
                (this.height - MARGIN * 2 - BAR_H - 1 - FOOTER_H - 8) / ROW_H));
        boolean empty = shown().isEmpty();
        int bodyH = empty ? EMPTY_BODY_H : maxRows * ROW_H + 8;
        // Where the bar sits: in the upper part of the screen, but high enough for the open list to fit.
        int fullH = BAR_H + 1 + (maxRows * ROW_H + 8) + FOOTER_H;
        int y = Math.max(MARGIN, Math.min(Math.round(this.height * 0.22f), this.height - MARGIN - fullH));
        boolean detail = w >= DETAIL_MIN_WIN_W && !empty;
        int listX = x + 4;
        int listW = (detail ? w - DETAIL_W : w) - 8;
        int detailX = x + w - DETAIL_W;
        return new Frame(x, y, w, BAR_H, bodyH, bodyH, maxRows, listX, listW, detailX, detail, textScale());
    }

    /**
     * The query is drawn larger than the rest, at a size that still lands on whole screen pixels, so
     * the font stays crisp: 1.5 at GUI scale 2, 1.33 at 3, 1.25 at 4.
     */
    private static float textScale() {
        int gui = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        return gui == 1 ? 2f : (gui + 1f) / gui;
    }

    @Override
    protected void init() {
        if (openTimeMs == 0L) openTimeMs = System.currentTimeMillis();
        if (AlpakaConfig.instance.allowApiCalls) ItemPrices.refresh();
        reload();
    }

    /** Reads everything again: the list for the chosen place and how many items are known in all. */
    private void reload() {
        List<ItemSearchResults.Entry> everything = ItemSearchResults.collect(null);
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
            if (!key.equals(shownFor)) {
                selected = 0;
                targetScrollY = 0;
            }
            shownFor = key;
            pricesVersion = version;
        }
        return shown;
    }

    private ItemSearchResults.Entry selectedEntry() {
        List<ItemSearchResults.Entry> list = shown();
        if (list.isEmpty()) return null;
        return list.get(Math.max(0, Math.min(list.size() - 1, selected)));
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
            hoverSelect(f, mouseX, mouseY);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }

        drawBar(graphics, f, mouseX, mouseY);
        ItemSearchResults.Entry tooltipFor = null;
        boolean hoverSort = false;
        if (extra > 1) {
            graphics.enableScissor(f.x, f.y + f.barH, f.x + f.w, f.y + panelH);
            ModernGuiUtils.drawRect(graphics, f.x + AlpakaStyle.PAD, f.y + f.barH, f.w - AlpakaStyle.PAD * 2, 1, AlpakaStyle.LINE);
            tooltipFor = drawBody(graphics, f, bodyH, mouseX, mouseY, deltaSec);
            hoverSort = drawFooter(graphics, f, f.bodyY() + bodyH, mouseX, mouseY);
            graphics.disableScissor();
        }

        if (pushed) graphics.pose().popMatrix();
        if (tooltipFor != null) graphics.setComponentTooltipForNextFrame(this.font, tooltip(tooltipFor), mouseX, mouseY);
        else if (hoverSort) AlpakaStyle.tooltip(graphics, this.font, this.width, this.height, mouseX, mouseY,
                sort == ItemSearchResults.Sort.VALUE && !AlpakaConfig.instance.allowApiCalls
                        ? "Value needs Network Features, which are off." : "Click to change the order.");
    }

    /** The opening pop, around the bar rather than the screen's centre, since the bar sits high up. */
    private boolean beginOpen(GuiGraphicsExtractor graphics, Frame f) {
        float scale = PloppAnimation.getOpenScale(openTimeMs);
        if (scale >= 0.999f) return false;
        graphics.pose().pushMatrix();
        graphics.pose().scaleAround(scale, scale, f.x + f.w / 2.0f, f.y + f.barH / 2.0f);
        return true;
    }

    private void drawBar(GuiGraphicsExtractor graphics, Frame f, int mouseX, int mouseY) {
        int cy = f.y + f.barH / 2;
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

        String countText = open() ? countLabel(shown().size(), "result") : countLabel(allCount, "item");
        int countW = GuiFont.width(this.font, countText);
        int countX = f.x + f.w - 12 - countW;
        graphics.text(this.font, GuiFont.text(countText), countX, cy - 4, AlpakaStyle.TEXT_MUTED, false);

        float s = f.textScale;
        int fieldRight = countX - 10;
        int maxW = Math.max(10, Math.round((fieldRight - textX) / s));
        String text = input.getText();
        int start = visibleStart(text, maxW);
        int glyphH = Math.round(8 * s);
        int textY = cy - glyphH / 2;

        graphics.enableScissor(textX - 1, f.y, fieldRight, f.y + f.barH);
        graphics.pose().pushMatrix();
        graphics.pose().translate(textX, textY);
        graphics.pose().scale(s, s);
        if (text.isEmpty()) {
            String hint = category == null ? "Search your items" : "Search " + category.label;
            graphics.text(this.font, GuiFont.text(hint), 0, 0, AlpakaStyle.TEXT_MUTED, false);
        }
        if (input.hasSelection()) {
            int sx = GuiFont.width(this.font, text.substring(start, Math.max(input.getSelectionStart(), start)));
            int ex = GuiFont.width(this.font, text.substring(start, Math.max(input.getSelectionEnd(), start)));
            ModernGuiUtils.drawRect(graphics, sx, -1, ex - sx, 10, AlpakaStyle.accentDim());
        }
        graphics.text(this.font, GuiFont.text(text.substring(start)), 0, 0, AlpakaStyle.TEXT, false);
        graphics.pose().popMatrix();
        if ((System.currentTimeMillis() - caretFrom) / 500 % 2 == 0) {
            int caretX = textX + Math.round(s * GuiFont.width(this.font, text.substring(start, Math.max(start, input.getCaret()))));
            ModernGuiUtils.drawRect(graphics, caretX, textY - 1, 1, glyphH + 2, AlpakaStyle.accent());
        }
        graphics.disableScissor();
    }

    private static String countLabel(int n, String word) {
        return String.format(Locale.ROOT, "%,d", n) + " " + word + (n == 1 ? "" : "s");
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

    /** The result list and the detail pane; returns the entry whose item tooltip is to be shown. */
    private ItemSearchResults.Entry drawBody(GuiGraphicsExtractor graphics, Frame f, int bodyH, int mouseX, int mouseY,
                                             float deltaSec) {
        List<ItemSearchResults.Entry> list = shown();
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

        double maxScroll = Math.max(0, list.size() * ROW_H - listH);
        targetScrollY = Math.max(0, Math.min(maxScroll, targetScrollY));
        scrollY += (targetScrollY - scrollY) * Math.min(1.0, deltaSec * 16.0);

        ItemSearchResults.Entry tooltipFor = null;
        graphics.enableScissor(f.listX, top, f.listX + f.listW, top + listH);
        int first = Math.max(0, (int) (scrollY / ROW_H));
        for (int i = first; i < list.size(); i++) {
            int y = top + i * ROW_H - (int) Math.round(scrollY);
            if (y > top + listH) break;
            ItemSearchResults.Entry entry = list.get(i);
            boolean isSelected = i == selected;
            if (isSelected) {
                ModernGuiUtils.drawRoundedRect(graphics, f.listX, y, f.listW, ROW_H, AlpakaStyle.ROW_RADIUS + 1, AlpakaStyle.ROW_SELECTED);
                ModernGuiUtils.drawRoundedRect(graphics, f.listX + 1, y + 6, 2, ROW_H - 12, 1, AlpakaStyle.accent());
            }
            graphics.item(entry.stack, f.listX + 6, y + (ROW_H - 16) / 2);
            if (inside(mouseX, mouseY, f.listX + 6, y + 4, 16, 16) && inside(mouseX, mouseY, f.listX, top, f.listW, listH)) {
                tooltipFor = entry;
            }

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
        AlpakaStyle.scrollFades(graphics, f.listX, top, f.listW, listH, scrollY > 0.5, scrollY < maxScroll - 0.5);
        graphics.disableScissor();

        if (f.detail) {
            ItemSearchResults.Entry detailTooltip = drawDetail(graphics, f, bodyH, mouseX, mouseY);
            if (detailTooltip != null) tooltipFor = detailTooltip;
        }
        return tooltipFor;
    }

    /** Where an entry lies, in a few words: "Mining Sack", "Ender Chest 1 and 2 more". */
    private static String placesLine(ItemSearchResults.Entry entry) {
        String best = entry.best().name();
        int more = entry.places.size() - 1;
        return more <= 0 ? best : best + " and " + more + (more == 1 ? " more" : " more places");
    }

    /** The detail pane for the picked entry: a large icon, its name and rarity, the total and every place. */
    private ItemSearchResults.Entry drawDetail(GuiGraphicsExtractor graphics, Frame f, int bodyH, int mouseX, int mouseY) {
        ItemSearchResults.Entry entry = selectedEntry();
        if (entry == null) return null;
        int x = f.detailX;
        int top = f.bodyY();
        ModernGuiUtils.drawRect(graphics, x, top + 8, 1, bodyH - 16, AlpakaStyle.LINE);
        int innerX = x + 10;
        int innerW = DETAIL_W - 18;
        int cx = x + DETAIL_W / 2;

        int iconY = top + 10;
        drawIcon(graphics, entry.stack, cx - 16, iconY, 2f);
        ItemSearchResults.Entry tooltipFor = inside(mouseX, mouseY, cx - 16, iconY, 32, 32) ? entry : null;

        int y = iconY + 38;
        ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, entry.name, innerW)),
                cx, y, nameColor(entry.stack));
        y += 11;
        String rarity = rarityLine(entry);
        if (rarity != null) {
            ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, rarity, innerW)),
                    cx, y, AlpakaStyle.TEXT_MUTED);
            y += 11;
        }
        y += 4;
        y = detailRow(graphics, innerX, innerW, y, "Total", String.format(Locale.ROOT, "%,d", entry.total), AlpakaStyle.TEXT);
        double value = value(entry);
        if (!Double.isNaN(value)) {
            y = detailRow(graphics, innerX, innerW, y, "Value", ItemSearchMenus.shortCount(Math.round(value)) + " coins", 0xFFFFC94D);
        }
        y += 4;
        ModernGuiUtils.drawRect(graphics, innerX, y, innerW, 1, AlpakaStyle.LINE);
        y += 5;

        int bottom = top + bodyH - 6;
        int room = Math.max(0, (bottom - y) / PLACE_ROW_H);
        int count = entry.places.size();
        int listed = count > room ? Math.max(0, room - 1) : count;
        for (int i = 0; i < listed; i++) {
            ItemSearchResults.Place place = entry.places.get(i);
            boolean hover = inside(mouseX, mouseY, innerX - 3, y, innerW + 6, PLACE_ROW_H);
            if (hover) ModernGuiUtils.drawRoundedRect(graphics, innerX - 3, y, innerW + 6, PLACE_ROW_H, AlpakaStyle.ROW_RADIUS,
                    AlpakaStyle.ROW_HOVER);
            drawIcon(graphics, icon(place.source()), innerX, y + 2, 0.5625f);
            String n = ItemSearchMenus.shortCount(place.count());
            int nW = GuiFont.width(this.font, n);
            graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, place.name(), innerW - 14 - nW - 4)),
                    innerX + 13, y + 3, hover ? AlpakaStyle.accent() : AlpakaStyle.TEXT_SOFT, false);
            graphics.text(this.font, GuiFont.text(n), innerX + innerW - nW, y + 3, AlpakaStyle.TEXT_MUTED, false);
            y += PLACE_ROW_H;
        }
        if (listed < count) {
            graphics.text(this.font, GuiFont.text("and " + (count - listed) + " more places"), innerX + 13, y + 3,
                    AlpakaStyle.TEXT_MUTED, false);
        }
        return tooltipFor;
    }

    private int detailRow(GuiGraphicsExtractor graphics, int x, int w, int y, String label, String value, int color) {
        graphics.text(this.font, GuiFont.text(label), x, y, AlpakaStyle.TEXT_MUTED, false);
        int vw = GuiFont.width(this.font, value);
        graphics.text(this.font, GuiFont.text(value), x + w - vw, y, color, false);
        return y + 11;
    }

    /** What all of an entry is worth, NaN without a price or with Network Features off. */
    private static double value(ItemSearchResults.Entry entry) {
        if (!Double.isNaN(entry.value)) return entry.value;
        if (!AlpakaConfig.instance.allowApiCalls) return Double.NaN;
        ItemPrices.Quote quote = ItemPrices.quote(entry.id);
        return quote == null ? Double.NaN : quote.price() * entry.total;
    }

    /** The rarity line at the end of the lore, such as "LEGENDARY DUNGEON BOW", if the item has one. */
    private static String rarityLine(ItemSearchResults.Entry entry) {
        for (int i = entry.lore.size() - 1; i >= 0; i--) {
            String line = entry.lore.get(i).trim();
            if (line.isEmpty()) continue;
            return line.equals(line.toUpperCase(Locale.ROOT)) && line.chars().anyMatch(Character::isLetter) ? line : null;
        }
        return null;
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
                net.minecraft.network.chat.TextColor legacy = format == null ? null : net.minecraft.network.chat.TextColor.fromLegacyFormat(format);
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

    /** The key hints along the bottom and the order on the right; returns whether the order is hovered. */
    private boolean drawFooter(GuiGraphicsExtractor graphics, Frame f, int y, int mouseX, int mouseY) {
        ModernGuiUtils.drawRect(graphics, f.x + AlpakaStyle.PAD, y, f.w - AlpakaStyle.PAD * 2, 1, AlpakaStyle.LINE);
        int cy = y + 1 + (FOOTER_H - 1) / 2;
        int x = f.x + 10;
        x = hint(graphics, x, cy, "↑↓", "Select");
        x = hint(graphics, x, cy, "Enter", "Open");
        x = hint(graphics, x, cy, "Tab", category == null ? "Place" : "Next Place");

        String sortText = "Sort: " + sort.label;
        int sortW = GuiFont.width(this.font, sortText) + 10;
        int sortX = f.x + f.w - 8 - sortW;
        int sortY = cy - 6;
        boolean hover = inside(mouseX, mouseY, sortX, sortY, sortW, 12);
        if (hover) ModernGuiUtils.drawRoundedRect(graphics, sortX, sortY, sortW, 12, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW_HOVER);
        graphics.text(this.font, GuiFont.text(sortText), sortX + 5, cy - 4, hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_MUTED, false);
        return hover;
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

    /** The item's own tooltip, then how many there are and where. */
    private List<Component> tooltip(ItemSearchResults.Entry entry) {
        List<Component> lines = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), entry.stack));
        lines.add(Component.empty());
        lines.add(Component.literal("Total: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(String.format(Locale.ROOT, "%,d", entry.total)).withStyle(ChatFormatting.WHITE)));
        lines.add(Component.literal(action(entry.best())).withStyle(ChatFormatting.YELLOW));
        return lines;
    }

    /** What opening the place does, in words. */
    private String action(ItemSearchResults.Place place) {
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
        if (!open() || shown().isEmpty()) return -1;
        int top = f.bodyY() + 4;
        int listH = f.bodyH - 8;
        if (!inside(mx, my, f.listX, top, f.listW, listH)) return -1;
        int index = (int) ((my - top + scrollY) / ROW_H);
        return index >= 0 && index < shown().size() ? index : -1;
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
            caretFrom = System.currentTimeMillis();
            int textX = f.x + 28 + (category == null ? 0 : GuiFont.width(this.font, category.label) + 30);
            if (category != null && mx < textX) {
                // The place pill: clicking it goes back to searching everywhere.
                pick(null);
                return true;
            }
            int offset = GuiFont.width(this.font, input.getText().substring(0, visibleStart(input.getText(),
                    Math.round((f.w - 28 - (textX - f.x)) / f.textScale))));
            input.setCaretFromX((int) ((mx - textX) / f.textScale) + offset, s -> GuiFont.width(this.font, s));
            return true;
        }
        if (!open()) {
            // A click beside the bar closes it, as a click outside a launcher's search does.
            onClose();
            return true;
        }

        int row = rowAt(f, mx, my);
        if (row >= 0) {
            AlpakaStyle.click();
            ItemSearchResults.Entry entry = shown().get(row);
            go(entry, entry.best());
            return true;
        }

        int footerY = f.footerY();
        String sortText = "Sort: " + sort.label;
        int sortW = GuiFont.width(this.font, sortText) + 10;
        int cy = footerY + 1 + (FOOTER_H - 1) / 2;
        if (inside(mx, my, f.x + f.w - 8 - sortW, cy - 6, sortW, 12)) {
            AlpakaStyle.click();
            sort = sort.next();
            return true;
        }

        if (f.detail) {
            ItemSearchResults.Place place = placeAt(f, mx, my);
            if (place != null) {
                AlpakaStyle.click();
                go(selectedEntry(), place);
                return true;
            }
        }
        if (!inside(mx, my, f.x, f.y, f.w, f.fullH())) {
            onClose();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** The place row of the detail pane under the mouse, or null; laid out as {@link #drawDetail} draws it. */
    private ItemSearchResults.Place placeAt(Frame f, double mx, double my) {
        ItemSearchResults.Entry entry = selectedEntry();
        if (entry == null) return null;
        int innerX = f.detailX + 10;
        int innerW = DETAIL_W - 18;
        int y = f.bodyY() + 10 + 38 + 11 + (rarityLine(entry) != null ? 11 : 0) + 4 + 11
                + (Double.isNaN(value(entry)) ? 0 : 11) + 4 + 5;
        int room = Math.max(0, (f.bodyY() + f.bodyH - 6 - y) / PLACE_ROW_H);
        int count = entry.places.size();
        int listed = count > room ? Math.max(0, room - 1) : count;
        for (int i = 0; i < listed; i++) {
            if (inside(mx, my, innerX - 3, y, innerW + 6, PLACE_ROW_H)) return entry.places.get(i);
            y += PLACE_ROW_H;
        }
        return null;
    }

    private void pick(ItemSearchSource source) {
        if (source == category) return;
        AlpakaStyle.click();
        category = source;
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
        Minecraft mc = Minecraft.getInstance();
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

    private void moveSelection(int by) {
        List<ItemSearchResults.Entry> list = shown();
        if (list.isEmpty()) return;
        selected = Math.max(0, Math.min(list.size() - 1, selected + by));
        // Keeps the picked row in view.
        Frame f = frame();
        int listH = f.bodyH - 8;
        double rowTop = selected * ROW_H;
        if (rowTop < targetScrollY) targetScrollY = rowTop;
        else if (rowTop + ROW_H > targetScrollY + listH) targetScrollY = rowTop + ROW_H - listH;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollYAmount) {
        if (scrollYAmount != 0 && open()) {
            targetScrollY = Math.max(0, targetScrollY - scrollYAmount * ROW_H * 2);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollYAmount);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int codePoint = event.codepoint();
        if (Character.isBmpCodePoint(codePoint) && input.insert((char) codePoint)) {
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
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> {
                // Escape first empties the search, as a launcher's does, and closes the window after that.
                if (!input.isEmpty() || category != null) {
                    input.setText("");
                    pick(null);
                    caretFrom = System.currentTimeMillis();
                } else {
                    onClose();
                }
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                ItemSearchResults.Entry entry = open() ? selectedEntry() : null;
                if (entry != null) go(entry, entry.best());
                return true;
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
        if (input.keyPressed(key, event.hasControlDownWithQuirk(), event.hasShiftDown())) {
            caretFrom = System.currentTimeMillis();
            return true;
        }
        return super.keyPressed(event);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return AlpakaStyle.inside(mx, my, x, y, w, h);
    }
}
