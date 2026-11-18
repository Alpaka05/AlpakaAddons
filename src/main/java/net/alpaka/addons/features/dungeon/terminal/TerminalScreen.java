package net.alpaka.addons.features.dungeon.terminal;

import net.alpaka.addons.client.gui.AlpakaStyle;
import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.GuiItemFade;
import net.alpaka.addons.client.gui.ModernGuiUtils;
import net.alpaka.addons.compliance.Outbound;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.market.MarketIcons;
import net.alpaka.addons.utils.DungeonState;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

/**
 * The custom terminal window (design.md 5.2): the F7/M7 terminals in a larger, cleaner window in
 * the config menu's design, with the solution drawn into it.
 *
 * <h2>Only the look changes</h2>
 *
 * This is still Hypixel's chest, the MarketScreen pattern: a vanilla chest screen whose drawing was
 * replaced, on the menu vanilla itself made from the server's packet; closing it is vanilla's own
 * close (compliance.md C2). The play area is drawn as a fixed grid, each cell exactly one chest slot
 * at a place that follows from the slot index alone ({@link TerminalGuiLayout}). A press on a cell
 * is the click vanilla sends for that press on that slot - left or right, a quick move with Shift -
 * through {@link Outbound#menuClick}, at most one per press and in that press's own handler, or its
 * release when vanilla would wait for that ({@link TerminalGuiInput}). Before it goes out it passes
 * the terminal's guards ({@link TerminalClicks}); a refused click is not sent at all and only
 * shows a short red flash and a quiet local sound. Everything else - the middle button, drags,
 * scrolling, number and drop keys, clicks between cells or on filler - does nothing, so nothing
 * reaches the invisible vanilla slots underneath. Esc and the inventory key close it.
 *
 * What it never does: click by itself, remap a key or button to a click, queue or repeat a click,
 * lay cells out by the solution, or mark a moment to click. Every color is steady; Click the Button
 * on Time's button looks the same whether or not the pane is lined up.
 *
 * "Classic" in the header hands this terminal to the vanilla chest, the overlay's look, until the
 * game restarts.
 *
 * <h2>Practice</h2>
 *
 * With a {@link TerminalPractice} the same window plays a simulated terminal on a local chest: a
 * click goes to the practice instead of the server, closing does not close any container on the
 * server, and the header shows the time instead of Classic.
 */
public class TerminalScreen extends ContainerScreen {

    /** Terminals the player switched to the vanilla chest, for the rest of the session. */
    private static final EnumSet<TerminalKind> CLASSIC = EnumSet.noneOf(TerminalKind.class);

    private static final int BUTTON = 16;
    /** How long a refused cell flashes red. */
    private static final long FLASH_MS = 320L;
    /** How long the header explains a refused click. */
    private static final long HINT_MS = 1200L;
    /** The refusal sound at most this often, so a burst of refused clicks is one sound, not a rattle. */
    private static final long SOUND_GAP_MS = 150L;
    private static final long TIP_DELAY_MS = 300L;

    private final TerminalKind kind;
    private final String plainTitle;
    private final String argument;
    private final Inventory inventory;
    /** The terminal this window belongs to; null in a preview or practice, which never send anything. */
    private final TerminalSession session;
    /** Terminal practice, which takes the clicks instead of the server; null otherwise. */
    private final TerminalPractice practice;
    private final TerminalGuiInput input = new TerminalGuiInput();
    /**
     * The last stack seen in each slot. A click puts the item on the cursor until the server
     * answers; meanwhile the cell keeps showing it instead of going blank.
     */
    private final ItemStack[] shown = new ItemStack[TerminalSolution.MAX_SLOTS];

    // A preview solves its local chest itself, the way a session would.
    private final TerminalSession.State previewState;
    private List<TerminalItem> previewItems = List.of();
    private TerminalSolution previewSolution;
    private long previewSignature = Long.MIN_VALUE;

