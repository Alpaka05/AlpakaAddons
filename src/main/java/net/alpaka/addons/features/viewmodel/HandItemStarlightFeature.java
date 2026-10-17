package net.alpaka.addons.features.viewmodel;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.mixin.RenderPipelinesAccessor;
import net.alpaka.addons.mixin.RenderTypeInvoker;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Starlight: a drifting, twinkling galaxy seen through the first-person held item, as if the item
 * were a window into space.
 *
 * It covers every held item, or - with "Replace Enchant Glint" - takes the glint's place: only an
 * item vanilla would make glint gets it, and its glint is left out.
 *
 * The item's quads are drawn a second time on top of it, the way vanilla draws the enchantment
 * glint: same geometry, a depth test that only passes where the item itself is the nearest
 * surface, no depth writes. The starlight shader paints a nebula and three layers of stars there
 * ({@code alpaka:core/starlight}). The galaxy hangs in the sky around the player, not on the item:
 * every pixel of the item shows the part of it that lies in the direction the pixel is seen in.
 * Turning the camera sweeps the galaxy past the item, swinging or bobbing the item moves it over
 * the galaxy, as if the item were a window into space. The camera's angles go to the shader in the
 * vertices' UV, so it can turn the view direction into a direction in the world.
 *
 * Works the same with "Disable Item Lighting": that swaps the item's render type, but the item
 * still goes through vanilla's entity vertex shader, whose depth the starlight pass matches.
 *
 * The hand item's submits are told apart the way {@link HandItemLightingFeature} does it: the
 * hand renderer flags its submit call, and every item submit created meanwhile is remembered by
 * identity. Motion blur ghosts are submitted after the flag is cleared, so they trail plain.
 */
public final class HandItemStarlightFeature {

    private HandItemStarlightFeature() {
    }

    /** Set while the hand renderer submits the local player's first-person item. */
    private static boolean capturing = false;

    /** Item submits created while {@link #capturing} was set, matched by identity. */
    private static final Set<Object> STARLIT_SUBMITS = Collections.newSetFromMap(new IdentityHashMap<>());

    private static RenderType renderType;

    /** The modes, by {@link AlpakaConfig#itemStarlightMode}. */
    public static final String[] MODE_NAMES = {"Every Item", "Replace Enchant Glint"};
    private static final int MODE_REPLACE_GLINT = 1;

    private static boolean replacesGlint() {
        return AlpakaConfig.instance.itemStarlightMode == MODE_REPLACE_GLINT;
    }

    /**
     * Whether the starlight pass draws over this submit: every hand item, or with "Replace Enchant
     * Glint" only those vanilla would give a glint.
     */
    public static boolean drawsOver(Object itemSubmit, boolean glinted) {
        return isStarlit(itemSubmit) && (glinted || !replacesGlint());
    }

    /** Whether vanilla's glint is to be left out on this submit, the starlight standing in for it. */
    public static boolean hidesGlint(Object itemSubmit) {
        return replacesGlint() && isStarlit(itemSubmit);
    }

    public static boolean isEnabled() {
        return AlpakaConfig.instance.itemSizeFeatureEnabled && AlpakaConfig.instance.itemStarlightEnabled;
    }

    /** Builds the pipeline up front, so it is compiled with vanilla's rather than on first use. */
    public static void init() {
        renderType();
    }

    /** Called right before ItemInHandRenderer hands the item state to the collector. */
    public static void beginHandItem(LivingEntity entity, ItemDisplayContext context) {
        capturing = isEnabled() && context.firstPerson() && entity == Minecraft.getInstance().player;
    }

    /** Called right after that submit call returns, before any motion blur ghost is submitted. */
    public static void endHandItem() {
        capturing = false;
    }

    /** Called for every item submit the collector creates. */
    public static void onItemSubmitted(Object itemSubmit) {
        if (capturing) {
            STARLIT_SUBMITS.add(itemSubmit);
        }
    }

    /** Whether the feature renderer is to draw starlight over this item submit. */
    public static boolean isStarlit(Object itemSubmit) {
        return !STARLIT_SUBMITS.isEmpty() && STARLIT_SUBMITS.contains(itemSubmit);
    }

    /** At the start of the next hand render; anything left over was drawn or never will be. */
    public static void endFrame() {
        STARLIT_SUBMITS.clear();
    }

    public static RenderType renderType() {
        if (renderType == null) {
            RenderPipeline pipeline = RenderPipelinesAccessor.alpaka$register(
                    RenderPipeline.builder()
                            .withLocation(Identifier.fromNamespaceAndPath("alpaka", "pipeline/hand_item_starlight"))
                            .withVertexShader(Identifier.fromNamespaceAndPath("alpaka", "core/starlight"))
                            .withFragmentShader(Identifier.fromNamespaceAndPath("alpaka", "core/starlight"))
                            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                            .withCull(false)
                            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
                            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                            .withPrimitiveTopology(PrimitiveTopology.QUADS)
                            // Like the glint: only where the item itself is the nearest surface.
                            .withDepthStencilState(new DepthStencilState(CompareOp.EQUAL, false))
                            .build());
            renderType = RenderTypeInvoker.alpaka$create("alpaka_hand_item_starlight",
                    RenderSetup.builder(pipeline).createRenderSetup());
        }
        return renderType;
    }

    /** The color and strength the starlight vertices carry, from the settings. */
    private static int vertexColor() {
        float strength = Mth.clamp(AlpakaConfig.instance.itemStarlightStrength, 0.0f, 1.0f);
        return ARGB.color(Math.round(strength * 255.0f), AlpakaConfig.instance.itemStarlightColor);
    }

    /** A buffer for the starlight pass over the hand item's quads. */
    public static VertexConsumer starlightBuffer(VertexConsumer delegate) {
        // The same angles Camera.setRotation builds its rotation from: rotationYXZ(yaw, pitch, 0)
        // takes a view space direction into the world.
        Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
        float yaw = Mth.PI - camera.yRot() * Mth.DEG_TO_RAD;
        float pitch = -camera.xRot() * Mth.DEG_TO_RAD;
        return new StarlightBuffer(delegate, vertexColor(), yaw, pitch);
    }

    /**
     * Keeps the item's vertex positions as they are put into the buffer and gives every vertex the
     * starlight's color and the camera's angles; everything else the item carries (texture, light,
     * normal) is dropped.
     */
    private static final class StarlightBuffer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int color;
        private final float yaw;
        private final float pitch;

        StarlightBuffer(VertexConsumer delegate, int color, float yaw, float pitch) {
            this.delegate = delegate;
            this.color = color;
            this.yaw = yaw;
            this.pitch = pitch;
        }

        @Override
        public void addVertex(float x, float y, float z, int vertexColor, float u, float v,
                              int overlayCoords, int lightCoords, float nx, float ny, float nz) {
            delegate.addVertex(x, y, z).setUv(yaw, pitch).setColor(color);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return delegate.addVertex(x, y, z);
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }
}
