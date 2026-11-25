package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;

import java.util.Locale;
import java.util.Map;

/**
 * Whether a player is online on Hypixel and where, as Hypixel's status tells it: the game, and in
 * SkyBlock the island. Which server of it Hypixel doesn't say. A player can turn the status off in
 * Hypixel's API settings and then always shows as offline.
 */
record PlayerStatus(boolean online, String gameType, String mode, String map) {

    /** The API's answer; null for one without the online flag. */
    static PlayerStatus read(JsonElement answer) {
        if (answer == null || !answer.isJsonObject() || !answer.getAsJsonObject().has("online")) return null;
        return new PlayerStatus(Json.bool(answer, "online"), Json.str(answer, "gameType"), Json.str(answer, "mode"), Json.str(answer, "map"));
    }

    /** SkyBlock's islands by the mode Hypixel names them with. */
    private static final Map<String, String> ISLANDS = Map.ofEntries(
            Map.entry("dynamic", "Private Island"),
            Map.entry("hub", "Hub"),
            Map.entry("garden", "Garden"),
            Map.entry("farming_1", "The Farming Islands"),
            Map.entry("foraging_1", "The Park"),
            Map.entry("foraging_2", "Galatea"),
            Map.entry("combat_1", "Spider's Den"),
            Map.entry("combat_3", "The End"),
            Map.entry("crimson_isle", "Crimson Isle"),
            Map.entry("mining_1", "Gold Mine"),
            Map.entry("mining_2", "Deep Caverns"),
            Map.entry("mining_3", "Dwarven Mines"),
            Map.entry("crystal_hollows", "Crystal Hollows"),
            Map.entry("mineshaft", "Glacite Mineshaft"),
            Map.entry("dungeon_hub", "Dungeon Hub"),
            Map.entry("dungeon", "The Catacombs"),
            Map.entry("kuudra", "Kuudra"),
            Map.entry("instanced", "Kuudra"),
            Map.entry("winter", "Jerry's Workshop"),
            Map.entry("rift", "The Rift"),
            Map.entry("dark_auction", "Dark Auction"),
            Map.entry("fishing_1", "Backwater Bayou"));

    /** Hypixel's games by their type, where the type itself doesn't read well. */
    private static final Map<String, String> GAMES = Map.ofEntries(
            Map.entry("SKYBLOCK", "SkyBlock"),
            Map.entry("MAIN", "Main"),
            Map.entry("BEDWARS", "Bed Wars"),
            Map.entry("SKYWARS", "SkyWars"),
            Map.entry("MURDER_MYSTERY", "Murder Mystery"),
            Map.entry("BUILD_BATTLE", "Build Battle"),
            Map.entry("TNTGAMES", "TNT Games"),
            Map.entry("SURVIVAL_GAMES", "Blitz SG"),
            Map.entry("PIT", "The Pit"),
            Map.entry("LEGACY", "Classic Games"),
            Map.entry("MCGO", "Cops and Crims"),
            Map.entry("WOOL_GAMES", "Wool Games"),
            Map.entry("SUPER_SMASH", "Smash Heroes"),
            Map.entry("WALLS3", "Mega Walls"));

    /** Where the player is, in a few words: "Dwarven Mines", "Bed Wars Lobby"; null when offline. */
    String where() {
        if (!online) return null;
        String game = gameType == null ? null : GAMES.getOrDefault(gameType, title(gameType));
        boolean lobby = mode != null && mode.equalsIgnoreCase("LOBBY");
        if ("SKYBLOCK".equals(gameType) && mode != null && !lobby) {
            String island = ISLANDS.get(mode.toLowerCase(Locale.ROOT));
            if (island != null) return island;
            return map != null && !map.isBlank() ? map : title(mode);
        }
        if (game == null) return "Hypixel";
        if (lobby || mode == null) return game + " Lobby";
        return map != null && !map.isBlank() ? game + ", " + map : game;
    }

    /** Where the player is with the game named too, for the tooltip: "SkyBlock, Dwarven Mines". */
    String fullWhere() {
        String where = where();
        return where != null && "SKYBLOCK".equals(gameType) && mode != null && !mode.equalsIgnoreCase("LOBBY") ? "SkyBlock, " + where : where;
    }

    /** "CRYSTAL_HOLLOWS" as "Crystal Hollows". */
    static String title(String id) {
        StringBuilder out = new StringBuilder();
        for (String word : id.toLowerCase(Locale.ROOT).split("_")) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }
}