    /** The last frame's layout, which the mouse is tested against. */
    private TerminalGuiLayout layout;

    private int refusedSlot = -1;
    private long refusedAtMs = Long.MIN_VALUE / 2;
    private TerminalClicks.Verdict refusedVerdict = null;
    private long lastSoundMs = Long.MIN_VALUE / 2;

    /** Classic was pressed; it switches on the release, see {@link #mouseReleased}. */
    private boolean classicPressed = false;

    private Object tipKey = null;
    private long tipSince = 0L;

    private TerminalScreen(ChestMenu menu, Inventory inventory, Component title, TerminalKind kind, String plainTitle,
                           TerminalSession session, TerminalPractice practice) {
        super(menu, inventory, title);
        this.kind = kind;
        this.plainTitle = plainTitle;
        this.argument = TerminalGuiText.argument(kind, plainTitle);
        this.inventory = inventory;
        this.session = session;
        this.practice = practice;
        this.previewState = practice != null ? practice.state() : new TerminalSession.State();
    }

    /**
     * From MenuScreensMixin, for every chest Hypixel opens, after the market screens had their
     * turn; only called while the Terminal Solvers master is on, and after
     * {@link TerminalSession#beforeMenuOpens}. Opens the custom window and returns true for a
     * terminal it handles: Custom Terminal GUI on, the terminal's own switch on, not switched to
     * Classic, in a Catacombs run. The menu is made from the packet exactly as vanilla makes it and
     * set as the player's open menu, as vanilla does; the session starts (or carries on, when
     * Hypixel replaced the same terminal's window) with {@link TerminalSession#open}. False leaves
     * the chest to vanilla, which then starts the session itself.
     */
    public static boolean tryOpen(MenuType<?> type, Minecraft mc, int containerId, Component title) {
        if (mc.player == null || !isChest(type)) return false;
        AlpakaConfig cfg = AlpakaConfig.instance;
        if (!cfg.terminalSolversEnabled || !cfg.terminalCustomGui) return false;
        String plain = SkyblockUtils.cleanColor(title.getString());
        TerminalKind kind = TerminalKind.byTitle(plain);
        if (kind == null || CLASSIC.contains(kind) || !kind.enabled(cfg) || !DungeonState.inDungeon()) return false;
        if (!(type.create(containerId, mc.player.getInventory()) instanceof ChestMenu menu)) return false;

        Screen open = mc.gui.screen();
        mc.player.containerMenu = menu;
        TerminalSession session = TerminalSession.open(kind, menu, plain);
        TerminalScreen screen = new TerminalScreen(menu, mc.player.getInventory(), title, kind, plain, session, null);
        if (open instanceof TerminalScreen previous && previous.kind == kind && session.continued()) {
            screen.continueFrom(previous);
        }
        mc.gui.setScreen(screen);
        return true;
    }

    private static boolean isChest(MenuType<?> type) {
        return type == MenuType.GENERIC_9x1 || type == MenuType.GENERIC_9x2 || type == MenuType.GENERIC_9x3
                || type == MenuType.GENERIC_9x4 || type == MenuType.GENERIC_9x5 || type == MenuType.GENERIC_9x6;
    }

    /**
     * Takes over from the window Hypixel replaced with this one, so the same terminal does not look
     * like a new one: a refusal still showing stays.
     */
    private void continueFrom(TerminalScreen previous) {
        refusedSlot = previous.refusedSlot;
        refusedAtMs = previous.refusedAtMs;
        refusedVerdict = previous.refusedVerdict;
        lastSoundMs = previous.lastSoundMs;
    }

    /** The custom window on a locally filled chest, for the screenshot tour; sends nothing. Null without a world. */
    public static Screen preview(TerminalKind kind) {
        return TerminalScreenPreview.screen(kind);
    }

    /** The vanilla chest with the terminal overlay on a locally filled chest, for the screenshot tour; sends nothing. */
    public static Screen previewVanilla(TerminalKind kind) {
        return TerminalScreenPreview.vanilla(kind);
    }

