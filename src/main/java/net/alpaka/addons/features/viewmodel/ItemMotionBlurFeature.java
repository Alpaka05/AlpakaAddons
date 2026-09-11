package net.alpaka.addons.features.viewmodel;

import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.PoseStack;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.mixin.RenderTypeInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Motion blur for the first-person held item: while the item swings, faded copies of it are drawn
 * where it was over the last few dozen milliseconds, so it smears into a trail instead of jumping
 * from pose to pose. Nothing changes while the item rests, walks or turns with the camera; the
 * trail only follows the attack swing, which is the motion fast enough to look choppy.
 *
 * How it works: every frame, the hand renderer submits the item as one {@code ItemSubmit} per model
 * layer, each carrying a copy of the layer's final pose. The submits made for the local player's
 * hand are captured (the same way the lighting feature does), and their poses are stored in a short
 * per-hand history stamped with the frame time. Right after the real submit, the same quads are
 * submitted again with poses sampled back along that history - one ghost per step, spaced evenly
 * over the trail length and interpolated between the two recorded frames around each sample time,
 * so the spacing is the same at 60 and at 240 fps. The ghost submits are remembered by identity
 * together with their alpha; when the feature renderer draws one, its render type is swapped for a
 * translucent twin of the item type and its quad colour gets the alpha.
 *
 * The twin is a clone of the vanilla item pipeline that blends but does not write depth. Without
 * that, overlapping ghosts would occlude each other where one is a hair nearer and the smear would
 * break into visible steps. Two clones exist because the lighting feature draws the hand item with
 * a differently-lit pipeline, and a ghost has to match the item it trails. If cloning fails for any
 * reason, the plain vanilla translucent types stand in - a stepped trail beats no trail.
 *
 * Only quads on the vanilla item and block-item sheets are trailed. A layer drawn by a special
 * renderer (banner, shield, head) never reaches the collector as an ItemSubmit and simply gets no
 * ghost, which is far better than an opaque duplicate.
 */
public final class ItemMotionBlurFeature {

    private static final Logger LOGGER = LoggerFactory.getLogger("Alpaka/ItemMotionBlur");

    private ItemMotionBlurFeature() {
    }

    // ---------------------------------------------------------------------------------------------
    // Tuning limits. The config sliders share these so the feature never sees a value it cannot take.
    // ---------------------------------------------------------------------------------------------

    public static final float MIN_LENGTH_MS = 20.0f;
    public static final float MAX_LENGTH_MS = 400.0f;
    public static final int MIN_STEPS = 1;
    public static final int MAX_STEPS = 12;

    /** History kept per hand; a little more than the longest configurable trail. */
    private static final long HISTORY_NANOS = (long) ((MAX_LENGTH_MS + 50.0f) * 1_000_000L);

    /**
     * A gap between recorded frames longer than this means the item was not drawn for a while
     * (empty hand, hidden GUI, a screen without the world). Trailing across it would smear the item
     * from wherever it last was, so the history restarts instead.
     */
    private static final long HISTORY_GAP_RESET_NANOS = 250_000_000L;

    // ---------------------------------------------------------------------------------------------
    // Per-frame capture state (render thread only)
    // ---------------------------------------------------------------------------------------------

    /** Set while the hand renderer submits the local player's first-person item. */
    private static boolean capturing = false;

    /** The hand whose item is being submitted; set right before the submit by the arm renderer. */
    private static int currentHand = 0;

    /** ItemSubmits of this frame's real hand item, one per model layer, in submit order. */
    private static final List<SubmitNodeStorage.ItemSubmit> currentSubmits = new ArrayList<>();

    /** Alpha for the ghost submit being created right now, or negative while none is. */
    private static float pendingGhostAlpha = -1.0f;

    /** Last frame time at which each hand's swing progress was above zero. */
    private static final long[] lastSwingNanos = {Long.MIN_VALUE / 2, Long.MIN_VALUE / 2};

    private static final HandHistory[] histories = {new HandHistory(), new HandHistory()};

    /** Scratch stack whose single pose is overwritten with each ghost pose before submitting. */
    private static final PoseStack GHOST_STACK = new PoseStack();

