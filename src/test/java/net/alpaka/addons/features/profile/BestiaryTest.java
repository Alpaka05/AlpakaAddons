package net.alpaka.addons.features.profile;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BestiaryTest {
    // As NEU has it, cut down: two bracket tables, an island, and Fishing in sub-categories.
    private static final String REPO = """
            {"brackets": {"1": [20, 40, 60, 100, 200], "6": [1, 2, 3, 5, 7]},
             "bracketSets": {"CRITTERS": {"2": [1, 5, 10]}},
             "combat_3": {"name": "The End", "mobs": [
                 {"name": "§aDragon", "item": "dragon_egg", "cap": 5, "mobs": ["old_dragon_100", "young_dragon_100"], "bracket": 6},
                 {"name": "§aZealot", "texture": "abc", "cap": 100, "mobs": ["zealot_enderman_55"], "bracket": 1}]},
             "fishing": {"name": "Fishing", "hasSubcategories": true,
                 "lava": {"name": "Lava", "mobs": [{"name": "§aMagma Slug", "cap": 3, "mobs": ["magma_slug_200"], "bracket": 6}]}},
             "safari": {"name": "Critter Safari", "hasSubcategories": true,
                 "forest_biome": {"name": "Forest Biome", "mobs": [
                     {"name": "§aBluebird", "cap": 5, "mobs": ["bluebird_1"], "bracketType": "CRITTERS", "bracket": 2}]}}}
            """;

    @Test
    void islandsFamiliesAndTiersUpToTheCap() {
        List<Bestiary.IslandDef> repo = Bestiary.parse(REPO);
        assertEquals(List.of("The End", "Fishing · Lava", "Critter Safari · Forest Biome"),
                repo.stream().map(Bestiary.IslandDef::name).toList());
        Bestiary.FamilyDef dragon = repo.get(0).families().get(0);
        assertEquals("Dragon", dragon.name());
        assertArrayEquals(new long[] {1, 2, 3, 5}, dragon.tiers());
        assertArrayEquals(new long[] {1, 5}, repo.get(2).families().get(0).tiers());
    }

    @Test
    void aFamilysTierFromAllItsMobs() {
        List<Bestiary.Island> islands = Bestiary.of(Bestiary.parse(REPO), JsonParser.parseString(
                "{\"old_dragon_100\": 2, \"young_dragon_100\": 1, \"zealot_enderman_55\": 70}"));
        Bestiary.Family dragon = islands.get(0).families().get(0);
        assertEquals(3, dragon.kills());
        assertEquals(3, dragon.tier());
        assertEquals(5, dragon.nextAt());
        Bestiary.Family zealot = islands.get(0).families().get(1);
        assertEquals(3, zealot.tier());
        assertEquals(60, zealot.prevAt());
        Bestiary.Family slug = islands.get(1).families().get(0);
        assertEquals(0, slug.tier());
        assertTrue(Bestiary.of(Bestiary.parse(REPO), JsonParser.parseString("{\"magma_slug_200\": 9}")).get(1).families().get(0).maxed());
    }
}
