package net.alpaka.addons.features.enchantments;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.IntFunction;

/**
 * Shows the answers of the Experimentation Table's three games: Chronomatron, Ultrasequencer and
 * Superpairs.
 *
 * It only looks: what to click next is highlighted, and in Ultrasequencer and Superpairs the items
 * that were shown and hidden again are drawn back into their slots. Every click is still the
 * player's own, and no click is held back or sent for them.
 *
 * Everything is read from the slot updates Hypixel sends while a game's menu is open
 * ({@code AbstractContainerMenuSlotSetMixin}) and the player's clicks. The status item in
 * slot 49 says which half of a round it is: a clock while the player answers, glowstone while the
 * game shows its pattern. The board is the menu's rows between the top and bottom borders, slots
 * 9 to 44. The rules follow what Skyblocker, NEU and NoFrills read from the same menus.
 */
public final class ExperimentSolver {

    private ExperimentSolver() {
    }

    /** Chronomatron's steps and clicks go to the log, so a wrong highlight can be traced afterwards. */
    private static final Logger LOGGER = LoggerFactory.getLogger("Alpaka/Experiments");

    private static final int STATUS_SLOT = 49;
    private static final int BOARD_FIRST = 9;
    private static final int BOARD_LAST = 44;

    enum Game { CHRONOMATRON, ULTRASEQUENCER, SUPERPAIRS }

    private static AbstractContainerMenu menu;
    /** The game in {@link #menu}, or null when that menu holds none. */
    private static Session session;

    /** The game a menu title belongs to, or null. */
    static Game gameOf(String title) {
        if (title.startsWith("Chronomatron (")) return Game.CHRONOMATRON;
        if (title.startsWith("Ultrasequencer (")) return Game.ULTRASEQUENCER;
        if (title.startsWith("Superpairs (")) return Game.SUPERPAIRS;
        return null;
    }

    /** The game open in this menu, or null; a new menu starts every game afresh. */
    private static Session current(AbstractContainerMenu changed) {
        if (!AlpakaConfig.instance.experimentSolverEnabled) return null;
        if (!(Minecraft.getInstance().gui.screen() instanceof ContainerScreen screen)) return null;
        if (screen.getMenu() != changed) return null;
        if (changed != menu) {
            menu = changed;
            Game game = gameOf(screen.getTitle().getString());
            session = game != null ? new Session(game) : null;
        }
        return session;
    }

    /** Called for every slot Hypixel sets in an open menu. */
    public static void onSlotSet(AbstractContainerMenu changed, int slot, ItemStack stack) {
        Session current = current(changed);
        if (current != null) current.onSlotSet(slot, stack, i -> changed.getSlot(i).getItem());
    }

    /** Called when Hypixel sends a menu's whole contents. */
    public static void onContentsSet(AbstractContainerMenu changed, List<ItemStack> items) {
        Session current = current(changed);
        if (current != null) current.onContentsSet(items);
    }

    /**
     * Whether a click on this slot of the open menu should be dropped: with Prevent Misclicks on,
     * a board slot that isn't the next step while the player repeats a known pattern.
     */
    public static boolean isMisclick(AbstractContainerMenu clicked, int slot) {
        if (!AlpakaConfig.instance.experimentSolverPreventMisclicks) return false;
        Session current = current(clicked);
        return current != null && slot >= BOARD_FIRST && slot <= BOARD_LAST
                && current.isMisclick(slot, clicked.getSlot(slot).getItem());
    }

    /** Called when the player clicks a slot of the open menu. */
    public static void onSlotClicked(AbstractContainerMenu clicked, int slot) {
        Session current = current(clicked);
        if (current != null && slot >= BOARD_FIRST && slot <= BOARD_LAST) {
            current.onClicked(slot, clicked.getSlot(slot).getItem());
        }
    }

    /** Draws the highlight behind a slot's item. */
    public static void extractSlotBackground(ContainerScreen screen, GuiGraphicsExtractor graphics, Slot slot) {
        Session current = current(screen.getMenu());
        if (current == null || slot.container != menu.slots.get(0).container) return;
        int color = current.highlight(slot.index, slot.getItem());
        if (color != 0) graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
    }

