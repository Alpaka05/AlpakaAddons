package net.alpaka.addons.features.dungeon.puzzle;

/**
 * Three Weirdos: of the three NPCs, only one tells the truth, and the reward is in that one's
 * chest. Reads each NPC's {@code [NPC] <name>: <line>} chat answer once the player has asked them
 * all, works out the honest one from the 18 known statements, and marks that chest (and the liars'
 * chests in the Wrong color). Chests sit at room positions (14|16|18, 69, 24|25|24).
 *
 * Spec: puzzles-chat.md section 2 (statements, positions, edge cases, fixtures).
 *
 * Not built yet: until then it recognises no room and draws nothing.
 */
public final class ThreeWeirdos extends PuzzleSolver {

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.THREE_WEIRDOS;
    }
}
