package net.alpaka.addons.features.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
