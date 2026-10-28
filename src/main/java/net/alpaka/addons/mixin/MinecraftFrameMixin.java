package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuSurface;
import net.alpaka.addons.utils.AlpakaPerf;
import net.alpaka.addons.utils.AlpakaPerf.Phase;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.PacketProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Splits each pass of the client loop into its phases for AlpakaPerf's slow-frame report. Off,
 * every hook only reads a flag. In the optional config: another mod rewriting the loop should
 * cost a phase in a debug report, not the game.
 */
@Mixin(Minecraft.class)
public class MinecraftFrameMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void alpaka$frameStart(boolean advanceGameTime, CallbackInfo ci) {
        AlpakaPerf.frameStart();
    }

    @WrapOperation(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketProcessor;processQueuedPackets()V"))
    private void alpaka$timePackets(PacketProcessor packets, Operation<Void> original) {
        long t = AlpakaPerf.phaseBegin();
        try {
            original.call(packets);
        } finally {
            AlpakaPerf.phaseEnd(Phase.PACKETS, t);
        }
    }

    @WrapOperation(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;runAllTasks()V"))
    private void alpaka$timeTasks(Minecraft minecraft, Operation<Void> original) {
        long t = AlpakaPerf.phaseBegin();
        try {
            original.call(minecraft);
        } finally {
            AlpakaPerf.phaseEnd(Phase.TASKS, t);
        }
    }

    @WrapOperation(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;tick()V"))
    private void alpaka$timeTick(Minecraft minecraft, Operation<Void> original) {
        long t = AlpakaPerf.phaseBegin();
        try {
            original.call(minecraft);
        } finally {
            AlpakaPerf.phaseEnd(Phase.TICK, t);
        }
    }

    @WrapOperation(method = "renderFrame",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;extract(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void alpaka$timeExtract(GameRenderer renderer, DeltaTracker delta, boolean advance, Operation<Void> original) {
        long t = AlpakaPerf.phaseBegin();
        try {
            original.call(renderer, delta, advance);
        } finally {
            AlpakaPerf.phaseEnd(Phase.EXTRACT, t);
        }
    }

    @WrapOperation(method = "renderFrame",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;executePendingTasks()V"))
    private void alpaka$timeGpuTasks(Operation<Void> original) {
        long t = AlpakaPerf.phaseBegin();
        try {
            original.call();
        } finally {
            AlpakaPerf.phaseEnd(Phase.GPU_TASKS, t);
        }
    }

    @WrapOperation(method = "renderFrame",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private void alpaka$timeRender(GameRenderer renderer, DeltaTracker delta, boolean advance, Operation<Void> original) {
        long t = AlpakaPerf.phaseBegin();
        try {
            original.call(renderer, delta, advance);
        } finally {
            AlpakaPerf.phaseEnd(Phase.RENDER, t);
        }
    }

    @WrapOperation(method = "renderFrame",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuSurface;acquireNextTexture()V"))
    private void alpaka$timeAcquire(GpuSurface surface, Operation<Void> original) {
        long t = AlpakaPerf.phaseBegin();
        try {
            original.call(surface);
        } finally {
            AlpakaPerf.phaseEnd(Phase.ACQUIRE, t);
        }
    }

    @WrapOperation(method = "renderFrame",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;submit()V"))
    private void alpaka$timeSubmit(CommandEncoder encoder, Operation<Void> original) {
        long t = AlpakaPerf.phaseBegin();
        try {
            original.call(encoder);
        } finally {
            AlpakaPerf.phaseEnd(Phase.SUBMIT, t);
        }
    }

    @WrapOperation(method = "renderFrame",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/GpuSurface;present()V"))
    private void alpaka$timePresent(GpuSurface surface, Operation<Void> original) {
        long t = AlpakaPerf.phaseBegin();
        try {
            original.call(surface);
        } finally {
            AlpakaPerf.phaseEnd(Phase.PRESENT, t);
        }
    }

    @WrapOperation(method = "renderFrame",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/FramerateLimiter;limitDisplayFPS(I)V"))
    private void alpaka$timeLimiter(int fps, Operation<Void> original) {
        long t = AlpakaPerf.phaseBegin();
        try {
            original.call(fps);
        } finally {
            AlpakaPerf.phaseEnd(Phase.LIMITER, t);
        }
    }
}
