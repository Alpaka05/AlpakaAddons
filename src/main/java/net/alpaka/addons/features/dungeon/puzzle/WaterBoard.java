package net.alpaka.addons.features.dungeon.puzzle;

/**
 * Water Board: identifies the board's variant and the open doors, then shows which levers to
 * flip and when, counted in server ticks (ServerTicks) from the player's own flip of the water
 * lever (onOwnBlockUse) or the lever's change (onBlockChange). Countdowns stay a steady label; no
 * sound or flash when a flip is due. The solution tables go into their own data class.
 *
 * Spec: puzzles-water.md section 7 (approach, data, constants, state machine, display) and 8
 * (fixtures); attribution rules in section 10.
 *
 * Not built yet: until then it recognises no room and draws nothing.
 */
public final class WaterBoard extends PuzzleSolver {

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.WATER_BOARD;
    }
}