    // ---------------------------------------------------------------------------------------------
    // Drawing state (feature renderer)
    // ---------------------------------------------------------------------------------------------

    /** Ghost submits of the current frame, by identity, with the alpha to draw them at. */
    private static final Map<Object, Float> GHOST_ALPHA = new IdentityHashMap<>();

    /** Alpha of the ItemSubmit the feature renderer is drawing, or negative for a real item. */
    private static float drawingAlpha = -1.0f;

    private static final int ATLAS_ITEMS = 0;
    private static final int ATLAS_BLOCKS = 1;
    private static final RenderType[] ghostLit = new RenderType[2];
    private static final RenderType[] ghostUnlit = new RenderType[2];

    public static boolean isEnabled() {
        return AlpakaConfig.instance.itemSizeFeatureEnabled && AlpakaConfig.instance.itemMotionBlurEnabled;
    }

    // ---------------------------------------------------------------------------------------------
    // Hooks from ItemInHandRenderer
    // ---------------------------------------------------------------------------------------------

    /**
     * Called right before the arm renderer hands one hand's item to {@code renderItem}, with the
     * swing progress that hand is drawn at (after this mod's own swing tweaks).
     */
    public static void noteArmItem(InteractionHand hand, float swingProgress) {
        currentHand = hand == InteractionHand.OFF_HAND ? 1 : 0;
        if (swingProgress > 0.0f) {
            lastSwingNanos[currentHand] = System.nanoTime();
        }
    }

    /** Called right before ItemInHandRenderer hands the item state to the collector. */
    public static void beginHandItem(LivingEntity entity, ItemDisplayContext context) {
        currentSubmits.clear();
        capturing = isEnabled() && context.firstPerson() && entity == Minecraft.getInstance().player;
    }

    /** Called for every ItemSubmit the collector creates. */
    public static void onItemSubmitted(Object itemSubmit) {
        if (pendingGhostAlpha >= 0.0f) {
            GHOST_ALPHA.put(itemSubmit, pendingGhostAlpha);
        } else if (capturing && itemSubmit instanceof SubmitNodeStorage.ItemSubmit submit) {
            currentSubmits.add(submit);
        }
    }

    /**
     * Called right after the real submit returns. Records this frame's poses and, while the hand
     * is swinging, submits the ghost copies through the same collector.
     */
    public static void endHandItem(SubmitNodeCollector collector) {
        if (!capturing) {
            return;
        }
        capturing = false;
        if (currentSubmits.isEmpty()) {
            return;
        }
        int hand = currentHand;
        long now = System.nanoTime();
        HandHistory history = histories[hand];

        PoseStack.Pose[] poses = new PoseStack.Pose[currentSubmits.size()];
        for (int i = 0; i < poses.length; i++) {
            // The collector already copied the pose for its record; nothing mutates it afterwards.
            poses[i] = currentSubmits.get(i).pose();
        }
        history.record(now, poses);

        AlpakaConfig config = AlpakaConfig.instance;
        long trailNanos = (long) (Mth.clamp(config.itemMotionBlurLength, MIN_LENGTH_MS, MAX_LENGTH_MS) * 1_000_000.0f);
        if (now - lastSwingNanos[hand] > trailNanos) {
            currentSubmits.clear();
            return;
        }

        int steps = Mth.clamp(Math.round(config.itemMotionBlurSteps), MIN_STEPS, MAX_STEPS);
        float opacity = Mth.clamp(config.itemMotionBlurOpacity, 0.0f, 1.0f);
        Frame[] frames = history.snapshot();

        try {
            // Oldest ghost first, so that with blending the newer, stronger copies land on top.
            for (int step = steps; step >= 1; step--) {
                long sampleTime = now - trailNanos * step / steps;
                float alpha = opacity * (1.0f - (step - 1) / (float) steps);
                if (alpha <= 0.0f) {
                    continue;
                }
                for (int layer = 0; layer < poses.length; layer++) {
                    SubmitNodeStorage.ItemSubmit submit = currentSubmits.get(layer);
                    if (!usesVanillaSheets(submit.quads())) {
                        continue;
                    }
                    if (!samplePose(frames, sampleTime, layer, GHOST_STACK.last())) {
                        continue;
                    }
                    pendingGhostAlpha = alpha;
                    collector.submitItem(GHOST_STACK, submit.displayContext(), submit.lightCoords(),
                            submit.overlayCoords(), 0, submit.tintLayers(), submit.quads(),
                            ItemStackRenderState.FoilType.NONE);
                }
            }
        } finally {
            pendingGhostAlpha = -1.0f;
            currentSubmits.clear();
        }
    }

