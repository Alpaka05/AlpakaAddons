package net.alpaka.addons.features.damagetags;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
            "§71,234§d❤      | true",
            "§7139k §d❤      | true",
            "§f✧§e1,234✧§d❤  | false",
            "§d❤             | false",
    })
    void hidesOnlyPlainDamageNumbers(String name, boolean hide) {
        assertEquals(hide, DamageTagFeature.isNonCritDamageTag(name.strip()));
    }

    /** Wing of Love's doubled hits end in a heart; the custom tags read them like any other splash. */
    @ParameterizedTest(name = "{0} -> {1} crit {2} love {3}")
    @CsvSource(delimiter = '|', value = {
            "§71,234             | 1,234 | false | false",
            "§71,234§d❤          | 1,234 | false | true",
            "§7139k §d❤          | 139k  | false | true",
            "§f✧§e1§6,§c2§43§f4✧§d❤ | 1,234 | true  | true",
            "§f✧1,234✧♥          | 1,234 | true  | true",
    })
    void readsWingOfLoveSplashes(String name, String number, boolean crit, boolean love) {
        assertEquals(new DamageTagFeature.Splash(number, crit, love), DamageTagFeature.parseDamageTag(name.strip()));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {"§d❤", "§c❤ Health", "§71.5❤"})
    void heartAloneIsNoSplash(String name) {
        assertNull(DamageTagFeature.parseDamageTag(name.strip()));
    }
}
