package net.alpaka.addons.features.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatCopyFeatureTest {
    @Test
    void plainTextDropsCodes() {
        Component message = Component.literal("§6[MVP§c++§6] ").append(Component.literal("Alpaka").withStyle(ChatFormatting.GOLD));
        assertEquals("[MVP++] Alpaka", ChatCopyFeature.plainText(message));
    }

    @Test
    void codedTextWritesStyleChanges() {
        Component message = Component.empty()
                .append(Component.literal("Party ").withStyle(ChatFormatting.BLUE))
                .append(Component.literal("> ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal("hi").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(" there"));
        assertEquals("§9Party §8> §f§lhi§r there", ChatCopyFeature.codedText(message));
    }

    @Test
    void codedTextKeepsCodesInTheText() {
        assertEquals("§aYou are §bAlpaka", ChatCopyFeature.codedText(Component.literal("§aYou are §bAlpaka")));
    }

    @Test
    void codedTextTakesNearestLegacyColor() {
        Component message = Component.literal("x").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFE5050)));
        assertEquals("§cx", ChatCopyFeature.codedText(message));
    }
}
