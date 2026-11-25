package net.alpaka.addons.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * The tab list's footer, where Hypixel lists the player's active effects, and its slots in the
 * order vanilla draws them.
 */
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
    @Accessor("footer")
    Component alpaka$getFooter();

    @Invoker("getPlayerInfos")
    List<PlayerInfo> alpaka$getPlayerInfos();
}