    /**
     * Writes the pose of {@code layer} at {@code time} into {@code out}, interpolated between the
     * two recorded frames around it. False when the history does not reach back that far - a
     * ghost that would have to be guessed is better left out than piled onto the oldest frame.
     */
    private static boolean samplePose(Frame[] frames, long time, int layer, PoseStack.Pose out) {
        if (frames.length == 0 || time < frames[0].nanos) {
            return false;
        }
        int older = frames.length - 1;
        while (older > 0 && frames[older].nanos > time) {
            older--;
        }
        Frame a = frames[older];
        if (older == frames.length - 1 || a.nanos == time) {
            out.set(a.poses[layer]);
            return true;
        }
        Frame b = frames[older + 1];
        float t = (time - a.nanos) / (float) (b.nanos - a.nanos);
        // set() first so the pose keeps its flags; the matrices are then overwritten in place.
        out.set(a.poses[layer]);
        a.poses[layer].pose().lerp(b.poses[layer].pose(), t, out.pose());
        a.poses[layer].normal().lerp(b.poses[layer].normal(), t, out.normal());
        return true;
    }

    private static boolean usesVanillaSheets(List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            RenderType type = quad.materialInfo().itemRenderType();
            if (type != Sheets.cutoutItemSheet() && type != Sheets.translucentItemSheet()
                    && type != Sheets.cutoutBlockItemSheet() && type != Sheets.translucentBlockItemSheet()) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Hooks from ItemFeatureRenderer
    // ---------------------------------------------------------------------------------------------

    /** Called when the feature renderer starts drawing one ItemSubmit. */
    public static void beginDrawing(Object itemSubmit) {
        if (GHOST_ALPHA.isEmpty()) {
            drawingAlpha = -1.0f;
            return;
        }
        Float alpha = GHOST_ALPHA.remove(itemSubmit);
        drawingAlpha = alpha == null ? -1.0f : alpha;
    }

    /** Once the translucent item pass is done; anything left over was never drawn. */
    public static void endFrame() {
        drawingAlpha = -1.0f;
        GHOST_ALPHA.clear();
    }

    /**
     * Render type to draw the quad with: the original for a real item, the translucent ghost twin
     * on the same atlas for a ghost. The lighting feature's own swap runs after this one and leaves
     * the ghost type alone, so whether the ghost is lit or unlit is decided here from its state.
     */
    public static RenderType pickRenderType(RenderType original) {
        if (drawingAlpha < 0.0f) {
            return original;
        }
        boolean unlit = HandItemLightingFeature.isDrawingUnlit();
        if (original == Sheets.cutoutItemSheet() || original == Sheets.translucentItemSheet()) {
            return ghostType(ATLAS_ITEMS, unlit);
        }
        if (original == Sheets.cutoutBlockItemSheet() || original == Sheets.translucentBlockItemSheet()) {
            return ghostType(ATLAS_BLOCKS, unlit);
        }
        return original;
    }

    /** The quad colour the feature renderer is about to use, with the ghost's alpha folded in. */
    public static int applyGhostAlpha(int color) {
        if (drawingAlpha < 0.0f) {
            return color;
        }
        return ARGB.color(Math.round(ARGB.alpha(color) * drawingAlpha), color);
    }

    // ---------------------------------------------------------------------------------------------
    // Ghost render types
    // ---------------------------------------------------------------------------------------------

    private static RenderType ghostType(int atlas, boolean unlit) {
        RenderType[] cache = unlit ? ghostUnlit : ghostLit;
        if (cache[atlas] == null) {
            cache[atlas] = createGhostType(atlas, unlit);
        }
        return cache[atlas];
    }

    private static RenderType createGhostType(int atlas, boolean unlit) {
        Identifier sheet = atlas == ATLAS_ITEMS ? Sheets.ITEMS_MAPPER.sheet() : Sheets.BLOCKS_MAPPER.sheet();
        try {
            if (unlit) {
                // Same pipeline the lighting feature draws the unlit hand item with, minus depth write.
                RenderPipeline pipeline = cloneWithoutDepthWrite(RenderPipelines.BREEZE_WIND, "item_ghost_unlit");
                RenderSetup setup = RenderSetup.builder(pipeline)
                        .withTexture("Sampler0", sheet)
                        .setTextureTransform(new TextureTransform.OffsetTextureTransform(0.0f, 0.0f))
                        .useLightmap()
                        .sortOnUpload()
                        .createRenderSetup();
                return RenderTypeInvoker.alpaka$create("alpaka_item_ghost_unlit", setup);
            }
            RenderPipeline pipeline = cloneWithoutDepthWrite(RenderPipelines.ITEM_TRANSLUCENT, "item_ghost");
            RenderSetup setup = RenderSetup.builder(pipeline)
                    .withTexture("Sampler0", sheet)
                    .useLightmap()
                    .sortOnUpload()
                    .createRenderSetup();
            return RenderTypeInvoker.alpaka$create("alpaka_item_ghost", setup);
        } catch (Throwable t) {
            LOGGER.warn("Could not build the item motion blur render type; falling back to vanilla translucency", t);
            return unlit ? RenderTypes.breezeWind(sheet, 0.0f, 0.0f) : RenderTypes.itemTranslucent(sheet);
        }
    }

    /**
     * A copy of {@code base} that keeps everything - shaders, defines, samplers, uniform blocks,
     * blending, culling - but does not write depth. Built from the public getters only, so it
     * follows whatever the vanilla pipeline is on this version.
     */
    private static RenderPipeline cloneWithoutDepthWrite(RenderPipeline base, String name) {
        DepthStencilState depth = base.getDepthStencilState();
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("alpaka", "pipeline/" + name))
                .withVertexShader(base.getVertexShader())
                .withFragmentShader(base.getFragmentShader())
                .withVertexFormat(base.getVertexFormat(), base.getVertexFormatMode())
                .withPolygonMode(base.getPolygonMode())
                .withCull(base.isCull())
                .withColorTargetState(base.getColorTargetState())
                .withDepthStencilState(new DepthStencilState(depth.depthTest(), false,
                        depth.depthBiasScaleFactor(), depth.depthBiasConstant()));
        for (String sampler : base.getSamplers()) {
            builder.withSampler(sampler);
        }
        for (RenderPipeline.UniformDescription uniform : base.getUniforms()) {
            if (uniform.textureFormat() != null) {
                builder.withUniform(uniform.name(), uniform.type(), uniform.textureFormat());
            } else {
                builder.withUniform(uniform.name(), uniform.type());
            }
        }
        ShaderDefines defines = base.getShaderDefines();
        for (String flag : defines.flags()) {
            builder.withShaderDefine(flag);
        }
        for (Map.Entry<String, String> define : defines.values().entrySet()) {
            String value = define.getValue();
            if (value.contains(".") || value.contains("e") || value.contains("E")) {
                builder.withShaderDefine(define.getKey(), Float.parseFloat(value));
            } else {
                builder.withShaderDefine(define.getKey(), Integer.parseInt(value));
            }
        }
        return builder.build();
    }

    // ---------------------------------------------------------------------------------------------
    // Pose history
    // ---------------------------------------------------------------------------------------------

    /** One frame's item poses, one per model layer. */
    private record Frame(long nanos, PoseStack.Pose[] poses) {
    }

    private static final class HandHistory {
        private final ArrayDeque<Frame> frames = new ArrayDeque<>();

        void record(long now, PoseStack.Pose[] poses) {
            Frame last = frames.peekLast();
            if (last != null && (now - last.nanos > HISTORY_GAP_RESET_NANOS || last.poses.length != poses.length)) {
                frames.clear();
            }
            frames.addLast(new Frame(now, poses));
            long cutoff = now - HISTORY_NANOS;
            while (frames.size() > 1 && frames.peekFirst().nanos < cutoff) {
                frames.pollFirst();
            }
        }

        Frame[] snapshot() {
            return frames.toArray(new Frame[0]);
        }
    }
}
