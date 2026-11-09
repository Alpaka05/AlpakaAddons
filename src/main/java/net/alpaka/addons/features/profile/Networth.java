package net.alpaka.addons.features.profile;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.alpaka.addons.features.itemvalue.ItemPrices;
import net.alpaka.addons.features.itemvalue.ItemValue;

import java.util.Map;

/**
 * What a profile is worth: its coins, and everything it holds at today's prices - every item in
 * every inventory with what was put on it, the pets, the sacks, the essence and what is in the
 * museum. Items are priced by {@link ItemValue}, the same way the Item Value breakdown and the
 * Chest Value do it, so an item is worth the same wherever the mod shows it.
 *
 * Hypixel hands out no networth; every profile viewer works one out its own way, and they differ
 * (this one prices a pet as the cheapest of its kind and rarity, whatever its level). Free of the
 * game, so the sums can be checked in a test.
 */
final class Networth {
    private Networth() {}

    /** The inventories that aren't counted: icons, and the Rift's, whose items can't leave it. */
    private static boolean counted(String inventory) {
        return !inventory.equals("icons_backpack") && !inventory.startsWith(ProfileSession.RIFT);
    }

    /**
     * The breakdown by where the worth sits: {@code categories.<name>} with a {@code total} and the
     * {@code items} that make it up, and {@code normal} and {@code liquid} over all of them.
     * "Liquid" leaves out what can't be sold as it is: soulbound items and what the museum holds.
     *
     * @param model the profile as {@link HypixelProfile#model} made it
     * @param essence the member's essence as Hypixel has it: {@code {"WITHER": {"current": 12}}}
     * @param inventories every inventory read so far, the museum among them once it is there
     */
    static JsonObject of(JsonObject model, JsonElement essence, Map<String, JsonElement> inventories,
                         ItemValue.Prices prices, ItemValue.Catalog catalog) {
        JsonObject categories = new JsonObject();
        double total = 0;
        double liquid = 0;

        double coins = Json.num(model, "purse", 0) + Json.num(model, "bankBalance", 0) + Json.num(model, "personalBank", 0);
        JsonObject currency = new JsonObject();
        currency.addProperty("total", coins);
        currency.add("items", new JsonArray());
        categories.add("currency", currency);
        total += coins;
        liquid += coins;

        for (var inventory : inventories.entrySet()) {
            if (!counted(inventory.getKey())) continue;
            boolean museum = inventory.getKey().equals("museum");
            JsonArray items = new JsonArray();
            double sum = 0;
            for (var slot : Json.obj(inventory.getValue(), "items").entrySet()) {
                JsonElement item = slot.getValue();
                JsonElement data = Json.at(item, "data");
                if (data == null || !data.isJsonObject() || Json.str(item, "skyblockId") == null) continue;
                // One taken out of the museum is with the player, and counted where it is.
                if (museum && Museum.borrowed(item)) continue;
                int count = Math.max(1, (int) Json.num(item, "count", 1));
                double value = ItemValue.of(data.getAsJsonObject(), prices, catalog).total() * count;
                if (!(value > 0)) continue;
                items.add(valued(Json.str(item, "name"), Json.str(item, "skyblockId"), count, Json.str(item, "uuid"), value));
                sum += value;
                if (!museum && !soulbound(item)) liquid += value;
            }
            if (items.isEmpty()) continue;
            categories.add(inventory.getKey(), category(sum, items));
            total += sum;
        }

        JsonArray pets = new JsonArray();
        double petSum = 0;
        for (JsonElement pet : Json.arr(model, "pets")) {
            // Priced as the pet's item would be: Hypixel keeps the same data on it.
            JsonObject info = new JsonObject();
            copy(pet, info, "type");
            copy(pet, info, "tier");
            copy(pet, info, "heldItem");
            copy(pet, info, "skin");
            JsonObject data = new JsonObject();
            data.addProperty("id", "PET");
            data.add("petInfo", info);
            double value = ItemValue.of(data, prices, catalog).total();
            if (!(value > 0)) continue;
            String type = Json.str(pet, "type");
            pets.add(valued("[Lvl " + (int) Json.num(pet, "level", 1) + "] " + ProfileData.title(type), type, 1, Json.str(pet, "uuid"), value));
            petSum += value;
        }
        if (!pets.isEmpty()) {
            categories.add("pets", category(petSum, pets));
            total += petSum;
            liquid += petSum;
        }

        JsonArray sacks = new JsonArray();
        double sackSum = 0;
        for (var e : Json.obj(model, "sacks").entrySet()) {
            double value = worth(prices, e.getKey(), Json.num(e.getValue()));
            if (!(value > 0)) continue;
            sacks.add(valued(e.getKey(), e.getKey(), (int) Math.min(Integer.MAX_VALUE, Json.num(e.getValue())), null, value));
            sackSum += value;
        }
        if (!sacks.isEmpty()) {
            categories.add("sacks", category(sackSum, sacks));
            total += sackSum;
            liquid += sackSum;
        }

        JsonArray essences = new JsonArray();
        double essenceSum = 0;
        if (essence != null && essence.isJsonObject()) {
            for (var e : essence.getAsJsonObject().entrySet()) {
                double amount = Json.num(e.getValue(), "current", 0);
                double value = worth(prices, "ESSENCE_" + e.getKey(), amount);
                if (!(value > 0)) continue;
                essences.add(valued("ESSENCE_" + e.getKey(), "ESSENCE_" + e.getKey(), (int) Math.min(Integer.MAX_VALUE, amount), null, value));
                essenceSum += value;
            }
        }
        if (!essences.isEmpty()) {
            categories.add("essence", category(essenceSum, essences));
            total += essenceSum;
            liquid += essenceSum;
        }

        JsonObject out = new JsonObject();
        out.addProperty("normal", total);
        out.addProperty("liquid", liquid);
        out.add("categories", categories);
        return out;
    }

    private static double worth(ItemValue.Prices prices, String id, double amount) {
        if (Double.isNaN(amount) || amount <= 0) return 0;
        ItemPrices.Quote quote = prices.quote(id);
        return quote == null ? 0 : quote.price() * amount;
    }

    private static void copy(JsonElement from, JsonObject to, String key) {
        String value = Json.str(from, key);
        if (value != null) to.addProperty(key, value);
    }

    /** Whether an item is bound to the player or the co-op, which its lore says in a line of its own. */
    static boolean soulbound(JsonElement item) {
        for (JsonElement line : Json.arr(item, "lore")) {
            if (line.isJsonPrimitive() && line.getAsString().contains("Soulbound")) return true;
        }
        return false;
    }

    private static JsonObject category(double total, JsonArray items) {
        JsonObject out = new JsonObject();
        out.addProperty("total", total);
        out.add("items", items);
        return out;
    }

    private static JsonObject valued(String name, String id, int count, String uuid, double value) {
        JsonObject item = new JsonObject();
        item.addProperty("name", name);
        item.addProperty("skyblockId", id);
        item.addProperty("count", count);
        if (uuid != null) item.addProperty("uuid", uuid);
        JsonObject out = new JsonObject();
        out.addProperty("networth", value);
        out.add("item", item);
        return out;
    }
}
