package net.alpaka.addons.features.enchantments;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The highest level of every SkyBlock enchantment, by the name its lore shows.
 *
 * The levels are SkyHanni's (SkyHanni-REPO constants/Enchants.json, September 2026), with Looting
 * VII and Smoldering VI added since. "Highest" means the best level that exists at all, not the
 * best an enchanting table gives: Sharpness tops out at VII, not at V. Stacking
 * enchantments (Expertise, Compact, ...) count by the level they show, so they are maxed at X.
 * An enchantment missing here is simply never coloured.
 *
 * The table is only the floor. When Hypixel adds a level, its book turns up on the Bazaar, so
 * {@link #raise} moves an enchantment's top up to the highest level the Bazaar trades, and a new
 * level is coloured without a new release.
 */
final class Enchants {

    private Enchants() {
    }

    static final Map<String, Integer> NORMAL = Map.ofEntries(
        Map.entry("Angler", 6), Map.entry("Aqua Affinity", 1), Map.entry("Bane of Arthropods", 7),
        Map.entry("Big Brain", 5), Map.entry("Blast Protection", 7), Map.entry("Blessing", 6),
        Map.entry("Bug Blender", 5), Map.entry("Caster", 6), Map.entry("Cayenne", 5),
        Map.entry("Chance", 5), Map.entry("Cleave", 6), Map.entry("Counter-Strike", 5),
        Map.entry("Critical", 7), Map.entry("Cubism", 6), Map.entry("Charm", 6),
        Map.entry("Corruption", 5), Map.entry("Dedication", 4), Map.entry("Delicate", 5),
        Map.entry("Depth Strider", 3), Map.entry("Divine Gift", 3), Map.entry("Dragon Tracer", 5),
        Map.entry("Drain", 5), Map.entry("Efficiency", 10), Map.entry("Ender Slayer", 7),
        Map.entry("Execute", 6), Map.entry("Experience", 5), Map.entry("Feast", 5),
        Map.entry("Feather Falling", 10), Map.entry("Fire Aspect", 3),
        Map.entry("Fire Protection", 7), Map.entry("First Strike", 5), Map.entry("Flame", 2),
        Map.entry("Fortune", 4), Map.entry("Frail", 7), Map.entry("Frost Walker", 2),
        Map.entry("Vivacious Vitality", 10), Map.entry("Forest Pledge", 6),
        Map.entry("Giant Killer", 7), Map.entry("Gravity", 6), Map.entry("Great Spook", 1),
        Map.entry("Green Thumb", 5), Map.entry("Growth", 7), Map.entry("Harvesting", 6),
        Map.entry("Hardened Vitality", 10), Map.entry("Ice Cold", 5), Map.entry("Impaling", 5),
        Map.entry("Infinite Quiver", 10), Map.entry("Karma", 6), Map.entry("Knockback", 2),
        Map.entry("Lapidary", 5), Map.entry("Lethality", 6), Map.entry("Life Steal", 5),
        Map.entry("Looting", 7), Map.entry("Luck", 7), Map.entry("Luck of the Sea", 7),
        Map.entry("Lure", 6), Map.entry("Magnet", 6), Map.entry("Mana Steal", 3),
        Map.entry("Vampiric Vitality", 10), Map.entry("Overload", 5),
        Map.entry("Paleontologist", 5), Map.entry("Pesterminator", 6), Map.entry("Petalfall", 5),
        Map.entry("Piercing", 1), Map.entry("Piscary", 7), Map.entry("Power", 7),
        Map.entry("Prismatic", 5), Map.entry("Projectile Protection", 7), Map.entry("Prosecute", 6),
        Map.entry("Prosperity", 5), Map.entry("Protection", 7), Map.entry("Punch", 2),
        Map.entry("Pyroclasm", 6), Map.entry("Quantum", 5), Map.entry("Quick Bite", 5),
        Map.entry("Rainbow", 3), Map.entry("Reflection", 5), Map.entry("Rejuvenate", 5),
        Map.entry("Replenish", 1), Map.entry("Respiration", 4), Map.entry("Respite", 5),
        Map.entry("Scavenger", 6), Map.entry("Scuba", 6), Map.entry("Sharpness", 7),
        Map.entry("Silk Touch", 1), Map.entry("Small Brain", 5), Map.entry("Smarty Pants", 5),
        Map.entry("Smelting Touch", 1), Map.entry("Smite", 7), Map.entry("Snipe", 4),
        Map.entry("Spiked Hook", 7), Map.entry("Sugar Rush", 3), Map.entry("Sunder", 6),
        Map.entry("Smoldering", 6), Map.entry("Stealth", 6), Map.entry("Strong Vitality", 10),
        Map.entry("Tabasco", 3), Map.entry("Thorns", 4), Map.entry("Thunderbolt", 7),
        Map.entry("Thunderlord", 7), Map.entry("Tidal", 3), Map.entry("Titan Killer", 7),
        Map.entry("Transylvanian", 5), Map.entry("Triple-Strike", 5),
        Map.entry("True Protection", 1), Map.entry("Turbo-Cacti", 7), Map.entry("Turbo-Cane", 7),
        Map.entry("Turbo-Carrot", 7), Map.entry("Turbo-Cocoa", 7), Map.entry("Turbo-Melon", 7),
        Map.entry("Turbo-Mushrooms", 7), Map.entry("Turbo-Potato", 7),
        Map.entry("Turbo-Pumpkin", 7), Map.entry("Turbo-Warts", 7), Map.entry("Turbo-Wheat", 7),
        Map.entry("Turbo-Rose", 7), Map.entry("Turbo-Moonflower", 7),
        Map.entry("Turbo-Sunflower", 7), Map.entry("Vampirism", 6), Map.entry("Venomous", 7),
        Map.entry("Vicious", 5), Map.entry("Woodsplitter", 6), Map.entry("Absorb", 10),
        Map.entry("Compact", 10), Map.entry("Cultivating", 10), Map.entry("Expertise", 10),
        Map.entry("Hecatomb", 10), Map.entry("Champion", 10), Map.entry("Toxophilite", 10));

    /**
     * Bazaar ids that don't follow from the lore name: these enchantments were renamed in the lore
     * and kept their old id ({@code ENCHANTMENT_<id>_<level>}). From SkyHanni's nbtName.
     */
    private static final Map<String, String> RENAMED = Map.ofEntries(
        Map.entry("Dragon Tracer", "AIMING"), Map.entry("Drain", "SYPHON"),
        Map.entry("Duplex", "ULTIMATE_REITERATE"), Map.entry("Gravity", "DRAGON_HUNTER"),
        Map.entry("Hardened Vitality", "HARDENED_MANA"), Map.entry("Looting", "LOOTING_NEW"),
        Map.entry("Prismatic", "PRISTINE"), Map.entry("Pyroclasm", "MAGMARIZER"),
        Map.entry("Strong Vitality", "STRONG_MANA"), Map.entry("Turbo-Cacti", "TURBO_CACTUS"),
        Map.entry("Turbo-Cocoa", "TURBO_COCO"), Map.entry("Vampiric Vitality", "MANA_VAMPIRE"),
        Map.entry("Vivacious Vitality", "FEROCIOUS_MANA"), Map.entry("Woodsplitter", "ARCANE"));

    /** The Bazaar id of the enchantment's book at {@code level}, e.g. ENCHANTMENT_ULTIMATE_WISE_5. */
    static String bazaarId(String name, int level) {
        String id = RENAMED.get(name);
        if (id == null) {
            id = name.toUpperCase(Locale.ROOT).replace("'", "").replaceAll("[^A-Z0-9]+", "_");
            if (ULTIMATE.containsKey(name) && !id.startsWith("ULTIMATE_")) id = "ULTIMATE_" + id;
        }
        return "ENCHANTMENT_" + id + "_" + level;
    }

    /** Levels above the table the Bazaar has shown, by name; replaced whole, so it is read safely off-thread. */
    private static volatile Map<String, Integer> raised = Map.of();

    /** The enchantment's highest level, or null for one not in the tables. */
    static Integer max(String name) {
        Integer max = raised.get(name);
        if (max != null) return max;
        max = NORMAL.get(name);
        return max != null ? max : ULTIMATE.get(name);
    }

    /**
     * Raises every enchantment whose next levels the Bazaar trades ({@code traded} says whether a
     * product has orders: Hypixel keeps dead products like Feather Falling XX listed with none).
     * Never lowers below the table. Returns whether any top changed.
     */
    static boolean raise(Predicate<String> traded) {
        Map<String, Integer> next = new HashMap<>();
        raiseAll(NORMAL, traded, next);
        raiseAll(ULTIMATE, traded, next);
        if (next.equals(raised)) return false;
        raised = Map.copyOf(next);
        return true;
    }

    private static void raiseAll(Map<String, Integer> table, Predicate<String> traded, Map<String, Integer> out) {
        for (Map.Entry<String, Integer> e : table.entrySet()) {
            int level = e.getValue();
            // A few steps at most: a new level comes one at a time.
            while (level < e.getValue() + 5 && traded.test(bazaarId(e.getKey(), level + 1))) level++;
            if (level > e.getValue()) out.put(e.getKey(), level);
        }
    }

    /** Hypixel already marks these with bold pink, so they are only recoloured when asked to. */
    static final Map<String, Integer> ULTIMATE = Map.ofEntries(
        Map.entry("Bank", 5), Map.entry("Bobbin' Time", 5), Map.entry("Chimera", 5),
        Map.entry("Combo", 5), Map.entry("Duplex", 5), Map.entry("First Impression", 5),
        Map.entry("Flash", 5), Map.entry("Flowstate", 3), Map.entry("Last Stand", 5),
        Map.entry("Fatal Tempo", 5), Map.entry("Habanero Tactics", 5), Map.entry("Inferno", 5),
        Map.entry("Ultimate Jerry", 5), Map.entry("Legion", 5), Map.entry("Missile", 5),
        Map.entry("No Pain No Gain", 5), Map.entry("One For All", 1), Map.entry("Refrigerate", 5),
        Map.entry("Rend", 5), Map.entry("Soul Eater", 5), Map.entry("Sunset", 5),
        Map.entry("The One", 5), Map.entry("Swarm", 5), Map.entry("Ultimate Wise", 5),
        Map.entry("Wisdom", 5), Map.entry("Crop Fever", 5));
}
