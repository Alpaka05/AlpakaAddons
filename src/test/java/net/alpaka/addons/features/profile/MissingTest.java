package net.alpaka.addons.features.profile;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissingTest {
    private static final String MISC = """
            {"talisman_upgrades": {
                "SPEED_TALISMAN": ["SPEED_RING", "SPEED_ARTIFACT"],
                "SPEED_RING": ["SPEED_ARTIFACT"],
                "WOLF_TALISMAN": ["WOLF_RING"]},
             "ignored_talisman": ["CRUX_TALISMAN_1"]}
            """;
    private static final String PETNUMS = """
            {"BEE": {"COMMON": {}, "LEGENDARY": {}}, "ROCK": {"COMMON": {}, "EPIC": {}}, "WOLF": {"MYTHIC": {}},
             "BINGO": {"MYTHIC": {}}, "DROPLET_WISP": {"UNCOMMON": {}}, "FROST_WISP": {"RARE": {}},
             "GLACIAL_WISP": {"EPIC": {}}, "SUBZERO_WISP": {"LEGENDARY": {}}}
            """;
    private static final Map<String, String> TIERS = Map.of(
            "SPEED_TALISMAN", "COMMON", "SPEED_RING", "UNCOMMON", "SPEED_ARTIFACT", "RARE",
            "WOLF_TALISMAN", "UNCOMMON", "WOLF_RING", "RARE",
            "CRUX_TALISMAN_1", "COMMON", "HEGEMONY_ARTIFACT", "LEGENDARY");

    private final Missing.Neu neu = Missing.parse(MISC, PETNUMS);

    @Test
    void onlyTheTopOfALineIsMissing() {
        List<Missing.Gap> gaps = Missing.accessories(Set.of(), TIERS, neu, id -> null);
        assertEquals(List.of("HEGEMONY_ARTIFACT", "SPEED_ARTIFACT", "WOLF_RING"), gaps.stream().map(Missing.Gap::id).toList());
        assertEquals(16, gaps.get(0).power());
        assertTrue(gaps.stream().allMatch(g -> g.upgradeOf() == null));
    }

    @Test
    void anOwnedLowerVersionMakesItAnUpgrade() {
        List<Missing.Gap> gaps = Missing.accessories(Set.of("SPEED_TALISMAN", "SPEED_RING", "WOLF_RING"), TIERS, neu, id -> null);
        Missing.Gap speed = gaps.stream().filter(g -> g.id().equals("SPEED_ARTIFACT")).findFirst().orElseThrow();
        // From the ring, the better of the two owned: rare 8 minus uncommon 5.
        assertEquals("SPEED_RING", speed.upgradeOf());
        assertEquals(3, speed.power());
        // The wolf line is complete, the ignored Rift talisman never shows.
        assertTrue(gaps.stream().noneMatch(g -> g.id().startsWith("WOLF") || g.id().startsWith("CRUX")));
    }

    @Test
    void missingPetsInTheirHighestRarity() {
        List<Missing.MissingPet> pets = Missing.pets(Set.of("BEE", "SUBZERO_WISP"), neu, false);
        assertEquals(List.of("WOLF", "ROCK"), pets.stream().map(Missing.MissingPet::type).toList());
        assertEquals("MYTHIC", pets.get(0).tier());
        assertEquals("EPIC", pets.get(1).tier());
    }

    @Test
    void theWispsAreOnePet() {
        // A lower wisp owned counts for the whole line, none owned shows the top one only.
        assertTrue(Missing.pets(Set.of("FROST_WISP"), neu, false).stream().noneMatch(p -> p.type().endsWith("_WISP")));
        assertEquals(List.of("SUBZERO_WISP"), Missing.pets(Set.of(), neu, false).stream()
                .map(Missing.MissingPet::type).filter(t -> t.endsWith("_WISP")).toList());
    }

    @Test
    void theBingoPetOnlyOnBingoProfiles() {
        assertTrue(Missing.pets(Set.of(), neu, false).stream().noneMatch(p -> p.type().equals("BINGO")));
        assertTrue(Missing.pets(Set.of(), neu, true).stream().anyMatch(p -> p.type().equals("BINGO")));
    }

    @Test
    void magicalPowerByRarity() {
        assertEquals(22, Missing.magicalPower("MYTHIC"));
        assertEquals(3, Missing.magicalPower("SPECIAL"));
        assertEquals(0, Missing.magicalPower(null));
    }
}
