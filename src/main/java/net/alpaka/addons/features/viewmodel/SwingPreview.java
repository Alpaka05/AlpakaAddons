package net.alpaka.addons.features.viewmodel;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/**
 * A swing of the held item for trying the swing settings from the config menu, seen only by the
 * player.
 *
 * It starts the arm's swing animation through LivingEntity's two-argument swing, which on the
 * client only sets the animation going - it is LocalPlayer's one-argument swing that also tells the
 * server. So nothing is hit, no packet leaves, and other players see no swing. Like any swing it
 * plays out over the game's ticks; the config screen stops pausing singleplayer in setup mode for that.
 */
public final class SwingPreview {
    private SwingPreview() {}

    public static void swing() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        player.swing(InteractionHand.MAIN_HAND, false);
    }
}
