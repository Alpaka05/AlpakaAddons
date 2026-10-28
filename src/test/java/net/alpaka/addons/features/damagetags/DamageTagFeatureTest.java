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
    private static final String TABLE_NOTE = "name | number, or - for no splash | crit | marks";

    @ParameterizedTest(name = "{0} -> {1} crit {2} marks {3}")
    @CsvSource(delimiter = '|', value = {
            // Plain hits.
            "§71,234             | 1,234 | false | ",
            "§7999               | 999   | false | ",
            "§7139k              | 139k  | false | ",
            "§7139.9k            | 139.9k | false | ",
            "§71.5M              | 1.5M  | false | ",
            // Crits, the digits often in a color each.
            "§f✧§e1§6,§c2§43§f4✧ | 1,234 | true  | ",
            "✧1,234✧             | 1,234 | true  | ",
            // Ring of Love doubled the hit: a heart after the number.
            "§71,234§d❤          | 1,234 | false | ❤",
            "§7139k §d❤          | 139k  | false | ❤",
            "§f✧§e1§6,§c2§43§f4✧§d❤ | 1,234 | true | ❤",
            "§f✧1,234✧♥          | 1,234 | true  | ♥",
            "§f✧§e1,234✧§d❤      | 1,234 | true  | ❤",
            // Magic damage and the other marks after the number; an Overload crit's stars.
            "§b90,562§b✷         | 90,562 | false | ✷",
            "§b120,749✷          | 120,749 | false | ✷",
            "§71,234§6⚔          | 1,234 | false | ⚔",
            "§71,234§e+          | 1,234 | false | +",
            "§f✯§e1,234§f✯       | 1,234 | true  | ",
            "§f✧1,234✧§d❤§b✷     | 1,234 | true  | ❤✷",
            // Asterisks around the number are decoration, for both readers.
            "§7*1,234*           | 1,234 | false | ",
            "§f✧*§e1,234*✧       | 1,234 | true  | ",
            // Not splashes: bold counters, plain decimals, broken grouping, words, a mark alone or before the number.
            "§e§l1.5             | -     | false | ",
            "§e§l12              | -     | false | ",
            "§c§l!!!             | -     | false | ",
            "§71.5               | -     | false | ",
            "§712,34             | -     | false | ",
            "§f✧§e1§62§c3§4,§f4✧ | -     | false | ",
            "§7Blaze             | -     | false | ",
            "§d❤                 | -     | false | ",
            "§d❤❤                | -     | false | ",
            "§d♥                 | -     | false | ",
            "§c❤ Health          | -     | false | ",
            "§71.5❤              | -     | false | ",
            "§7*                 | -     | false | ",
            "§7+12               | -     | false | ",
            "§7✷                 | -     | false | ",
            "§712✷3              | -     | false | ",
    })
    void readsSplashesAndHidesTheHitsThatDidNotCrit(String name, String number, boolean crit, String marks) {
        String raw = name.strip();
        DamageTagFeature.Splash expected = number.equals("-") ? null : new DamageTagFeature.Splash(number, crit, marks == null ? "" : marks);
        assertEquals(expected, DamageTagFeature.parseDamageTag(raw), TABLE_NOTE);
        assertEquals(expected != null && !crit, DamageTagFeature.isNonCritDamageTag(raw), "hidden by Only Show Crit Damage");
    }
}
