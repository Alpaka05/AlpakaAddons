package net.alpaka.addons.features.chat;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.alpaka.addons.mixin.RenderPipelinesAccessor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The dual Kawase blur behind the blurred panels.
 *
 * The frame is halved in size a few times, each step blurring a little ({@code blur_down}), and
 * then doubled back up the same steps ({@code blur_up}). Every pass reads five or eight bilinear
 * samples, and all but the first work on a quarter of the pixels of the one before, so a wide blur
 * costs a fraction of a separable one at full size: the old box blur read about two hundred samples
 * per screen pixel at full strength, this about three. The result stays at half the screen's size;
 * the panels sample it with the bilinear filter, which a blurred image loses nothing to.
 *
 * How wide it gets is the number of steps ({@link Plan#levels}) and how far apart each pass
 * samples ({@link Plan#offset}), which rides in a small uniform buffer, one per offset.
 */
final class DualKawaseBlur {
    /** The most halvings: a sixteenth of the screen, wide enough for full strength at GUI scale 4. */
    static final int MAX_LEVELS = 4;

    /** How wide the blur is: halvings, and the sample spacing in texels of each pass's input. */
    record Plan(int levels, float offset) {
        static final Plan NONE = new Plan(0, 0.0f);
    }

    private static RenderPipeline down;
    private static RenderPipeline up;

    /** level[i] is the frame at 1/2^(i+1) of its size. */
    private final List<TextureTarget> levels = new ArrayList<>();
    private final Map<Float, GpuBuffer> configs = new HashMap<>();
    private int width;
    private int height;

    static void init() {
        down();
        up();
    }

    private static RenderPipeline down() {
        if (down == null) down = register("blur_down");
        return down;
    }

    private static RenderPipeline up() {
        if (up == null) up = register("blur_up");
        return up;
    }

    private static RenderPipeline register(String name) {
        return RenderPipelinesAccessor.alpaka$register(
                RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                        .withLocation(Identifier.fromNamespaceAndPath("alpaka", "pipeline/" + name))
                        .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                        .withFragmentShader(Identifier.fromNamespaceAndPath("alpaka", "post/" + name))
                        .withBindGroupLayout(BindGroupLayout.builder()
                                .withSampler("InSampler")
                                .withUniform("BlurConfig", UniformType.UNIFORM_BUFFER)
                                .build())
                        .build());
    }

    /**
     * The plan for a blur radius in screen pixels: as many halvings as fit in the radius, and the
     * spacing that makes up the rest. Each halving doubles the width, the spacing scales it within
     * one; 0 means no blur.
     */
    static Plan plan(int radius) {
        if (radius <= 0) return Plan.NONE;
        int levels = Math.clamp(31 - Integer.numberOfLeadingZeros(radius), 1, MAX_LEVELS);
        float offset = Math.clamp(radius / (float) (1 << levels), 0.5f, 2.5f);
        // In sixteenths, so the few offsets in use share their uniform buffers.
        return new Plan(levels, Math.round(offset * 16.0f) / 16.0f);
    }

    /** Whether the targets exist at all. */
    boolean exists() {
        return !levels.isEmpty();
    }

    /** Whether the targets fit a frame of this size; {@link #resize} makes them fit. */
    boolean fits(int frameWidth, int frameHeight) {
        return !levels.isEmpty() && width == frameWidth && height == frameHeight;
    }

    /**
     * Makes the targets for a frame of this size. The old ones go to {@code retired}, for the caller
     * to close once nothing can sample them any more.
     */
    void resize(int frameWidth, int frameHeight, GpuFormat format, List<TextureTarget> retired) {
        retired.addAll(levels);
        levels.clear();
        width = frameWidth;
        height = frameHeight;
        for (int i = 1; i <= MAX_LEVELS; i++) {
            int w = Math.max(1, (frameWidth + (1 << i) - 1) >> i);
            int h = Math.max(1, (frameHeight + (1 << i) - 1) >> i);
            levels.add(new TextureTarget("Alpaka blur 1/" + (1 << i), w, h, false, format));
        }
    }

    /** The result: the first level, which {@link #run} leaves holding the blurred frame. */
    GpuTextureView output() {
        return levels.getFirst().getColorTextureView();
    }

    /** Blurs the source, which must be the size given to {@link #resize}, into {@link #output()}. */
    void run(GpuTextureView source, Plan plan) {
        int n = Math.clamp(plan.levels(), 1, MAX_LEVELS);
        GpuBuffer config = config(plan.offset());
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

        GpuTextureView input = source;
        for (int i = 0; i < n; i++) {
            GpuTextureView target = levels.get(i).getColorTextureView();
            pass(encoder, down(), input, target, sampler, config);
            input = target;
        }
        for (int i = n - 2; i >= 0; i--) {
            GpuTextureView target = levels.get(i).getColorTextureView();
            pass(encoder, up(), input, target, sampler, config);
            input = target;
        }
    }

    private static void pass(CommandEncoder encoder, RenderPipeline pipeline, GpuTextureView input,
                             GpuTextureView target, GpuSampler sampler, GpuBuffer config) {
        try (RenderPass pass = encoder.createRenderPass(() -> "Alpaka blur", target, Optional.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("BlurConfig", config);
            pass.bindTexture("InSampler", input, sampler);
            pass.draw(3, 1, 0, 0);
        }
    }

    private GpuBuffer config(float offset) {
        return configs.computeIfAbsent(offset, value -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                return RenderSystem.getDevice().createBuffer(() -> "Alpaka blur config",
                        GpuBuffer.USAGE_UNIFORM, Std140Builder.onStack(stack, 16).putFloat(value).get());
            }
        });
    }
}
