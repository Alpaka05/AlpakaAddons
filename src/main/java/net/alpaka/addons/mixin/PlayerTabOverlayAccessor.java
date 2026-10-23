package net.alpaka.addons.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * The tab list's footer, where Hypixel lists the player's active effects, its header, and its
 * slots in the order vanilla draws them (the Custom Tab reads Hypixel's columns from that order).
 */
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
    @Accessor("footer")
    Component alpaka$getFooter();

    @Accessor("header")
    Component alpaka$getHeader();

    @Invoker("getPlayerInfos")
    List<PlayerInfo> alpaka$getPlayerInfos();
}
