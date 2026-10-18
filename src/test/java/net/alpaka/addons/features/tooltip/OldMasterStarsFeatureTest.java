package net.alpaka.addons.features.tooltip;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class OldMasterStarsFeatureTest {
    private static final TextColor RED = TextColor.fromLegacyFormat(ChatFormatting.RED);
    private static final TextColor GOLD = TextColor.fromLegacyFormat(ChatFormatting.GOLD);

    @Test
    void planFindsStarsAndNumber() {
        OldMasterStarsFeature.Plan plan = OldMasterStarsFeature.plan("Heroic Hyperion ✪✪✪✪✪➌");
        assertEquals(new OldMasterStarsFeature.Plan(16, 3, 21, 1), plan);
    }

    @Test
    void planIgnoresNamesWithoutNumber() {
        assertNull(OldMasterStarsFeature.plan("Heroic Hyperion ✪✪✪✪✪"));
        assertNull(OldMasterStarsFeature.plan("Hyperion"));
        assertNull(OldMasterStarsFeature.plan("➎ alone"));
    }

    @Test
    void componentStarsTurnRed() {
        Component name = Component.literal("Heroic Hyperion ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal("✪✪✪✪✪").withStyle(ChatFormatting.GOLD))
                .append(Component.literal("➋").withStyle(ChatFormatting.RED));
        Component out = OldMasterStarsFeature.convert(name);
        assertEquals("Heroic Hyperion ✪✪✪✪✪", out.getString());
        assertEquals(List.of(RED, RED, GOLD, GOLD, GOLD), starColors(out));
    }

    @Test
    void legacyCodesAreHandled() {
        Component out = OldMasterStarsFeature.convert(Component.literal("§dHeroic Hyperion §6✪✪✪✪✪§c➎"));
        assertEquals("Heroic Hyperion ✪✪✪✪✪", out.getString());
        assertEquals(List.of(RED, RED, RED, RED, RED), starColors(out));
    }

    @Test
    void namesWithoutNumberAreUntouched() {
        Component name = Component.literal("Hyperion ✪✪✪").withStyle(ChatFormatting.GOLD);
        assertSame(name, OldMasterStarsFeature.convert(name));
    }

    private static List<TextColor> starColors(Component c) {
        List<TextColor> colors = new ArrayList<>();
        c.visit((Style style, String text) -> {
            text.chars().filter(ch -> ch == '✪').forEach(ch -> colors.add(style.getColor()));
            return Optional.empty();
        }, Style.EMPTY);
        return colors;
    }
}
