package net.alpaka.addons.features.teleportwarp;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.etherwarp.EtherwarpDetector;
import net.alpaka.addons.features.sound.LocalAttackTracker;
import net.alpaka.addons.mixin.RenderPipelinesAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Optional;

/**
 * Teleport Effects, the screen effect: the picture warps for a moment after the player teleports
 * with an item, like the Aspect of the End's Instant Transmission or the Aspect of the Void's
 * Etherwarp.
 *
 * Two styles, one shader each: Warp ({@code teleport_warp}) pulls the picture towards the middle
 * with a ring running outward; Hyperspace ({@code teleport_hyperspace}) zooms in with streaks of
 * light shooting past to the edges, like a jump to light speed.
 *
 * Separately, Smooth Teleport ({@link SmoothTeleport}) lets the camera glide to the destination.
 *
 * Purely visual. The teleport is the server's own position packet, which vanilla handles and
 * answers as always; this only compares where the player stood before and after it
 * ({@code ClientPacketListenerTeleportWarpMixin}) and then redraws the finished world frame with
 * the chosen style's shader before the HUD goes on top. Nothing is sent, and the position,
 * camera and movement are untouched.
 *
 * Only a teleport the player caused counts: they right-clicked within the last second, and the item
 * in their hand teleports, by its tooltip ("Teleport 8 blocks ahead", "Ether Transmission", ...).
 * A lag-back, a warp pad or a server move while holding something else stays plain.
 */
public final class TeleportWarpFeature {
    private static final Logger LOGGER = LoggerFactory.getLogger("AlpakaAddons/TeleportWarp");

    /** A move shorter than this is a correction, not a teleport. */
    private static final double MIN_DISTANCE = 2.0;
    /** How long after a right-click the server's teleport may arrive, ping included. */
    private static final long USE_WINDOW_MS = 1_000L;
    /** How long the warp takes to build up; the rest of the duration it fades. */
    private static final float RISE_SECONDS = 0.07f;
    /** The copy of the frame is let go after this long without a teleport. */
    private static final long RELEASE_AFTER_NANOS = 15_000_000_000L;
    /** Tooltip words of a teleporting ability: Instant and Ether Transmission, Wither Impact, ... */
    private static final String[] TELEPORT_MARKERS = {"teleport", "transmission"};

    /** The styles, in the order of {@link #STYLE_SHADERS}; the setting stores the index. */
    public static final String[] STYLE_NAMES = {"Warp", "Hyperspace"};
    private static final String[] STYLE_SHADERS = {"teleport_warp", "teleport_hyperspace"};

    private static final RenderPipeline[] PIPELINES = new RenderPipeline[STYLE_SHADERS.length];
    private static TextureTarget snapshot;
    /** A snapshot replaced after a resize, closed once the frame that sampled it is done. */
    private static TextureTarget retired;
    private static GpuBuffer config;

    /** When the current warp started, or -1 while none runs. */
    private static long startNanos = -1L;
    private static long lastWarpNanos;
    private static boolean warned;

    private static ItemStack cachedStack;
    private static boolean cachedTeleports;

    private TeleportWarpFeature() {}

    /** Registers the styles' pipelines with vanilla's, before the shader manager precompiles them. */
    public static void init() {
        for (int i = 0; i < STYLE_SHADERS.length; i++) {
            String name = STYLE_SHADERS[i];
            PIPELINES[i] = RenderPipelinesAccessor.alpaka$register(
                    RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                            .withLocation(Identifier.fromNamespaceAndPath("alpaka", "pipeline/" + name))
                            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
                            .withFragmentShader(Identifier.fromNamespaceAndPath("alpaka", "post/" + name))
                            .withBindGroupLayout(BindGroupLayout.builder()
                                    .withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
                                    .withUniform("WarpConfig", UniformType.UNIFORM_BUFFER)
                                    .build())
                            // Since 26.3 a pipeline draws to no target unless it names one; the
                            // main target it draws to is in the default format.
                            .withColorTargetState(ColorTargetState.DEFAULT)
                            .build());
        }
    }