    /** A preview on {@code menu}, which is not the player's open menu, so no click could go anywhere. */
    static TerminalScreen preview(ChestMenu menu, Inventory inventory, String title, TerminalKind kind) {
        return new TerminalScreen(menu, inventory, Component.literal(title), kind, title, null, null);
    }

    /** Terminal practice on {@code menu}, a local chest that is not the player's open menu. */
    static TerminalScreen practice(ChestMenu menu, Inventory inventory, String title, TerminalKind kind,
                                   TerminalPractice practice) {
        return new TerminalScreen(menu, inventory, Component.literal(title), kind, title, null, practice);
    }

    // ---- The terminal as it is now --------------------------------------------------------------

    private TerminalSolution solution() {
        if (session != null) return session.solution();
        // Practice shows the solution as a run would: only where that terminal's solver is on.
        if (practice != null && !kind.enabled()) return TerminalSolution.empty(kind);
        solvePreview();
        return previewSolution;
    }

    private List<TerminalItem> items() {
        if (session != null) return session.items();
        solvePreview();
        return previewItems;
    }

    private boolean pending(int slot, long nowMs) {
        return session != null ? session.pending(slot, nowMs) : previewState.isPending(slot);
    }

    /** The chest part's size as the client has it; slots from here on are the player's inventory. */
    private int chestSize() {
        return Math.min(menu.getRowCount() * 9, menu.slots.size());
    }

