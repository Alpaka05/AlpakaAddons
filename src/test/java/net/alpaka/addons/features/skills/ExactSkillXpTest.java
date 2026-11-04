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
        ExactSkillXp.forget();
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
}
