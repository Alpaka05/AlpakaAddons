package net.alpaka.addons.features.market;

import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.EditableText;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.wheel.WheelMesh;
import net.alpaka.addons.mixin.AbstractSignEditScreenAccessor;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
 * sign's Done button does. Each line stays within the sign's width as vanilla's own editor measures
 * it, and an answer longer than one line is broken at a space (see {@link SignPrompt#split}) - so the
 * lines are always ones a player could have typed into the vanilla sign. Cancel and Escape empty
 * the answer first, which Hypixel takes as no answer. The chips only fill the field; the player
 * still confirms.
 */
public class MarketInputScreen extends SignEditScreen {
    private static final int HEADER_H = AlpakaStyle.HEADER_H;
    private static final int PAD = AlpakaStyle.PAD;
    private static final int FIELD_H = 24;
    private static final int CHIP_H = 14;
    private static final int COIN = 0xFFFFAA00;

    private final SignPrompt prompt;
    private final MarketKind context;
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

    // ---- Suggestions, for a search ---------------------------------------------------------------

    private static final int SUGGESTION_ROWS = 6;
    private static final int SUGGESTION_H = 16;
    private static final int SUGGESTION_FOOTER_H = 14;

    /** One name offered under the field, with the stretches of it that matched. */
    private record Suggestion(String name, int[] ranges, boolean recent) {}

    private List<Suggestion> suggestions = List.of();
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
        return HEADER_H + 10 + FIELD_H + 6 + 10 + (hasChips() ? CHIP_H + 8 : 0) + 8 + AlpakaStyle.BUTTON_H + 10;
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
        int[] list = dropdownOpen() ? dropdownRect(fx, fy + FIELD_H + 3, fw) : null;
        boolean overList = list != null && AlpakaStyle.inside(realMouseX, realMouseY, list[0], list[1], list[2], list[3]);
        int mouseX = overList ? -10000 : realMouseX;
        int mouseY = overList ? -10000 : realMouseY;

        AlpakaStyle.panel(graphics, x, y, w, h);

        int accent = AlpakaStyle.accent();
        WheelMesh glow = new WheelMesh(1.0f / guiScale());
        glow.radialGradient(x + PAD + 10, y + HEADER_H / 2.0f, 18f, MarketIcons.fade(accent, 0.30f), accent & 0x00FFFFFF);
        glow.submit(graphics);

        // Header: what is asked, with the market's icon, and a close button.
        int chip = 20;
        int chipX = x + PAD;
        int chipY = y + (HEADER_H - chip) / 2;
        ModernGuiUtils.drawRoundedRect(graphics, chipX, chipY, chip, chip, 6, MarketIcons.fade(accent, 0.18f));
        MarketIcons.draw(graphics, glyph(), chipX + 3, chipY + 3, 14, glyph() == MarketIcons.Glyph.COIN ? COIN : accent);
        AlpakaStyle.title(graphics, this.font, AlpakaStyle.ellipsize(this.font, prompt.heading(context), w - 70), chipX + chip + 7, y);
        int closeX = x + w - PAD - 16;
        int closeY = y + (HEADER_H - 16) / 2;
        boolean hoverClose = AlpakaStyle.inside(mouseX, mouseY, closeX, closeY, 16, 16);
        if (hoverClose) ModernGuiUtils.drawRoundedRect(graphics, closeX, closeY, 16, 16, AlpakaStyle.FIELD_RADIUS, MarketIcons.fade(AlpakaStyle.DANGER, 0.2f));
        MarketIcons.draw(graphics, MarketIcons.Glyph.CLOSE, closeX + 4, closeY + 4, 8, hoverClose ? AlpakaStyle.DANGER : AlpakaStyle.TEXT_MUTED);
        hits.add(new Hit(closeX, closeY, 16, 16, mx -> cancel(), true));
        AlpakaStyle.headerLine(graphics, x, y, w);

        // The field.
        drawField(graphics, mouseX, mouseY, fx, fy, fw);

        // Under it: Hypixel's own question on the left, the amount written out on the right.
        int hy = fy + FIELD_H + 6;
        String question = prompt.question().isEmpty() ? "" : prompt.question();
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, question, fw / 2)), fx + 2, hy, AlpakaStyle.TEXT_MUTED, false);
        drawReading(graphics, fx + fw - 2, hy);

        int cy = hy + 10 + 8;
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

        if (list != null) drawDropdown(graphics, realMouseX, realMouseY, list);
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
        List<Suggestion> out = new ArrayList<>();
        if (!text.isBlank()) {
            java.util.Set<String> taken = new java.util.HashSet<>();
            for (SearchSuggestions.Match m : SearchSuggestions.rank(text, Markets.recentSearches(context), 3)) {
                if (split(m.name()) == null) continue;
                out.add(new Suggestion(m.name(), m.ranges(), true));
                taken.add(m.name().toLowerCase(java.util.Locale.ROOT));
            }
            if (names != null) {
                for (SearchSuggestions.Match m : SearchSuggestions.rank(text, names, SUGGESTION_ROWS * 3)) {
                    if (out.size() >= SUGGESTION_ROWS) break;
                    if (taken.contains(m.name().toLowerCase(java.util.Locale.ROOT)) || split(m.name()) == null) continue;
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

    /** The list's place: under the field, as many rows as fit on the screen. */
    private int[] dropdownRect(int x, int y, int w) {
        int rows = Math.max(1, suggestions.size());
        int room = (this.height - 6 - y - SUGGESTION_FOOTER_H - 8) / SUGGESTION_H;
        rows = Math.max(1, Math.min(rows, room));
        return new int[] {x, y, w, 4 + rows * SUGGESTION_H + SUGGESTION_FOOTER_H + 2};
    }

    private void drawDropdown(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int[] rect) {
        int x = rect[0];
        int y = rect[1];
        int w = rect[2];
        int h = rect[3];
        // Over everything drawn so far, the buttons' text included.
        graphics.nextStratum();
        AlpakaStyle.popover(graphics, x, y, w, h);
        hits.add(new Hit(x, y, w, h, mx -> { }, false));
        int rows = (h - 4 - SUGGESTION_FOOTER_H - 2) / SUGGESTION_H;
        boolean moved = mouseX != lastMouseX || mouseY != lastMouseY;
        int accent = AlpakaStyle.accent();

        if (suggestions.isEmpty()) {
            graphics.text(this.font, GuiFont.text("Loading item names…"), x + 22, y + 2 + (SUGGESTION_H - 8) / 2,
                    AlpakaStyle.TEXT_MUTED, false);
        }
        for (int i = 0; i < Math.min(rows, suggestions.size()); i++) {
            Suggestion s = suggestions.get(i);
            int ry = y + 2 + i * SUGGESTION_H;
            int rx = x + 3;
            int rw = w - 6;
            boolean hover = AlpakaStyle.inside(mouseX, mouseY, rx, ry, rw, SUGGESTION_H);
            if (hover && moved) highlight = i;
            boolean lit = i == highlight;
            if (lit) {
                ModernGuiUtils.drawRoundedRect(graphics, rx, ry, rw, SUGGESTION_H, AlpakaStyle.ROW_RADIUS, AlpakaStyle.ROW_SELECTED);
                ModernGuiUtils.drawRoundedRect(graphics, rx, ry + 4, 2, SUGGESTION_H - 8, 1, accent);
            }
            MarketIcons.Glyph glyph = s.recent() ? MarketIcons.Glyph.CLOCK
                    : context == null ? MarketIcons.Glyph.SEARCH : MarketIcons.of(context);
            MarketIcons.draw(graphics, glyph, rx + 7, ry + (SUGGESTION_H - 8) / 2f, 8, lit ? accent : AlpakaStyle.TEXT_MUTED);

            int tx = rx + 20;
            String hint = lit ? "Enter ↵" : "";
            int hintW = hint.isEmpty() ? 0 : GuiFont.width(this.font, hint) + 8;
            int room = rx + rw - 6 - hintW - tx;
            drawMatch(graphics, s, tx, ry + (SUGGESTION_H - 8) / 2, room, lit);
            if (!hint.isEmpty()) {
                graphics.text(this.font, GuiFont.text(hint), rx + rw - 6 - GuiFont.width(this.font, hint), ry + (SUGGESTION_H - 8) / 2,
                        AlpakaStyle.TEXT_MUTED, false);
            }
            int index = i;
            hits.add(new Hit(rx, ry, rw, SUGGESTION_H, mx -> choose(index), true));
        }

        String footer = AlpakaConfig.instance.allowApiCalls
                ? "↑↓ choose · Tab fill in · click to search"
                : "Turn on Network Features for item suggestions";
        ModernGuiUtils.drawRect(graphics, x + 8, y + h - SUGGESTION_FOOTER_H - 1, w - 16, 1, AlpakaStyle.LINE);
        graphics.text(this.font, GuiFont.text(AlpakaStyle.ellipsize(this.font, footer, w - 16)), x + 8,
                y + h - SUGGESTION_FOOTER_H + 3, AlpakaStyle.DIVIDER, false);
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

    private MarketIcons.Glyph glyph() {
        return switch (prompt.type()) {
            case SEARCH -> MarketIcons.Glyph.SEARCH;
            case PRICE -> MarketIcons.Glyph.COIN;
            case AMOUNT -> MarketIcons.Glyph.BOX;
            case DURATION -> MarketIcons.Glyph.CLOCK;
            default -> context == null ? MarketIcons.Glyph.ORDERS : MarketIcons.of(context);
        };
    }

    private void drawField(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w) {
        long now = System.currentTimeMillis();
        boolean full = now - fullAtMs < 400;
        int accent = AlpakaStyle.accent();
        ModernGuiUtils.drawRoundedRect(graphics, x, y, w, FIELD_H, 6, AlpakaStyle.FIELD);
        ModernGuiUtils.drawRoundedOutline(graphics, x, y, w, FIELD_H, 6, full ? MarketIcons.fade(AlpakaStyle.DANGER, 0.8f) : AlpakaStyle.accentDim());
        // The focus line along the bottom, brightest in the middle.
        ModernGuiUtils.drawRect(graphics, x + 6, y + FIELD_H - 1, w - 12, 1, full ? AlpakaStyle.DANGER : accent);

        int iconSize = 10;
        MarketIcons.draw(graphics, glyph(), x + 8, y + (FIELD_H - iconSize) / 2f, iconSize,
                prompt.type() == SignPrompt.Type.PRICE ? COIN : accent);
        int tx = x + 24;
        int ty = y + (FIELD_H - 8) / 2;
        int room = w - 24 - 8;
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

        hits.add(new Hit(x, y, w, FIELD_H, mx -> placeCaret((int) Math.round(mx) - ox), false));
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
        graphics.text(this.font, GuiFont.text(reading), right - rw, y, coins ? COIN : AlpakaStyle.TEXT_SOFT, false);
        if (coins) MarketIcons.draw(graphics, MarketIcons.Glyph.COIN, right - rw - 11, y, 8, COIN);
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

    private void placeCaret(int x) {
        input.setCaretFromX(x, s -> GuiFont.width(this.font, s));
        caretFrom = System.currentTimeMillis();
    }

    /**
     * Writes the answer into the sign's answer lines and closes the editor: vanilla sends the lines,
     * as the sign's Done button does.
     */
    private void confirm() {
        if (closing) return;
        String[] answer = split(text());
        if (answer == null) {
            fullAtMs = System.currentTimeMillis();
            return;
        }
        closing = true;
        String[] lines = lines();
        System.arraycopy(answer, 0, lines, 0, answer.length);
        if (prompt.type() == SignPrompt.Type.SEARCH) Markets.rememberSearch(context, text());
        super.onClose();
    }

    /** Empties the answer lines and closes: Hypixel takes an empty answer as none. */
    private void cancel() {
        if (closing) return;
        closing = true;
        String[] lines = lines();
        for (int i = 0; i < prompt.inputLines(); i++) lines[i] = "";
        super.onClose();
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
            if (open) highlight = Math.min(suggestions.size() - 1, highlight + 1);
            return true;
        }
        if (event.isUp()) {
            if (open) highlight = Math.max(-1, highlight - 1);
            return true;
        }
        boolean[] handled = {false};
        edit(() -> handled[0] = input.keyPressed(event.key(), event.hasControlDownWithQuirk(), event.hasShiftDown()));
        return handled[0];
    }

    /** Any other way of closing the bar counts as Cancel. */
    @Override
    public void onClose() {
        cancel();
    }

    private static int guiScale() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? 2 : (int) Math.max(1, mc.getWindow().getGuiScale());
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
