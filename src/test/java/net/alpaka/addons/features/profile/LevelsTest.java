package net.alpaka.addons.features.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LevelsTest {

    @Test
    void tablesAddUpToWhatTheGameNeeds() {
        // Totals from Hypixel's resources and from real profiles read through Elite.
        assertEquals(55_172_425L, Levels.total(Levels.SKILL, 50));
        assertEquals(111_672_425L, Levels.total(Levels.SKILL, 60));
        assertEquals(94_450L, Levels.total(Levels.RUNECRAFTING, 25));
        assertEquals(272_800L, Levels.total(Levels.SOCIAL, 25));
        assertEquals(569_809_640L, Levels.total(Levels.CATACOMBS, 50));
    }

    @Test
    void xpTurnsIntoTheLevelItReaches() {
        assertEquals(0.0, Levels.skill("farming", 0, 60));
        assertEquals(1.0, Levels.skill("farming", 50, 60), 1e-9);
        assertEquals(1.5, Levels.skill("farming", 50 + 62.5, 60), 1e-9);
        // A capped skill stops at its cap however much XP there is.
        assertEquals(50.0, Levels.skill("farming", 260_056_683, 50), 1e-9);
        assertEquals(60.0, Levels.skill("combat", 665_230_194, 60), 1e-9);
        // A real Catacombs 38 at 4 % of the way, as Elite reports it.
        assertEquals(38.0415, Levels.catacombs(30_741_859.63), 1e-3);
        // Past 50 it goes on at 200M a level.
        assertEquals(51.0, Levels.catacombs(569_809_640 + 200_000_000), 1e-9);
    }

    @Test
    void maxedSkillsCountOnAsOverflowLevels() {
        // Level 61 takes 7.6M past the 111,672,425 of level 60, each level after 600k more.
        Levels.Overflow at61 = Levels.skillOverflow("combat", 111_672_425L + 7_600_000L);
        assertEquals(61, at61.level());
        assertEquals(0.0, at61.xpCurrent(), 1e-9);
        assertEquals(8_200_000.0, at61.xpForNext(), 1e-9);
        // Levels 61 to 70 take 103M together; at 70 the step doubles, so 71 takes 13.6M, 72 14.8M.
        Levels.Overflow at70 = Levels.skillOverflow("combat", 111_672_425L + 103_000_000L);
        assertEquals(70, at70.level());
        assertEquals(13_600_000.0, at70.xpForNext(), 1e-9);
        Levels.Overflow at71 = Levels.skillOverflow("combat", 111_672_425L + 103_000_000L + 13_600_000L);
        assertEquals(14_800_000.0, at71.xpForNext(), 1e-9);
        // A skill capped at 50 keeps going through the table's levels 51 to 60 first.
        assertEquals(55.0, Levels.skillOverflow("alchemy", Levels.total(Levels.SKILL, 55)).exact(), 1e-9);
        assertNull(Levels.skillOverflow("runecrafting", 1e9));
    }

    @Test
    void dungeonOverflowGoesOnAt200MALevel() {
        Levels.Overflow over = Levels.catacombsOverflow(569_809_640 + 2.5 * 200_000_000);
        assertEquals(52, over.level());
        assertEquals(52.5, over.exact(), 1e-9);
        assertEquals(38.0415, Levels.catacombsOverflow(30_741_859.63).exact(), 1e-3);
    }
}
