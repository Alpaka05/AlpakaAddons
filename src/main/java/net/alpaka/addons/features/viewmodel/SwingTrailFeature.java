package net.alpaka.addons.features.viewmodel;

import com.mojang.blaze3d.vertex.PoseStack;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Swing trail: a sweep drawn in the air behind the tip of the first-person held item while it
 * strikes, a solid crescent of pixels like Minecraft's sweep attack particle.
 *
 * The hand item's submits are captured the way {@link ItemMotionBlurFeature} does it. Each carries
 * the item's final pose - the model's own display transform, the swing, and everything the
 * viewmodel settings add - and its quads. {@link SwingTrailTip} finds the tip and the grip end in
 * the quads, the pose takes both to where they are drawn, and {@link SwingTrailRibbon} draws the
 * sweep along the way the tip takes. Because the tip comes from the model and goes through the
 * same pose as the item, the sweep hangs on the tip at any size, offset and rotation, and its
 * pixels are the size the item's are drawn at.
 *
 * The sweep is kept in the space the arm is placed in, not in the view's. The two differ by what
 * the game does to the whole hand: the bobbing of the view, and the sway that lets the hand lag
 * behind the camera when it turns. Kept in the view's space, turning while striking drew the sway
 * into the sweep - turning fast, right across the screen. Kept with the arm, the sweep moves with
 * the item as one and only the swing itself draws it.
 *
 * It is submitted as plain colored quads. It tests against the hand item's depth but writes none:
 * the item covers the part behind it.
 *
 * An item drawn by a special renderer (a head, a shield) has no quads to find a tip in and gets no
 * trail.
 */
public final class SwingTrailFeature {

    private SwingTrailFeature() {
    }

    // The config sliders share these so the feature never sees a value it cannot take.
    public static final float MIN_LENGTH_MS = 100.0f;
    public static final float MAX_LENGTH_MS = 1000.0f;
    public static final float MIN_WIDTH = 0.25f;
    public static final float MAX_WIDTH = 2.0f;

    /** Set while the hand renderer submits the local player's first-person item. */
    private static boolean capturing = false;

    /** The hand whose item is being submitted and its swing progress; set by the arm renderer. */
    private static int currentHand = 0;
    private static float currentProgress = 0.0f;

    /**
     * The pose the arm renderer was handed this hand in - the view with its bobbing and sway, before
     * anything of the arm or the item - and the way back from the view into it.
     */
    private static final Matrix4f armPose = new Matrix4f();
    private static final Matrix4f fromView = new Matrix4f();

    /** ItemSubmits of this frame's hand item, one per model layer. */
    private static final List<ItemFeatureRenderer.Submit> currentSubmits = new ArrayList<>();

    private static final SwingTrailRibbon[] trails = {new SwingTrailRibbon(), new SwingTrailRibbon()};

    /** The tip each hand's trail was last recorded for; another one means another item. */
    private static final SwingTrailTip[] lastTips = new SwingTrailTip[2];

    /** The sweep is taken to where it is drawn here, so it is submitted without a pose of its own. */
    private static final PoseStack NO_POSE = new PoseStack();

    private static final float[] quads = new float[SwingTrailRibbon.MAX_FLOATS];
    /** Where this frame's tip and grip end are drawn, and how big one pixel of the item is there. */
    private static final float[] point = new float[6];
    private static float texel;
    private static final Vector3f scratch = new Vector3f();
    /** Where the viewer is in the arm's space. */
    private static final Vector3f eye = new Vector3f();

    public static boolean isEnabled() {
        return AlpakaConfig.instance.itemSizeFeatureEnabled && AlpakaConfig.instance.itemSwingTrailEnabled;
    }

    // ---------------------------------------------------------------------------------------------
    // Hooks from ItemInHandRenderer
    // ---------------------------------------------------------------------------------------------

    /** Called when the arm renderer starts on one hand, with the pose it was handed. */
    public static void noteArmPose(Matrix4fc pose) {
        armPose.set(pose);
    }

    /**
     * Called right before the arm renderer hands one hand's item to {@code renderItem}, with the
     * swing progress that hand is drawn at (after this mod's own swing tweaks).
     */
    public static void noteArmItem(InteractionHand hand, float swingProgress) {
        currentHand = hand == InteractionHand.OFF_HAND ? 1 : 0;
        currentProgress = swingProgress;
    }

    /** Called right before ItemInHandRenderer hands the item state to the collector. */
    public static void beginHandItem(LivingEntity entity, ItemDisplayContext context) {
        currentSubmits.clear();
        capturing = isEnabled() && context.firstPerson() && entity == Minecraft.getInstance().player;
    }

    /** Called for every ItemSubmit the collector creates. */
    public static void onItemSubmitted(Object itemSubmit) {
        if (capturing && itemSubmit instanceof ItemFeatureRenderer.Submit submit) {
            currentSubmits.add(submit);
        }
    }

