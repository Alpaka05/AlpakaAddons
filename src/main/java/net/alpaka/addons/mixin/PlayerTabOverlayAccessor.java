package net.alpaka.addons.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Comparator;

/**
 * The tab list's footer, where Hypixel lists the player's active effects, and the order the tab list
 * draws its rows in.
 */
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
    @Accessor("footer")
    Component alpaka$getFooter();

    /** The client keeps tab entries in a hash set; this comparator is how the tab list orders them. */
    @Accessor("PLAYER_COMPARATOR")
    static Comparator<PlayerInfo> alpaka$playerComparator() {
        throw new AssertionError();
    }
}