    /**
     * The server moved the player from {@code from} to {@code to}; vanilla has already applied it.
     * Starts the warp if it was a teleport the player made with an item.
     */
    public static void onServerTeleport(Vec3 from, Vec3 to) {
        if (!AlpakaConfig.instance.teleportWarpEnabled) return;
        if (from.distanceToSqr(to) < MIN_DISTANCE * MIN_DISTANCE) return;
        if (!LocalAttackTracker.usedItemWithin(USE_WINDOW_MS)) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !teleports(player.getMainHandItem())) return;
        if (!AlpakaConfig.instance.teleportWarpEtherwarp && EtherwarpDetector.isAimingEtherwarp()) return;
        SmoothTeleport.onTeleport(from, to);
        if (AlpakaConfig.instance.teleportWarpScreenEffect) start();
    }

    /** Starts the warp, or builds it back up from where it is when one is still fading. */
    private static void start() {
        long now = System.nanoTime();
        float elapsed = startNanos < 0 ? Float.MAX_VALUE : (now - startNanos) / 1.0e9f;
        if (elapsed < RISE_SECONDS) return;
        // Spamming teleports keeps the warp up instead of snapping it back to nothing each time.
        float current = envelope(elapsed, duration());
        startNanos = now - (long) (current * RISE_SECONDS * 1.0e9f);
    }

    private static float duration() {
        return Mth.clamp(AlpakaConfig.instance.teleportWarpDuration, 0.2f, 1.5f);
    }

    /**
     * How strong the warp is {@code elapsed} seconds in: a quick build-up over {@link #RISE_SECONDS},
     * then an eased fade to nothing at {@code duration}.
     */
    static float envelope(float elapsed, float duration) {
        if (elapsed < 0.0f || elapsed >= duration) return 0.0f;
        if (elapsed < RISE_SECONDS) {
            float x = elapsed / RISE_SECONDS;
            return x * x * (3.0f - 2.0f * x);
        }
        float x = (elapsed - RISE_SECONDS) / Math.max(1.0e-3f, duration - RISE_SECONDS);
        return (1.0f - x) * (1.0f - x);
    }

    /** Whether the item's name or tooltip names a teleporting ability. Cached per stack. */
    private static boolean teleports(ItemStack stack) {
        if (stack == cachedStack) return cachedTeleports;
        cachedStack = stack;
        cachedTeleports = advertisesTeleport(stack);
        return cachedTeleports;
    }

    private static boolean advertisesTeleport(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (hasMarker(stack.getHoverName())) return true;
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) return false;
        for (Component line : lore.lines()) {
            if (hasMarker(line)) return true;
        }
        return false;
    }

    private static boolean hasMarker(Component text) {
        String s = text.getString().toLowerCase(Locale.ROOT);
        for (String marker : TELEPORT_MARKERS) {
            if (s.contains(marker)) return true;
        }
        return false;
    }

    /**
     * Warps the finished world frame, after vanilla's own post effects and before the HUD. Called
     * every frame; does nothing unless a warp is running.
     */
    public static void renderFrame() {
        if (retired != null) {
            retired.destroyBuffers();
            retired = null;
        }
        long now = System.nanoTime();
        if (startNanos < 0) {
            if (snapshot != null && now - lastWarpNanos > RELEASE_AFTER_NANOS) {
                retired = snapshot;
                snapshot = null;
            }
            return;
        }
        float elapsed = (now - startNanos) / 1.0e9f;
        float duration = duration();
        if (elapsed >= duration || !AlpakaConfig.instance.teleportWarpEnabled
                || !AlpakaConfig.instance.teleportWarpScreenEffect) {
            startNanos = -1L;
            return;
        }
        lastWarpNanos = now;
        Minecraft mc = Minecraft.getInstance();
        RenderPipeline pipeline = PIPELINES[Mth.clamp(AlpakaConfig.instance.teleportWarpStyle, 0, PIPELINES.length - 1)];
        if (mc.level == null || mc.player == null || pipeline == null) return;

        float strength = Mth.clamp(AlpakaConfig.instance.teleportWarpStrength, 0.25f, 2.0f);
        float amount = envelope(elapsed, duration) * strength;
        if (amount <= 0.001f) return;
        try {
            draw(mc.gameRenderer.mainRenderTarget(), pipeline, amount, elapsed / duration);
        } catch (RuntimeException e) {
            startNanos = -1L;
            if (!warned) {
                warned = true;
                LOGGER.warn("Teleport warp failed; leaving the frame as it is", e);
            }
        }
    }

    private static void draw(RenderTarget main, RenderPipeline pipeline, float amount, float progress) {
        GpuTexture source = main.getColorTexture();
        if (source == null || main.width <= 0 || main.height <= 0) return;
        if (snapshot == null) {
            snapshot = new TextureTarget("Alpaka teleport warp", main.width, main.height, source.getFormat(), null);
        } else if (snapshot.width != main.width || snapshot.height != main.height) {
            // The old one may still be read by this frame's commands so far; closed next frame.
            retired = snapshot;
            snapshot = new TextureTarget("Alpaka teleport warp", main.width, main.height, source.getFormat(), null);
        }

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        if (config == null) {
            config = RenderSystem.getDevice().createBuffer(() -> "Alpaka teleport warp config",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, 16);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            encoder.writeToBuffer(config.slice(), Std140Builder.onStack(stack, 16)
                    .putFloat(amount)
                    .putFloat(progress)
                    .putFloat(main.width / (float) main.height)
                    .get());
        }

        encoder.copyTextureToTexture(source, snapshot.getColorTexture(), 0, 0, 0, 0, 0, main.width, main.height);
        try (RenderPass pass = encoder.createRenderPass(() -> "Alpaka teleport warp",
                main.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("WarpConfig", config);
            pass.setUniform("InSampler", snapshot.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
    }
}
