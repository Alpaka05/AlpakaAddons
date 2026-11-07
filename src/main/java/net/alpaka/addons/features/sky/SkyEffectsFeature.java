package net.alpaka.addons.features.sky;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.mixin.RenderPipelinesAccessor;
import net.alpaka.addons.utils.SkyblockUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.SplittableRandom;

/**
 * Sky Effects: animated skies. A firestorm over the Nether, which vanilla gives no sky at all; an
 * aurora, the Milky Way and shooting stars over the Overworld by night and drifting clouds and a
 * glowing sun by day;
 * a drifting nebula in place of the End's textured sky.
 *
 * Each is painted by the shader {@code alpaka:core/sky_effects} on a box around the camera, the
 * box vanilla draws the End's sky on: it is centered on the camera and turns with the view, so
 * every pixel of it lies in the direction that pixel looks, and the shader paints the sky from that
 * direction and the time alone - no seams, and the same cost at any render distance. Its only
 * texture is a small grid of random values the noise is built from ({@link #noise}).
 * Its parameters ride in vanilla's DynamicTransforms uniform, which every draw fills anyway; the
 * shader's header lists them.
 *
 * Where each goes in vanilla's frame:
 * - Nether: where a dimension has no skybox, vanilla adds no sky pass and the frame keeps the fog
 *   color it was cleared with; the storm goes over that ({@link #beforeSkyPass}). It is built from
 *   the fog's color, so it matches each biome.
 * - Overworld: over vanilla's sky, sun, moon and stars ({@link #renderOverworld}), its lights
 *   added and its clouds laid over them.
 * - End: in place of the textured sky ({@link #renderEnd}). SkyBlock's End island gets it too,
 *   though Hypixel sends that island as an Overworld-like dimension: there the sky is switched to
 *   the End's before vanilla draws it, without the Overworld's clouds, and the air fogs into the
 *   End's color ({@link #endSkyOnEndIsland}).
 *
 * When: the shader is the costly part, and vanilla draws its sky before the world, on every pixel,
 * even those the world then covers. So each sky is only asked for where vanilla would draw it,
 * and drawn later, right after the opaque world ({@link #afterOpaqueWorld}), on the far plane
 * behind a depth test: only where the sky still shows. The End's flashes, drawn over its sky, keep
 * it in place while they light up. That point is only taken inside the world's frame
 * ({@link #levelFrameStart}): the hand and items in menus pass through the same code. Should
 * another mod render the world without passing it, a sky asked for is dropped at the frame's end,
 * and after a few such frames in a row they are drawn in place again.
 *
 * Where vanilla shows no sky - under lava, in powder snow, blinded, or in the fog a boss bar like
 * the Ender Dragon's brings - none of them show either: the Overworld's and the End's are asked
 * for from inside vanilla's sky pass, and the Nether's from where that pass is added, making the
 * same checks.
 */
public final class SkyEffectsFeature {
    /** Half the edge of the box the skies are drawn on, the size of vanilla's End sky box. */
    private static final float BOX = 100.0f;

    /** The corners of a face of the box, in order around it, on the face's two other axes. */
    private static final float[][] FACE_CORNERS = {{-1.0f, -1.0f}, {1.0f, -1.0f}, {1.0f, 1.0f}, {-1.0f, 1.0f}};

    /** The End's own fog color, its dimension's {@code visual/fog_color}. */
    public static final int END_FOG_COLOR = 0xFF181318;

    private static final SkyClock CLOCK = new SkyClock();

    private static RenderPipeline nether;
    private static RenderPipeline overworld;
    private static RenderPipeline end;
    private static GpuBuffer box;
    private static DynamicTexture noise;

    /** The edge of the noise grid, as the shader divides by it. */
    private static final int NOISE_SIZE = 256;

    /** A sky asked for and not yet drawn: its shader, its parameters and the view it was seen in. */
    private record Pending(RenderPipeline pipeline, float night, float clear, Matrix4f directions, Matrix4f view) {}

    /** How many world frames in a row may drop their sky before skies are drawn in place. */
    private static final int MISSES_ALLOWED = 3;

    private static Pending pending;
    /** Whether a frame of the world is being rendered, the only time skies are drawn late. */
    private static boolean inLevelFrame;
    /** World frames in a row whose sky was asked for and never drawn. */
    private static int misses;
    /** Whether skies wait for the opaque world; false once too many were never drawn there. */
    private static boolean afterWorld = true;
    /** Whether the End's flashes light up this frame, which are drawn over its sky in vanilla's pass. */
    private static boolean endFlashing;

