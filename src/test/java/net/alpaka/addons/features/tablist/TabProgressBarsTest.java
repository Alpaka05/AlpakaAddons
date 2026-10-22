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
    void countsTowardAGoalGetABarAfterTheRow() {
        TabListParser.Line souls = TabProgressBars.withBar(line("Fairy Souls: §d29/29"), false);
        assertEquals(1.0f, souls.bar().fraction(), 1.0e-6);
        assertEquals("Fairy Souls: 29/29", TabListParser.plain(souls.text()));

        TabListParser.Line rng = TabProgressBars.withBar(line(" §d25,850/188,040"), false);
        assertEquals(25850 / 188040.0f, rng.bar().fraction(), 1.0e-6);

        TabListParser.Line level = TabProgressBars.withBar(line("SB Level: [621] 52/100 XP"), false);
        assertEquals(0.52f, level.bar().fraction(), 1.0e-6);

        TabListParser.Line bank = TabProgressBars.withBar(line("Bank: 1B / 7.5B"), false);
        assertEquals(1 / 7.5f, bank.bar().fraction(), 1.0e-6);
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
