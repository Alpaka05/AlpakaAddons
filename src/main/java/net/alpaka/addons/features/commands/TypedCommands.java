package net.alpaka.addons.features.commands;

import net.alpaka.addons.compliance.Outbound;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;

/**
 * Commands the player sends by hand, typed into chat or picked on the command wheel, on their way
 * out: Shorten Warp and Warp Cooldown get to look at each before it is sent, so "/barn" on the
 * wheel does what it does in chat.
 */
public final class TypedCommands {

    private TypedCommands() {
    }

    /**
     * A command typed into chat and sent with Enter, without its slash. True when it was taken care
     * of here - sent in another form, or held - and must not be sent as typed.
     */
    public static boolean handle(String command) {
        String rewritten = shortened(command);
        String outgoing = rewritten != null ? rewritten : command;
        if (WarpCooldownQueue.onTyped(outgoing)) return true;
        if (rewritten == null) return false;
        Outbound.command(rewritten, Outbound.Cause.CHAT_INPUT);
        return true;
    }

    /**
     * A command picked on the command wheel, without its slash: sent once, in the form Shorten Warp
     * makes of it, or held by Warp Cooldown like a typed one.
     */
    public static void sendPicked(String command) {
        String rewritten = shortened(command);
        String outgoing = rewritten != null ? rewritten : command;
        if (WarpCooldownQueue.onTyped(outgoing)) return;
        Outbound.command(outgoing, Outbound.Cause.WHEEL);
    }

    /** The warp Shorten Warp makes of a command, or null. */
    private static String shortened(String command) {
        if (!AlpakaConfig.instance.shortenWarpEnabled || !WarpCooldownQueue.onSkyblock()) return null;
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return null;
        // The dispatcher holds the server's commands and every mod's client commands alike.
        return WarpShortcut.rewrite(command, name -> connection.getCommands().getRoot().getChild(name) != null,
                SkyblockUtils.isOnGarden());
    }
}
