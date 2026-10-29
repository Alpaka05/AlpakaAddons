package net.alpaka.addons.features.enchantments;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaxEnchantColorFeatureTest {
    private static final TextColor GOLD = TextColor.fromLegacyFormat(ChatFormatting.GOLD);
    private static final TextColor BLUE = TextColor.fromLegacyFormat(ChatFormatting.BLUE);

    @Test
    void romanAndDigitLevels() {
        assertEquals(7, MaxEnchantColorFeature.level("VII"));
        assertEquals(4, MaxEnchantColorFeature.level("IV"));
        assertEquals(10, MaxEnchantColorFeature.level("X"));
        assertEquals(9, MaxEnchantColorFeature.level("IX"));
        assertEquals(10, MaxEnchantColorFeature.level("10"));
    }

    @Test
    void findsOnlyMaxedEnchantments() {
        String line = "Sharpness VII, Critical VI, Luck of the Sea VII";
        List<int[]> ranges = MaxEnchantColorFeature.maxedRanges(line, false);
        assertEquals(2, ranges.size());
        assertEquals("Sharpness VII", line.substring(ranges.get(0)[0], ranges.get(0)[1]));
        assertEquals("Luck of the Sea VII", line.substring(ranges.get(1)[0], ranges.get(1)[1]));
    }

    @Test
    void ignoresDescriptionsAndUltimatesUnlessAsked() {
        assertTrue(MaxEnchantColorFeature.maxedRanges("Grants Sharpness VII to you", false).isEmpty());
        assertTrue(MaxEnchantColorFeature.maxedRanges("Ultimate Wise V", false).isEmpty());
        assertEquals(1, MaxEnchantColorFeature.maxedRanges("Ultimate Wise V, Sharpness VI", true).size());
    }

    @Test
    void stackingCounterKeepsItsColor() {
        Component line = Component.literal("Expertise X ").withStyle(ChatFormatting.BLUE)
                .append(Component.literal("15k").withStyle(ChatFormatting.DARK_GRAY));
        Component out = MaxEnchantColorFeature.recolor(line, GOLD, false);
        assertEquals("Expertise X 15k", out.getString());
        List<TextColor> colors = colors(out);
        assertEquals(GOLD, colors.get(0));
        assertEquals(GOLD, colors.get("Expertise ".length()));
        assertEquals(BLUE, colors.get("Expertise X".length()));
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.DARK_GRAY), colors.get("Expertise X ".length()));
    }

    @Test
    void legacyCodesAreResolved() {
        Component line = Component.literal("§9Sharpness VII§9, §9Smite VI");
        Component out = MaxEnchantColorFeature.recolor(line, GOLD, false);
        assertEquals("Sharpness VII, Smite VI", out.getString());
        List<TextColor> colors = colors(out);
        assertEquals(GOLD, colors.get(0));
        assertEquals(BLUE, colors.get("Sharpness VII, ".length()));
    }

    @Test
    void untouchedLineIsReturnedAsIs() {
        Component line = Component.literal("Sharpness V").withStyle(ChatFormatting.BLUE);
        assertSame(line, MaxEnchantColorFeature.recolor(line, GOLD, false));
    }

    private static List<TextColor> colors(Component c) {
        List<TextColor> out = new ArrayList<>();
        c.visit((style, part) -> {
            for (int i = 0; i < part.length(); i++) out.add(style.getColor());
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }
}
