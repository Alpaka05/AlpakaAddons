package net.alpaka.addons.features.dungeon.puzzle;

/**
 * Higher Or Lower (the blaze room): reads the health in the blazes' name tags and marks the one
 * to kill next and the one after it - lowest health first in the room whose blazes are above Y 69,
 * highest first below it. Needs no room frame.
 *
 * Spec: puzzles-entities.md section 2 (tag format with its U+E07C glyph, order, pairing tag and
 * blaze, edge cases, fixtures; see its Critic additions).
 *
 * Not built yet: until then it recognises no room and draws nothing.
 */
public final class HigherOrLower extends PuzzleSolver {

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.HIGHER_OR_LOWER;
    }
}
