package net.alpaka.addons.features.dungeon.device;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.dungeon.DungeonSolver;
import net.alpaka.addons.features.dungeon.SolverContext;
import net.alpaka.addons.features.dungeon.render.SolverColors;
import net.alpaka.addons.features.dungeon.render.SolverDraw;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Lights (section 2): boxes the levers on the wall at Z 142 that are still off, in the Solution
 * Color. The device is done when all six are on, so every lever that is off is one to flip.
 *
 * Which model is current is still open (terminals.md Critic additions B): this follows the six
 * levers that all have to be on, which the maintained 26.2 references use. The community wiki
 * describes a 5 x 4 lamp grid where a lever also switches its neighbours instead; if a real run
 * shows that, the levers to flip would have to be worked out from the lamps.
 *
 * Read from the blocks every tick, so it is right however the levers got the way they are (the
 * server decides; the client does not switch a lever it clicks). Nothing is refused or sent.
 *
 * Spec: terminals.md 4.2. Absolute F7/M7 boss coordinates; ticked only during the Goldor phase,
 * and draws only while the player is at the device.
 */
public final class LightsDevice extends DungeonSolver {

    /** The six levers, top row first, each row from the player's left (terminals.md 4.2). */
    public static final List<BlockPos> LEVERS = List.of(
            new BlockPos(62, 136, 142), new BlockPos(58, 136, 142),
            new BlockPos(60, 135, 142),
            new BlockPos(60, 134, 142),
            new BlockPos(62, 133, 142), new BlockPos(58, 133, 142));
    /** The middle of the levers. */
    public static final Vec3 DEVICE = new Vec3(60.5, 134.5, 142.5);
    /** Further than this the player is not at the device. */
    private static final double REACH = 16.0;
    private static final double BOX_MARGIN = 0.02;

    private final List<AABB> off = new ArrayList<>();
    private boolean atDevice = false;
    private int levers = 0;

    @Override
    public String name() {
        return "Lights";
    }

    @Override
    public boolean enabled() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        return cfg.deviceSolversEnabled && cfg.solveLightsDevice;
    }

    @Override
    public void tick(SolverContext ctx) {
        off.clear();
        levers = 0;
        atDevice = ctx.player().distanceToSqr(DEVICE) <= REACH * REACH;
        if (!atDevice) return;
        ClientLevel level = ctx.level();
        for (BlockPos pos : LEVERS) {
            BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.LEVER)) continue;
            levers++;
            if (!state.getValue(LeverBlock.POWERED)) off.add(leverBox(level, state, pos));
        }
    }

    private static AABB leverBox(ClientLevel level, BlockState state, BlockPos pos) {
        VoxelShape shape = state.getShape(level, pos);
        AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);
        return box.inflate(BOX_MARGIN);
    }

    @Override
    public void render(SolverDraw draw, float partialTick) {
        if (!atDevice) return;
        int color = SolverColors.solution();
        for (AABB box : off) draw.box(box, color);
    }

    @Override
    public void reset() {
        off.clear();
        atDevice = false;
        levers = 0;
    }

    @Override
    public boolean claimsRoom() {
        return atDevice && levers > 0;
    }

    @Override
    public String debugState() {
        if (!atDevice) return null;
        return levers + " of " + LEVERS.size() + " levers found, " + off.size() + " off";
    }
}
