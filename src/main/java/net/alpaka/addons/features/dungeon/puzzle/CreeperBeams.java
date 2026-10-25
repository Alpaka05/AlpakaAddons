package net.alpaka.addons.features.dungeon.puzzle;

/**
 * Creeper Beams: pairs the room's sea lanterns so that each pair's beam passes through the
 * creeper, and marks the pairs still to connect. A lantern pair turns into prismarine once done.
 *
 * Spec: puzzles-entities.md section 3 (Odin's pair table, BSD-3 with attribution, or the geometric
 * pairing; what to draw, fixtures).
 *
 * Not built yet: until then it recognises no room and draws nothing.
 */
public final class CreeperBeams extends PuzzleSolver {

    @Override
    public DungeonPuzzle puzzle() {
        return DungeonPuzzle.CREEPER_BEAMS;
    }
}
