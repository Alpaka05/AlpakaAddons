package net.alpaka.addons.features.market;

import java.util.Locale;
import java.util.Map;

/**
 * Today's item id for one of Hypixel's item materials, which are still written the 1.8 way: a
 * material name and a damage value ({@code INK_SACK} 4 is lapis lazuli, {@code GOLD_SWORD} is a
 * golden sword). Used to show SkyBlock items in the search suggestions.
 */
public final class LegacyItems {
    private LegacyItems() {}

    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
    private static final String[] DYES = {"ink_sac", "red_dye", "green_dye", "cocoa_beans", "lapis_lazuli", "purple_dye",
            "cyan_dye", "light_gray_dye", "gray_dye", "pink_dye", "lime_dye", "yellow_dye", "light_blue_dye", "magenta_dye",
            "orange_dye", "bone_meal"};
    private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};

    private static final Map<String, String> RENAMED = Map.ofEntries(
            Map.entry("gold_sword", "golden_sword"), Map.entry("gold_axe", "golden_axe"),
            Map.entry("gold_pickaxe", "golden_pickaxe"), Map.entry("gold_spade", "golden_shovel"),
            Map.entry("gold_hoe", "golden_hoe"), Map.entry("gold_helmet", "golden_helmet"),
            Map.entry("gold_chestplate", "golden_chestplate"), Map.entry("gold_leggings", "golden_leggings"),
            Map.entry("gold_boots", "golden_boots"), Map.entry("wood_sword", "wooden_sword"),
            Map.entry("wood_axe", "wooden_axe"), Map.entry("wood_pickaxe", "wooden_pickaxe"),
            Map.entry("wood_spade", "wooden_shovel"), Map.entry("wood_hoe", "wooden_hoe"),
            Map.entry("iron_spade", "iron_shovel"), Map.entry("diamond_spade", "diamond_shovel"),
            Map.entry("stone_spade", "stone_shovel"), Map.entry("empty_map", "map"),
            Map.entry("carrot_stick", "carrot_on_a_stick"), Map.entry("firework", "firework_rocket"),
            Map.entry("firework_charge", "firework_star"), Map.entry("carrot_item", "carrot"),
            Map.entry("potato_item", "potato"), Map.entry("exp_bottle", "experience_bottle"),
            Map.entry("ender_stone", "end_stone"), Map.entry("water_lily", "lily_pad"),
            Map.entry("nether_stalk", "nether_wart"), Map.entry("iron_plate", "heavy_weighted_pressure_plate"),
            Map.entry("gold_plate", "light_weighted_pressure_plate"), Map.entry("stone_plate", "stone_pressure_plate"),
            Map.entry("wood_plate", "oak_pressure_plate"), Map.entry("gold_record", "music_disc_13"),
            Map.entry("green_record", "music_disc_cat"), Map.entry("record_3", "music_disc_blocks"),
            Map.entry("record_4", "music_disc_chirp"), Map.entry("record_5", "music_disc_far"),
            Map.entry("record_6", "music_disc_mall"), Map.entry("record_7", "music_disc_mellohi"),
            Map.entry("record_8", "music_disc_stal"), Map.entry("record_9", "music_disc_strad"),
            Map.entry("record_10", "music_disc_ward"), Map.entry("record_11", "music_disc_11"),
            Map.entry("record_12", "music_disc_wait"), Map.entry("leash", "lead"),
            Map.entry("yellow_flower", "dandelion"), Map.entry("melon_block", "melon"),
            Map.entry("redstone_comparator", "comparator"), Map.entry("gold_barding", "golden_horse_armor"),
            Map.entry("iron_barding", "iron_horse_armor"), Map.entry("diamond_barding", "diamond_horse_armor"),
            Map.entry("redstone_lamp_off", "redstone_lamp"), Map.entry("mushroom_soup", "mushroom_stew"),
            Map.entry("sulphur", "gunpowder"), Map.entry("speckled_melon", "glistering_melon_slice"),
            Map.entry("boat", "oak_boat"), Map.entry("watch", "clock"), Map.entry("grilled_pork", "cooked_porkchop"),
            Map.entry("pork", "porkchop"), Map.entry("trap_door", "oak_trapdoor"), Map.entry("flower_pot_item", "flower_pot"),
            Map.entry("storage_minecart", "chest_minecart"), Map.entry("powered_minecart", "furnace_minecart"),
            Map.entry("explosive_minecart", "tnt_minecart"), Map.entry("huge_mushroom_1", "brown_mushroom_block"),
            Map.entry("huge_mushroom_2", "red_mushroom_block"), Map.entry("mycel", "mycelium"),
            Map.entry("raw_chicken", "chicken"), Map.entry("raw_beef", "beef"), Map.entry("redstone_torch_on", "redstone_torch"),
            Map.entry("fence", "oak_fence"), Map.entry("fence_gate", "oak_fence_gate"), Map.entry("eye_of_ender", "ender_eye"),
            Map.entry("snow_ball", "snowball"), Map.entry("web", "cobweb"), Map.entry("diode", "repeater"),
            Map.entry("nether_brick_item", "nether_brick"), Map.entry("mob_spawner", "spawner"), Map.entry("clay_brick", "brick"),
            Map.entry("quartz_ore", "nether_quartz_ore"), Map.entry("spruce_wood_stairs", "spruce_stairs"),
            Map.entry("birch_wood_stairs", "birch_stairs"), Map.entry("jungle_wood_stairs", "jungle_stairs"),
            Map.entry("wood_stairs", "oak_stairs"), Map.entry("piston_sticky_base", "sticky_piston"),
            Map.entry("piston_base", "piston"), Map.entry("cobble_wall", "cobblestone_wall"),
            Map.entry("cauldron_item", "cauldron"), Map.entry("sign", "oak_sign"), Map.entry("spruce_door_item", "spruce_door"),
            Map.entry("jungle_door_item", "jungle_door"), Map.entry("dark_oak_door_item", "dark_oak_door"),
            Map.entry("birch_door_item", "birch_door"), Map.entry("acacia_door_item", "acacia_door"),
            Map.entry("wood_door", "oak_door"), Map.entry("bed", "red_bed"), Map.entry("grass", "grass_block"),
            Map.entry("iron_fence", "iron_bars"), Map.entry("smooth_stairs", "stone_brick_stairs"),
            Map.entry("hard_clay", "terracotta"), Map.entry("thin_glass", "glass_pane"), Map.entry("wood_button", "oak_button"),
            Map.entry("command", "command_block"), Map.entry("rails", "rail"), Map.entry("nether_fence", "nether_brick_fence"),
            Map.entry("book_and_quill", "writable_book"), Map.entry("brewing_stand_item", "brewing_stand"),
            Map.entry("enchantment_table", "enchanting_table"), Map.entry("workbench", "crafting_table"),
            Map.entry("ender_portal_frame", "end_portal_frame"), Map.entry("monster_egg", "pig_spawn_egg"),
            Map.entry("skull_item", "player_head"), Map.entry("long_grass", "short_grass"));

    /** The item id, without its namespace, for a material and damage value. */
    public static String modernId(String material, int damage) {
        String m = material == null ? "" : material.toLowerCase(Locale.ROOT);
        int d = Math.max(0, damage);
        String byDamage = switch (m) {
            case "raw_fish" -> pick(d, "cod", "salmon", "tropical_fish", "pufferfish");
            case "cooked_fish" -> pick(d, "cooked_cod", "cooked_salmon");
            case "ink_sack" -> pick(d, DYES);
            case "wool", "carpet", "stained_glass", "stained_glass_pane" -> d < 16 ? COLORS[d] + "_" + m : null;
            case "stained_clay" -> d < 16 ? COLORS[d] + "_terracotta" : null;
            case "banner" -> d < 16 ? COLORS[15 - d] + "_banner" : null;
            case "skull_item" -> pick(d, "skeleton_skull", "wither_skeleton_skull", "zombie_head", "player_head",
                    "creeper_head", "dragon_head");
            case "log" -> d < 4 ? WOODS[d] + "_log" : null;
            case "log_2" -> d < 2 ? WOODS[d + 4] + "_log" : null;
            case "leaves" -> d < 4 ? WOODS[d] + "_leaves" : null;
            case "leaves_2" -> d < 2 ? WOODS[d + 4] + "_leaves" : null;
            case "wood" -> d < 6 ? WOODS[d] + "_planks" : null;
            case "sapling" -> d < 6 ? WOODS[d] + "_sapling" : null;
            case "wood_step" -> d < 6 ? WOODS[d] + "_slab" : null;
            case "step" -> pick(d, "smooth_stone_slab", "sandstone_slab", "oak_slab", "cobblestone_slab", "brick_slab",
                    "stone_brick_slab", "nether_brick_slab", "quartz_slab");
            case "red_rose" -> pick(d, "poppy", "blue_orchid", "allium", "azure_bluet", "red_tulip", "orange_tulip",
                    "white_tulip", "pink_tulip", "oxeye_daisy");
            case "double_plant" -> pick(d, "sunflower", "lilac", "tall_grass", "large_fern", "rose_bush", "peony");
            case "long_grass" -> d == 2 ? "fern" : "short_grass";
            case "smooth_brick" -> pick(d, "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks", "chiseled_stone_bricks");
            case "coal" -> d == 1 ? "charcoal" : "coal";
            case "sponge" -> d == 1 ? "wet_sponge" : "sponge";
            case "sand" -> d == 1 ? "red_sand" : "sand";
            case "golden_apple" -> d == 1 ? "enchanted_golden_apple" : "golden_apple";
            case "stone" -> pick(d, "stone", "granite", "polished_granite", "diorite", "polished_diorite", "andesite",
                    "polished_andesite");
            case "dirt" -> pick(d, "dirt", "coarse_dirt", "podzol");
            case "sandstone" -> pick(d, "sandstone", "chiseled_sandstone", "cut_sandstone");
            case "red_sandstone" -> pick(d, "red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone");
            case "quartz_block" -> pick(d, "quartz_block", "chiseled_quartz_block", "quartz_pillar");
            case "prismarine" -> pick(d, "prismarine", "prismarine_bricks", "dark_prismarine");
            default -> null;
        };
        if (byDamage != null) return byDamage;
        return RENAMED.getOrDefault(m, m);
    }

    private static String pick(int index, String... ids) {
        return index < ids.length ? ids[index] : ids[0];
    }
}
