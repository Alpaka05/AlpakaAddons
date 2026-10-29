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
 * 9 to 44. The rules follow what Skyblocker and NoFrills read from the same menus.
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
    private static Game game;
    private static boolean answering;
    private static final Chronomatron CHRONOMATRON = new Chronomatron();
    private static final Ultrasequencer ULTRASEQUENCER = new Ultrasequencer();
    private static final Superpairs SUPERPAIRS = new Superpairs();

    /** The game a menu title belongs to, or null. */
    static Game gameOf(String title) {
        if (title.startsWith("Chronomatron (")) return Game.CHRONOMATRON;
        if (title.startsWith("Ultrasequencer (")) return Game.ULTRASEQUENCER;
        if (title.startsWith("Superpairs (")) return Game.SUPERPAIRS;
        return null;
    }

    /** The game open in this menu, or null; a new menu starts every game afresh. */
    private static Game current(AbstractContainerMenu changed) {
        if (!AlpakaConfig.instance.experimentSolverEnabled) return null;
        if (!(Minecraft.getInstance().gui.screen() instanceof ContainerScreen screen)) return null;
        if (screen.getMenu() != changed) return null;
        if (changed != menu) {
            menu = changed;
            game = gameOf(screen.getTitle().getString());
            answering = false;
            CHRONOMATRON.reset();
            ULTRASEQUENCER.reset();
            SUPERPAIRS.reset();
        }
        return game;
    }

    /** Called for every slot Hypixel sets in an open menu. */
    public static void onSlotSet(AbstractContainerMenu changed, int slot, ItemStack stack) {
        Game current = current(changed);
        if (current == null) return;
        if (slot == STATUS_SLOT) {
            boolean nowAnswering = stack.is(Items.CLOCK);
            if (nowAnswering != answering) {
                answering = nowAnswering;
                if (current == Game.CHRONOMATRON) {
                    if (answering) CHRONOMATRON.startAnswering();
                    else CHRONOMATRON.startShowing();
                }
            }
            return;
        }
        if (slot < BOARD_FIRST || slot > BOARD_LAST) return;
        switch (current) {
            case CHRONOMATRON -> {
                if (!answering) CHRONOMATRON.onShown(slot, litColor(stack), glassColor(stack) != null);
            }
            case ULTRASEQUENCER -> {
                if (!answering) {
                    int number = number(stack);
                    if (number > 0) ULTRASEQUENCER.onShown(slot, number, stack.copy());
                }
            }
            case SUPERPAIRS -> {
                if (!isHidden(stack)) SUPERPAIRS.onRevealed(slot, stack.copy());
            }
        }
    }

    /** Called when the player clicks a slot of the open menu. */
    public static void onSlotClicked(AbstractContainerMenu clicked, int slot) {
        Game current = current(clicked);
        if (current == null || !answering) return;
        switch (current) {
            case CHRONOMATRON -> {
                if (slot >= BOARD_FIRST && slot <= BOARD_LAST) CHRONOMATRON.onClicked(color(clicked.getSlot(slot).getItem()));
            }
            case ULTRASEQUENCER -> ULTRASEQUENCER.onClicked(slot);
            case SUPERPAIRS -> { }
        }
    }

    /** Draws the highlight behind a slot's item. */
    public static void extractSlotBackground(ContainerScreen screen, GuiGraphicsExtractor graphics, Slot slot) {
        Game current = current(screen.getMenu());
        if (current == null || slot.container != menu.slots.get(0).container) return;
        int color = switch (current) {
            case CHRONOMATRON -> answering ? CHRONOMATRON.highlight(color(slot.getItem())) : 0;
            case ULTRASEQUENCER -> answering ? ULTRASEQUENCER.highlight(slot.index) : 0;
            case SUPERPAIRS -> isHidden(slot.getItem()) ? SUPERPAIRS.highlight(slot.index) : 0;
        };
        if (color != 0) graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
    }

    /** The item to draw in a slot: the remembered one where the game has hidden it again. */
    public static ItemStack displayed(ContainerScreen screen, Slot slot, ItemStack actual) {
        Game current = current(screen.getMenu());
        if (current == null || slot.container != menu.slots.get(0).container) return actual;
        ItemStack remembered = switch (current) {
            case ULTRASEQUENCER -> answering ? ULTRASEQUENCER.remembered(slot.index) : null;
            case SUPERPAIRS -> isHidden(actual) ? SUPERPAIRS.remembered(slot.index) : null;
            case CHRONOMATRON -> null;
        };
        return remembered != null ? remembered : actual;
    }

    private static String path(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    /** The color of a lit Chronomatron block ("red" for red terracotta), or null. */
    static String litColor(ItemStack stack) {
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
        String lit = litColor(stack);
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
     * Chronomatron lights colored blocks one after another (terracotta in place of the block's
     * glass, on one slot or a column of two), then the player repeats them. Every color has its
     * own slots, so the sequence is kept as colors and every slot of the next color is highlighted.
     *
     * Each round replays the whole sequence and adds one step. The sequence is kept across rounds
     * and the replay only checked against it, so a block lit late or a missed update can't shift
     * the steps; where the replay disagrees, the rest is recorded anew. A step starts when a block
     * lights while none is lit, or one of another color; that also holds when the next block lights
     * before the last one has gone dark.
     */
    static final class Chronomatron {
        final List<String> sequence = new ArrayList<>();
        /** Steps shown so far in this round's replay. */
        int replayed;
        /** Steps the player has answered this round. */
        int answered;
        /** The slots lit right now, all of {@link #litColor}. */
        private final Set<Integer> lit = new HashSet<>();
        private String litColor;

        void reset() {
            sequence.clear();
            startShowing();
            answered = 0;
        }

        void startShowing() {
            replayed = 0;
            lit.clear();
            litColor = null;
        }

        void startAnswering() {
            // A replay that ended early leaves the rest of an older sequence that wasn't shown.
            if (replayed < sequence.size()) sequence.subList(replayed, sequence.size()).clear();
            answered = 0;
            lit.clear();
            litColor = null;
            LOGGER.info("[chronomatron] answer {}", sequence);
        }

        /** A board slot changed while the game shows its pattern. */
        void onShown(int slot, String lightsUp, boolean darkens) {
            if (lightsUp != null) {
                if (!lit.isEmpty() && lightsUp.equals(litColor)) {
                    lit.add(slot);
                    return;
                }
                lit.clear();
                lit.add(slot);
                litColor = lightsUp;
                step(lightsUp);
            } else if (darkens) {
                lit.remove(slot);
            }
        }

        private void step(String color) {
            if (replayed < sequence.size() && !sequence.get(replayed).equals(color)) {
                sequence.subList(replayed, sequence.size()).clear();
            }
            boolean added = replayed >= sequence.size();
            if (added) sequence.add(color);
            replayed++;
            LOGGER.info("[chronomatron] step {} {} ({})", replayed, color, added ? "new" : "replay");
        }

        /** The player clicked a block of this color (null for anything else). */
        void onClicked(String color) {
            boolean right = color != null && answered < sequence.size() && sequence.get(answered).equals(color);
            if (right) answered++;
            LOGGER.info("[chronomatron] click {} ({})", color, right ? "step " + answered : "not the next step");
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
