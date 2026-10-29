package net.alpaka.addons.features.dungeon.device;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.dungeon.DungeonSolver;
import net.alpaka.addons.features.dungeon.SolverContext;
import net.alpaka.addons.features.dungeon.render.SolverColors;
import net.alpaka.addons.features.dungeon.render.SolverDraw;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Sharpshooter (section 4): boxes the lit target (the emerald block among the blue terracotta at
 * Z 50) in the Solution Color and the targets already hit in this attempt in the Wrong Color. The
 * rules for hits are in {@link SharpshooterTargets}.
 *
 * The lit target is read from the blocks every tick, so it is right even after a rejoin. There are
 * no aim points between targets (critic.md C7: that is aim guidance, not a mark on the device);
 * nothing is sent, and the player's aim and shots are their own.
 *
 * Spec: terminals.md 4.4. Absolute F7/M7 boss coordinates; ticked only during the Goldor phase,
 * and draws only while the player is in the device's room.
 */
public final class Sharpshooter extends DungeonSolver {

    /** The room of the device, plate and targets (the bounds Odin uses; a fact of the arena). */
    public static final AABB ROOM = new AABB(20, 100, 30, 89, 151, 51);

    private final SharpshooterTargets targets = new SharpshooterTargets();
    private final List<BlockPos> lit = new ArrayList<>();
    private boolean inRoom = false;

    @Override
    public String name() {
        return "Sharpshooter";
    }

    @Override
    public boolean enabled() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        return cfg.deviceSolversEnabled && cfg.solveSharpshooter;
    }

    @Override
    public void tick(SolverContext ctx) {
        lit.clear();
        inRoom = ROOM.contains(ctx.player().position());
        if (!inRoom) return;
        for (BlockPos pos : SharpshooterTargets.TARGETS) {
            if (ctx.level().getBlockState(pos).is(Blocks.EMERALD_BLOCK)) lit.add(pos);
        }
    }

    @Override
    public void onBlockChange(BlockPos pos, BlockState oldState, BlockState newState) {
        if (pos.equals(SharpshooterTargets.PLATE)) {
            targets.onPlate(platePower(oldState), platePower(newState));
        } else if (SharpshooterTargets.isTarget(pos)) {
            targets.onTarget(pos, oldState.is(Blocks.EMERALD_BLOCK), newState.is(Blocks.EMERALD_BLOCK),
                    newState.is(Blocks.DYED_TERRACOTTA.pick(DyeColor.BLUE)));
        }
    }

    private static int platePower(BlockState state) {
        if (!state.is(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE)) return 0;
        return state.getValue(BlockStateProperties.POWER);
    }

    @Override
    public void render(SolverDraw draw, float partialTick) {
        if (!inRoom) return;
        int hit = SolverColors.wrong();
        for (BlockPos pos : targets.hits()) {
            if (!lit.contains(pos)) draw.block(pos, hit);
        }
        int now = SolverColors.solution();
        for (BlockPos pos : lit) draw.block(pos, now);
    }

    @Override
    public void reset() {
        targets.reset();
        lit.clear();
        inRoom = false;
    }

    @Override
    public boolean claimsRoom() {
        return inRoom;
    }

    @Override
    public String debugState() {
        if (!inRoom) return null;
        Boolean plate = targets.plateOn();
        return (lit.isEmpty() ? "no target lit" : "target " + lit.getFirst().toShortString()) + ", "
                + targets.hitCount() + " hit, plate " + (plate == null ? "unknown" : plate ? "on" : "off");
    }
}