    /** A preview's solution, worked out again only when an item changed, as the session does it. */
    private void solvePreview() {
        long signature = 17L;
        int size = chestSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            signature = signature * 31 + System.identityHashCode(stack);
            signature = signature * 31 + stack.getCount();
        }
        signature = signature * 31 + previewState.version();
        if (previewSolution != null && signature == previewSignature) return;
        List<TerminalItem> read = new ArrayList<>(size);
        for (int i = 0; i < size; i++) read.add(TerminalItem.of(i, menu.getSlot(i).getItem()));
        previewItems = Collections.unmodifiableList(read);
        previewSolution = TerminalSolver.solve(kind, plainTitle, previewItems, previewState);
        previewSignature = signature;
    }

    /**
     * The slot if a click there may be sent, else -1: inside the chest part, holding an item the
     * server sent that is not the filler (compliance.md C6). A slot whose item went onto the cursor
     * with the last click still counts, as vanilla would put it back with the next.
     */
    private int clickable(int slot, List<TerminalItem> items) {
        if (slot < 0 || slot >= chestSize() || slot >= items.size()) return -1;
        TerminalItem item = items.get(slot);
        return item.empty() || item.filler() ? -1 : slot;
    }

    /** What to draw in a slot: the item there, or while its click is pending the one it held. */
    private ItemStack stackToDraw(int slot, long nowMs) {
        if (slot < 0 || slot >= chestSize()) return ItemStack.EMPTY;
        ItemStack stack = menu.getSlot(slot).getItem();
        if (!stack.isEmpty()) {
            shown[slot] = stack;
            return stack;
        }
        if (pending(slot, nowMs) && shown[slot] != null) return shown[slot];
        shown[slot] = null;
        return ItemStack.EMPTY;
    }

    private boolean carrying() {
        return !menu.getCarried().isEmpty();
    }

    // ---- Geometry of the header -----------------------------------------------------------------

    /** How wide the header's content needs the panel: name, argument, room for a refusal note, buttons. */
    private int minPanelWidth() {
        int width = TerminalGuiLayout.PAD + 2 + GuiFont.width(this.font, kind.displayName());
        if (argument != null) width += 6 + GuiFont.width(this.font, argument);
        int note = 0;
        for (TerminalClicks.Verdict verdict : TerminalClicks.Verdict.values()) {
            String text = TerminalGuiText.refusal(verdict);
            if (text != null) note = Math.max(note, GuiFont.width(this.font, text));
        }
        if (practice != null) note = Math.max(note, GuiFont.width(this.font, "Solved in 99.99 s  New Best!"));
        return width + 10 + note + 8 + BUTTON + 4 + BUTTON + TerminalGuiLayout.PAD;
    }

    private static TerminalGuiLayout.Rect closeButton(TerminalGuiLayout l) {
        TerminalGuiLayout.Rect panel = l.panel();
        return new TerminalGuiLayout.Rect(panel.x() + panel.w() - TerminalGuiLayout.PAD - BUTTON,
                panel.y() + (TerminalGuiLayout.HEADER_H - BUTTON) / 2, BUTTON, BUTTON);
    }

    private static TerminalGuiLayout.Rect classicButton(TerminalGuiLayout l) {
        TerminalGuiLayout.Rect close = closeButton(l);
        return new TerminalGuiLayout.Rect(close.x() - 4 - BUTTON, close.y(), BUTTON, BUTTON);
    }

    // ---- Drawing --------------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        AlpakaStyle.backdrop(graphics, this.width, this.height);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        if (practice != null) practice.update(now);
        TerminalSettings settings = TerminalSettings.current();
        TerminalGuiPalette palette = TerminalGuiPalette.current();
        TerminalSolution solution = solution();
        List<TerminalItem> items = items();
        TerminalGuiLayout l = TerminalGuiLayout.of(kind, settings.guiScale(), this.width, this.height, minPanelWidth());
        layout = l;

        TerminalGuiLayout.Rect panel = l.panel();
        ModernGuiUtils.drawPanelShadow(graphics, panel.x(), panel.y(), panel.w(), panel.h(), AlpakaStyle.PANEL_RADIUS, 1.0f);
        ModernGuiUtils.drawRoundedRect(graphics, panel.x(), panel.y(), panel.w(), panel.h(), AlpakaStyle.PANEL_RADIUS,
                palette.background());
        ModernGuiUtils.drawRoundedOutline(graphics, panel.x(), panel.y(), panel.w(), panel.h(), AlpakaStyle.PANEL_RADIUS,
                palette.neutral(0x1C));

        String tip = drawHeader(graphics, l, palette, mouseX, mouseY, now);
        drawGrid(graphics, l, palette, settings, solution, items, mouseX, mouseY, now);

        if (tip == null) {
            tipKey = null;
        } else {
            if (!tip.equals(tipKey)) {
                tipKey = tip;
                tipSince = now;
            }
            if (now - tipSince >= TIP_DELAY_MS) {
                AlpakaStyle.tooltip(graphics, this.font, this.width, this.height, mouseX, mouseY, tip);
            }
        }
    }

    /** The header: the terminal's name and what it asks for, a refusal note, Classic and close. Returns a tooltip or null. */
    private String drawHeader(GuiGraphicsExtractor graphics, TerminalGuiLayout l, TerminalGuiPalette palette,
                              int mouseX, int mouseY, long now) {
        TerminalGuiLayout.Rect panel = l.panel();
        int textY = panel.y() + (TerminalGuiLayout.HEADER_H - 8) / 2;
        int x = panel.x() + TerminalGuiLayout.PAD + 2;
        graphics.text(this.font, GuiFont.text(kind.displayName()), x, textY, palette.text(), false);
        x += GuiFont.width(this.font, kind.displayName());
        if (argument != null) {
            graphics.text(this.font, GuiFont.text(argument), x + 6, textY, AlpakaStyle.accent(), false);
        }

        TerminalGuiLayout.Rect close = closeButton(l);
        TerminalGuiLayout.Rect classic = classicButton(l);
        boolean hoverClose = close.contains(mouseX, mouseY);
        boolean hoverClassic = practice == null && classic.contains(mouseX, mouseY);
        if (practice == null) glyphButton(graphics, classic, MarketIcons.Glyph.CLASSIC, hoverClassic, palette, false);
        glyphButton(graphics, close, MarketIcons.Glyph.CLOSE, hoverClose, palette, true);
        if (practice != null) {
            drawPracticeStatus(graphics, close, textY, palette, now);
            ModernGuiUtils.drawRect(graphics, panel.x() + TerminalGuiLayout.PAD, panel.y() + TerminalGuiLayout.HEADER_H - 1,
                    panel.w() - 2 * TerminalGuiLayout.PAD, 1, palette.neutral(0x24));
            if (hoverClose) return "Close";
            return null;
        }

        // A refused click explains itself for a moment, after the fact.
        String note = TerminalGuiText.refusal(refusedVerdict);
        long since = now - refusedAtMs;
        if (note != null && since >= 0 && since < HINT_MS) {
            float fade = since < HINT_MS - 300 ? 1.0f : (HINT_MS - since) / 300.0f;
            int w = GuiFont.width(this.font, note);
            graphics.text(this.font, GuiFont.text(note), classic.x() - 8 - w, textY,
                    TerminalGuiPalette.scaleAlpha(AlpakaStyle.DANGER, fade), false);
        }

        ModernGuiUtils.drawRect(graphics, panel.x() + TerminalGuiLayout.PAD, panel.y() + TerminalGuiLayout.HEADER_H - 1,
                panel.w() - 2 * TerminalGuiLayout.PAD, 1, palette.neutral(0x24));

        if (hoverClose) return "Close";
        if (hoverClassic) return "Classic View: show Hypixel's own chest for this terminal instead, until you restart the game.";
        return null;
    }

    /** Practice: the time so far and the best, or once solved the final time, right of the title. */
    private void drawPracticeStatus(GuiGraphicsExtractor graphics, TerminalGuiLayout.Rect close, int textY,
                                    TerminalGuiPalette palette, long now) {
        long best = practice.bestMs(kind);
        String text;
        int color;
        if (practice.solved()) {
            text = "Solved in " + seconds(practice.elapsedMs(now)) + (practice.newBest() ? "  New Best!" : "");
            color = AlpakaStyle.accent();
        } else {
            text = seconds(practice.elapsedMs(now)) + (best >= 0 ? "  Best " + seconds(best) : "");
            color = palette.mutedText();
        }
        if (practice.pingMs() > 0) text = practice.pingMs() + " ms  " + text;
        int w = GuiFont.width(this.font, text);
        graphics.text(this.font, GuiFont.text(text), close.x() - 8 - w, textY, color, false);
    }

    private static String seconds(long ms) {
        return String.format(java.util.Locale.ROOT, "%.2f s", ms / 1000.0);
    }

    private void glyphButton(GuiGraphicsExtractor graphics, TerminalGuiLayout.Rect r, MarketIcons.Glyph glyph,
                             boolean hover, TerminalGuiPalette palette, boolean danger) {
        if (hover) {
            int back = danger ? TerminalGuiPalette.withAlpha(AlpakaStyle.DANGER, 0x40) : palette.neutral(0x22);
            ModernGuiUtils.drawRoundedRect(graphics, r.x(), r.y(), r.w(), r.h(), AlpakaStyle.FIELD_RADIUS, back);
        }
        float size = r.w() * 0.5f;
        int color = hover ? (danger ? AlpakaStyle.DANGER : palette.text()) : palette.mutedText();
        MarketIcons.draw(graphics, glyph, r.x() + (r.w() - size) / 2f, r.y() + (r.h() - size) / 2f, size, color);
    }

    /**
     * The cells. First every cell's ground and item, then - over the items - what goes on top: the
     * pending mark, the hover ring, a refusal's flash and the labels.
     */
    private void drawGrid(GuiGraphicsExtractor graphics, TerminalGuiLayout l, TerminalGuiPalette palette,
                          TerminalSettings settings, TerminalSolution solution, List<TerminalItem> items,
                          int mouseX, int mouseY, long now) {
        int radius = Math.max(2, l.cell() / 7);
        int hovered = clickable(l.slotAt(mouseX, mouseY), items);
        if (kind.isMelody()) drawMelodyBands(graphics, l, palette, solution, radius);

        int[] slots = kind.playSlots();
        for (int slot : slots) {
            TerminalGuiLayout.Rect r = l.cell(slot);
            boolean real = clickable(slot, items) >= 0;
            int fill = real ? palette.fill(solution, slot) : 0;
            TerminalSolution.Mark mark = solution.mark(slot);
            int ground;
            if (!real) ground = palette.neutral(0x0A);
            else if (fill != 0) ground = fill;
            else if (mark == TerminalSolution.Mark.DONE) ground = palette.neutral(0x30);
            else ground = palette.neutral(0x16);
            ModernGuiUtils.drawRoundedRect(graphics, r.x(), r.y(), r.w(), r.h(), radius, ground);
            if (fill != 0) {
                // A ring in the color around a dark centre only tinted with it: glass panes and the
                // other see-through items would otherwise take on the solution's color, and a red
                // pane on a green cell reads as a green one.
                int ring = Math.max(2, l.cell() / 12);
                int innerRadius = Math.max(1, radius - ring);
                int x = r.x() + ring, y = r.y() + ring, w = r.w() - 2 * ring, h = r.h() - 2 * ring;
                ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, innerRadius,
                        TerminalGuiPalette.withAlpha(palette.background(), 0xFF));
                ModernGuiUtils.drawRoundedRect(graphics, x, y, w, h, innerRadius, TerminalGuiPalette.withAlpha(fill, 0x48));
            }

            if (!real) continue;
            // Hide Other Items leaves out what the solution does not use, once it knows what that is.
            boolean other = fill == 0 && mark == TerminalSolution.Mark.OTHER;
            if (other && settings.hideOtherItems() && solution.loaded()) continue;
            float alpha = fill != 0 ? 1.0f
                    : mark == TerminalSolution.Mark.DONE ? 0.5f
                    : solution.loaded() ? 0.45f : 1.0f;
            if (pending(slot, now)) alpha = Math.min(alpha, 0.5f);
            ItemStack stack = stackToDraw(slot, now);
            if (!stack.isEmpty()) drawItem(graphics, stack, r, alpha);
        }

        graphics.nextStratum();
        for (int slot : slots) {
            TerminalGuiLayout.Rect r = l.cell(slot);
            if (clickable(slot, items) < 0) continue;
            int fill = palette.fill(solution, slot);
            if (pending(slot, now)) {
                // Neutral, and the same for a right or a wrong click: only "the server has not answered yet".
                ModernGuiUtils.drawRoundedRect(graphics, r.x(), r.y(), r.w(), r.h(), radius,
                        TerminalGuiPalette.withAlpha(palette.background(), 0x70));
                ModernGuiUtils.drawRoundedOutline(graphics, r.x(), r.y(), r.w(), r.h(), radius, palette.neutral(0xB0));
            }
            if (slot == hovered) {
                ModernGuiUtils.drawRoundedRect(graphics, r.x(), r.y(), r.w(), r.h(), radius, palette.neutral(0x18));
                ModernGuiUtils.drawRoundedOutline(graphics, r.x(), r.y(), r.w(), r.h(), radius, palette.neutral(0x90));
            }
            if (slot == refusedSlot) {
                long since = now - refusedAtMs;
                if (since >= 0 && since < FLASH_MS) {
                    float t = 1.0f - since / (float) FLASH_MS;
                    ModernGuiUtils.drawRoundedRect(graphics, r.x(), r.y(), r.w(), r.h(), radius,
                            TerminalGuiPalette.withAlpha(AlpakaStyle.DANGER, Math.round(0x90 * t)));
                    ModernGuiUtils.drawRoundedOutline(graphics, r.x(), r.y(), r.w(), r.h(), radius,
                            TerminalGuiPalette.scaleAlpha(AlpakaStyle.DANGER, t));
                }
            }
            String label = solution.label(slot);
            if (label != null && !label.isEmpty()) {
                drawLabel(graphics, label, r, fill != 0 ? 0xFFFFFFFF : palette.text());
            } else if (kind == TerminalKind.NUMBERS && fill == 0 && solution.mark(slot) != TerminalSolution.Mark.DONE) {
                // A pane of Click in Order the solution gives no label: its number as the stack
                // says it, so nothing the vanilla chest shows goes missing.
                ItemStack stack = stackToDraw(slot, now);
                if (!stack.isEmpty()) drawCount(graphics, stack.getCount(), r, palette.mutedText());
            }
        }
    }

    /**
     * Click the Button on Time: a band down the target column, from its top pane to its bottom one,
     * and a faint band along the active row up to its button, so where to look reads at a glance.
     * Both stay where they are until the terminal itself moves on.
     */
    private void drawMelodyBands(GuiGraphicsExtractor graphics, TerminalGuiLayout l, TerminalGuiPalette palette,
                                 TerminalSolution solution, int radius) {
        int inflate = Math.max(1, l.gap() / 2);
        int row = solution.activeRow();
        if (row >= 1 && row <= 3) {
            TerminalGuiLayout.Rect band = l.span(row * 9 + 1, row * 9 + 7).inflate(inflate);
            ModernGuiUtils.drawRoundedRect(graphics, band.x(), band.y(), band.w(), band.h(), radius + inflate, palette.neutral(0x12));
        }
        int target = solution.columnTarget();
        if (target >= 1 && target <= 5) {
            TerminalGuiLayout.Rect band = l.span(target, 4 * 9 + target).inflate(inflate);
            ModernGuiUtils.drawRoundedRect(graphics, band.x(), band.y(), band.w(), band.h(), radius + inflate,
                    TerminalGuiPalette.scaleAlpha(palette.melody(), 0.3f));
        }
    }

    private void drawItem(GuiGraphicsExtractor graphics, ItemStack stack, TerminalGuiLayout.Rect r, float alpha) {
        float size = Math.max(8.0f, r.w() * 0.78f);
        float scale = size / 16.0f;
        graphics.pose().pushMatrix();
        graphics.pose().translate(r.x() + (r.w() - size) / 2.0f, r.y() + (r.h() - size) / 2.0f);
        graphics.pose().scale(scale, scale);
        if (alpha < 1.0f) GuiItemFade.begin(alpha);
        graphics.item(stack, 0, 0);
        GuiItemFade.end();
        graphics.pose().popMatrix();
    }

    private void drawLabel(GuiGraphicsExtractor graphics, String label, TerminalGuiLayout.Rect r, int color) {
        Component text = GuiFont.text(label);
        float scale = Math.max(1.0f, Math.min(2.5f, r.h() / 18.0f));
        graphics.pose().pushMatrix();
        graphics.pose().translate(r.x() + r.w() / 2.0f, r.y() + r.h() / 2.0f);
        graphics.pose().scale(scale, scale);
        graphics.text(this.font, text, -this.font.width(text) / 2, -4, color, true);
        graphics.pose().popMatrix();
    }

    private void drawCount(GuiGraphicsExtractor graphics, int count, TerminalGuiLayout.Rect r, int color) {
        Component text = GuiFont.text(Integer.toString(count));
        graphics.text(this.font, text, r.x() + r.w() - 2 - this.font.width(text), r.y() + r.h() - 9, color, true);
    }

    // ---- Input ----------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        TerminalGuiLayout l = layout;
        if (l == null) return true;
        int button = event.button();
        classicPressed = false;
        if (button == 0 || button == 1) {
            if (closeButton(l).contains(event.x(), event.y())) {
                AlpakaStyle.click();
                onClose();
                return true;
            }
            if (practice == null && classicButton(l).contains(event.x(), event.y())) {
                AlpakaStyle.click();
                input.reset();
                classicPressed = true;
                return true;
            }
        }
        int slot = clickable(l.slotAt(event.x(), event.y()), items());
        send(input.press(slot, button, event.hasShiftDown(), carrying()));
        // Handled whatever it was: nothing here may fall through to the vanilla slots underneath.
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        TerminalGuiLayout l = layout;
        if (classicPressed) {
            // On the release, not the press: the vanilla screen must not get this release, which
            // with an item on the cursor it would send as a click on whatever slot lies under it.
            classicPressed = false;
            if (l != null && classicButton(l).contains(event.x(), event.y())) switchToClassic();
            return true;
        }
        if (l != null) {
            int slot = clickable(l.slotAt(event.x(), event.y()), items());
            send(input.release(slot, event.button(), event.hasShiftDown(), carrying()));
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // Keys only close the window. Vanilla's number keys, drop key and pick key would act on the
        // slot under the mouse, which here is not the one drawn there. Any other key is left to the
        // game, so a screenshot or fullscreen still works.
        if (event.isEscape() || (this.minecraft != null && this.minecraft.options.keyInventory.matches(event))) {
            onClose();
            return true;
        }
        return false;
    }

    /**
     * Sends one click the player just made, if the guards let it through: exactly the click vanilla
     * sends for it, and the session hears of it only once it really went out.
     */
    private void send(TerminalGuiInput.Click click) {
        if (click == null) return;
        long now = Util.getMillis();
        if (practice != null) {
            // Practice: the click goes to the simulated terminal and nowhere else.
            practice.click(click.slot(), click.button(), now);
            return;
        }
        if (session == null) return;
        TerminalClicks.Verdict verdict = TerminalClicks.check(session, click.slot(), click.button(), now);
        if (verdict != TerminalClicks.Verdict.ALLOW) {
            refused(click.slot(), verdict, now);
            return;
        }
        ContainerInput kindOfClick = click.quickMove() ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP;
        if (Outbound.menuClick(menu, click.slot(), click.button(), kindOfClick, Outbound.Cause.MENU_CLICK)) {
            session.onClickSent(click.slot(), click.button(), now);
        }
    }

    /** Local feedback for a refused click, and only that: a flash of its cell, a note, a quiet thud. */
    private void refused(int slot, TerminalClicks.Verdict verdict, long now) {
        refusedSlot = slot;
        refusedAtMs = now;
        refusedVerdict = verdict;
        if (now - lastSoundMs >= SOUND_GAP_MS && this.minecraft != null) {
            lastSoundMs = now;
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7f, 0.3f));
        }
    }

    /**
     * Hands this terminal to the vanilla chest screen, on the same menu: nothing is closed or sent,
     * only the screen showing it changes, and the solver's overlay and guards take over there.
     * Terminals of this kind open in the vanilla chest from then on, until the game restarts.
     */
    private void switchToClassic() {
        CLASSIC.add(kind);
        input.reset();
        if (this.minecraft != null) this.minecraft.gui.setScreen(new ContainerScreen(menu, inventory, getTitle()));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (practice != null) practice.nextIfDue(Util.getMillis());
    }

    /** Practice closes only the window: vanilla's close would tell the server a container closed. */
    @Override
    public void onClose() {
        if (practice != null) {
            if (this.minecraft != null) this.minecraft.gui.setScreen(null);
            return;
        }
        super.onClose();
    }

    /**
     * Never a vanilla slot click: every click this window sends goes through {@link #send}. Nothing
     * here calls vanilla's mouse handling, so this only matters should something still reach it -
     * in practice it would otherwise click the player's own inventory on the server.
     */
    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ContainerInput input) {
    }

    @Override
    public void removed() {
        input.reset();
        super.removed();
    }
}
