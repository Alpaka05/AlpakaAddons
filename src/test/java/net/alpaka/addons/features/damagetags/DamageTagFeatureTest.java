package net.alpaka.addons.features.damagetags;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DamageTagFeatureTest {
    /** Raw names as Hypixel sends them (with § codes), and whether Only Show Crit Damage hides them. */
    @ParameterizedTest(name = "{0} -> hide {1}")
    @CsvSource(delimiter = '|', value = {
            "§71,234         | true",
            "§7999           | true",
            "§7139k          | true",
            "§7139.9k        | true",
            "§71.5M          | true",
            "§f✧§e1§62§c3§4,§f4✧ | false",
            "✧1,234✧         | false",
            "§e§l1.5         | false",
            "§e§l12          | false",
            "§c§l!!!         | false",
            "§71.5           | false",
            "§712,34         | false",
            "§7Blaze         | false",
    })
    void hidesOnlyPlainDamageNumbers(String name, boolean hide) {
        assertEquals(hide, DamageTagFeature.isNonCritDamageTag(name.strip()));
    }
}
