package net.alpaka.addons.features.dungeon.terminal;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The six terminals filled locally the way Hypixel fills them (terminals.md 3, 0.27.2 layouts),
 * for the screenshot tour: the custom window can be looked at without a server, and once the
 * solvers are in, with their real hints.
 *
 * Nothing here is sent anywhere. The chest is not the player's open menu and the window has no
 * session, so it has nothing to click and does not try.
 */
final class TerminalScreenPreview {

    private TerminalScreenPreview() {}

    /** The window for {@code kind} on a filled chest, or null without a player (the title screen). */
    static Screen screen(TerminalKind kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return null;
        Inventory inventory = mc.player.getInventory();
        int rows = kind.rows();
        SimpleContainer chest = new SimpleContainer(rows * 9);
        for (int i = 0; i < rows * 9; i++) chest.setItem(i, filler());
        String title = switch (kind) {
            case PANES -> panes(chest);
            case RUBIX -> rubix(chest);
            case NUMBERS -> numbers(chest);
            case STARTS_WITH -> startsWith(chest);
            case SELECT_ALL -> selectAll(chest);
            case MELODY -> melody(chest);
        };
        ChestMenu menu = new ChestMenu(type(rows), 9_100 + kind.ordinal(), inventory, chest, rows);
        return TerminalScreen.preview(menu, inventory, title, kind);
    }

    private static MenuType<ChestMenu> type(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    // ---- The terminals --------------------------------------------------------------------------

    /** Correct All the Panes: red panes still to turn, lime ones already on. */
    private static String panes(SimpleContainer chest) {
        boolean[] on = {true, false, false, true, false, false, true, true, false, false, true, false, false, false, true};
        int[] slots = TerminalKind.PANES.playSlots();
        for (int i = 0; i < slots.length; i++) chest.setItem(slots[i], pane(on[i] ? DyeColor.LIME : DyeColor.RED, ""));
        return "Correct all the panes!";
    }

    /** Change All to Same Color: nine panes in the five colors of the cycle. */
    private static String rubix(SimpleContainer chest) {
        DyeColor[] colors = {
                DyeColor.RED, DyeColor.ORANGE, DyeColor.RED,
                DyeColor.YELLOW, DyeColor.RED, DyeColor.BLUE,
                DyeColor.ORANGE, DyeColor.GREEN, DyeColor.RED};
        int[] slots = TerminalKind.RUBIX.playSlots();
        for (int i = 0; i < slots.length; i++) chest.setItem(slots[i], pane(colors[i], ""));
        return "Change all to same color!";
    }

    /** Click in Order: ten red panes numbered by their stack size, the first two already clicked. */
    private static String numbers(SimpleContainer chest) {
        int[] order = {7, 2, 9, 4, 10, 1, 6, 3, 8, 5};
        int[] slots = TerminalKind.NUMBERS.playSlots();
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = pane(order[i] <= 2 ? DyeColor.LIME : DyeColor.RED, "");
            stack.setCount(order[i]);
            chest.setItem(slots[i], stack);
        }
        return "Click in order!";
    }

