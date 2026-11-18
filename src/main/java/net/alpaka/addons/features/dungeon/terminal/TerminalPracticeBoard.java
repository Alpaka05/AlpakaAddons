package net.alpaka.addons.features.dungeon.terminal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * One practice terminal, played entirely on the client: what Hypixel would put in the chest and
 * what it would do with a click (terminals.md 3, 0.27.2 layouts). Plain values only, so it can be
 * tested without a game; {@link TerminalPractice} turns the cells into item stacks.
 *
 * The rules follow the research and Odin's simulator where Hypixel's own behaviour is not
 * documented: a wrong click on Click in Order, What Starts With or Select All changes nothing, and
 * a mistimed Click the Button on Time press holds the moving pane for a moment.
 */
final class TerminalPracticeBoard {

    /** The letters What Starts With is seen asking for (Odin's simulator; not a complete list). */
    static final String[] LETTERS = {"A", "B", "C", "D", "G", "M", "N", "R", "S", "T", "W"};

    /** The sixteen colors Select All can ask for, as TerminalItem names them. */
    static final String[] COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan",
            "purple", "blue", "brown", "green", "red", "black"};

    /** One column step of Click the Button on Time's moving pane (Odin's simulator; Hypixel's speed is not documented). */
    static final long MELODY_STEP_MS = 500L;
    /** How long the moving pane holds after a mistimed press (wiki: "waits 2 s"). */
    static final long MELODY_PAUSE_MS = 2000L;

    /** An item What Starts With may show: its registry path and English name. */
    record Choice(String itemId, String name) {}

    private final TerminalKind kind;
    private final String title;
    private final TerminalItem[] cells;
    /** What Starts With and Select All: the slots the title asks for. */
    private final boolean[] wanted;
    /** What Starts With and Select All: the slots clicked, also for items that glint anyway. */
    private final boolean[] clicked;
    private final Random random;

    private int melodyRow = 1;
    private int melodyTarget = 1;
    private int melodyColumn = 1;
    private int melodyDirection = 1;
    private long melodyNextMs = 0L;

    private TerminalPracticeBoard(TerminalKind kind, String title, Random random) {
        this.kind = kind;
        this.title = title;
        this.random = random;
        int size = kind.rows() * 9;
        this.cells = new TerminalItem[size];
        this.wanted = new boolean[size];
        this.clicked = new boolean[size];
        for (int i = 0; i < size; i++) cells[i] = filler(i);
    }

    /**
     * A new terminal of {@code kind}. {@code startsWithItems} is what What Starts With picks its
     * items from; it must hold names starting with every letter of {@link #LETTERS}.
     */
    static TerminalPracticeBoard create(TerminalKind kind, Random random, List<Choice> startsWithItems, long nowMs) {
        return switch (kind) {
            case PANES -> panes(random);
            case RUBIX -> rubix(random);
            case NUMBERS -> numbers(random);
            case STARTS_WITH -> startsWith(random, startsWithItems);
            case SELECT_ALL -> selectAll(random);
            case MELODY -> melody(random, nowMs);
        };
    }

    TerminalKind kind() {
        return kind;
    }

    /** The chest title, as Hypixel writes it. */
    String title() {
        return title;
    }

    int size() {
        return cells.length;
    }

    TerminalItem item(int slot) {
        return cells[slot];
    }

    /** Every cell from slot 0, for the solver. */
    List<TerminalItem> items() {
        List<TerminalItem> all = new ArrayList<>(cells.length);
        Collections.addAll(all, cells);
        return all;
    }

    // ---- Playing --------------------------------------------------------------------------------

    /**
     * What the server would do with a click on {@code slot}: true when the terminal changed. A
     * click on filler, outside the chest or that the terminal does not take changes nothing.
     */
    boolean click(int slot, int button, long nowMs) {
        if (slot < 0 || slot >= cells.length || cells[slot].filler() || cells[slot].empty() || solved()) return false;
        TerminalItem item = cells[slot];
        switch (kind) {
            case PANES -> {
                boolean red = "red_stained_glass_pane".equals(item.itemId());
                if (!red && !"lime_stained_glass_pane".equals(item.itemId())) return false;
                cells[slot] = pane(slot, red ? "lime" : "red", 1);
                return true;
            }
            case RUBIX -> {
                int color = TerminalSolver.rubixColor(item);
                if (color < 0) return false;
                int next = Math.floorMod(color + (button == 1 ? -1 : 1), TerminalSolver.RUBIX_CYCLE.length);
                cells[slot] = pane(slot, TerminalSolver.RUBIX_CYCLE[next], 1);
                return true;
            }
            case NUMBERS -> {
                if (!"red_stained_glass_pane".equals(item.itemId()) || item.count() != lowestRed()) return false;
                cells[slot] = pane(slot, "lime", item.count());
                return true;
            }
            case STARTS_WITH, SELECT_ALL -> {
                if (!wanted[slot] || clicked[slot]) return false;
                clicked[slot] = true;
                cells[slot] = TerminalItem.of(slot, item.itemId(), item.name(), item.count(), true);
                return true;
            }
            case MELODY -> {
                return melodyPress(slot, nowMs);
            }
        }
        return false;
    }

    /** Moves Click the Button on Time's pane on to {@code nowMs}; true when it moved. */
    boolean advance(long nowMs) {
        if (kind != TerminalKind.MELODY || solved()) return false;
        boolean moved = false;
        // A frame that came late catches up step by step, but never by more than one sweep.
        for (int steps = 0; nowMs >= melodyNextMs && steps < 8; steps++) {
            melodyColumn += melodyDirection;
            if (melodyColumn <= 1 || melodyColumn >= 5) {
                melodyColumn = Math.max(1, Math.min(5, melodyColumn));
                melodyDirection = -melodyDirection;
            }
            melodyNextMs += MELODY_STEP_MS;
            moved = true;
        }
        if (nowMs >= melodyNextMs) melodyNextMs = nowMs + MELODY_STEP_MS;
        if (moved) drawMelody();
        return moved;
    }

    boolean solved() {
        switch (kind) {
            case PANES -> {
                for (TerminalItem item : cells) {
                    if ("red_stained_glass_pane".equals(item.itemId())) return false;
                }
                return true;
            }
            case RUBIX -> {
                int first = -1;
                for (TerminalItem item : cells) {
                    int color = TerminalSolver.rubixColor(item);
                    if (color < 0) continue;
                    if (first < 0) first = color;
                    else if (color != first) return false;
                }
                return true;
            }
            case NUMBERS -> {
                return lowestRed() < 0;
            }
            case STARTS_WITH, SELECT_ALL -> {
                for (int i = 0; i < cells.length; i++) {
                    if (wanted[i] && !clicked[i]) return false;
                }
                return true;
            }
            case MELODY -> {
                return melodyRow > 3;
            }
        }
        return false;
    }

    private int lowestRed() {
        int lowest = -1;
        for (TerminalItem item : cells) {
            if (!"red_stained_glass_pane".equals(item.itemId())) continue;
            if (lowest < 0 || item.count() < lowest) lowest = item.count();
        }
        return lowest;
    }

    // ---- Click the Button on Time ---------------------------------------------------------------

    private boolean melodyPress(int slot, long nowMs) {
        if (slot != melodyRow * 9 + 7) return false;
        if (melodyColumn != melodyTarget) {
            melodyNextMs = Math.max(melodyNextMs, nowMs + MELODY_PAUSE_MS);
            return false;
        }
        melodyRow++;
        melodyTarget = 1 + random.nextInt(5);
        melodyColumn = 1;
        melodyDirection = 1;
        melodyNextMs = nowMs + MELODY_STEP_MS;
        drawMelody();
        return true;
    }

    private void drawMelody() {
        for (int row = 0; row <= 4; row++) {
            for (int column = 1; column <= 5; column++) {
                int slot = row * 9 + column;
                if (row == 0 || row == 4) {
                    cells[slot] = column == melodyTarget && melodyRow <= 3 ? pane(slot, "magenta", 1) : filler(slot);
                } else if (row == melodyRow) {
                    cells[slot] = pane(slot, column == melodyColumn ? "lime" : "red", 1);
                } else {
                    cells[slot] = pane(slot, "white", 1);
                }
            }
            if (row >= 1 && row <= 3) {
                int slot = row * 9 + 7;
                cells[slot] = TerminalItem.of(slot, (row == melodyRow ? "lime" : "red") + "_terracotta", "", 1, false);
            }
        }
    }

    // ---- Filling the chest ----------------------------------------------------------------------

    private static TerminalPracticeBoard panes(Random random) {
        TerminalPracticeBoard b = new TerminalPracticeBoard(TerminalKind.PANES, "Correct all the panes!", random);
        int[] slots = TerminalKind.PANES.playSlots();
        boolean anyRed = false;
        for (int slot : slots) {
            boolean lime = random.nextFloat() < 0.25f;
            anyRed |= !lime;
            b.cells[slot] = pane(slot, lime ? "lime" : "red", 1);
        }
        if (!anyRed) {
            int slot = slots[random.nextInt(slots.length)];
            b.cells[slot] = pane(slot, "red", 1);
        }
        return b;
    }

    private static TerminalPracticeBoard rubix(Random random) {
        TerminalPracticeBoard b = new TerminalPracticeBoard(TerminalKind.RUBIX, "Change all to same color!", random);
        int[] slots = TerminalKind.RUBIX.playSlots();
        int n = TerminalSolver.RUBIX_CYCLE.length;
        int[] colors = new int[slots.length];
        for (int i = 0; i < slots.length; i++) colors[i] = random.nextInt(n);
        boolean same = true;
        for (int color : colors) same &= color == colors[0];
        // Never one that is solved before it starts.
        if (same) colors[random.nextInt(colors.length)] = (colors[0] + 1 + random.nextInt(n - 1)) % n;
        for (int i = 0; i < slots.length; i++) b.cells[slots[i]] = pane(slots[i], TerminalSolver.RUBIX_CYCLE[colors[i]], 1);
        return b;
    }

    private static TerminalPracticeBoard numbers(Random random) {
        TerminalPracticeBoard b = new TerminalPracticeBoard(TerminalKind.NUMBERS, "Click in order!", random);
        int[] slots = TerminalKind.NUMBERS.playSlots();
        List<Integer> order = new ArrayList<>();
        for (int i = 1; i <= slots.length; i++) order.add(i);
        Collections.shuffle(order, random);
        for (int i = 0; i < slots.length; i++) b.cells[slots[i]] = pane(slots[i], "red", order.get(i));
        return b;
    }

    private static TerminalPracticeBoard startsWith(Random random, List<Choice> pool) {
        String letter = LETTERS[random.nextInt(LETTERS.length)];
        List<Choice> matching = new ArrayList<>();
        List<Choice> others = new ArrayList<>();
        for (Choice choice : pool) {
            if (choice.name().isBlank()) continue;
            if (choice.name().regionMatches(true, 0, letter, 0, 1)) matching.add(choice);
            else others.add(choice);
        }
        TerminalPracticeBoard b = new TerminalPracticeBoard(TerminalKind.STARTS_WITH, "What starts with: '" + letter + "'?", random);
        int[] slots = TerminalKind.STARTS_WITH.playSlots();
        int guaranteed = slots[random.nextInt(slots.length)];
        for (int slot : slots) {
            boolean match = !matching.isEmpty() && (slot == guaranteed || random.nextFloat() < 0.3f || others.isEmpty());
            Choice choice = match ? matching.get(random.nextInt(matching.size())) : others.get(random.nextInt(others.size()));
            b.cells[slot] = TerminalItem.of(slot, choice.itemId(), choice.name(), 1, false);
            b.wanted[slot] = match;
        }
        return b;
    }

    private static TerminalPracticeBoard selectAll(Random random) {
        String color = COLORS[random.nextInt(COLORS.length)];
        String words = color.equals("light_gray") ? "SILVER" : color.replace('_', ' ').toUpperCase(Locale.ROOT);
        TerminalPracticeBoard b = new TerminalPracticeBoard(TerminalKind.SELECT_ALL, "Select all the " + words + " items!", random);
        int[] slots = TerminalKind.SELECT_ALL.playSlots();
        int guaranteed = slots[random.nextInt(slots.length)];
        for (int slot : slots) {
            boolean match = slot == guaranteed || random.nextFloat() < 0.25f;
            String itemColor = color;
            while (!match && itemColor.equals(color)) itemColor = COLORS[random.nextInt(COLORS.length)];
            b.cells[slot] = colored(slot, itemColor, random.nextInt(4));
            b.wanted[slot] = match;
        }
        return b;
    }

    private static TerminalPracticeBoard melody(Random random, long nowMs) {
        TerminalPracticeBoard b = new TerminalPracticeBoard(TerminalKind.MELODY, "Click the button on time!", random);
        b.melodyTarget = 1 + random.nextInt(5);
        b.melodyNextMs = nowMs + MELODY_STEP_MS;
        b.drawMelody();
        return b;
    }

    // ---- Items ----------------------------------------------------------------------------------

    private static TerminalItem filler(int slot) {
        return TerminalItem.of(slot, "black_stained_glass_pane", "", 1, false);
    }

    private static TerminalItem pane(int slot, String color, int count) {
        return TerminalItem.of(slot, color + "_stained_glass_pane", "", count, false);
    }

    /**
     * Select All's items, Hypixel's four 1.8 families under their old names: stained glass, wool,
     * stained clay (terracotta) and dye, whose 1.8 names differ per color.
     */
    static TerminalItem colored(int slot, String color, int family) {
        String words = words(color);
        return switch (family) {
            case 0 -> TerminalItem.of(slot, color + "_stained_glass", words + " Stained Glass", 1, false);
            case 1 -> TerminalItem.of(slot, color + "_wool", color.equals("white") ? "Wool" : words + " Wool", 1, false);
            case 2 -> TerminalItem.of(slot, color + "_terracotta", words + " Stained Clay", 1, false);
            default -> switch (color) {
                case "white" -> TerminalItem.of(slot, "bone_meal", "Bone Meal", 1, false);
                case "blue" -> TerminalItem.of(slot, "lapis_lazuli", "Lapis Lazuli", 1, false);
                case "brown" -> TerminalItem.of(slot, "cocoa_beans", "Cocoa Beans", 1, false);
                case "black" -> TerminalItem.of(slot, "ink_sac", "Ink Sac", 1, false);
                case "red" -> TerminalItem.of(slot, "red_dye", "Rose Red", 1, false);
                case "green" -> TerminalItem.of(slot, "green_dye", "Cactus Green", 1, false);
                case "yellow" -> TerminalItem.of(slot, "yellow_dye", "Dandelion Yellow", 1, false);
                default -> TerminalItem.of(slot, color + "_dye", words + " Dye", 1, false);
            };
        };
    }

    /** "light_blue" -> "Light Blue". */
    static String words(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
