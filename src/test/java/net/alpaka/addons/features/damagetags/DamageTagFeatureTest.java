package net.alpaka.addons.features.damagetags;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Damage splashes as Hypixel names them (with § codes). One table for both readers: the custom tags
 * take what {@code parseDamageTag} reads, and Only Show Crit Damage hides what it reads as a hit
 * that did not crit. They used to be two parsers that disagreed on asterisks.
 */
class DamageTagFeatureTest {
    private static final String TABLE_NOTE = "name | number, or - for no splash | crit | love";

    @ParameterizedTest(name = "{0} -> {1} crit {2} love {3}")
    @CsvSource(delimiter = '|', value = {
            // Plain hits.
            "§71,234             | 1,234 | false | false",
            "§7999               | 999   | false | false",
            "§7139k              | 139k  | false | false",
            "§7139.9k            | 139.9k | false | false",
            "§71.5M              | 1.5M  | false | false",
            // Crits, the digits often in a color each.
            "§f✧§e1§6,§c2§43§f4✧ | 1,234 | true  | false",
            "✧1,234✧             | 1,234 | true  | false",
            // Ring of Love doubled the hit: a heart after the number.
            "§71,234§d❤          | 1,234 | false | true",
            "§7139k §d❤          | 139k  | false | true",
            "§f✧§e1§6,§c2§43§f4✧§d❤ | 1,234 | true | true",
            "§f✧1,234✧♥          | 1,234 | true  | true",
            "§f✧§e1,234✧§d❤      | 1,234 | true  | true",
            // Asterisks around the number are decoration, for both readers.
            "§7*1,234*           | 1,234 | false | false",
            "§f✧*§e1,234*✧       | 1,234 | true  | false",
            // Not splashes: bold counters, plain decimals, broken grouping, words, a heart alone.
            "§e§l1.5             | -     | false | false",
            "§e§l12              | -     | false | false",
            "§c§l!!!             | -     | false | false",
            "§71.5               | -     | false | false",
            "§712,34             | -     | false | false",
            "§f✧§e1§62§c3§4,§f4✧ | -     | false | false",
            "§7Blaze             | -     | false | false",
            "§d❤                 | -     | false | false",
            "§d❤❤                | -     | false | false",
            "§d♥                 | -     | false | false",
            "§c❤ Health          | -     | false | false",
            "§71.5❤              | -     | false | false",
            "§7*                 | -     | false | false",
    })
    void readsSplashesAndHidesTheHitsThatDidNotCrit(String name, String number, boolean crit, boolean love) {
        String raw = name.strip();
        DamageTagFeature.Splash expected = number.equals("-") ? null : new DamageTagFeature.Splash(number, crit, love);
        assertEquals(expected, DamageTagFeature.parseDamageTag(raw), TABLE_NOTE);
        assertEquals(expected != null && !crit, DamageTagFeature.isNonCritDamageTag(raw), "hidden by Only Show Crit Damage");
    }
}
