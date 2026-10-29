package net.alpaka.addons.features.dungeon.device;

import net.alpaka.addons.features.dungeon.room.DungeonRooms;
import net.alpaka.addons.utils.DungeonState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Whether the player is in the F7/M7 boss's third phase, Goldor's, where the terminals and the
 * four devices are; and which of its four sections they are in.
 *
 * The boss arena is one fixed build, the same in every run, so the phase follows from the player's
 * height (terminals.md 1.3, how Odin tells it): above Y 210 Maxor, above 155 Storm, above 100
 * Goldor, above 45 the core, below that Necron. Goldor's opening line marks the phase as started
 * and "The Core entrance is opening!" as over; the height alone still works after a rejoin
 * mid-phase, when the opening line was missed.
 */
public final class GoldorPhase {

    /** Goldor's first line, which starts the phase. */
    public static final String START_LINE = "[BOSS] Goldor: Who dares trespass into my domain?";
    /** The last section's gate is down; the phase is over. */
    public static final String END_LINE = "The Core entrance is opening!";

    /** The section boundaries' centre, worked out from the positions of the sections' terminals and devices. */
    private static final double CENTRE_X = 54.0;
    private static final double CENTRE_Z = 85.0;

    private static boolean started = false;
    private static boolean ended = false;

    private GoldorPhase() {}

    /** In the floor 7 boss, normal or Master Mode; after a rejoin mid-boss too (DungeonRooms.inBossRoom). */
    public static boolean inF7Boss() {
        String floor = DungeonState.getFloor();
        return floor != null && floor.endsWith("7") && DungeonRooms.inBossRoom();
    }

    /** In the F7/M7 boss, at Goldor's height, and the core is not open yet. */
    public static boolean inP3() {
        if (!inF7Boss() || ended) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && phaseAt(player.getY()) == 3;
    }

    /** The boss phase (1..5) a height in the F7/M7 arena belongs to. */
    public static int phaseAt(double y) {
        if (y > 210) return 1;
        if (y > 155) return 2;
        if (y > 100) return 3;
        if (y > 45) return 4;
        return 5;
    }

    /**
     * The Goldor section (1..4) a position in the arena belongs to: 1 east (Simon Says), 2 south
     * (Lights), 3 west (Arrow Align), 4 north (Sharpshooter). Each section's terminals, levers and
     * device lie on its side of the arena (terminals.md 1.1).
     */
    public static int sectionAt(double x, double z) {
        double dx = x - CENTRE_X;
        double dz = z - CENTRE_Z;
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? 1 : 3;
        return dz >= 0 ? 2 : 4;
    }

    /** Whether Goldor's opening line has been seen this run. */
    public static boolean started() {
        return started;
    }

    /** A chat line, formatting stripped and trimmed. */
    public static void onChat(String plain) {
        if (plain.equals(START_LINE)) {
            started = true;
            ended = false;
        } else if (plain.equals(END_LINE) && inF7Boss()) {
            ended = true;
        }
    }

    /** World change or disconnect. */
    public static void reset() {
        started = false;
        ended = false;
    }
}