    /** What Starts With 'S': assorted items under their English names, one S item already clicked. */
    private static String startsWith(SimpleContainer chest) {
        Object[][] items = {
                {Items.SUGAR_CANE, "Sugar Cane"}, {Items.APPLE, "Apple"}, {Items.STONE, "Stone"},
                {Items.BONE, "Bone"}, {Items.DIAMOND, "Diamond"}, {Items.SAND, "Sand"},
                {Items.PAPER, "Paper"}, {Items.GOLD_INGOT, "Gold Ingot"}, {Items.SLIME_BALL, "Slimeball"},
                {Items.REDSTONE, "Redstone"}, {Items.COBBLESTONE, "Cobblestone"}, {Items.ARROW, "Arrow"},
                {Items.SHEARS, "Shears"}, {Items.BREAD, "Bread"}, {Items.COAL, "Coal"},
                {Items.SNOWBALL, "Snowball"}, {Items.FEATHER, "Feather"}, {Items.LEATHER, "Leather"},
                {Items.EMERALD, "Emerald"}, {Items.STRING, "String"}, {Items.BOOK, "Book"}};
        int[] slots = TerminalKind.STARTS_WITH.playSlots();
        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = named((Item) items[i][0], (String) items[i][1]);
            if ("Stone".equals(items[i][1])) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            chest.setItem(slots[i], stack);
        }
        return "What starts with: 'S'?";
    }

    /** Select All the RED Items: Hypixel's four 1.8 families under their old names, one red one already clicked. */
    private static String selectAll(SimpleContainer chest) {
        ItemStack[] items = {
                named(Items.DYE.pick(DyeColor.RED), "Rose Red"), named(Items.WOOL.pick(DyeColor.ORANGE), "Orange Wool"),
                named(Items.STAINED_GLASS.pick(DyeColor.RED), "Red Stained Glass"), named(Items.DYE.pick(DyeColor.GREEN), "Cactus Green"),
                named(Items.DYED_TERRACOTTA.pick(DyeColor.LIGHT_BLUE), "Light Blue Stained Clay"), named(Items.BONE_MEAL, "Bone Meal"),
                named(Items.WOOL.pick(DyeColor.RED), "Red Wool"),
                named(Items.LAPIS_LAZULI, "Lapis Lazuli"), named(Items.DYED_TERRACOTTA.pick(DyeColor.RED), "Red Stained Clay"),
                named(Items.STAINED_GLASS.pick(DyeColor.YELLOW), "Yellow Stained Glass"), named(Items.DYE.pick(DyeColor.YELLOW), "Dandelion Yellow"),
                named(Items.WOOL.pick(DyeColor.PINK), "Pink Wool"), named(Items.INK_SAC, "Ink Sac"),
                named(Items.STAINED_GLASS.pick(DyeColor.LIME), "Lime Stained Glass"),
                named(Items.DYED_TERRACOTTA.pick(DyeColor.ORANGE), "Orange Stained Clay"), named(Items.WOOL.pick(DyeColor.RED), "Red Wool"),
                named(Items.COCOA_BEANS, "Cocoa Beans"), named(Items.DYE.pick(DyeColor.PURPLE), "Purple Dye"),
                named(Items.STAINED_GLASS.pick(DyeColor.MAGENTA), "Magenta Stained Glass"), named(Items.WOOL.pick(DyeColor.GRAY), "Gray Wool"),
                named(Items.DYE.pick(DyeColor.RED), "Rose Red"),
                named(Items.DYED_TERRACOTTA.pick(DyeColor.CYAN), "Cyan Stained Clay"), named(Items.WOOL.pick(DyeColor.LIGHT_GRAY), "Light Gray Wool"),
                named(Items.STAINED_GLASS.pick(DyeColor.BLUE), "Blue Stained Glass"), named(Items.DYE.pick(DyeColor.LIME), "Lime Dye"),
                named(Items.DYED_TERRACOTTA.pick(DyeColor.RED), "Red Stained Clay"), named(Items.WOOL.pick(DyeColor.BROWN), "Brown Wool"),
                named(Items.DYE.pick(DyeColor.ORANGE), "Orange Dye")};
        // One red item the player has already clicked: Hypixel marks it with the glint.
        items[6].set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        int[] slots = TerminalKind.SELECT_ALL.playSlots();
        for (int i = 0; i < slots.length; i++) chest.setItem(slots[i], items[i]);
        return "Select all the RED items!";
    }

    /**
     * Click the Button on Time, 0.27.2: the magenta target in rows 0 and 4, three rows of panes
     * with the lime one moving through the active (red) row, and the three terracotta buttons in
     * column 7, lime for the active row.
     */
    private static String melody(SimpleContainer chest) {
        int target = 4;
        int activeRow = 2;
        int moving = 2;
        for (int row = 0; row <= 4; row++) {
            for (int column = 1; column <= 5; column++) {
                int slot = row * 9 + column;
                if (row == 0 || row == 4) {
                    if (column == target) chest.setItem(slot, pane(DyeColor.MAGENTA, ""));
                } else if (row == activeRow) {
                    chest.setItem(slot, pane(column == moving ? DyeColor.LIME : DyeColor.RED, ""));
                } else {
                    chest.setItem(slot, pane(DyeColor.WHITE, ""));
                }
            }
            if (row >= 1 && row <= 3) {
                chest.setItem(row * 9 + 7, named(Items.DYED_TERRACOTTA.pick(row == activeRow ? DyeColor.LIME : DyeColor.RED), ""));
            }
        }
        return "Click the button on time!";
    }

    // ---- Items ----------------------------------------------------------------------------------

    /** The black pane with a blank name that fills the rest of every terminal. */
    private static ItemStack filler() {
        return pane(DyeColor.BLACK, "");
    }

    private static ItemStack pane(DyeColor color, String name) {
        return named(Items.STAINED_GLASS_PANE.pick(color), name);
    }

    /** An item with a custom name, as Hypixel names every item; an empty name shows nothing. */
    private static ItemStack named(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(s -> s.withItalic(false)));
        return stack;
    }
}
