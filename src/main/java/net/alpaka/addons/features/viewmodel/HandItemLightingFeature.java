package net.alpaka.addons.features.viewmodel;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.mixin.RenderPipelinesAccessor;
import net.alpaka.addons.mixin.RenderTypeInvoker;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Removes the directional ("cardinal") shading from the first-person held item so it stays evenly
 * lit instead of flickering darker and brighter while the hand bobs, sways or the camera turns.
 *
 * The item is not made full-bright: the lightmap still applies, so a torch-lit cave and a sunny
 * field still differ. Only the per-face light computed from the quad normals goes away, and that is
 * exactly the effect the shading flicker comes from.
 *
 * How it works: item quads are handed to the deferred renderer as an {@code ItemSubmit}, and the
 * feature renderer later picks the buffer from each quad's own render type. Neither of those carries
 * a "this came from the hand" marker, so {@code FirstPersonHandsAndItemsRenderer.submitArmWithItem} sets
 * {@link #capturing} for the duration of its submit call, the collector mixin remembers every
 * ItemSubmit created while it is set, and the feature renderer mixin swaps the render type for the
 * remembered submits. The replacement is the breeze wind type: the same entity vertex format and
 * lightmap use as the item types, translucent blending with an alpha cutout, but compiled with
 * NO_CARDINAL_LIGHTING. A zero texture offset makes its texture transform a no-op.
 */
public final class HandItemLightingFeature {

    private HandItemLightingFeature() {
    }

    /** Set while the hand renderer submits the local player's first-person item. */
    private static boolean capturing = false;

    /** ItemSubmits created while {@link #capturing} was set, matched by identity. */
    private static final Set<Object> UNLIT_SUBMITS = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Whether the ItemSubmit currently being drawn by the feature renderer is one of ours. */
    private static boolean drawingUnlit = false;

    /**
     * Unlit twins of the vanilla item sheets, keyed by the sheet they stand in for. Cached because
     * BufferSource keys its buffers by render type, so a fresh instance per quad would open a new
     * buffer every time.
     */
    private static RenderType unlitItemSheet;
    private static RenderType unlitBlockItemSheet;
    private static RenderType unlitItemGlintSheet;
    private static RenderType unlitBlockItemGlintSheet;
    private static RenderPipeline unlitGlintPipeline;

    public static boolean isEnabled() {
        return AlpakaConfig.instance.itemSizeFeatureEnabled && AlpakaConfig.instance.itemLightingDisabled;
    }

    /** Called right before FirstPersonHandsAndItemsRenderer hands the item state to the collector. */
    public static void beginHandItem(LivingEntity entity, ItemDisplayContext context) {
        capturing = isEnabled() && context.firstPerson() && entity == Minecraft.getInstance().player;
    }

    /** Called right after that submit call returns, whether or not anything was captured. */
    public static void endHandItem() {
        capturing = false;
    }

    /** Called for every ItemSubmit the collector creates. */
    public static void onItemSubmitted(Object itemSubmit) {
        if (capturing) {
            UNLIT_SUBMITS.add(itemSubmit);
        }
    }

    /** Called when the feature renderer starts drawing one ItemSubmit. */
    public static void beginDrawing(Object itemSubmit) {
        drawingUnlit = !UNLIT_SUBMITS.isEmpty() && UNLIT_SUBMITS.remove(itemSubmit);
    }

    /** Whether the ItemSubmit currently being drawn is a hand item that should lose its shading. */
    public static boolean isDrawingUnlit() {
        return drawingUnlit;
    }

    /** Called once the translucent item pass is done; anything left over was never drawn. */
    public static void endFrame() {
        drawingUnlit = false;
        UNLIT_SUBMITS.clear();
    }

    /**
     * Render type to draw the quad with; the original unless the current submit is a hand item.
     *
     * Item sprites and block sprites live on two different atlases, and the quad UVs only make
     * sense against the atlas their own render type binds. So the swap is done per vanilla sheet:
     * the two item sheets get an unlit type on the items atlas, the two block-item sheets one on the
     * blocks atlas. Anything else (a mod's own type, a special renderer) is left as it is; a shaded
     * item beats one drawn with the wrong texture.
     */
    public static RenderType pickRenderType(RenderType original) {
        if (!drawingUnlit) {
            return original;
        }
        if (original == Sheets.cutoutItemSheet() || original == Sheets.translucentItemSheet()) {
            if (unlitItemSheet == null) {
                unlitItemSheet = RenderTypes.breezeWind(Sheets.ITEMS_MAPPER.sheet(), 0.0f, 0.0f);
            }
            return unlitItemSheet;
        }
        if (original == Sheets.cutoutBlockItemSheet() || original == Sheets.translucentBlockItemSheet()) {
            if (unlitBlockItemSheet == null) {
                unlitBlockItemSheet = RenderTypes.breezeWind(Sheets.BLOCKS_MAPPER.sheet(), 0.0f, 0.0f);
            }
            return unlitBlockItemSheet;
        }
        // Since 26.3 an enchanted item is drawn once, with a render type that adds the glint in
        // the same shader, instead of a second glint pass over the plain one. Those get an unlit
        // type that draws the glint as well. The special glint of a compass or clock uses another
        // vertex layout and is left shaded.
        if (original == Sheets.cutoutItemGlintSheet() || original == Sheets.translucentItemGlintSheet()) {
            if (unlitItemGlintSheet == null) unlitItemGlintSheet = unlitGlint(Sheets.ITEMS_MAPPER.sheet(), "items");
            return unlitItemGlintSheet != null ? unlitItemGlintSheet : original;
        }
        if (original == Sheets.cutoutBlockItemGlintSheet() || original == Sheets.translucentBlockItemGlintSheet()) {
            if (unlitBlockItemGlintSheet == null) unlitBlockItemGlintSheet = unlitGlint(Sheets.BLOCKS_MAPPER.sheet(), "blocks");
            return unlitBlockItemGlintSheet != null ? unlitBlockItemGlintSheet : original;
        }
        return original;
    }

    /** Builds the glint pipeline up front, so it is precompiled with vanilla's rather than on first use. */
    public static void init() {
        unlitGlintPipeline();
    }

    /**
     * The breeze wind pipeline again - entity shader, no cardinal lighting, translucent with an
     * alpha cutout - with vanilla's glint snippet on top. Without APPLY_TEXTURE_MATRIX: with the
     * glint the texture matrix moves the glint, not the item's own texture.
     */
    private static RenderPipeline unlitGlintPipeline() {
        if (unlitGlintPipeline == null) {
            unlitGlintPipeline = RenderPipelinesAccessor.alpaka$register(
                    RenderPipeline.builder(RenderPipelinesAccessor.alpaka$entitySnippet(), RenderPipelines.GLINT_SNIPPET)
                            .withLocation(Identifier.fromNamespaceAndPath("alpaka", "pipeline/item_unlit_glint"))
                            .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                            .withShaderDefine("NO_OVERLAY")
                            .withShaderDefine("NO_CARDINAL_LIGHTING")
                            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                            .withCull(false)
                            .build());
        }
        return unlitGlintPipeline;
    }

    private static RenderType unlitGlint(Identifier sheet, String name) {
        try {
            return RenderTypeInvoker.alpaka$create("alpaka_item_unlit_glint_" + name,
                    RenderSetup.builder(unlitGlintPipeline())
                            .withTexture("Sampler0", sheet)
                            .withTexture("GlintSampler", ItemFeatureRenderer.ENCHANTED_GLINT_ITEM)
                            .setTextureTransform(TextureTransform.GLINT_TEXTURING)
                            .useLightmap()
                            .sortOnUpload()
                            .createRenderSetup());
        } catch (Throwable t) {
            AlpakaAddons.LOGGER.warn("Could not build the unlit glint render type; enchanted hand items keep their shading", t);
            return null;
        }
    }
}
