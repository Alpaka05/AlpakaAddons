package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MuseumTest {
    // As NEU has it, cut down: two categories, a set, an upgrade over a lower tier and a starred item.
    private static final String REPO = """
            {"items": {"combat": ["ASPECT_OF_THE_END", "ASPECT_OF_THE_VOID", "TANK_MINER", "MIDAS_SWORD"],
                       "farming": ["ROOKIE_HOE"], "special": ["CAKE_SOUL"]},
             "sets_to_items": {"TANK_MINER": ["TANK_MINER_BOOTS", "TANK_MINER_HELMET"]},
             "armor_to_id": {"TANK_MINER": "TANK_MINER_HELMET"},
             "children": {"ASPECT_OF_THE_VOID": "ASPECT_OF_THE_END"},
             "mapped_ids": {"STARRED_MIDAS_SWORD": "MIDAS_SWORD"}}
            """;

    private static List<JsonElement> donated(String... ids) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) json.append(',');
            json.append("{\"item\": {\"skyblockId\": \"").append(ids[i]).append("\"}, \"networth\": 10}");
        }
        List<JsonElement> out = new java.util.ArrayList<>();
        JsonParser.parseString(json.append(']').toString()).getAsJsonArray().forEach(out::add);
        return out;
    }

    @Test
    void categoriesWithoutTheSpecialOne() {
        Museum.Repo repo = Museum.parse(REPO);
        assertEquals(List.of("combat", "farming"), repo.categories().stream().map(Museum.CategoryDef::id).toList());
        assertEquals(5, repo.total());
        assertEquals("TANK_MINER_HELMET", Museum.iconId(repo, "TANK_MINER"));
    }

    @Test
    void setsUpgradesAndStarredItemsCount() {
        Museum.Repo repo = Museum.parse(REPO);
        Museum.Donated m = Museum.of(repo, donated("ASPECT_OF_THE_VOID", "TANK_MINER_BOOTS", "TANK_MINER_HELMET",
                "STARRED_MIDAS_SWORD", "RIFT_COMPLETION_MEMENTO"));
        Museum.Category combat = m.categories().get(0);
        Map<String, Museum.Entry> byId = new java.util.HashMap<>();
        for (Museum.Entry e : combat.entries()) byId.put(e.id(), e);
        assertEquals(4, combat.donated());
        // Donated through the Aspect of the Void, with no item of its own.
        assertTrue(byId.get("ASPECT_OF_THE_END").donated());
        assertTrue(byId.get("ASPECT_OF_THE_END").items().isEmpty());
        assertEquals(2, byId.get("TANK_MINER").items().size());
        assertEquals(20, byId.get("TANK_MINER").value());
        assertTrue(byId.get("MIDAS_SWORD").donated());
        assertFalse(m.categories().get(1).entries().get(0).donated());
        assertEquals(1, m.special().size());
        assertEquals(4, m.donated());
    }

    @Test
    void starredArmorNeuDoesNotMapIsThePlainPiece() {
        Museum.Repo repo = Museum.parse(REPO);
        Museum.Donated m = Museum.of(repo, donated("STARRED_TANK_MINER_BOOTS"));
        assertEquals(1, m.donated());
        assertTrue(m.special().isEmpty());
    }

    @Test
    void hoppitysRabbitsByRarity() {
        Map<String, Integer> rabbits = Hoppity.parse("""
                {"hoppity": {"rarities": {"common": {"rabbits": ["aaron", "able"]}, "divine": {"rabbits": ["zest"]}}}}
                """);
        assertEquals(Map.of("common", 2, "divine", 1), rabbits);
        assertEquals(List.of("common", "divine"), List.copyOf(rabbits.keySet()));
    }
}
