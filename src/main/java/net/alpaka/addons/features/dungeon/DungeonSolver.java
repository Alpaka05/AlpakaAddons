package net.alpaka.addons.features.dungeon;

import net.alpaka.addons.features.dungeon.render.SolverDraw;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * One puzzle or device solver. {@link DungeonSolvers} owns one instance of each and calls these
 * methods on the client thread; every one of them is optional.
 *
 * What a solver may do is narrow (design.md section 1): read what the server sent and draw through
 * the {@link SolverDraw} it is handed. It never sends anything, never cancels or changes an
 * interaction, and never draws through walls.
 *
 * Calls only arrive while {@link #enabled()} holds and the solver's phase applies: puzzle solvers
 * in a run outside the boss room (and not once the tab list shows their puzzle solved or failed),
 * device solvers in the F7/M7 boss's Goldor phase. Within that a solver works out for itself
 * whether the player is in its room or at its device, and draws nothing otherwise. A solver that
 * is switched off, and every solver when the world changes, gets {@link #reset()}.
 */
public abstract class DungeonSolver {

    /** Short name for the debug command, e.g. "Water Board". */
    public abstract String name();

    /** Its master and its own switch. Read every tick; a change to false resets the solver. */
    public abstract boolean enabled();

    /** Every client tick while it applies. Do detection here and return quickly when it does not. */
    public void tick(SolverContext ctx) {}

    /** A chat line (game messages, also ones another mod hid; never the action bar), formatting stripped and trimmed. */
    public void onChat(String plain) {}

    /**
     * A block the server changed, before the change is applied; {@code oldState} is what the client
     * had there. Arrives for every changed block in the world, so return as fast as possible for
     * positions the solver does not care about. {@code pos} is immutable.
     */
    public void onBlockChange(BlockPos pos, BlockState oldState, BlockState newState) {}

    /** The server moved the player (a teleport), after it was applied; the player's position and rotation. */
    public void onServerTeleport(Vec3 pos, float yRot, float xRot) {}

    /**
     * The player right-clicked a block with the main hand and the client went through with it
     * (lever flips, Boulder buttons). Only an observation: the click already happened as vanilla made it.
     */
    public void onOwnBlockUse(BlockPos pos, InteractionHand hand) {}

    /** The player right-clicked an entity with the main hand (item frames); observed the same way. */
    public void onOwnEntityUse(Entity target, InteractionHand hand) {}

    /** This frame's marks. Only called on a solver that was ticked this tick. */
    public void render(SolverDraw draw, float partialTick) {}

    /** World change, leaving the run, or switched off: forget everything. */
    public void reset() {}

    /** Whether it has recognised its room or device right now; for /alpakadebug dungeon. */
    public boolean claimsRoom() {
        return false;
    }

    /** One line about its state for /alpakadebug dungeon, or null. */
    public String debugState() {
        return null;
    }
}