    private SkyEffectsFeature() {}

    /** Registers the pipelines up front, so the shader is compiled with vanilla's rather than on first use. */
    public static void init() {
        nether();
        overworld();
        end();
    }

    /** Whether this sky is animated: Sky Effects is on, and so is the sky's own switch. */
    private static boolean shows(boolean sky) {
        return AlpakaConfig.instance.skyEffectsEnabled && sky;
    }

    /**
     * Whether the End's sky stands in for the Overworld's here: on SkyBlock's End island, which
     * Hypixel sends as an Overworld-like dimension, so vanilla would draw a blue sky with a sun and
     * clouds over it.
     */
    public static boolean endSkyOnEndIsland(ClientLevel level) {
        return shows(AlpakaConfig.instance.skyEffectsEnd) && level != null
                && level.dimensionType().skybox() == DimensionType.Skybox.OVERWORLD
                && SkyblockUtils.isOnEndIsland();
    }

    /**
     * Called where vanilla adds its sky pass, before it looks at the sky: switches SkyBlock's End
     * island to the End's sky, and adds the Nether's pass.
     */
    public static void beforeSkyPass(FrameGraphBuilder frame, LevelTargetBundle targets, LevelRenderState level,
                                     CameraRenderState camera, GpuBufferSlice fog) {
        SkyRenderState sky = level.skyRenderState;
        endFlashing = sky.endFlashIntensity > 1.0e-5f;
        if (sky.skybox == DimensionType.Skybox.OVERWORLD && endSkyOnEndIsland(Minecraft.getInstance().level)) {
            // From here on vanilla draws the End's sky, so no sun, moon or stars. A clear cloud
            // color is how vanilla itself leaves out the clouds of a dimension without any, and an
            // End flash belongs to a real End only.
            sky.skybox = DimensionType.Skybox.END;
            sky.endFlashIntensity = 0.0f;
            level.cloudColor = 0;
        }
        addNetherPass(frame, targets, sky, camera, fog);
    }

    /**
     * Adds the Nether's sky pass, where vanilla adds none because the dimension has no skybox.
     */
    private static void addNetherPass(FrameGraphBuilder frame, LevelTargetBundle targets, SkyRenderState sky,
                                      CameraRenderState camera, GpuBufferSlice fog) {
        if (sky.skybox != DimensionType.Skybox.NONE || !shows(AlpakaConfig.instance.skyEffectsNether)) return;
        // Where vanilla would show no sky either.
        if (camera.fogType == FogType.LAVA || camera.fogType == FogType.POWDER_SNOW
                || camera.entityRenderState.doesMobEffectBlockSky) return;
        // The main pass sets this same fog before it draws the world.
        if (afterWorld && request(nether(), 0.0f, 1.0f, new Matrix4f())) return;
        FramePass pass = frame.addPass("alpaka_sky");
        targets.main = pass.readsAndWrites(targets.main);
        ResourceHandle<RenderTarget> main = targets.main;
        pass.executes(() -> {
            RenderSystem.setShaderFog(fog);
            draw(nether(), main.get(), 0.0f, 1.0f, new Matrix4f(), RenderSystem.getModelViewMatrixCopy());
        });
    }

    /**
     * Adds the Overworld's effects over vanilla's sky, with the angles vanilla has just drawn the
     * sun, moon and stars at and how clear the sky is (1 dry, 0 in heavy rain).
     */
    public static void renderOverworld(RenderTarget target, float sunAngle, float moonAngle, float starAngle,
                                       float rainBrightness) {
        boolean lights = shows(AlpakaConfig.instance.skyEffectsOverworld);
        boolean clouds = shows(AlpakaConfig.instance.skyEffectsClouds);
        // In heavy rain the lights are hidden entirely.
        if (!clouds && (!lights || rainBrightness <= 0.0f)) return;
        Vector3f sun = towards(sunAngle);
        Vector3f moon = towards(moonAngle);
        // Night by the sun's height rather than the stars' brightness, which a server's own
        // dimension may leave at nothing: from the sun touching the horizon to a while after.
        float dusk = Mth.clamp((0.1f - sun.y) / 0.4f, 0.0f, 1.0f);
        float night = dusk * dusk * (3.0f - 2.0f * dusk);
        Matrix4f directions = new Matrix4f(
                sun.x, sun.y, sun.z, 0.0f,
                moon.x, moon.y, moon.z, 0.0f,
                starAngle, 0.0f, 0.0f, 0.0f,
                lights ? 1.0f : 0.0f, clouds ? 1.0f : 0.0f, 0.0f, 0.0f);
        drawOrRequest(overworld(), target, night, Mth.clamp(rainBrightness, 0.0f, 1.0f), directions);
    }

