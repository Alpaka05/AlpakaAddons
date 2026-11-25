package net.alpaka.addons.features.profile;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Hypixel's status as the overview words it. */
class PlayerStatusTest {
    private static PlayerStatus read(String json) {
        return PlayerStatus.read(JsonParser.parseString(json));
    }

    @Test
    void skyBlockShowsTheIsland() {
        PlayerStatus s = read("{\"success\":true,\"online\":true,\"gameType\":\"SKYBLOCK\",\"mode\":\"mining_3\"}");
        assertEquals("Dwarven Mines", s.where());
        assertEquals("SkyBlock, Dwarven Mines", s.fullWhere());
        assertEquals("Private Island", read("{\"online\":true,\"gameType\":\"SKYBLOCK\",\"mode\":\"dynamic\"}").where());
        // An island not known yet still reads as words.
        assertEquals("New Island", read("{\"online\":true,\"gameType\":\"SKYBLOCK\",\"mode\":\"new_island\"}").where());
        assertEquals("SkyBlock Lobby", read("{\"online\":true,\"gameType\":\"SKYBLOCK\",\"mode\":\"LOBBY\"}").where());
    }

    @Test
    void otherGamesShowTheGameAndLobby() {
        assertEquals("Main Lobby", read("{\"online\":true,\"gameType\":\"MAIN\",\"mode\":\"LOBBY\"}").where());
        assertEquals("Bed Wars Lobby", read("{\"online\":true,\"gameType\":\"BEDWARS\",\"mode\":\"LOBBY\"}").where());
        assertEquals("Bed Wars, Lighthouse", read("{\"online\":true,\"gameType\":\"BEDWARS\",\"mode\":\"BEDWARS_EIGHT_ONE\",\"map\":\"Lighthouse\"}").where());
        assertEquals("Duels", read("{\"online\":true,\"gameType\":\"DUELS\",\"mode\":\"DUELS_SUMO\"}").where());
        assertEquals("Hypixel", read("{\"online\":true}").where());
    }

    @Test
    void offlineSaysNothingMore() {
        PlayerStatus s = read("{\"success\":true,\"online\":false}");
        assertFalse(s.online());
        assertNull(s.where());
        assertNull(read("{\"success\":false}"));
    }
}
