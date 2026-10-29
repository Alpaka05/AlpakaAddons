package net.alpaka.addons.features.dungeon.puzzle;

import net.alpaka.addons.utils.SkyblockUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading the blaze name tags of Higher Or Lower (puzzles-entities.md 2.9 and Critic additions A). */
class HigherOrLowerTagTest {

    private static void reads(String name, long current, long max, boolean approximate) {
        HigherOrLowerTag tag = HigherOrLowerTag.parse(name);
        assertNotNull(tag, name);
        assertEquals(current, tag.current(), name);
        assertEquals(max, tag.max(), name);
        assertEquals(approximate, tag.approximate(), name);
    }

    @Test
    void theRealTagCarriesAnIconGlyphBetweenLevelAndName() {
        // U+E07C, the mob icon from Hypixel's resource pack; it must never be stripped or assumed away.
        reads("[Lv15]  Blaze 289/289❤", 289, 289, false);
        reads("[Lv15]  Blaze 1,234/2,000❤", 1234, 2000, false);
    }

    @Test
    void whateverStandsBetweenLevelAndNameIsAccepted() {
        reads("[Lv15]  Blaze 289/289❤", 289, 289, false);
        reads("[Lv15] Blaze 1,234/1,234❤", 1234, 1234, false);
        reads("[Lv15] ￼ Blaze 500/500❤", 500, 500, false);
        reads("[Lv15] [mob_icon] Blaze 77/120❤", 77, 120, false);
    }

    @Test
    void formattingCodesAreStrippedFirst() {
        reads(SkyblockUtils.cleanColor("§8[§7Lv15§8] §cBlaze §a1,234§f/§a1,234§c❤"), 1234, 1234, false);
    }

    @Test
    void zeroHealthMeansDead() {
        HigherOrLowerTag tag = HigherOrLowerTag.parse("[Lv15] Blaze 0/1,500❤");
        assertNotNull(tag);
        assertTrue(tag.dead());
        assertEquals(1500, tag.max());
        assertFalse(HigherOrLowerTag.parse("[Lv15] Blaze 1/1,500❤").dead());
    }

    @Test
    void abbreviatedHealthIsApproximate() {
        reads("[Lv15] Blaze 1.2k/1.5k❤", 1200, 1500, true);
        reads("[Lv15] Blaze 900/1.5k❤", 900, 1500, true);
        reads("[Lv15] Blaze 2M/2M❤", 2_000_000, 2_000_000, true);
    }

    @Test
    void otherNamesAreNotBlazeTags() {
        assertNull(HigherOrLowerTag.parse("[Lv10] Zombie 100/100❤"));
        assertNull(HigherOrLowerTag.parse("Blaze"));
        assertNull(HigherOrLowerTag.parse("[Lv15] FireBlaze 100/100❤"));
        assertNull(HigherOrLowerTag.parse("[Lv15] Blaze 100/100"));
        assertNull(HigherOrLowerTag.parse("Blaze 100/100❤"));
        assertNull(HigherOrLowerTag.parse("[Lv15] Blaze 1.2.3/100❤"));
        assertNull(HigherOrLowerTag.parse("[Lv15] Blaze 5/0❤"));
        assertNull(HigherOrLowerTag.parse(null));
    }
}