    /** Draws the End's sky in place of vanilla's. False when that sky is off and vanilla's is to be drawn. */
    public static boolean renderEnd(RenderTarget target) {
        if (!shows(AlpakaConfig.instance.skyEffectsEnd)) return false;
        // The flashes are drawn over the sky in this pass; a sky drawn later would hide them.
        if (endFlashing) {
            draw(end(), target, 0.0f, 1.0f, new Matrix4f(), RenderSystem.getModelViewMatrixCopy());
        } else {
            drawOrRequest(end(), target, 0.0f, 1.0f, new Matrix4f());
        }
        return true;
    }

    /** Asks for a sky to be drawn after the opaque world, or draws it now where that does not work. */
    private static void drawOrRequest(RenderPipeline pipeline, RenderTarget target, float night, float clear,
                                      Matrix4f directions) {
        if (!afterWorld || !request(pipeline, night, clear, directions)) {
            draw(pipeline, target, night, clear, directions, RenderSystem.getModelViewMatrixCopy());
        }
    }

    /** Asks for a sky to be drawn after the opaque world of this frame. False outside a world frame. */
    private static boolean request(RenderPipeline pipeline, float night, float clear, Matrix4f directions) {
        if (!inLevelFrame) return false;
        pending = new Pending(pipeline, night, clear, directions, RenderSystem.getModelViewMatrixCopy());
        return true;
    }

    /** The start of LevelRenderer.render: the world's frame. */
    public static void levelFrameStart() {
        inLevelFrame = true;
        pending = null;
    }

    /**
     * The end of LevelRenderer.render. A sky asked for and not drawn is dropped, never carried into
     * another frame; when that happens frame after frame, nothing draws them late, so from then on
     * they are drawn in place.
     */
    public static void levelFrameEnd() {
        inLevelFrame = false;
        if (pending == null) return;
        pending = null;
        if (++misses >= MISSES_ALLOWED) afterWorld = false;
    }

    /**
     * Right after the opaque world: draws the sky asked for this frame, where nothing covers it.
     * The main pass has its fog and projection set by then. The hand and items in menus pass
     * through here too, outside the world's frame, and are left alone.
     */
    public static void afterOpaqueWorld() {
        Pending sky = pending;
        if (sky == null || !inLevelFrame) return;
        pending = null;
        misses = 0;
        draw(sky.pipeline(), Minecraft.getInstance().gameRenderer.mainRenderTarget(), sky.night(), sky.clear(),
                sky.directions(), sky.view());
    }

    /**
     * Towards a body vanilla puts in the sky at this angle: it turns the sky -90 degrees about Y,
     * then by the angle about X, and draws the sun and the moon straight up from there.
     */
    private static Vector3f towards(float angle) {
        return new Vector3f(0.0f, 1.0f, 0.0f).rotateX(angle).rotateY(-Mth.HALF_PI);
    }

