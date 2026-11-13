package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alpaka.addons.features.itemvalue.ItemPrices;
import net.alpaka.addons.features.itemvalue.ItemValue;
import net.alpaka.addons.features.market.ItemIcons;
import net.alpaka.addons.features.market.MarketKind;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworthTest {
    private static final Map<String, Double> PRICE_LIST = Map.of("HYPERION", 1000.0, "RECOMBOBULATOR_3000", 10.0, "SOUL", 7.0,
            "WHEAT", 2.0, "ESSENCE_WITHER", 3.0, "TIGER;4", 500.0, "PET_ITEM_X", 20.0, "MUSEUM_PIECE", 40.0);
    private static final ItemValue.Prices PRICES = id -> PRICE_LIST.containsKey(id)
            ? new ItemPrices.Quote(PRICE_LIST.get(id), MarketKind.BAZAAR) : null;
    private static final ItemValue.Catalog CATALOG = new ItemValue.Catalog() {
        @Override
        public String name(String id) {
            return null;
        }

        @Override
        public ItemIcons.Upgrades upgrades(String id) {
            return null;
        }
    };

    private static final String HYPERION = """
            {"skyblockId": "HYPERION", "name": "§dHyperion", "count": 1, "uuid": "u1", "lore": ["§7Damage"],
             "attributes": {}, "data": {"id": "HYPERION", "rarity_upgrades": 1}}""";

    private static JsonElement items(String... slots) {
        return JsonParser.parseString("{\"items\": {" + String.join(",", slots) + "}}");
    }

    @Test
    void everythingAProfileHoldsAddsUp() {
        JsonObject model = JsonParser.parseString("""
                {"purse": 100, "bankBalance": 50, "personalBank": 25, "sacks": {"WHEAT": 10, "UNPRICED": 3},
                 "pets": [{"uuid": "p1", "type": "TIGER", "tier": "LEGENDARY", "level": 100, "heldItem": "PET_ITEM_X"},
                          {"uuid": "p2", "type": "UNKNOWN", "tier": "COMMON", "level": 1}]}""").getAsJsonObject();
        JsonElement essence = JsonParser.parseString("{\"WITHER\": {\"current\": 4}, \"DRAGON\": {\"current\": 0}}");
        Map<String, JsonElement> inventories = new LinkedHashMap<>();
        inventories.put("inventory", items("\"0\": " + HYPERION, "\"1\": null", """
                "2": {"skyblockId": "SOUL", "name": "§fSoul", "count": 2, "lore": ["§8§l* §8Co-op Soulbound §8§l*"],
                      "attributes": {}, "data": {"id": "SOUL"}}"""));
        // Neither the backpacks' icons nor what is in the Rift counts.
        inventories.put("icons_backpack", items("\"0\": " + HYPERION));
        inventories.put("rift_inventory", items("\"0\": " + HYPERION));
        inventories.put("museum", items("""
                "0": {"skyblockId": "HYPERION", "name": "§dHyperion", "count": 1, "uuid": "u1", "lore": [],
                      "attributes": {"museum_borrowing": "1"}, "data": {"id": "HYPERION"}}""", """
                "1": {"skyblockId": "MUSEUM_PIECE", "name": "§fPiece", "count": 1, "uuid": "u2", "lore": [],
                      "attributes": {}, "data": {"id": "MUSEUM_PIECE"}}"""));

        JsonObject worth = Networth.of(model, essence, inventories, PRICES, CATALOG);

        assertEquals(175, Json.num(worth, "categories.currency.total", 0));
        // The Hyperion with its Recombobulator, and two of the soulbound item.
        assertEquals(1024, Json.num(worth, "categories.inventory.total", 0));
        assertEquals(2, Json.arr(worth, "categories.inventory.items").size());
        JsonElement hyperion = Json.arr(worth, "categories.inventory.items").get(0);
        assertEquals(1010, Json.num(hyperion, "networth", 0));
        assertEquals("§dHyperion", Json.str(hyperion, "item.name"));
        assertEquals("u1", Json.str(hyperion, "item.uuid"));
        // The Hyperion taken out of the museum is counted in the inventory it is in, not twice.
        assertEquals(40, Json.num(worth, "categories.museum.total", 0));
        assertEquals("u2", Json.str(Json.arr(worth, "categories.museum.items").get(0), "item.uuid"));
        // The cheapest Legendary Tiger and what it holds; a pet nobody sells is worth nothing.
        assertEquals(520, Json.num(worth, "categories.pets.total", 0));
        assertEquals("[Lvl 100] Tiger", Json.str(Json.arr(worth, "categories.pets.items").get(0), "item.name"));
        assertEquals(20, Json.num(worth, "categories.sacks.total", 0));
        assertEquals(12, Json.num(worth, "categories.essence.total", 0));
        assertFalse(Json.obj(worth, "categories").has("icons_backpack"));
        assertFalse(Json.obj(worth, "categories").has("rift_inventory"));

        assertEquals(175 + 1024 + 40 + 520 + 20 + 12, Json.num(worth, "normal", 0));
        // Without what is soulbound and what the museum holds.
        assertEquals(175 + 1010 + 520 + 20 + 12, Json.num(worth, "liquid", 0));
    }

    @Test
    void aProfileWithItsInventoryApiOffIsWorthItsCoins() {
        JsonObject model = JsonParser.parseString("{\"purse\": 5, \"bankBalance\": 7}").getAsJsonObject();
        JsonObject worth = Networth.of(model, null, Map.of(), PRICES, CATALOG);
        assertEquals(12, Json.num(worth, "normal", 0));
        assertEquals(12, Json.num(worth, "liquid", 0));
        assertEquals(1, Json.obj(worth, "categories").size());
    }

    @Test
    void soulboundIsSaidInTheLore() {
        assertTrue(Networth.soulbound(JsonParser.parseString("{\"lore\": [\"§7Text\", \"§8§l* §8Soulbound §8§l*\"]}")));
        assertFalse(Networth.soulbound(JsonParser.parseString("{\"lore\": [\"§7Text\"]}")));
    }
}
