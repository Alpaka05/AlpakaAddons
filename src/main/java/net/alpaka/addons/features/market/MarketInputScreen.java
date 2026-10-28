package net.alpaka.addons.features.market;

import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.EditableText;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.mixin.AbstractSignEditScreenAccessor;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.StringUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * An input bar in place of the sign Hypixel opens to ask for a search, a price or an amount.
 *
 * <h2>Only the look changes</h2>
 *
 * This is vanilla's sign editor with its drawing replaced, and the answer reaches Hypixel the way
 * it always does: vanilla sends the sign's four lines when the editor closes. Nothing here sends
 * anything. Confirming writes the answer into the sign's answer lines and closes the editor, as the
 * sign's Done button does (see {@link #close} for why not through Done itself). Each line stays
 * within the sign's width as vanilla's own editor measures it, and an answer longer than one line is
 * broken at a space (see {@link SignPrompt#split}) - so the lines are always ones a player could
 * have typed into the vanilla sign. A price or an amount goes in plain digits, "4000000" for "4m"
 * (see {@link SignPrompt#answer}). Cancel and Escape empty the answer first, which Hypixel takes as
 * no answer. The chips only fill the field; the player still confirms.
 */
public class MarketInputScreen extends SignEditScreen {
    private static final int HEADER_H = AlpakaStyle.HEADER_H;
    private static final int PAD = AlpakaStyle.PAD;
    private static final int FIELD_H = 24;
    private static final int CHIP_H = 14;
    private static final int COIN = 0xFFFFAA00;

    private final SignPrompt prompt;
    private final MarketKind context;
    /** The market menu the sign was opened from, for the heading. */
    private final String menuTitle = Markets.lastMenuTitle();
    private long openTimeMs = 0L;
    private long caretFrom = System.currentTimeMillis();
    /** When typing last ran into what the sign can hold, to flash the field. */
    private long fullAtMs = 0L;
    private boolean closing = false;
    /**
     * The answer as one line, however many sign lines it takes. Kept to what fits the sign: an edit
     * that would not is refused.
     */
    private final EditableText input = new EditableText(64);
    private boolean loaded = false;
    /** Text for the field before it exists, for the screenshot tour. */
    private String pendingText = null;

    private final List<Hit> hits = new ArrayList<>();
    /** The item being put up for auction, for a price sign that asks what it sells for; else null. */
    private final LowestBin.Item selling;

    // ---- Suggestions, for a search ---------------------------------------------------------------

    /** Rows the list shows at once; the rest are a scroll away. */
    private static final int SUGGESTION_ROWS = 6;
    /** At most this many suggestions: beyond that, typing on finds an item sooner than scrolling. */
    private static final int MAX_SUGGESTIONS = 60;
    private static final int SUGGESTION_H = 19;
    private static final int SUGGESTION_FOOTER_H = 14;

    /** One name offered under the field, with the stretches of it that matched. */
    private record Suggestion(String name, int[] ranges, boolean recent) {}

    private List<Suggestion> suggestions = List.of();
    /** Whether more names matched than {@link #MAX_SUGGESTIONS}. */
    private boolean moreMatches = false;
    /** How far the list is scrolled, in pixels: where it is and where it glides to. */
    private double listScroll = 0;
    private double listScrollTarget = 0;
    private int listScrollMax = 0;
    /** The rows the list showed last frame, for the arrow keys to keep the chosen one in view. */
    private int visibleRows = SUGGESTION_ROWS;
    private int lastListOffset = 0;
    private long lastFrameMs = 0L;
    /** What the suggestions were worked out for: the text and how many names there were to search. */
    private String suggestedFor = null;
    /** SkyCofl's item names are still on their way. */
    private boolean namesPending = false;
    /** The suggestion picked with the arrow keys or the mouse, -1 for none. */
    private int highlight = -1;
    /** The text the list was closed for with Escape; it opens again once the text changes. */
    private String dismissedFor = null;
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;

    private record Hit(int x, int y, int w, int h, java.util.function.DoubleConsumer action, boolean sound) {
        boolean contains(double mx, double my) {
            return AlpakaStyle.inside(mx, my, x, y, w, h);
        }
    }

    public MarketInputScreen(SignBlockEntity sign, boolean isFrontText, boolean filtered, SignPrompt prompt, MarketKind context) {
        super(sign, isFrontText, filtered);
        this.prompt = prompt;
        this.context = context;
        this.selling = prompt.type() == SignPrompt.Type.PRICE && prompt.auctionPrice() ? LowestBin.selling() : null;
    }

    /**
     * Opens the input bar for a sign Hypixel is opening, if it is one of its prompts. Returns false,
     * leaving the sign to vanilla, for every other sign.
     */
    public static boolean tryOpen(SignBlockEntity sign, boolean isFrontText) {
        if (!Markets.signInput() || sign instanceof HangingSignBlockEntity) return false;
        Minecraft mc = Minecraft.getInstance();
        boolean filtered = mc.isTextFilteringEnabled();
        String[] lines = new String[4];
        for (int i = 0; i < 4; i++) {
            lines[i] = SkyblockUtils.cleanColor(sign.getText(isFrontText).getMessage(i, filtered).getString());
        }
        SignPrompt prompt = SignPrompt.of(lines);
        if (prompt == null) return false;
        mc.gui.setScreen(new MarketInputScreen(sign, isFrontText, filtered, prompt, Markets.recentContext(mc.gui.screen())));
        return true;
    }

    public MarketKind context() {
        return context;
    }

    @Override
    protected void init() {
        super.init();
        // Vanilla's Done button: the bar has its own buttons.
        clearWidgets();
        if (openTimeMs == 0L) openTimeMs = System.currentTimeMillis();
        if (!loaded) {
            loaded = true;
            input.setText(initialAnswer());
        }
        if (pendingText != null) {
            replace(pendingText);
            pendingText = null;
        }
    }

    /** The sign's four lines, which vanilla sends when the editor closes. */
    private String[] lines() {
        return ((AbstractSignEditScreenAccessor) this).alpaka$messages();
    }

    /** What the answer lines already hold, a previous search say, joined as Hypixel joins them. */
    private String initialAnswer() {
        String[] lines = lines();
        StringBuilder answer = new StringBuilder();
        for (int i = 0; i < prompt.inputLines(); i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isEmpty()) continue;
            if (!answer.isEmpty()) answer.append(' ');
            answer.append(line);
        }
        return answer.toString();
    }

    private String text() {
        return input.getText();
    }

    /** The answer as the sign's lines, measured as vanilla's sign editor measures them; null if it does not fit. */
    private String[] split(String text) {
        return prompt.split(text, this.font::width, this.sign.getMaxTextLineWidth());
    }

    /** Runs an edit, and takes it back if the answer no longer fits the sign. */
    private void edit(Runnable change) {
        String before = input.getText();
        int caret = input.getCaret();
        change.run();
        if (!input.getText().equals(before) && split(input.getText()) == null) {
            input.setText(before);
            input.setCaret(caret);
            fullAtMs = System.currentTimeMillis();
        }
        caretFrom = System.currentTimeMillis();
    }

    // ---- Geometry -------------------------------------------------------------------------------

    private int panelW() {
        return Math.max(220, Math.min(330, this.width - 24));
    }

    private int panelH() {
        return HEADER_H + 10 + FIELD_H + 6 + 10 + (selling != null ? CHIP_H + 8 : 0) + (hasChips() ? CHIP_H + 8 : 0) + 8 + AlpakaStyle.BUTTON_H + 10;
    }

    private boolean hasChips() {
        return !chips().isEmpty();
    }

    /** What the chips put into the field: recent searches, or common amounts and suffixes. */
    private List<String> chips() {
        return switch (prompt.type()) {
            case SEARCH -> Markets.recentSearches(context);
            // Stack sizes make sense for the Bazaar's orders; an amount elsewhere, coins for the
            // bank say, gets no suggestions.
            case AMOUNT -> context == MarketKind.BAZAAR ? List.of("64", "160", "640", "1k") : List.of();
            case PRICE -> List.of("k", "m", "b");
            default -> List.of();
        };
    }

    // ---- Drawing --------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        AlpakaStyle.backdrop(graphics, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int realMouseX, int realMouseY, float partialTick) {
        hits.clear();
        boolean scaling = AlpakaStyle.beginOpen(graphics, openTimeMs, this.width, this.height);
        int w = panelW();
        int h = panelH();
        int x = (this.width - w) / 2;
        // A little above the middle, where the eye already is after clicking a menu's search.
        int y = Math.max(8, (this.height - h) / 2 - 20);

        // The suggestion list hangs under the field, over whatever is below it, which then takes no mouse.
        updateSuggestions();
        int fx = x + PAD + 2;
        int fw = w - PAD * 2 - 4;
        int fy = y + HEADER_H + 10;
        int[] list = dropdownOpen() ? dropdownRect(fx, fy + FIELD_H, fw) : null;
        boolean overList = list != null && AlpakaStyle.inside(realMouseX, realMouseY, list[0], list[1], list[2], list[3]);
        int mouseX = overList ? -10000 : realMouseX;
        int mouseY = overList ? -10000 : realMouseY;

        AlpakaStyle.panel(graphics, x, y, w, h);

        // Header: what is asked, and a close button.
        AlpakaStyle.title(graphics, this.font, AlpakaStyle.ellipsize(this.font, prompt.heading(context, menuTitle), w - 50), x + PAD + 2, y);
        int closeX = x + w - PAD - 16;
        int closeY = y + (HEADER_H - 16) / 2;
        boolean hoverClose = AlpakaStyle.inside(mouseX, mouseY, closeX, closeY, 16, 16);
        if (hoverClose) ModernGuiUtils.drawRoundedRect(graphics, closeX, closeY, 16, 16, AlpakaStyle.FIELD_RADIUS, MarketIcons.fade(AlpakaStyle.DANGER, 0.2f));
        MarketIcons.draw(graphics, MarketIcons.Glyph.CLOSE, closeX + 4, closeY + 4, 8, hoverClose ? AlpakaStyle.DANGER : AlpakaStyle.TEXT_MUTED);
        hits.add(new Hit(closeX, closeY, 16, 16, mx -> cancel(), true));
        AlpakaStyle.headerLine(graphics, x, y, w);

        // The field; with suggestions open it is drawn last, as the top of one box with the list.
        if (list == null) drawField(graphics, mouseX, mouseY, fx, fy, fw, 0);

        // Under it: Hypixel's own question on the left, the amount written out on the right.
        int hy = fy + FIELD_H + 6;
        String question = prompt.subtitle(menuTitle);
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, question, fw / 2)), fx + 2, hy, AlpakaStyle.TEXT_MUTED, false);
        drawReading(graphics, fx + fw - 2, hy);

        int cy = hy + 10 + 8;
        if (selling != null) {
            drawLowestBin(graphics, mouseX, mouseY, fx, cy, fw);
            cy += CHIP_H + 8;
        }
        if (hasChips()) {
            drawChips(graphics, mouseX, mouseY, fx, cy, fw);
            cy += CHIP_H + 8;
        }

        // Buttons: Cancel and the confirm, on the right.
        int bh = AlpakaStyle.BUTTON_H;
        String confirm = prompt.confirmLabel();
        int confirmW = Math.max(64, GuiFont.width(this.font, confirm) + 24);
        int cancelW = Math.max(56, GuiFont.width(this.font, "Cancel") + 20);
        int confirmX = x + w - PAD - confirmW;
        int cancelX = confirmX - 6 - cancelW;
        boolean hoverConfirm = AlpakaStyle.inside(mouseX, mouseY, confirmX, cy, confirmW, bh);
        boolean hoverCancel = AlpakaStyle.inside(mouseX, mouseY, cancelX, cy, cancelW, bh);
        AlpakaStyle.button(graphics, this.font, cancelX, cy, cancelW, bh, "Cancel", hoverCancel, AlpakaStyle.ButtonKind.PLAIN);
        AlpakaStyle.button(graphics, this.font, confirmX, cy, confirmW, bh, confirm, hoverConfirm, AlpakaStyle.ButtonKind.PRIMARY);
        hits.add(new Hit(cancelX, cy, cancelW, bh, mx -> cancel(), true));
        hits.add(new Hit(confirmX, cy, confirmW, bh, mx -> confirm(), true));
        graphics.text(this.font, GuiFont.text("Enter to confirm · Esc to cancel"), x + PAD + 2, cy + (bh - 8) / 2,
                AlpakaStyle.DIVIDER, false);

        if (list != null) {
            // Over everything drawn so far, the buttons' text included.
            graphics.nextStratum();
            ModernGuiUtils.drawPanelShadow(graphics, fx, fy, fw, FIELD_H + list[3], 6, 0.8f);
            drawField(graphics, realMouseX, realMouseY, fx, fy, fw, list[3]);
            drawDropdown(graphics, realMouseX, realMouseY, list);
        }
        lastMouseX = realMouseX;
        lastMouseY = realMouseY;

        AlpakaStyle.endOpen(graphics, scaling);
    }

    // ---- Suggestions ----------------------------------------------------------------------------

    /**
     * Works the suggestions out again when the text, or the names to search, changed: matching
     * recent searches first, then item names from SkyCofl's list for this market. Only names that
     * fit the sign are offered, so picking one always goes through.
     */
    private void updateSuggestions() {
        if (prompt.type() != SignPrompt.Type.SEARCH) return;
        String text = text();
        boolean online = AlpakaConfig.instance.allowApiCalls;
        List<String> names = online ? ItemTags.names(context) : null;
        namesPending = online && names == null && ItemTags.loading();
        String key = text + "|" + (names == null ? -1 : names.size());
        if (key.equals(suggestedFor)) return;
        suggestedFor = key;
        highlight = -1;
        listScroll = listScrollTarget = 0;
        moreMatches = false;
        List<Suggestion> out = new ArrayList<>();
        if (!text.isBlank()) {
            java.util.Set<String> taken = new java.util.HashSet<>();
            for (SearchSuggestions.Match m : SearchSuggestions.rank(text, Markets.recentSearches(context), 3)) {
                if (split(m.name()) == null) continue;
                out.add(new Suggestion(m.name(), m.ranges(), true));
                taken.add(m.name().toLowerCase(java.util.Locale.ROOT));
            }
            if (names != null) {
                for (SearchSuggestions.Match m : SearchSuggestions.rank(text, names, MAX_SUGGESTIONS * 2)) {
                    if (taken.contains(m.name().toLowerCase(java.util.Locale.ROOT)) || split(m.name()) == null) continue;
                    if (out.size() >= MAX_SUGGESTIONS) {
                        moreMatches = true;
                        break;
                    }
                    out.add(new Suggestion(m.name(), m.ranges(), false));
                }
            }
        }
        suggestions = out;
    }

    /** Whether the list shows: a search being typed, not closed with Escape, with something to offer. */
    private boolean dropdownOpen() {
        if (prompt.type() != SignPrompt.Type.SEARCH) return false;
        String text = text();
        if (text.isBlank() || text.equals(dismissedFor)) return false;
        return !suggestions.isEmpty() || namesPending;
    }

    /** The list's place: straight under the field, up to six rows, as many as fit on the screen. */
    private int[] dropdownRect(int x, int y, int w) {
        int rows = Math.max(1, Math.min(SUGGESTION_ROWS, suggestions.size()));
        int room = (this.height - 6 - y - SUGGESTION_FOOTER_H - 8) / SUGGESTION_H;
        rows = Math.max(1, Math.min(rows, room));
        return new int[] {x, y, w, 3 + rows * SUGGESTION_H + SUGGESTION_FOOTER_H + 2};
    }

    /**
     * The suggestions, inside the box the field opened into: each with its item, its name and the
     * match lit. More than fit scroll, with a bar at the side.
     */
    private void drawDropdown(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int[] rect) {
        int x = rect[0];
        int y = rect[1];
        int w = rect[2];
        int h = rect[3];
        hits.add(new Hit(x, y, w, h, mx -> { }, false));
        int rows = (h - 3 - SUGGESTION_FOOTER_H - 2) / SUGGESTION_H;
        visibleRows = Math.max(1, rows);
        int accent = AlpakaStyle.accent();

        int listY = y + 2;
        int listH = rows * SUGGESTION_H;
        listScrollMax = Math.max(0, suggestions.size() * SUGGESTION_H - listH);
        long now = System.currentTimeMillis();
        float deltaSec = lastFrameMs == 0L ? 0f : Math.min(0.1f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
        listScrollTarget = Math.max(0, Math.min(listScrollMax, listScrollTarget));
        listScroll += (listScrollTarget - listScroll) * Math.min(1.0f, deltaSec * 14.0f);
        if (Math.abs(listScrollTarget - listScroll) < 0.05) listScroll = listScrollTarget;
        int offset = (int) Math.round(listScroll);
        // The row under a still mouse changes as the list scrolls; it takes the highlight then too.
        boolean moved = mouseX != lastMouseX || mouseY != lastMouseY || offset != lastListOffset;
        lastListOffset = offset;
        boolean inList = AlpakaStyle.inside(mouseX, mouseY, x, listY, w, listH);

        if (suggestions.isEmpty()) {
            graphics.text(this.font, GuiFont.text("Loading item names…"), x + 28, y + 2 + (SUGGESTION_H - 8) / 2,
                    AlpakaStyle.TEXT_MUTED, false);
        }
        graphics.enableScissor(x + 1, listY, x + w - 1, listY + listH);
        int first = offset / SUGGESTION_H;
        int last = Math.min(suggestions.size() - 1, (offset + listH - 1) / SUGGESTION_H);
        for (int i = first; i <= last; i++) {
            Suggestion s = suggestions.get(i);
            int ry = listY + i * SUGGESTION_H - offset;
            int rx = x + 3;
            int rw = w - (listScrollMax > 0 ? 10 : 6);
            boolean hover = inList && AlpakaStyle.inside(mouseX, mouseY, rx, ry, rw, SUGGESTION_H);
            if (hover && moved) highlight = i;
            boolean lit = i == highlight;
            if (lit) {
                ModernGuiUtils.drawRoundedRect(graphics, rx, ry, rw, SUGGESTION_H, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW_SELECTED);
                ModernGuiUtils.drawRoundedRect(graphics, rx, ry + 5, 2, SUGGESTION_H - 10, 1, accent);
            }
            // The item itself, by its tag; a recent search no list knows keeps a clock.
            ItemStack icon = ItemIcons.forItem(ItemTags.suggestionTag(s.name(), context), s.name());
            if (!icon.isEmpty()) {
                graphics.item(icon, rx + 6, ry + (SUGGESTION_H - 16) / 2);
            } else {
                MarketIcons.Glyph glyph = s.recent() ? MarketIcons.Glyph.CLOCK : MarketIcons.Glyph.BOX;
                MarketIcons.draw(graphics, glyph, rx + 9, ry + (SUGGESTION_H - 10) / 2f, 10, lit ? accent : AlpakaStyle.TEXT_MUTED);
            }

            int tx = rx + 27;
            String hint = lit ? "Enter ↵" : s.recent() ? "Recent" : "";
            int hintW = hint.isEmpty() ? 0 : GuiFont.width(this.font, hint) + 8;
            int room = rx + rw - 6 - hintW - tx;
            drawMatch(graphics, s, tx, ry + (SUGGESTION_H - 8) / 2, room, lit);
            if (!hint.isEmpty()) {
                graphics.text(this.font, GuiFont.text(hint), rx + rw - 6 - GuiFont.width(this.font, hint), ry + (SUGGESTION_H - 8) / 2,
                        lit ? AlpakaStyle.TEXT_MUTED : AlpakaStyle.DIVIDER, false);
            }
            // Only the part of a row that shows takes a click.
            int top = Math.max(ry, listY);
            int bottom = Math.min(ry + SUGGESTION_H, listY + listH);
            int index = i;
            if (bottom > top) hits.add(new Hit(rx, top, rw, bottom - top, mx -> choose(index), true));
        }
        graphics.disableScissor();

        if (listScrollMax > 0) {
            // Soft edges where rows go on, and the bar.
            int clear = AlpakaStyle.FIELD & 0x00FFFFFF;
            if (offset > 0) graphics.fillGradient(x + 1, listY, x + w - 1, listY + 6, AlpakaStyle.FIELD, clear);
            if (offset < listScrollMax) graphics.fillGradient(x + 1, listY + listH - 6, x + w - 1, listY + listH, clear, AlpakaStyle.FIELD);
            int trackY = listY + 2;
            int trackH = listH - 4;
            int thumbH = Math.max(12, trackH * listH / (suggestions.size() * SUGGESTION_H));
            int thumbY = trackY + Math.round((trackH - thumbH) * (offset / (float) listScrollMax));
            ModernGuiUtils.drawRoundedRect(graphics, x + w - 6, trackY, 3, trackH, 1, AlpakaStyle.ROW);
            ModernGuiUtils.drawRoundedRect(graphics, x + w - 6, thumbY, 3, thumbH, 1, inList ? accent : AlpakaStyle.accentDim());
        }

        String footer = AlpakaConfig.instance.allowApiCalls
                ? "↑↓ choose · Tab fill in · click to search"
                : "Turn on Network Features for item suggestions";
        int footerY = y + h - SUGGESTION_FOOTER_H + 3;
        ModernGuiUtils.drawRect(graphics, x + 8, y + h - SUGGESTION_FOOTER_H - 1, w - 16, 1, AlpakaStyle.LINE);
        // How many there are, where not all show at once.
        String count = suggestions.size() > rows ? (moreMatches ? suggestions.size() + "+" : String.valueOf(suggestions.size())) + " items" : "";
        int countW = count.isEmpty() ? 0 : GuiFont.width(this.font, count) + 10;
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, footer, w - 16 - countW)), x + 8, footerY, AlpakaStyle.DIVIDER, false);
        if (!count.isEmpty()) {
            graphics.text(this.font, GuiFont.text(count), x + w - 8 - GuiFont.width(this.font, count), footerY, AlpakaStyle.TEXT_MUTED, false);
        }
    }

    /** Scrolls the list just far enough that this row shows whole. */
    private void scrollTo(int index) {
        if (index < 0) return;
        int top = index * SUGGESTION_H;
        int view = visibleRows * SUGGESTION_H;
        if (top < listScrollTarget) listScrollTarget = top;
        else if (top + SUGGESTION_H > listScrollTarget + view) listScrollTarget = top + SUGGESTION_H - view;
    }

    /** A suggestion's name with the parts that matched lit in the accent. */
    private void drawMatch(GuiGraphicsExtractor graphics, Suggestion s, int x, int y, int room, boolean lit) {
        String name = s.name();
        int base = lit ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT;
        if (GuiFont.width(this.font, name) > room) {
            graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, name, room)), x, y, base, false);
            return;
        }
        // One text with colored stretches, so the font lays it out as a whole: drawn in parts, each
        // part's width is rounded up to a whole pixel and small gaps open between them.
        net.minecraft.network.chat.MutableComponent text = net.minecraft.network.chat.Component.empty();
        int at = 0;
        int[] r = s.ranges();
        for (int i = 0; i + 1 < r.length; i += 2) {
            int from = Math.max(at, Math.min(name.length(), r[i]));
            int to = Math.max(from, Math.min(name.length(), r[i + 1]));
            if (from > at) text.append(GuiFont.text(name.substring(at, from)).withColor(base));
            if (to > from) text.append(GuiFont.text(name.substring(from, to)).withColor(AlpakaStyle.accent()));
            at = to;
        }
        if (at < name.length()) text.append(GuiFont.text(name.substring(at)).withColor(base));
        graphics.text(this.font, text, x, y, base, false);
    }

    /** Searches for a suggestion: into the field, then confirmed as if typed and Enter pressed. */
    private void choose(int index) {
        if (index < 0 || index >= suggestions.size()) return;
        String name = suggestions.get(index).name();
        replace(name);
        if (text().equals(name)) confirm();
    }

    /** Puts a suggestion into the field without searching yet, for Tab. */
    private void fillIn(int index) {
        if (index < 0 || index >= suggestions.size()) return;
        replace(suggestions.get(index).name());
    }

    /**
     * The field. With {@code below} more than zero the suggestion list hangs from it, and the two
     * are one box: one outline round both, the field's focus line dividing them.
     */
    private void drawField(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int below) {
        long now = System.currentTimeMillis();
        boolean full = now - fullAtMs < 400;
        int accent = AlpakaStyle.accent();
        int h = FIELD_H + below;
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, 6, AlpakaStyle.FIELD);
        ModernGuiUtils.drawRoundedOutline(graphics, x, y, w, h, 6, full ? MarketIcons.fade(AlpakaStyle.DANGER, 0.8f) : AlpakaStyle.accentDim());
        // The focus line along the field's bottom, which with the list open is where the list begins.
        int lineInset = below > 0 ? 1 : 6;
        ModernGuiUtils.drawRect(graphics, x + lineInset, y + FIELD_H - 1, w - 2 * lineInset, 1, full ? AlpakaStyle.DANGER : accent);

        int iconSize = 10;
        MarketIcons.draw(graphics, MarketIcons.Glyph.PROMPT, x + 8, y + (FIELD_H - iconSize) / 2f, iconSize, accent);
        int textX = 24;
        int tx = x + textX;
        int ty = y + (FIELD_H - 8) / 2;

        // The paste button at the right end: the clipboard in, and confirmed at once.
        int pasteSize = FIELD_H - 8;
        int pasteX = x + w - 4 - pasteSize;
        int pasteY = y + 4;
        boolean hoverPaste = AlpakaStyle.inside(mouseX, mouseY, pasteX, pasteY, pasteSize, pasteSize);
        if (hoverPaste) ModernGuiUtils.drawRoundedRect(graphics, pasteX, pasteY, pasteSize, pasteSize, AlpakaStyle.FIELD_RADIUS, AlpakaStyle.ROW_SELECTED);
        MarketIcons.draw(graphics, MarketIcons.Glyph.PASTE, pasteX + (pasteSize - iconSize) / 2f, pasteY + (pasteSize - iconSize) / 2f, iconSize,
                hoverPaste ? accent : AlpakaStyle.TEXT_MUTED);
        hits.add(new Hit(pasteX, pasteY, pasteSize, pasteSize, mx -> pasteAndConfirm(), true));

        int room = pasteX - 6 - tx;
        String text = text();
        if (text.isEmpty()) {
            // A little after the caret, which blinks at the very start.
            graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, prompt.placeholder(), room - 3)), tx + 3, ty, AlpakaStyle.TEXT_MUTED, false);
        }
        int cursor = input.getCaret();
        int caretPx = GuiFont.width(this.font, text.substring(0, cursor));
        int scroll = Math.max(0, caretPx - room + 2);
        graphics.enableScissor(tx - 1, y + 2, tx + room + 1, y + FIELD_H - 2);
        int ox = tx - scroll;
        if (input.hasSelection()) {
            int from = ox + GuiFont.width(this.font, text.substring(0, input.getSelectionStart()));
            int to = ox + GuiFont.width(this.font, text.substring(0, input.getSelectionEnd()));
            ModernGuiUtils.drawRect(graphics, from, ty - 1, to - from, 10, AlpakaStyle.accentDim());
        }
        graphics.text(this.font, GuiFont.text(text), ox, ty, AlpakaStyle.TEXT, false);
        if ((now - caretFrom) / 500 % 2 == 0) ModernGuiUtils.drawRect(graphics, ox + caretPx, ty - 1, 1, 10, AlpakaStyle.TEXT);
        graphics.disableScissor();

        hits.add(new Hit(x, y, pasteX - 2 - x, FIELD_H, mx -> placeCaret((int) Math.round(mx) - ox), false));
    }

    /** For a price or an amount, what Hypixel will read the text as, so "1.5m" is seen to mean 1,500,000. */
    private void drawReading(GuiGraphicsExtractor graphics, int right, int y) {
        String text = text().trim();
        if (!prompt.type().numeric() || text.isEmpty()) {
            if (System.currentTimeMillis() - fullAtMs < 1200) {
                String full = "Too long for the sign";
                graphics.text(this.font, GuiFont.text(full), right - GuiFont.width(this.font, full), y, AlpakaStyle.DANGER_SOFT, false);
            }
            return;
        }
        Double value = Coins.parseInput(text);
        if (value == null) {
            String bad = "Not a number";
            graphics.text(this.font, GuiFont.text(bad), right - GuiFont.width(this.font, bad), y, AlpakaStyle.DANGER_SOFT, false);
            return;
        }
        boolean coins = prompt.type() == SignPrompt.Type.PRICE;
        String reading = "= " + Coins.full(value) + (coins ? " coins" : "");
        int rw = GuiFont.width(this.font, reading);
        graphics.text(this.font, GuiFont.text(reading), right - rw, y, coins ? MarketInk.ink(COIN) : AlpakaStyle.TEXT_SOFT, false);
    }

    private void drawChips(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w) {
        boolean suffixes = prompt.type() == SignPrompt.Type.PRICE;
        String label = switch (prompt.type()) {
            case SEARCH -> "Recent";
            case PRICE -> "Add";
            default -> "Quick";
        };
        graphics.text(this.font, GuiFont.text(label), x + 2, y + 3, AlpakaStyle.TEXT_MUTED, false);
        int cx = x + GuiFont.width(this.font, label) + 10;
        for (String chip : chips()) {
            String shown = AlpakaStyle.ellipsize(this.font, chip, 80);
            int cw = GuiFont.width(this.font, shown) + 12;
            if (cx + cw > x + w) break;
            boolean hover = AlpakaStyle.inside(mouseX, mouseY, cx, y, cw, CHIP_H);
            ModernGuiUtils.drawRoundedRect(graphics, cx, y, cw, CHIP_H, CHIP_H / 2, hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.ROW);
            graphics.text(this.font, GuiFont.text(shown), cx + 6, y + 3, hover ? AlpakaStyle.TEXT : AlpakaStyle.TEXT_SOFT, false);
            hits.add(new Hit(cx, y, cw, CHIP_H, mx -> {
                if (suffixes) append(chip);
                else replace(chip);
            }, true));
            cx += cw + 4;
        }
    }

    /**
     * The row of prices taken from the item's lowest BIN: that price and a little under it, each
     * one click into the field. Says why instead where there is no price to offer.
     */
    private void drawLowestBin(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w) {
        String label = "Lowest BIN";
        graphics.text(this.font, GuiFont.text(label), x + 2, y + 3, AlpakaStyle.TEXT_MUTED, false);
        int cx = x + GuiFont.width(this.font, label) + 10;
        String source = "SkyCofl";
        int sourceW = GuiFont.width(this.font, source);
        String note = null;
        if (!AlpakaConfig.instance.allowApiCalls) {
            note = "Turn on Network Features";
        } else {
            LowestBin.Result result = LowestBin.get(selling, LowestBin.ownUuid());
            switch (result.state()) {
                case LOADING -> note = "Looking it up…";
                case NONE -> note = "None listed right now";
                case FAILED -> note = "Could not load";
                case READY -> {
                    for (LowestBin.Suggestion chip : LowestBin.suggestions(result.each() * selling.count())) {
                        int cw = GuiFont.width(this.font, chip.label()) + 12;
                        if (cx + cw > x + w - sourceW - 6) break;
                        boolean hover = AlpakaStyle.inside(mouseX, mouseY, cx, y, cw, CHIP_H);
                        ModernGuiUtils.drawRoundedRect(graphics, cx, y, cw, CHIP_H, CHIP_H / 2, hover ? AlpakaStyle.ROW_SELECTED : AlpakaStyle.ROW);
                        graphics.text(this.font, GuiFont.text(chip.label()), cx + 6, y + 3, hover ? MarketInk.ink(COIN) : AlpakaStyle.TEXT_SOFT, false);
                        hits.add(new Hit(cx, y, cw, CHIP_H, mx -> replace(Coins.full(chip.coins())), true));
                        cx += cw + 4;
                    }
                }
            }
        }
        if (note != null) graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, note, x + w - sourceW - 6 - cx)), cx, y + 3, AlpakaStyle.TEXT_MUTED, false);
        // SkyCofl asks to be named where its prices show.
        if (AlpakaConfig.instance.allowApiCalls) {
            graphics.text(this.font, GuiFont.text(source), x + w - 2 - sourceW, y + 3, AlpakaStyle.DIVIDER, false);
        }
    }

    // ---- Editing --------------------------------------------------------------------------------

    /** Fills the field, as long as what is put in fits the sign. */
    private void replace(String value) {
        edit(() -> input.setText(value));
    }

    /** Puts a suffix at the end of the amount, replacing one that is already there. */
    private void append(String suffix) {
        String text = text().trim();
        if (!text.isEmpty() && Character.isLetter(text.charAt(text.length() - 1))) text = text.substring(0, text.length() - 1);
        if (text.isEmpty()) return;
        String value = text + suffix;
        edit(() -> input.setText(value));
    }

    /**
     * Puts the clipboard's first line in the field, as Ctrl+V would, and confirms it. Nothing is
     * sent if the clipboard is empty or does not fit the sign; the field then shows why.
     */
    private void pasteAndConfirm() {
        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
        if (clip == null) return;
        int newline = clip.indexOf('\n');
        if (newline >= 0) clip = clip.substring(0, newline);
        String value = StringUtil.filterText(clip.replace('\t', ' ')).trim();
        if (value.isEmpty()) return;
        replace(value);
        if (text().equals(value)) confirm();
        else fullAtMs = System.currentTimeMillis();
    }

    private void placeCaret(int x) {
        input.setCaretFromX(x, s -> GuiFont.width(this.font, s));
        caretFrom = System.currentTimeMillis();
    }

    /**
     * Writes the answer into the sign's answer lines and closes the editor: vanilla sends the lines,
     * as it does after the sign's Done button. A number goes in plain digits unless those would not
     * fit the sign, which only an absurd number does; then it goes as typed.
     */
    private void confirm() {
        if (closing) return;
        String[] answer = split(prompt.answer(text()));
        if (answer == null) answer = split(text());
        if (answer == null) {
            fullAtMs = System.currentTimeMillis();
            return;
        }
        closing = true;
        if (prompt.type() == SignPrompt.Type.SEARCH) Markets.rememberSearch(context, text());
        close(answer);
    }

    /** Empties the answer lines and closes: Hypixel takes an empty answer as none. */
    private void cancel() {
        if (closing) return;
        closing = true;
        String[] empty = new String[prompt.inputLines()];
        java.util.Arrays.fill(empty, "");
        close(empty);
    }

    /** The answer lines the editor closes with; null while it is open. */
    private String[] answerLines = null;

    /**
     * Closes the editor with these answer lines. Vanilla's Done only clears the screen, and the lines
     * go out as the editor is removed; this does the same, without going through Done. Other mods
     * hook Done to put what their own helper made of vanilla's editor into the first line - Skyblocker's
     * sign calculator writes its last result there, which for a bar it never saw is "0.0", and Hypixel
     * answered every price with "Your starting bid must be at least 10 coins!".
     */
    private void close(String[] answer) {
        answerLines = answer;
        System.arraycopy(answer, 0, lines(), 0, answer.length);
        this.minecraft.gui.setScreen(null);
    }

    /** Puts the answer in place once more right before vanilla sends the lines, whatever changed them since. */
    @Override
    public void removed() {
        if (answerLines != null) System.arraycopy(answerLines, 0, lines(), 0, answerLines.length);
        super.removed();
    }

    // ---- Input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (int i = hits.size() - 1; i >= 0; i--) {
                Hit hit = hits.get(i);
                if (hit.contains(event.x(), event.y())) {
                    if (hit.sound()) AlpakaStyle.click();
                    hit.action().accept(event.x());
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!event.isAllowedChatCharacter()) return false;
        for (char c : Character.toChars(event.codepoint())) edit(() -> input.insert(c));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        boolean open = dropdownOpen() && !suggestions.isEmpty();
        if (event.isEscape()) {
            // The first Escape closes the list, the next one the bar.
            if (dropdownOpen()) {
                dismissedFor = text();
                highlight = -1;
            } else {
                cancel();
            }
            return true;
        }
        if (event.isConfirmation()) {
            if (open && highlight >= 0) choose(highlight);
            else confirm();
            return true;
        }
        if (open && event.isCycleFocus()) {
            fillIn(Math.max(0, highlight));
            return true;
        }
        // Up and down walk the suggestions. Without them they would move to Hypixel's own lines,
        // which are not the answer, so they do nothing.
        if (event.isDown()) {
            if (open) {
                highlight = Math.min(suggestions.size() - 1, highlight + 1);
                scrollTo(highlight);
            }
            return true;
        }
        if (event.isUp()) {
            if (open) {
                highlight = Math.max(-1, highlight - 1);
                if (highlight < 0) listScrollTarget = 0;
                else scrollTo(highlight);
            }
            return true;
        }
        boolean[] handled = {false};
        edit(() -> handled[0] = input.keyPressed(event.key(), event.hasControlDownWithQuirk(), event.hasShiftDown()));
        return handled[0];
    }

    /** The wheel scrolls the suggestions, wherever the mouse is: the list is all there is to scroll. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (dropdownOpen() && listScrollMax > 0 && scrollY != 0) {
            listScrollTarget = Math.max(0, Math.min(listScrollMax, listScrollTarget - scrollY * SUGGESTION_H));
        }
        return true;
    }

    /** Any other way of closing the bar counts as Cancel. */
    @Override
    public void onClose() {
        cancel();
    }

    // ---- For the screenshot tour ----------------------------------------------------------------

    SignBlockEntity sign() {
        return this.sign;
    }

    /** Types into the field as the player would, for the screenshot tour. */
    public MarketInputScreen typed(String text) {
        if (!loaded) pendingText = text;
        else replace(text);
        return this;
    }
}
