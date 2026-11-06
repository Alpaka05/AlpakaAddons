package net.alpaka.addons.features.skills;

import net.alpaka.addons.features.profile.Levels;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExactSkillXpTest {

    @BeforeEach
    void forget() {
        ExactSkillXp.reset();
    }

    @Test
    void readsTheLevelFromTheSkillsWidget() {
        List<String> tab = List.of("Skills: Combat 60", " Farming 50: 42.1%", " Mining 14: 22,922/75k", " Taming 60: MAX");
        assertEquals(50, ExactSkillXp.levelInTab(tab, "Farming"));
        assertEquals(14, ExactSkillXp.levelInTab(tab, "Mining"));
        assertNull(ExactSkillXp.levelInTab(tab, "Taming"));
        assertNull(ExactSkillXp.levelInTab(tab, "Fishing"));
        assertEquals(37, ExactSkillXp.levelInTab(List.of("Skills: Foraging 37: 8.4%"), "Foraging"));
    }

    @Test
    void aFallingPercentageIsTheNextLevelWhileTheTabLags() {
        assertEquals(50, ExactSkillXp.level("farming", 50, 99.95));
        assertEquals(51, ExactSkillXp.level("farming", 50, 0.02));
        // The tab still says 50, the level stays 51.
        assertEquals(51, ExactSkillXp.level("farming", 50, 0.05));
        // Once the tab catches up nothing moves.
        assertEquals(51, ExactSkillXp.level("farming", 51, 0.07));
    }

    @Test
    void aSeenLevelFarAboveTheTabIsFromAnotherProfile() {
        assertEquals(50, ExactSkillXp.level("farming", 50, 40.0));
        // Switched to a profile at Farming 20 without a new login or world change.
        assertEquals(20, ExactSkillXp.level("farming", 20, 30.0));
        assertEquals(20, ExactSkillXp.level("farming", 20, 31.0));
    }

    @Test
    void resetForgetsTheSeenLevels() {
        assertEquals(50, ExactSkillXp.level("farming", 50, 99.95));
        assertEquals(51, ExactSkillXp.level("farming", 50, 0.02));
        ExactSkillXp.reset();
        assertEquals(50, ExactSkillXp.level("farming", 50, 0.05));
    }

    @Test
    void withoutAnyLevelNothingIsShown() {
        assertNull(ExactSkillXp.level("mining", null, 12.0));
    }

    @Test
    void formatsExactAndRounded() {
        long needed = Levels.skillXpForNext("farming", 50);
        assertEquals(4_300_000, needed);
        assertEquals("1,948,760/4,300,000", ExactSkillXp.format(needed * 45.32 / 100, needed, false));
        assertEquals("1.95M/4.3M", ExactSkillXp.format(needed * 45.32 / 100, needed, true));
        assertEquals(0, Levels.skillXpForNext("farming", 60));
        assertEquals(19_050, Levels.skillXpForNext("runecrafting", 24));
    }

    @Test
    void replacesOnlyThePercentageAndKeepsTheStyles() {
        Style red = Style.EMPTY.withColor(ChatFormatting.RED);
        Style aqua = Style.EMPTY.withColor(ChatFormatting.DARK_AQUA);
        List<ExactSkillXp.Piece> pieces = List.of(
                new ExactSkillXp.Piece(red, "1000/1000❤     "),
                new ExactSkillXp.Piece(aqua, "+12.5 Farming (45.32%)"),
                new ExactSkillXp.Piece(red, "     500/500✎ Mana"));
        String full = "1000/1000❤     +12.5 Farming (45.32%)     500/500✎ Mana";
        int start = full.indexOf("45.32");
        Component out = ExactSkillXp.replace(pieces, start, start + "45.32%".length(), "1.95M/4.3M");
        assertEquals("1000/1000❤     +12.5 Farming (1.95M/4.3M)     500/500✎ Mana", out.getString());
        assertEquals(3, out.getSiblings().size());
        assertEquals(aqua, out.getSiblings().get(1).getStyle());
    }

    @Test
    void readsTheCapOfAMaxedSkill() {
        List<String> tab = List.of("Skills: Farming 60: MAX", " Fishing 50: MAX", " Mining 14: 22,922/75k");
        assertEquals(60, ExactSkillXp.capInTab(tab, "Farming"));
        assertEquals(50, ExactSkillXp.capInTab(tab, "Fishing"));
        assertNull(ExactSkillXp.capInTab(tab, "Mining"));
        // Without the tab list each skill has its own cap.
        assertEquals(60, ExactSkillXp.cap("farming", null));
        assertEquals(57, ExactSkillXp.cap("foraging", null));
        assertEquals(50, ExactSkillXp.cap("hunting", null));
        assertNull(ExactSkillXp.cap("dancing", null));
        // A lower cap in the tab list (Farming before Anita's perks) is remembered until the world changes.
        assertEquals(50, ExactSkillXp.cap("farming", 50));
        assertEquals(50, ExactSkillXp.cap("farming", null));
        ExactSkillXp.reset();
        assertEquals(60, ExactSkillXp.cap("farming", null));
    }

    @Test
    void xpPastTheCapCountsOnAsOverflowLevels() {
        // Level 61 takes 7.6M, 62 then 8.2M.
        Levels.Overflow at60 = ExactSkillXp.overflow("farming", 60, 5_183_244);
        assertEquals(60, at60.level());
        assertEquals(5_183_244, at60.xpCurrent(), 1e-6);
        assertEquals(7_600_000, at60.xpForNext(), 1e-6);
        Levels.Overflow at62 = ExactSkillXp.overflow("farming", 60, 7_600_000 + 8_200_000 + 1_000);
        assertEquals(62, at62.level());
        assertEquals(1_000, at62.xpCurrent(), 1e-6);
        assertEquals(8_800_000, at62.xpForNext(), 1e-6);
        // A skill capped at 50 goes on through the table's levels 51 to 60 first: 51 takes 4.3M.
        Levels.Overflow fishing = ExactSkillXp.overflow("fishing", 50, 5_000_000);
        assertEquals(51, fishing.level());
        assertEquals(700_000, fishing.xpCurrent(), 1e-6);
        assertEquals(4_600_000, fishing.xpForNext(), 1e-6);
        // Foraging stops at 57 (91,572,425 XP), so 58 takes 6.4M.
        Levels.Overflow foraging = ExactSkillXp.overflow("foraging", 57, 100_000);
        assertEquals(57, foraging.level());
        assertEquals(91_572_425L, Levels.skillXpToReach("foraging", 57));
        assertEquals(6_400_000, foraging.xpForNext(), 1e-6);
        assertNull(ExactSkillXp.overflow("runecrafting", 25, 1_000));
        assertNull(ExactSkillXp.overflow("farming", 60, 1e300));
    }

    @Test
    void putsTheOverflowLevelBehindTheSkillName() {
        Style aqua = Style.EMPTY.withColor(ChatFormatting.DARK_AQUA);
        String gain = "+1,528.6 Combat (5,183,244/0)";
        List<ExactSkillXp.Piece> pieces = List.of(new ExactSkillXp.Piece(aqua, gain));
        int from = gain.indexOf(" (");
        int to = gain.indexOf("/0") + 2;
        Component out = ExactSkillXp.replace(pieces, from, to, " 60 (5,183,244/7,600,000");
        assertEquals("+1,528.6 Combat 60 (5,183,244/7,600,000)", out.getString());
    }
}
