// Solution data adapted from Odin by odtheking, BSD-3-Clause, see CREDITS.md
// (Odin's src/main/resources/assets/odin/puzzles/creeper-beams-solutions.json at b412ad1c).
// Copyright (c) 2025, odtheking. Odin's licence, with its conditions and disclaimer, is reproduced
// in full in CREDITS.md.
package net.alpaka.addons.features.dungeon.puzzle;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * The lantern pairs of the Creeper Beams room whose beam passes through the creeper, in room
 * coordinates (X/Z in the room frame, 0..30 with the creeper's column at 15, 15; Y absolute).
 *
 * A run places only some of these lanterns; a pair counts when both of its blocks are there. Odin's
 * table has 13 rows, of which one repeats another and one is another reversed; both are left out
 * here, so each pair is listed once and no lantern belongs to two pairs (puzzles-entities.md 3.3).
 * The first pair starts at the lantern under the creeper itself.
 */
final class CreeperBeamsPairs {

    /** Each pair as {x1, y1, z1, x2, y2, z2}. */
    private static final int[][] ROWS = {
            {15, 74, 15, 15, 84, 13},
            {15, 78, 3, 15, 76, 27},
            {22, 80, 22, 4, 72, 8},
            {24, 77, 7, 5, 76, 24},
            {27, 78, 14, 2, 75, 16},
            {26, 78, 12, 3, 76, 18},
            {25, 79, 21, 4, 75, 9},
            {25, 76, 23, 6, 74, 5},
            {18, 82, 8, 10, 69, 27},
            {18, 81, 21, 9, 69, 3},
            {9, 81, 20, 26, 70, 7},
    };

    /** The pairs, in room coordinates. */
    static final List<BlockPos[]> PAIRS = java.util.Arrays.stream(ROWS)
            .map(r -> new BlockPos[]{new BlockPos(r[0], r[1], r[2]), new BlockPos(r[3], r[4], r[5])})
            .toList();

    private CreeperBeamsPairs() {}
}
