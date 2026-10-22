package net.alpaka.addons.utils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The "[Alpaka]" in front of every chat line the mod writes, the name in the same gradient as the
 * AlpakaAddons scoreboard footer.
 */
public final class AlpakaPrefix {

    /** Built once; {@link #prefix} only nests it in a new parent, so a message never changes it. */
    private static final Component NAME = AlpakaGradient.of("Alpaka");

    private AlpakaPrefix() {}

    /**
     * A fresh "[Alpaka] " to append the message to. Each call builds a new component, because
     * appending to a shared one would grow it with every message.
     */
    public static MutableComponent prefix() {
        return Component.empty()
                .append(Component.literal("[").withStyle(ChatFormatting.DARK_GRAY))
                .append(NAME)
                .append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
    }
}
