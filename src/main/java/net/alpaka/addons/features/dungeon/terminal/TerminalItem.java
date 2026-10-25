package net.alpaka.addons.features.dungeon.terminal;

import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * What one chest slot of a terminal holds, reduced to what the solvers read, so they can be tested
 * without a game.
 *
 * @param slot     the chest slot index
 * @param itemId   the item's registry path, "lime_stained_glass_pane"; "air" for an empty slot
 * @param name     the stack's name with formatting stripped; Hypixel names every item in English,
 *                 so this is the same whatever language the game runs in
 * @param count    the stack size (Click in Order's numbers)
 * @param glint    whether it shows the enchantment glint (how Hypixel marks a clicked item)
 * @param dyeColor the color of a dyed item ("lime", "light_blue", ...): the stained glass, wool,
 *                 terracotta, dye, concrete and carpet families plus bone meal, lapis lazuli, cocoa
 *                 beans and ink sac, which are the 1.8 dyes; null for anything else
 */
public record TerminalItem(int slot, String itemId, String name, int count, boolean glint, String dyeColor) {

    public static final String AIR = "air";

    /** The sixteen dye colors as they start a registry path; compared whole, so "blue" never matches "light_blue". */
    private static final String[] COLORS = {
            "light_blue", "light_gray", "magenta", "orange", "yellow", "purple", "brown", "white", "green",
            "black", "lime", "pink", "gray", "cyan", "blue", "red"};

    /** The item families whose ids are "<color>_<family>". */
    private static final String[] FAMILIES = {
            "_stained_glass_pane", "_stained_glass", "_wool", "_terracotta", "_dye", "_concrete_powder",
            "_concrete", "_carpet"};

    /** A slot as the client holds it now. */
    public static TerminalItem of(int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return empty(slot);
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        String name = SkyblockUtils.cleanColor(stack.getHoverName().getString());
        return of(slot, id, name, stack.getCount(), stack.hasFoil());
    }

    /** A slot from plain values; the dye color follows from the id. For tests and the simulator. */
    public static TerminalItem of(int slot, String itemId, String name, int count, boolean glint) {
        return new TerminalItem(slot, itemId, name == null ? "" : name, count, glint, dyeColorOf(itemId));
    }

    public static TerminalItem empty(int slot) {
        return new TerminalItem(slot, AIR, "", 0, false, null);
    }

    public boolean empty() {
        return count <= 0 || AIR.equals(itemId);
    }

    /** The black pane with a blank name Hypixel fills the rest of the chest with. */
    public boolean filler() {
        return "black_stained_glass_pane".equals(itemId) && name.isBlank();
    }

    /** The color a registry path stands for, or null; see the record's description. */
    public static String dyeColorOf(String itemId) {
        if (itemId == null) return null;
        switch (itemId) {
            case "bone_meal": return "white";
            case "lapis_lazuli": return "blue";
            case "cocoa_beans": return "brown";
            case "ink_sac": return "black";
            default: break;
        }
        for (String family : FAMILIES) {
            if (!itemId.endsWith(family)) continue;
            String color = itemId.substring(0, itemId.length() - family.length());
            for (String known : COLORS) {
                if (known.equals(color)) return known;
            }
            return null;
        }
        return null;
    }
}
