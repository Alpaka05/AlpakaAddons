package net.alpaka.addons.features.market;

import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.EditableText;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.ModernGuiUtils;
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        hits.clear();
        boolean scaling = AlpakaStyle.beginOpen(graphics, openTimeMs, this.width, this.height);
        int w = panelW();
        int h = panelH();
        int x = (this.width - w) / 2;
        // A little above the middle, where the eye already is after clicking a menu's search.
        int y = Math.max(8, (this.height - h) / 2 - 20);
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
        int fx = x + PAD + 2;
        int fw = w - PAD * 2 - 4;
        int fy = y + HEADER_H + 10;
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

        AlpakaStyle.endOpen(graphics, scaling);
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
        if (event.isEscape()) {
            cancel();
            return true;
        }
        if (event.isConfirmation()) {
            confirm();
            return true;
        }
        // The answer is one field: the arrows up and down would move to Hypixel's own lines.
        if (event.isUp() || event.isDown()) return true;
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
