package net.alpaka.addons.features.sky;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.framegraph.FramePass;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.mixin.RenderPipelinesAccessor;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Sky Effects: animated skies. A firestorm over the Nether, which vanilla gives no sky at all; an
 * aurora, the Milky Way and shooting stars over the Overworld by night and a glowing sun by day;
 * a drifting nebula in place of the End's textured sky.
 *
 * Each is painted by the shader {@code alpaka:core/sky_effects} on a box around the camera, the
 * box vanilla draws the End's sky on: it is centered on the camera and turns with the view, so
 * every pixel of it lies in the direction that pixel looks, and the shader paints the sky from that
 * direction and the time alone - no textures, no seams, and the same cost at any render distance.
 * Its parameters ride in vanilla's DynamicTransforms uniform, which every draw fills anyway; the
 * shader's header lists them.
 *
 * Where each goes in vanilla's frame:
 * - Nether: where a dimension has no skybox, vanilla adds no sky pass and the frame keeps the fog
 *   color it was cleared with. {@link #addNetherPass} adds a pass there, before the world is drawn
 *   over it. The storm is built from the fog's color, so it matches each biome.
 * - Overworld: inside vanilla's sky pass, right after the sun, moon and stars, as light added over
 *   them ({@link #renderOverworld}).
 * - End: inside vanilla's sky pass, in place of the textured sky ({@link #renderEnd}); the End's
 *   flashes are still drawn over it.
 *
 * Where vanilla shows no sky - under lava, in powder snow, blinded, or in the fog a boss bar like
 * the Ender Dragon's brings - none of them show either: the Overworld's and the End's ride inside
 * vanilla's sky pass, and the Nether's is added from where that pass is, making the same checks.
 */
public final class SkyEffectsFeature {
    /** Half the edge of the box the skies are drawn on, the size of vanilla's End sky box. */
    private static final float BOX = 100.0f;

    /** The corners of a face of the box, in order around it, on the face's two other axes. */
    private static final float[][] FACE_CORNERS = {{-1.0f, -1.0f}, {1.0f, -1.0f}, {1.0f, 1.0f}, {-1.0f, 1.0f}};

    private static final SkyClock CLOCK = new SkyClock();

    private static RenderPipeline nether;
    private static RenderPipeline overworld;
    private static RenderPipeline end;
    private static GpuBuffer box;

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
     * Adds the Nether's sky pass. Called where vanilla adds its own sky pass, which it leaves out
     * when the dimension has no skybox.
     */
    public static void addNetherPass(FrameGraphBuilder frame, LevelTargetBundle targets, SkyRenderState sky,
                                     CameraRenderState camera, GpuBufferSlice fog) {
        if (sky.skybox != DimensionType.Skybox.NONE || !shows(AlpakaConfig.instance.skyEffectsNether)) return;
        // Where vanilla would show no sky either.
        if (camera.fogType == FogType.LAVA || camera.fogType == FogType.POWDER_SNOW
                || camera.entityRenderState.doesMobEffectBlockSky) return;
        FramePass pass = frame.addPass("alpaka_sky");
        targets.main = pass.readsAndWrites(targets.main);
        ResourceHandle<RenderTarget> main = targets.main;
        pass.executes(() -> {
            RenderSystem.setShaderFog(fog);
            draw(nether(), main.get(), 0.0f, 1.0f, new Matrix4f());
        });
    }

    /**
     * Adds the Overworld's effects over vanilla's sky, with the angles vanilla has just drawn the
     * sun, moon and stars at and how clear the sky is (1 dry, 0 in heavy rain).
     */
    public static void renderOverworld(RenderTarget target, float sunAngle, float moonAngle, float starAngle,
                                       float rainBrightness) {
        if (!shows(AlpakaConfig.instance.skyEffectsOverworld)) return;
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
                0.0f, 0.0f, 0.0f, 0.0f);
        draw(overworld(), target, night, Mth.clamp(rainBrightness, 0.0f, 1.0f), directions);
    }

    /** Draws the End's sky in place of vanilla's. False when that sky is off and vanilla's is to be drawn. */
    public static boolean renderEnd(RenderTarget target) {
        if (!shows(AlpakaConfig.instance.skyEffectsEnd)) return false;
        draw(end(), target, 0.0f, 1.0f, new Matrix4f());
        return true;
    }

    /**
     * Towards a body vanilla puts in the sky at this angle: it turns the sky -90 degrees about Y,
     * then by the angle about X, and draws the sun and the moon straight up from there.
     */
    private static Vector3f towards(float angle) {
        return new Vector3f(0.0f, 1.0f, 0.0f).rotateX(angle).rotateY(-Mth.HALF_PI);
    }

    private static void draw(RenderPipeline pipeline, RenderTarget target, float night, float clear, Matrix4f directions) {
        float time = CLOCK.advance(System.nanoTime(), Mth.clamp(AlpakaConfig.instance.skyEffectsSpeed, 0.25f, 3.0f));
        float intensity = Mth.clamp(AlpakaConfig.instance.skyEffectsIntensity, 0.1f, 1.0f);
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(
                RenderSystem.getModelViewMatrixCopy(),
                new Vector4f(1.0f, 1.0f, 1.0f, intensity),
                new Vector3f(time, night, clear),
                directions);
        // Both buffers before the pass opens, as vanilla's sky does.
        GpuBuffer vertices = box();
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        GpuBuffer indexBuffer = indices.getBuffer(36);
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Alpaka sky",
                target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, vertices.slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(36, 1, 0, 0, 0);
        }
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

    /** Light added over vanilla's sky: blended premultiplied, the shader taking nothing of the sky away. */
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
     * sky gets. Seen from inside the box, and with no depth test, like vanilla's sky.
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
                .withVertexBinding(0, DefaultVertexFormat.POSITION)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withCull(false);
        if (blend != null) builder.withColorTargetState(new ColorTargetState(blend));
        return RenderPipelinesAccessor.alpaka$register(builder.build());
    }
}
