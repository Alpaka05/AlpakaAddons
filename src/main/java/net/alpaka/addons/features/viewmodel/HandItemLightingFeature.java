package net.alpaka.addons.features.viewmodel;

import net.alpaka.addons.config.AlpakaConfig;
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
 * a "this came from the hand" marker, so {@code ItemInHandRenderer.renderItem} sets
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

    public static boolean isEnabled() {
        return AlpakaConfig.instance.itemSizeFeatureEnabled && AlpakaConfig.instance.itemLightingDisabled;
    }

    /** Called right before ItemInHandRenderer hands the item state to the collector. */
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
        return original;
    }
}
