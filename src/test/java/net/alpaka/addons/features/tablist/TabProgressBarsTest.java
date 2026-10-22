package net.alpaka.addons.features.tablist;

import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TabProgressBarsTest {

    private static TabListParser.Line line(String text) {
        return new TabListParser.Line(Component.literal(text), false, null);
    }

    @Test
    void hypixelPipesBecomeABarInTheirColor() {
        TabListParser.Line bar = TabProgressBars.withBar(line("§d||||||||||||§7|||||||| §r(60%)"), false);
        assertEquals(12 / 20.0f, bar.bar().fraction(), 1.0e-6);
        assertEquals(0xFFFF55FF, bar.bar().color());
        assertEquals("", TabListParser.plain(bar.text()));
        assertEquals("(60%)", TabListParser.plain(bar.after()));
    }

    @Test
    void electionBarsEndWhereTheirColorDoes() {
        // The election's empty pipes are white, not gray.
        TabListParser.Line cole = TabProgressBars.withBar(line("§cCole: §c||||||§f||||| §r(52%)"), false);
        assertEquals(0.52f, cole.bar().fraction(), 1.0e-6);
        assertEquals(0xFFFF5555, cole.bar().color());
        assertEquals("Cole:", TabListParser.plain(cole.text()));

        TabListParser.Line plain = TabProgressBars.withBar(line("§a|||||§f|||||"), false);
        assertEquals(0.5f, plain.bar().fraction(), 1.0e-6);
        assertEquals(0.0f, TabProgressBars.withBar(line("§7||||||||"), false).bar().fraction(), 1.0e-6);
    }

    @Test
    void countsTowardAGoalGetABarAfterTheRow() {
        TabListParser.Line souls = TabProgressBars.withBar(line("Fairy Souls: §d29/29"), false);
        assertEquals(1.0f, souls.bar().fraction(), 1.0e-6);
        assertEquals("Fairy Souls: 29/29", TabListParser.plain(souls.text()));

        TabListParser.Line rng = TabProgressBars.withBar(line(" §d25,850/188,040"), false);
        assertEquals(25850 / 188040.0f, rng.bar().fraction(), 1.0e-6);

        TabListParser.Line level = TabProgressBars.withBar(line("SB Level: [621] 52/100 XP"), false);
        assertEquals(0.52f, level.bar().fraction(), 1.0e-6);

        // Co-op bank and personal bank, not progress.
        assertNull(TabProgressBars.withBar(line("Bank: 1B / 7.5B"), false).bar());
    }

    @Test
    void aBarOnItsOwnRowJoinsTheValueButNeverTheTitle() {
        java.util.List<TabListParser.Line> rows = new java.util.ArrayList<>();
        for (String row : new String[]{"§5§lMage Reputation:", " §d29,110", " §d||||||||||||||| §r(100%)", " Max Reputation"}) {
            rows.add(TabProgressBars.withBar(line(row), false));
        }
        java.util.List<TabListParser.Line> joined = TabProgressBars.joinBarRows(rows);
        assertEquals(3, joined.size());
        assertEquals("Mage Reputation:", TabListParser.plain(joined.get(0).text()).trim());
        assertNull(joined.get(0).bar());
        assertEquals("29,110", TabListParser.plain(joined.get(1).text()).trim());
        assertEquals(1.0f, joined.get(1).bar().fraction(), 1.0e-6);
        assertEquals("(100%)", TabListParser.plain(joined.get(1).after()));
        assertEquals("Max Reputation", TabListParser.plain(joined.get(2).text()).trim());

        // A bar right under a title stays on its own row rather than joining the title.
        java.util.List<TabListParser.Line> titled = TabProgressBars.joinBarRows(java.util.List.of(
                rows.get(0), rows.get(2)));
        assertEquals(2, titled.size());
    }

    @Test
    void percentagesAreOnlyProgressInTheSkillsWidget() {
        assertEquals(0.421f, TabProgressBars.withBar(line("Farming 50: 42.1%"), true).bar().fraction(), 1.0e-6);
        TabListParser.Line stat = line("Crit Chance: 10%");
        assertSame(stat, TabProgressBars.withBar(stat, false));
        assertNull(TabProgressBars.withBar(line("Gems: 715"), false).bar());
    }

    @Test
    void changedValuesLightUpButCountdownsDoNot() {
        TabChangeFlash flash = new TabChangeFlash();
        flash.observe("Info/Area/Gems", "Gems: 715", 0L);
        flash.observe("Info/Area/Ashfall", "Ashfall: in 8m", 0L);
        assertFalse(flash.strength("Info/Area/Gems", 10L) > 0.0f);

        flash.observe("Info/Area/Gems", "Gems: 720", 100L);
        flash.observe("Info/Area/Ashfall", "Ashfall: in 7m", 100L);
        assertTrue(flash.strength("Info/Area/Gems", 200L) > 0.0f);
        assertEquals(0.0f, flash.strength("Info/Area/Ashfall", 200L));
        assertEquals(0.0f, flash.strength("Info/Area/Gems", 100L + TabChangeFlash.FLASH_MS));

        assertEquals("Info/Profile/Bank", TabChangeFlash.key("Info", "Profile", "Bank: 1B / 7.5B", 3));
        assertEquals("Info/Slayer/#1", TabChangeFlash.key("Info", "Slayer", "Inferno Demonlord IV", 1));
    }
}
