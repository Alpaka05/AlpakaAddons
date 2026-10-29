package net.alpaka.addons.features.dungeon.device;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.dungeon.DungeonSolver;
import net.alpaka.addons.features.dungeon.SolverContext;
import net.alpaka.addons.features.dungeon.render.SolverColors;
import net.alpaka.addons.features.dungeon.render.SolverDraw;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashSet;
import java.util.Set;

/**
 * Simon Says (section 1): remembers the sequence the 4 x 4 display at X 111 lights up and boxes
 * the button to press now in the Solution Color and the one after it in the Next Color. The rules
 * (what is recorded, when a round starts over, which rounds are not shown) are in
 * {@link SimonSaysSequence}; this class feeds it the server's block changes and draws.
 *
 * Presses come from two places, and either is enough: the server reporting a button as pressed,
 * and the player's own click on a button (observed after vanilla made it). The client marks a
 * button it clicks as pressed before the server answers, so the server's report is compared with
 * what the server said before, not with the block the client shows. Teammates' presses only count
 * if the server sends them; it may not (compliance.md D4: no guessing them).
 *
 * Only marks: no sound per step, no chat, and no click on the device is ever refused.
 *
 * Spec: terminals.md 4.1. Absolute F7/M7 boss coordinates; ticked only during the Goldor phase,
 * and draws only while the player is at the device.
 */
public final class SimonSays extends DungeonSolver {

    /** The middle of the button wall. */
    private static final Vec3 DEVICE = new Vec3(110.5, 121.5, 94.0);
    /** Further than this the player is not at the device; the buttons are pressed from a few blocks away. */
    private static final double REACH = 16.0;
    /** Boxes stand this far off the button, so the button's own faces do not cover them. */
    private static final double BOX_MARGIN = 0.02;

    private final SimonSaysSequence sequence = new SimonSaysSequence();
    /** The buttons (and start button) the server last reported as pressed. */
    private final Set<BlockPos> pressedByServer = new HashSet<>();
    private boolean atDevice = false;
    private AABB nextBox = null;
    private AABB afterNextBox = null;

    @Override
    public String name() {
        return "Simon Says";
    }

    @Override
    public boolean enabled() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        return cfg.deviceSolversEnabled && cfg.solveSimonSays;
    }

    @Override
    public void tick(SolverContext ctx) {
        atDevice = ctx.player().distanceToSqr(DEVICE) <= REACH * REACH;
        // Only a button that is there gets a box: while the display plays, the buttons are gone.
        nextBox = atDevice ? buttonBox(ctx.level(), sequence.nextButton()) : null;
        afterNextBox = atDevice ? buttonBox(ctx.level(), sequence.afterNextButton()) : null;
    }

    private static AABB buttonBox(ClientLevel level, BlockPos button) {
        if (button == null) return null;
        BlockState state = level.getBlockState(button);
        if (!state.is(Blocks.STONE_BUTTON)) return null;
        VoxelShape shape = state.getShape(level, button);
        AABB box = shape.isEmpty() ? new AABB(button) : shape.bounds().move(button);
        return box.inflate(BOX_MARGIN);
    }

    @Override
    public void onBlockChange(BlockPos pos, BlockState oldState, BlockState newState) {
        if (!SimonSaysSequence.isDevice(pos)) return;
        if (SimonSaysSequence.isDisplay(pos)) {
            if (oldState.is(Blocks.SEA_LANTERN) && newState.is(Blocks.OBSIDIAN)) sequence.lanternShown(pos);
            return;
        }
        boolean wasButton = oldState.is(Blocks.STONE_BUTTON);
        boolean isButton = newState.is(Blocks.STONE_BUTTON);
        if (SimonSaysSequence.isButton(pos)) {
            if (wasButton && !isButton) sequence.buttonsHidden();
            else if (!wasButton && isButton) sequence.buttonsShown();
        }
        if (!isButton || !newState.getValue(ButtonBlock.POWERED)) {
            pressedByServer.remove(pos);
            return;
        }
        // Pressed, and the server had not said so before: a new press.
        if (!pressedByServer.add(pos)) return;
        press(pos);
    }

    @Override
    public void onOwnBlockUse(BlockPos pos, InteractionHand hand) {
        if (SimonSaysSequence.isButton(pos) || pos.equals(SimonSaysSequence.START_BUTTON)) press(pos);
    }

    private void press(BlockPos pos) {
        if (pos.equals(SimonSaysSequence.START_BUTTON)) sequence.start();
        else sequence.pressed(pos);
    }

    @Override
    public void render(SolverDraw draw, float partialTick) {
        if (!atDevice) return;
        if (afterNextBox != null) draw.box(afterNextBox, SolverColors.next());
        if (nextBox != null) draw.box(nextBox, SolverColors.solution());
    }

    @Override
    public void reset() {
        sequence.reset();
        pressedByServer.clear();
        atDevice = false;
        nextBox = null;
        afterNextBox = null;
    }

    @Override
    public boolean claimsRoom() {
        return atDevice;
    }

    @Override
    public String debugState() {
        if (sequence.sequence().isEmpty() && !atDevice) return null;
        return "round of " + sequence.sequence().size() + ", " + sequence.progress() + " pressed, "
                + (sequence.trusted() ? "shown" : "not shown") + (sequence.firstRound() ? ", first round" : "");
    }
}
