package net.alpaka.addons.features.enchantments;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnchantsTest {

    @AfterEach
    void reset() {
        Enchants.raise(id -> false);
    }

    @Test
    void smolderingTopsOutAtSix() {
        assertEquals(6, Enchants.max("Smoldering"));
        assertTrue(MaxEnchantColorFeature.maxedRanges("Smoldering V", false).isEmpty());
        assertEquals(1, MaxEnchantColorFeature.maxedRanges("Smoldering VI", false).size());
    }

    @Test
    void bazaarIdsFollowTheLoreName() {
        assertEquals("ENCHANTMENT_SMOLDERING_6", Enchants.bazaarId("Smoldering", 6));
        assertEquals("ENCHANTMENT_LUCK_OF_THE_SEA_7", Enchants.bazaarId("Luck of the Sea", 7));
        assertEquals("ENCHANTMENT_TURBO_WHEAT_5", Enchants.bazaarId("Turbo-Wheat", 5));
        assertEquals("ENCHANTMENT_ULTIMATE_BOBBIN_TIME_5", Enchants.bazaarId("Bobbin' Time", 5));
        assertEquals("ENCHANTMENT_ULTIMATE_WISE_5", Enchants.bazaarId("Ultimate Wise", 5));
        assertEquals("ENCHANTMENT_ULTIMATE_REITERATE_5", Enchants.bazaarId("Duplex", 5));
        assertEquals("ENCHANTMENT_LOOTING_NEW_7", Enchants.bazaarId("Looting", 7));
    }

    @Test
    void aTradedNewLevelRaisesTheTop() {
        Set<String> traded = Set.of("ENCHANTMENT_SHARPNESS_7", "ENCHANTMENT_SHARPNESS_8", "ENCHANTMENT_ULTIMATE_WISE_6");
        assertTrue(Enchants.raise(traded::contains));
        assertEquals(8, Enchants.max("Sharpness"));
        assertEquals(6, Enchants.max("Ultimate Wise"));
        assertTrue(MaxEnchantColorFeature.maxedRanges("Sharpness VII", false).isEmpty());
        assertEquals(1, MaxEnchantColorFeature.maxedRanges("Sharpness VIII", false).size());
        // The same list again changes nothing.
        assertFalse(Enchants.raise(traded::contains));
    }

    @Test
    void neverBelowTheTable() {
        Enchants.raise(id -> false);
        assertEquals(7, Enchants.max("Sharpness"));
        assertEquals(null, Enchants.max("Not An Enchant"));
    }
}
