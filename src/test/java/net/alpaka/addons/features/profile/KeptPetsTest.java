package net.alpaka.addons.features.profile;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeptPetsTest {

    private static final JsonElement ANSWER = JsonParser.parseString("""
            {"profile": {"member": {"pets_data": {"pets": [
              {"type": "SILVERFISH", "tier": "LEGENDARY", "skin": "SILVERFISH_FOSSILIZED", "active": true},
              {"type": "GOLDEN_DRAGON", "tier": "LEGENDARY", "skin": null, "active": false},
              {"type": "RABBIT", "tier": "EPIC", "skin": "RABBIT_AQUAMARINE", "active": false},
              {"type": "RABBIT", "tier": "LEGENDARY", "skin": "RABBIT_ROSE", "active": false},
              {"tier": "COMMON"}
            ]}}}}
            """);

    @Test
    void readsThePetsOfTheKeptAnswer() {
        List<KeptPets.Kept> pets = KeptPets.parse(ANSWER);
        assertEquals(4, pets.size());
        assertEquals(new KeptPets.Kept("SILVERFISH", 4, "SILVERFISH_FOSSILIZED", true), pets.get(0));
        assertEquals(new KeptPets.Kept("GOLDEN_DRAGON", 4, null, false), pets.get(1));
    }

    @Test
    void nothingKeptMeansNoPets() {
        assertTrue(KeptPets.parse(null).isEmpty());
        assertTrue(KeptPets.parse(JsonParser.parseString("{}")).isEmpty());
    }

    @Test
    void picksTheSkinOfThePetType() {
        List<KeptPets.Kept> pets = KeptPets.parse(ANSWER);
        assertEquals("SILVERFISH_FOSSILIZED", KeptPets.pick(pets, "SILVERFISH", 4));
        assertNull(KeptPets.pick(pets, "GOLDEN_DRAGON", 4));
        assertNull(KeptPets.pick(pets, "BLAZE", 4));
    }

    @Test
    void picksByRarityAmongSeveralOfAType() {
        List<KeptPets.Kept> pets = KeptPets.parse(ANSWER);
        assertEquals("RABBIT_AQUAMARINE", KeptPets.pick(pets, "RABBIT", 3));
        assertEquals("RABBIT_ROSE", KeptPets.pick(pets, "RABBIT", 4));
    }

    @Test
    void aRarityNoSkinnedPetHasIsNotHeldAgainstThem() {
        List<KeptPets.Kept> pets = KeptPets.parse(ANSWER);
        // A Tier Boost shows the Legendary Silverfish as Mythic; the text may also give no rarity.
        assertEquals("SILVERFISH_FOSSILIZED", KeptPets.pick(pets, "SILVERFISH", 5));
        assertEquals("SILVERFISH_FOSSILIZED", KeptPets.pick(pets, "SILVERFISH", -1));
    }

    @Test
    void thePetThatWasOutGoesFirstAmongEquals() {
        List<KeptPets.Kept> pets = List.of(
                new KeptPets.Kept("RABBIT", 4, "RABBIT_ROSE", false),
                new KeptPets.Kept("RABBIT", 4, "RABBIT_LUNAR", true));
        assertEquals("RABBIT_LUNAR", KeptPets.pick(pets, "RABBIT", 4));
    }
}
