package net.alpaka.addons.client;

import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/**
 * Whether a client command was typed by the player, for the commands that delete or move data.
 *
 * Fabric API for 26.2 answers this itself with FabricClientCommandSource.attended(); the one for
 * 26.1.2 does not have it yet. Vanilla already tells the two apart, though: everything run on the
 * player's behalf - a click event, a dialog action - goes through
 * ClientPacketListener.sendUnattendedCommand, so UnattendedCommandMixin marks the span of those calls.
 */
public final class CommandAttendance {
    private static int unattendedDepth = 0;

    private CommandAttendance() {}

    public static void beginUnattended() {
        unattendedDepth++;
    }

    public static void endUnattended() {
        if (unattendedDepth > 0) unattendedDepth--;
    }

    /** Matches the signature of FabricClientCommandSource.attended(), for use in requires(). */
    public static boolean attended(FabricClientCommandSource source) {
        return unattendedDepth == 0;
    }
}