    /** The item to draw in a slot: the remembered one where the game has hidden it again. */
    public static ItemStack displayed(ContainerScreen screen, Slot slot, ItemStack actual) {
        Session current = current(screen.getMenu());
        if (current == null || slot.container != menu.slots.get(0).container) return actual;
        return current.displayed(slot.index, actual);
    }

    /** One game in one menu: what Hypixel has set in it and what the player has clicked. */
    static final class Session {
        final Game game;
        boolean answering;
        final Chronomatron chronomatron = new Chronomatron();
        final Ultrasequencer ultrasequencer = new Ultrasequencer();
        final Superpairs superpairs = new Superpairs();

        Session(Game game) {
            this.game = game;
        }

        /** A slot Hypixel set; {@code board} reads what the menu holds now. */
        void onSlotSet(int slot, ItemStack stack, IntFunction<ItemStack> board) {
            if (slot == STATUS_SLOT) {
                onStatus(stack, board);
                return;
            }
            if (slot < BOARD_FIRST || slot > BOARD_LAST) return;
            switch (game) {
                case CHRONOMATRON -> {
                    if (!answering) chronomatron.onShown(slot, litColor(stack));
                }
                case ULTRASEQUENCER -> {
                    if (!answering) {
                        int number = number(stack);
                        if (number > 0) ultrasequencer.onShown(slot, number, stack.copy());
                    }
                }
                case SUPERPAIRS -> {
                    if (!isHidden(stack)) superpairs.onRevealed(slot, stack.copy());
                }
            }
        }

        /**
         * The menu's whole contents. The status slot goes first, so the board that comes with it
         * is read in the half of the round it belongs to.
         */
        void onContentsSet(List<ItemStack> items) {
            IntFunction<ItemStack> board = i -> i < items.size() ? items.get(i) : ItemStack.EMPTY;
            if (STATUS_SLOT < items.size()) onSlotSet(STATUS_SLOT, items.get(STATUS_SLOT), board);
            for (int i = 0; i < items.size(); i++) if (i != STATUS_SLOT) onSlotSet(i, items.get(i), board);
        }

        private void onStatus(ItemStack stack, IntFunction<ItemStack> board) {
            boolean nowAnswering = stack.is(Items.CLOCK);
            if (nowAnswering == answering) return;
            answering = nowAnswering;
            if (game == Game.CHRONOMATRON) {
                if (answering) chronomatron.startAnswering();
                else chronomatron.startShowing();
            } else if (game == Game.ULTRASEQUENCER && !answering) {
                // Every round is a new sequence, one number longer; numbers already on the board
                // when the status turned are read from it.
                ultrasequencer.reset();
                for (int i = BOARD_FIRST; i <= BOARD_LAST; i++) {
                    ItemStack shown = board.apply(i);
                    int number = number(shown);
                    if (number > 0) ultrasequencer.onShown(i, number, shown.copy());
                }
            }
        }

        /** The player clicked a board slot holding this item. */
        void onClicked(int slot, ItemStack clicked) {
            if (!answering) return;
            switch (game) {
                case CHRONOMATRON -> chronomatron.onClicked(color(clicked));
                case ULTRASEQUENCER -> ultrasequencer.onClicked(slot);
                case SUPERPAIRS -> { }
            }
        }

        /**
         * Whether clicking this board slot would be wrong. Only while answering and only when the
         * next step is known; anything else is let through.
         */
        boolean isMisclick(int slot, ItemStack clicked) {
            if (!answering) return false;
            return switch (game) {
                case CHRONOMATRON -> chronomatron.isMisclick(color(clicked));
                case ULTRASEQUENCER -> ultrasequencer.isMisclick(slot);
                case SUPERPAIRS -> false;
            };
        }

        /** The highlight behind a board slot holding this item, or 0. */
        int highlight(int slot, ItemStack shown) {
            return switch (game) {
                case CHRONOMATRON -> answering ? chronomatron.highlight(color(shown)) : 0;
                case ULTRASEQUENCER -> answering ? ultrasequencer.highlight(slot) : 0;
                case SUPERPAIRS -> isHidden(shown) ? superpairs.highlight(slot) : 0;
            };
        }