    private static void draw(RenderPipeline pipeline, RenderTarget target, float night, float clear, Matrix4f directions,
                             Matrix4f view) {
        float time = CLOCK.advance(System.nanoTime(), Mth.clamp(AlpakaConfig.instance.skyEffectsSpeed, 0.25f, 3.0f));
        float intensity = Mth.clamp(AlpakaConfig.instance.skyEffectsIntensity, 0.1f, 1.0f);
        // The far plane in clip space, where the vertex shader puts the box (see the shader).
        float far = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 0.0f : -1.0f;
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                view,
                new Vector4f(far, 1.0f, 1.0f, intensity),
                new Vector3f(time, night, clear),
                directions);
        // Both buffers before the pass opens, as vanilla's sky does.
        GpuBuffer vertices = box();
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(36);
        DynamicTexture grid = noise();
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Alpaka sky",
                target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("Sampler0", grid.getTextureView(), RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertices.slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(36, 1, 0, 0, 0);
        }
    }

    /**
     * The grid the shader's noise is built from: random values at the corners of 256x256 cells
     * that wrap around, which the texture filter blends; a lookup costs far less than the hashes
     * the shader would work them out with. Red and blue are two independent grids, green is red
     * moved by (37, 17), so a 3D noise finds two slices of its cell in one lookup. Always the
     * same grid, so the skies look the same every time.
     */
    private static DynamicTexture noise() {
        if (noise == null) {
            SplittableRandom random = new SplittableRandom(0x5C1E5L);
            int[] red = new int[NOISE_SIZE * NOISE_SIZE];
            int[] blue = new int[NOISE_SIZE * NOISE_SIZE];
            for (int i = 0; i < red.length; i++) {
                red[i] = random.nextInt(256);
                blue[i] = random.nextInt(256);
            }
            NativeImage image = new NativeImage(NOISE_SIZE, NOISE_SIZE, false);
            int mask = NOISE_SIZE - 1;
            for (int y = 0; y < NOISE_SIZE; y++) {
                for (int x = 0; x < NOISE_SIZE; x++) {
                    int r = red[y * NOISE_SIZE + x];
                    int g = red[((y + 17) & mask) * NOISE_SIZE + ((x + 37) & mask)];
                    int b = blue[y * NOISE_SIZE + x];
                    image.setPixelABGR(x, y, 0xFF000000 | b << 16 | g << 8 | r);
                }
            }
            noise = new DynamicTexture(() -> "Alpaka sky noise", image);
            noise.upload();
        }
        return noise;
    }

    /** The box the skies are drawn on: six faces at {@link #BOX} around the camera. */
    private static GpuBuffer box() {
        if (box == null) {
            VertexFormat format = DefaultVertexFormat.POSITION;
            try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(24 * format.getVertexSize())) {
                BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, format);
                float[] corner = new float[3];
                for (int axis = 0; axis < 3; axis++) {
                    for (float side = -BOX; side <= BOX; side += 2.0f * BOX) {
                        for (float[] face : FACE_CORNERS) {
                            corner[axis] = side;
                            corner[(axis + 1) % 3] = face[0] * BOX;
                            corner[(axis + 2) % 3] = face[1] * BOX;
                            builder.addVertex(corner[0], corner[1], corner[2]);
                        }
                    }
                }
                try (MeshData mesh = builder.buildOrThrow()) {
                    box = RenderSystem.getDevice().createBuffer(() -> "Alpaka sky box",
                            GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, mesh.vertexBuffer());
                }
            }
        }
        return box;
    }

    private static RenderPipeline nether() {
        if (nether == null) nether = register("sky_nether", "SKY_NETHER", null);
        return nether;
    }

    /**
     * Light added over vanilla's sky, and clouds laid over it: blended premultiplied, so the
     * shader takes away only as much of the sky as its clouds cover.
     */
    private static RenderPipeline overworld() {
        if (overworld == null) {
            overworld = register("sky_overworld", "SKY_OVERWORLD", BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA);
        }
        return overworld;
    }

    private static RenderPipeline end() {
        if (end == null) end = register("sky_end", "SKY_END", null);
        return end;
    }

    /**
     * One shader, built once for each sky with that sky's define, with the uniforms vanilla's own
     * sky gets. Seen from inside the box. Unlike vanilla's sky it is depth tested: the box lies on
     * the far plane, so it shows only where the depth buffer is still clear. Drawn after the
     * opaque world, the GPU skips every pixel the world covers before the shader runs; drawn in
     * vanilla's sky pass it passes everywhere, but where a mod draws the world first (the
     * maintainer's OneClient setup showed the aurora over distant blocks) it still never paints
     * over it.
     */
    private static RenderPipeline register(String name, String define, BlendFunction blend) {
        RenderPipeline.Builder builder = RenderPipeline.builder()
                .withLocation(Identifier.fromNamespaceAndPath("alpaka", "pipeline/" + name))
                .withVertexShader(Identifier.fromNamespaceAndPath("alpaka", "core/sky_effects"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("alpaka", "core/sky_effects"))
                .withShaderDefine(define)
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.FOG)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withVertexBinding(0, DefaultVertexFormat.POSITION)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withCull(false)
                // The depth buffer is reversed (cleared to 0, nearer is greater), as vanilla's own
                // pipelines test it; the sky writes no depth.
                .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false));
        if (blend != null) builder.withColorTargetState(new ColorTargetState(blend));
        return RenderPipelinesAccessor.alpaka$register(builder.build());
    }
}
