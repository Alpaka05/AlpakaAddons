package net.alpaka.addons.features.dungeon.device;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.dungeon.DungeonSolver;
import net.alpaka.addons.features.dungeon.SolverContext;
import net.alpaka.addons.features.dungeon.render.SolverColors;
import net.alpaka.addons.features.dungeon.render.SolverDraw;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;

/**
 * Arrow Align (section 3): matches the arrows in the 5 x 5 item frames at X -2 against the nine
 * known layouts ({@link ArrowAlignPatterns}) and labels each frame with the right-clicks it still
 * needs, a little in front of the frame. A frame that is right shows nothing.
 *
 * The player's own right-clicks on a frame (onOwnEntityUse, observed after vanilla sent them) count
 * the label down before the server's rotation arrives ({@link ArrowAlignPrediction}); that changes
 * only what is drawn. No click is refused, held back or made: turning the arrows is the player's.
 *
 * Spec: terminals.md 4.3 and its Critic additions A. Absolute F7/M7 boss coordinates; ticked only
 * during the Goldor phase, and works only while the player stands at the wall.
 */
public final class ArrowAlign extends DungeonSolver {

    /** Further from where the player stands than this, the wall is not being worked (about Odin's 14 blocks). */
    private static final double REACH = 14.0;
    /** Around the frames' blocks, with room for the frames' own size. */
    private static final AABB FRAMES = new AABB(
            ArrowAlignPatterns.FRAME_X - 1, ArrowAlignPatterns.MIN_Y - 1, ArrowAlignPatterns.MIN_Z - 1,
            ArrowAlignPatterns.FRAME_X + 2, ArrowAlignPatterns.MIN_Y + 6, ArrowAlignPatterns.MIN_Z + 6);
    /** A digit at name tag size is a fifth of a block tall; a little larger reads from the standing spot. */
    private static final float LABEL_SCALE = 1.5f;

    private final ArrowAlignPrediction prediction = new ArrowAlignPrediction();
    /** The rotation each frame shows, from the server; {@link ArrowAlignPatterns#NONE} where no arrow hangs. */
    private final int[] rotations = new int[ArrowAlignPatterns.CELLS];
    /** Right-clicks still needed per frame, own unconfirmed clicks included; 0 = nothing to show. */
    private final int[] clicksLeft = new int[ArrowAlignPatterns.CELLS];
    private boolean atWall = false;
    private int pattern = -1;

    @Override
    public String name() {
        return "Arrow Align";
    }

    @Override
    public boolean enabled() {
        AlpakaConfig cfg = AlpakaConfig.instance;
        return cfg.deviceSolversEnabled && cfg.solveArrowAlign;
    }

    @Override
    public void tick(SolverContext ctx) {
        atWall = ctx.player().distanceToSqr(ArrowAlignPatterns.STAND) <= REACH * REACH;
        if (!atWall) {
            forget();
            return;
        }
        Arrays.fill(rotations, ArrowAlignPatterns.NONE);
        for (ItemFrame frame : ctx.level().getEntitiesOfClass(ItemFrame.class, FRAMES)) {
            int cell = ArrowAlignPatterns.cellAt(frame.getPos());
            if (cell >= 0 && frame.getItem().is(Items.ARROW)) rotations[cell] = frame.getRotation();
        }
        // The layout comes from the server's frames only; a predicted turn never changes which cells hold arrows.
        pattern = ArrowAlignPatterns.match(rotations);
        int[] counted = rotations.clone();
        for (int i = 0; i < counted.length; i++) {
            if (counted[i] != ArrowAlignPatterns.NONE) counted[i] = prediction.rotation(i, counted[i], ctx.nowMs());
        }
        System.arraycopy(ArrowAlignPatterns.clicksLeft(pattern, counted), 0, clicksLeft, 0, clicksLeft.length);
    }

    @Override
    public void onOwnEntityUse(Entity target, InteractionHand hand) {
        if (!atWall || !(target instanceof ItemFrame frame) || !frame.getItem().is(Items.ARROW)) return;
        int cell = ArrowAlignPatterns.cellAt(frame.getPos());
        if (cell >= 0) prediction.onOwnClick(cell, frame.getRotation(), Util.getMillis());
    }

    @Override
    public void render(SolverDraw draw, float partialTick) {
        if (!atWall || pattern < 0) return;
        int color = SolverColors.solution();
        for (int i = 0; i < clicksLeft.length; i++) {
            if (clicksLeft[i] > 0) draw.label(ArrowAlignPatterns.labelPos(i), Integer.toString(clicksLeft[i]), color, LABEL_SCALE);
        }
    }

    @Override
    public void reset() {
        forget();
    }

    private void forget() {
        atWall = false;
        pattern = -1;
        Arrays.fill(rotations, ArrowAlignPatterns.NONE);
        Arrays.fill(clicksLeft, 0);
        prediction.clear();
    }

    @Override
    public boolean claimsRoom() {
        return atWall && pattern >= 0;
    }

    @Override
    public String debugState() {
        if (!atWall) return null;
        int arrows = 0;
        int left = 0;
        for (int i = 0; i < rotations.length; i++) {
            if (rotations[i] != ArrowAlignPatterns.NONE) arrows++;
            left += clicksLeft[i];
        }
        String layout = pattern < 0 ? "no layout matches" : "layout " + pattern;
        return arrows + " arrows, " + layout + ", " + left + " clicks left, "
                + prediction.pendingCount(Util.getMillis()) + " unconfirmed";
    }
}