        ItemStack displayed(int slot, ItemStack actual) {
            ItemStack remembered = switch (game) {
                case ULTRASEQUENCER -> answering ? ultrasequencer.remembered(slot) : null;
                case SUPERPAIRS -> isHidden(actual) ? superpairs.remembered(slot) : null;
                case CHRONOMATRON -> null;
            };
            return remembered != null ? remembered : actual;
        }
    }

    private static String path(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    /**
     * The color of a block the game lights while it shows its pattern ("red" for red terracotta),
     * or null. Lit blocks are glinting terracotta, as Skyblocker and NEU read them too.
     */
    static String litColor(ItemStack stack) {
        return stack.hasFoil() ? terracottaColor(stack) : null;
    }

    /** The color of a terracotta block ("red" for red terracotta), or null. */
    private static String terracottaColor(ItemStack stack) {
        String path = path(stack);
        return path.endsWith("_terracotta") ? path.substring(0, path.length() - "_terracotta".length()) : null;
    }

    /** The color of an unlit Chronomatron block ("red" for red stained glass), or null. */
    static String glassColor(ItemStack stack) {
        String path = path(stack);
        return path.endsWith("_stained_glass") ? path.substring(0, path.length() - "_stained_glass".length()) : null;
    }

    /** A Chronomatron block's color, lit or not. */
    private static String color(ItemStack stack) {
        String lit = terracottaColor(stack);
        return lit != null ? lit : glassColor(stack);
    }

    /** A covered Superpairs card or the board's border: glass, a pane or nothing. */
    private static boolean isHidden(ItemStack stack) {
        String path = path(stack);
        return stack.isEmpty() || path.endsWith("stained_glass") || path.endsWith("stained_glass_pane");
    }

    /** The number an Ultrasequencer item is named, or 0. */
    private static int number(ItemStack stack) {
        String name = stack.getHoverName().getString();
        if (name.isEmpty() || name.length() > 3) return 0;
        for (int i = 0; i < name.length(); i++) if (!Character.isDigit(name.charAt(i))) return 0;
        return Integer.parseInt(name);
    }

    private static int nowColor() {
        return AlpakaConfig.instance.experimentSolverNextColor;
    }

    private static int laterColor() {
        return AlpakaConfig.instance.experimentSolverLaterColor;
    }

    /**
     * Chronomatron lights colored blocks one after another (glinting terracotta in place of the
     * block's glass, on one slot or a column of two), then the player repeats them. Every color has
     * its own slots, so the chain is kept as colors and every slot of the next color is highlighted.
     *
     * After a round the player got right, the game plays the same chain again with one note added
     * at the end. So the new chain is the last (old length + 1) notes it showed: the block the player
     * clicked last lights up again just after the status has turned (seen on Hypixel in November
     * 2026, glint and all), and would otherwise become a first note that isn't there. A step
     * starts when a block lights while none is lit, or one of another color; that also holds when
     * the next block lights before the last one has gone dark.
     */
    static final class Chronomatron {
        /** The chain to answer, or the one answered last while the game shows the next. */
        final List<String> sequence = new ArrayList<>();
        /** The notes shown in this round, in order. */
        final List<String> shown = new ArrayList<>();
        /** Steps the player has answered this round. */
        int answered;
        /** The slots lit right now, all of {@link #litColor}. */
        private final Set<Integer> lit = new HashSet<>();
        private String litColor;

        void startShowing() {
            shown.clear();
            lit.clear();
            litColor = null;
        }

        void startAnswering() {
            int length = sequence.size() + 1;
            List<String> next = new ArrayList<>();
            if (shown.size() >= length) {
                next.addAll(shown.subList(shown.size() - length, shown.size()));
            } else if (!shown.isEmpty()) {
                // A note went missing (two of one color lit without a gap): the old chain plus the
                // note shown last.
                next.addAll(sequence);
                next.add(shown.getLast());
            } else {
                next.addAll(sequence);
            }
            sequence.clear();
            sequence.addAll(next);
            answered = 0;
            lit.clear();
            litColor = null;
            LOGGER.info("[chronomatron] shown {} -> answer {}", shown, sequence);
        }

        /** A board slot changed while the game shows its pattern; null when it isn't lit (any more). */
        void onShown(int slot, String lightsUp) {
            if (lightsUp == null) {
                lit.remove(slot);
                return;
            }
            if (!lit.isEmpty() && lightsUp.equals(litColor)) {
                lit.add(slot);
                return;
            }
            lit.clear();
            lit.add(slot);
            litColor = lightsUp;
            shown.add(lightsUp);
            LOGGER.info("[chronomatron] note {} {} (slot {})", shown.size(), lightsUp, slot);
        }

        /** The player clicked a block of this color (null for anything else). */
        void onClicked(String color) {
            boolean right = color != null && answered < sequence.size() && sequence.get(answered).equals(color);
            if (right) answered++;
            LOGGER.info("[chronomatron] click {} ({})", color, right ? "step " + answered : "not the next step");
        }

        boolean isMisclick(String color) {
            return answered < sequence.size() && !sequence.get(answered).equals(color);
        }

        int highlight(String color) {
            if (color == null || answered >= sequence.size()) return 0;
            if (sequence.get(answered).equals(color)) return nowColor();
            if (answered + 1 < sequence.size() && sequence.get(answered + 1).equals(color)) return laterColor();
            return 0;
        }
    }

    /**
     * Ultrasequencer shows numbered items on the board, then hides them; the player clicks them in
     * order. The numbers are remembered and drawn back, with the next one to click highlighted.
     */
    static final class Ultrasequencer {
        /** Slot of each number, in order. */
        final TreeMap<Integer, Integer> slotByNumber = new TreeMap<>();
        final Map<Integer, ItemStack> stackBySlot = new HashMap<>();

        void reset() {
            slotByNumber.clear();
            stackBySlot.clear();
        }

        void onShown(int slot, int number, ItemStack stack) {
            // A slot holds one number; one it showed last round is gone.
            slotByNumber.values().remove(slot);
            slotByNumber.put(number, slot);
            stackBySlot.put(slot, stack);
        }

        void onClicked(int slot) {
            if (!slotByNumber.isEmpty() && slotByNumber.firstEntry().getValue() == slot) {
                slotByNumber.pollFirstEntry();
                stackBySlot.remove(slot);
            }
        }

        boolean isMisclick(int slot) {
            return !slotByNumber.isEmpty() && slotByNumber.firstEntry().getValue() != slot;
        }

        int highlight(int slot) {
            if (slotByNumber.isEmpty()) return 0;
            if (slotByNumber.firstEntry().getValue() == slot) return nowColor();
            Map.Entry<Integer, Integer> second = slotByNumber.higherEntry(slotByNumber.firstKey());
            return second != null && second.getValue() == slot ? laterColor() : 0;
        }

        ItemStack remembered(int slot) {
            return stackBySlot.get(slot);
        }
    }

    /**
     * Superpairs is memory: each click turns a card over and it is covered again unless it found
     * its pair. Every card seen is remembered and drawn back, and cards whose pair is known are
     * highlighted.
     */
    static final class Superpairs {
        final Map<Integer, ItemStack> seen = new HashMap<>();
        /** Slots whose pair has been seen too. */
        final Set<Integer> paired = new HashSet<>();

        void reset() {
            seen.clear();
            paired.clear();
        }

        void onRevealed(int slot, ItemStack stack) {
            for (Map.Entry<Integer, ItemStack> other : seen.entrySet()) {
                if (other.getKey() != slot && sameCard(other.getValue(), stack)) {
                    paired.add(other.getKey());
                    paired.add(slot);
                }
            }
            seen.put(slot, stack);
        }

        int highlight(int slot) {
            return paired.contains(slot) ? nowColor() : 0;
        }

        ItemStack remembered(int slot) {
            return seen.get(slot);
        }

        /** Rewards are told apart by item, name and count (an XP bottle's count is its amount). */
        private static boolean sameCard(ItemStack a, ItemStack b) {
            return a.getItem() == b.getItem() && a.getCount() == b.getCount()
                    && a.getHoverName().getString().equals(b.getHoverName().getString());
        }
    }
}