    /**
     * Called right after the real submit returns, before any motion blur ghost is submitted.
     * Records where the tip is this frame and submits the sweep through the same collector.
     */
    public static void endHandItem(SubmitNodeCollector collector) {
        if (!capturing) {
            return;
        }
        capturing = false;
        int hand = currentHand;
        SwingTrailRibbon trail = trails[hand];
        try {
            SwingTrailTip tip = locateTip();
            if (tip == null) {
                return;
            }
            // From the view, where the item's pose ends, into the arm's space.
            armPose.invert(fromView);
            move(fromView, point, 0);
            move(fromView, point, 3);
            fromView.transformPosition(0.0f, 0.0f, 0.0f, eye);
            long now = System.nanoTime();
            if (tip != lastTips[hand]) {
                // Another item: its tip is somewhere else, which is no strike from the old one's.
                trail.interrupt(now);
                lastTips[hand] = tip;
            }
            AlpakaConfig config = AlpakaConfig.instance;
            long lifeNanos = (long) (Mth.clamp(config.itemSwingTrailDuration, MIN_LENGTH_MS, MAX_LENGTH_MS) * 1_000_000.0f);
            trail.update(now, currentProgress, point, texel, lifeNanos);
            if (trail.isEmpty()) {
                return;
            }
            int floats = trail.build(now, texel, lifeNanos, Mth.clamp(config.itemSwingTrailWidth, MIN_WIDTH, MAX_WIDTH),
                    eye.x, eye.y, eye.z, quads);
            if (floats == 0) {
                return;
            }
            // The quads are drawn after this method returns, when the other hand or the next frame
            // may have written its own already; they get a copy of their own, taken to where the
            // arm is in the view this frame.
            float[] vertices = Arrays.copyOf(quads, floats);
            for (int i = 0; i < floats; i += SwingTrailRibbon.VERTEX_FLOATS) {
                move(armPose, vertices, i);
            }
            // Solid, whatever the color's alpha: the sweep is a particle, not a glow.
            int color = ARGB.opaque(config.itemSwingTrailColor);
            int edge = ARGB.scaleRGB(color, SwingTrailRibbon.EDGE_SHADE);
            collector.submitCustomGeometry(NO_POSE, RenderTypes.debugQuads(), (pose, buffer) -> {
                for (int i = 0; i < vertices.length; i += SwingTrailRibbon.VERTEX_FLOATS) {
                    buffer.addVertex(pose, vertices[i], vertices[i + 1], vertices[i + 2])
                            .setColor(vertices[i + 3] < 1.0f ? edge : color);
                }
            });
        } finally {
            currentSubmits.clear();
        }
    }

    /** Takes the position in three floats of an array through a pose. */
    private static void move(Matrix4f pose, float[] floats, int at) {
        pose.transformPosition(floats[at], floats[at + 1], floats[at + 2], scratch);
        floats[at] = scratch.x;
        floats[at + 1] = scratch.y;
        floats[at + 2] = scratch.z;
    }

    /**
     * Finds the tip of this frame's hand item and writes where it and the grip end are drawn into
     * {@link #point}, and the size of one of its pixels there into {@link #texel}. Of an item in
     * several layers, the layer that is longest on screen counts.
     */
    private static SwingTrailTip locateTip() {
        SwingTrailTip chosen = null;
        float longest = 0.0f;
        for (ItemFeatureRenderer.Submit submit : currentSubmits) {
            SwingTrailTip tip = tipOf(submit.quads());
            if (tip == null) {
                continue;
            }
            submit.pose().pose().transformPosition(tip.tipX, tip.tipY, tip.tipZ, scratch);
            float tx = scratch.x, ty = scratch.y, tz = scratch.z;
            submit.pose().pose().transformPosition(tip.gripX, tip.gripY, tip.gripZ, scratch);
            float length = scratch.distanceSquared(tx, ty, tz);
            if (length > longest) {
                longest = length;
                chosen = tip;
                point[0] = tx;
                point[1] = ty;
                point[2] = tz;
                point[3] = scratch.x;
                point[4] = scratch.y;
                point[5] = scratch.z;
                texel = submit.pose().pose().transformDirection(1.0f / 16.0f, 0.0f, 0.0f, scratch).length();
            }
        }
        return chosen;
    }

    // ---------------------------------------------------------------------------------------------
    // Tips by model
    // ---------------------------------------------------------------------------------------------

    /** A model's tip, with what tells the model apart: its first and last quad and their number. */
    private record KnownTip(int size, BakedQuad last, SwingTrailTip tip) {
    }

    /**
     * Tips of the models seen so far, by their first quad. The quad list itself is no key: the
     * hand's render state refills one list with whatever item is held. Baked quads are shared and
     * stay the same objects until the resources are reloaded.
     */
    private static final Map<BakedQuad, KnownTip> KNOWN_TIPS = new IdentityHashMap<>();
    private static final int MAX_KNOWN_TIPS = 64;

    private static SwingTrailTip tipOf(List<BakedQuad> quads) {
        int size = quads.size();
        if (size == 0) {
            return null;
        }
        BakedQuad first = quads.get(0);
        BakedQuad last = quads.get(size - 1);
        KnownTip known = KNOWN_TIPS.get(first);
        if (known != null && known.size == size && known.last == last) {
            return known.tip;
        }
        float[] corners = new float[size * 4 * 3];
        boolean[] rim = new boolean[size * 4];
        int count = 0;
        for (BakedQuad quad : quads) {
            Direction direction = quad.direction();
            boolean sideways = direction != Direction.NORTH && direction != Direction.SOUTH;
            for (int vertex = 0; vertex < 4; vertex++) {
                Vector3fc position = quad.position(vertex);
                corners[count * 3] = position.x();
                corners[count * 3 + 1] = position.y();
                corners[count * 3 + 2] = position.z();
                rim[count] = sideways;
                count++;
            }
        }
        SwingTrailTip tip = SwingTrailTip.locate(corners, rim, count);
        if (KNOWN_TIPS.size() >= MAX_KNOWN_TIPS) {
            // Old models after a resource reload, or simply many items held; start over.
            KNOWN_TIPS.clear();
        }
        KNOWN_TIPS.put(first, new KnownTip(size, last, tip));
        return tip;
    }
}
