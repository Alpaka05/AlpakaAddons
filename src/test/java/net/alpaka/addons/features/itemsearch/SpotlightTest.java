package net.alpaka.addons.features.itemsearch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpotlightTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "1+2|3",
            "2 * (3 + 4)|14",
            "2^10|1024",
            "2.5m * 3|7500000",
            "1.5k + 500|2000",
            "10x4|40",
            "100 / 8|12.5",
            "20% * 1m|200000",
            "-3 + 5|2",
            "sqrt(144)|12",
            "max(3; 9; 4)|9",
            "1,000,000 / 4|250000",
            "round(10/3; 2)|3.33",
            "2b - 1b|1000000000",
    })
    void works_out_sums(String input, double expected) {
        Double value = SpotlightMath.evaluate(input, null);
        assertNotNull(value, input);
        assertEquals(expected, value, 1e-9);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @ValueSource(strings = {"", "hyperion", "42", "enchanted diamond", "1+", "(2", "5 / 0", "foo(2)", "2 2", "mithril 3"})
    void leaves_what_is_no_sum(String input) {
        assertNull(SpotlightMath.evaluate(input, null));
    }

    @Test
    void prices_items_by_name() {
        SpotlightMath.Prices prices = name -> name.equals("enchanted diamond") ? 1500.0 : null;
        assertEquals(48_000, SpotlightMath.evaluate("bz(enchanted diamond) * 32", prices), 1e-9);
        assertNull(SpotlightMath.evaluate("ah(unknown thing) * 2", prices));
    }

    @Test
    void formats_results() {
        assertEquals("7,500,000", SpotlightMath.format(7_500_000));
        assertEquals("3.333333", SpotlightMath.format(10.0 / 3));
        assertEquals("-1,234.5", SpotlightMath.format(-1234.5));
        assertEquals("215,041.94", SpotlightMath.format(215_041.938094));
        assertEquals("7500000", SpotlightMath.plain(7_500_000));
    }

    @Test
    void reads_crafting_and_forge_recipes() {
        ItemRecipes.Info hyperion = ItemRecipes.parse("HYPERION", """
                {"itemid":"minecraft:iron_sword","displayname":"§6Hyperion","internalname":"HYPERION",
                 "infoType":"WIKI_URL","info":["https://hypixelskyblock.minecraft.wiki/w/Hyperion"],
                 "recipe":{"A1":"GIANT_FRAGMENT_LASER:1","A2":"GIANT_FRAGMENT_LASER:1","A3":"",
                           "B1":"","B2":"NECRON_BLADE:1","B3":"","C1":"","C2":"","C3":"WITHER_CATALYST:8"}}
                """);
        assertEquals(List.of("https://hypixelskyblock.minecraft.wiki/w/Hyperion"), hyperion.wiki());
        assertEquals(1, hyperion.recipes().size());
        ItemRecipes.Recipe crafting = hyperion.recipes().get(0);
        assertEquals(ItemRecipes.Kind.CRAFTING, crafting.kind());
        assertEquals(new ItemRecipes.Stack("NECRON_BLADE", 1), crafting.grid().get(4));
        assertNull(crafting.grid().get(2));
        assertEquals(new ItemRecipes.Stack("WITHER_CATALYST", 8), crafting.grid().get(8));
        assertEquals("HYPERION", crafting.output().id());

        ItemRecipes.Info refined = ItemRecipes.parse("REFINED_MITHRIL", """
                {"internalname":"REFINED_MITHRIL","crafttext":"Requires: HotM 2",
                 "recipes":[{"type":"forge","inputs":["ENCHANTED_MITHRIL:160"],"count":1,
                             "overrideOutputId":"REFINED_MITHRIL","duration":21600},
                            {"type":"mobdrop","drops":[]}]}
                """);
        assertEquals(1, refined.recipes().size());
        ItemRecipes.Recipe forge = refined.recipes().get(0);
        assertEquals(ItemRecipes.Kind.FORGE, forge.kind());
        assertEquals(List.of(new ItemRecipes.Stack("ENCHANTED_MITHRIL", 160)), forge.inputs());
        assertEquals(21600, forge.seconds());
        assertEquals("6h", ItemRecipes.duration(forge.seconds()));
        assertEquals("Requires: HotM 2", refined.craftText());
    }

    @Test
    void ranks_catalog_matches() {
        List<ItemCatalog.Item> all = List.of(
                new ItemCatalog.Item("ENCHANTED_DIAMOND_BLOCK", "Enchanted Diamond Block", "RARE"),
                new ItemCatalog.Item("DIAMOND", "Diamond", "COMMON"),
                new ItemCatalog.Item("ENCHANTED_DIAMOND", "Enchanted Diamond", "UNCOMMON"),
                new ItemCatalog.Item("DIAMOND_SWORD", "Diamond Sword", "UNCOMMON"),
                new ItemCatalog.Item("HYPERION", "Hyperion", "LEGENDARY"));
        List<String> ids = ItemCatalog.search(all, "diamond", 10).stream().map(ItemCatalog.Item::id).toList();
        assertEquals(List.of("DIAMOND", "DIAMOND_SWORD", "ENCHANTED_DIAMOND", "ENCHANTED_DIAMOND_BLOCK"), ids);
        assertEquals(List.of("ENCHANTED_DIAMOND", "ENCHANTED_DIAMOND_BLOCK"),
                ItemCatalog.search(all, "ench diam", 10).stream().map(ItemCatalog.Item::id).toList());
    }
}
