package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.alpaka.addons.features.resourcepack.ServerPackCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.PackLoadFeedback;
import net.minecraft.client.resources.server.PackReloadConfig;
import net.minecraft.client.resources.server.ServerPackManager;
import net.minecraft.network.Connection;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.URL;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Cache Resource Pack's hooks into the server pack source; see ServerPackCache. */
@Mixin(DownloadedPackSource.class)
public class DownloadedPackSourceMixin {
    @Shadow @Final private ServerPackManager manager;
    @Shadow private PackLoadFeedback packFeedback;
    @Shadow private PackReloadConfig.@Nullable Callbacks pendingReload;

    /**
     * A pack that is already loaded is answered the way vanilla answers one it just loaded, so the
     * server sees accepted, downloaded and applied, without anything being reloaded.
     */
    @Inject(method = "pushPack", at = @At("HEAD"), cancellable = true)
    private void alpaka$skipLoadedPack(UUID id, URL url, @Nullable String hash, CallbackInfo ci) {
        if (ServerPackCache.isAlreadyLoaded(id, hash)) {
            packFeedback.reportUpdate(id, PackLoadFeedback.Update.ACCEPTED);
            packFeedback.reportUpdate(id, PackLoadFeedback.Update.DOWNLOADED);
            packFeedback.reportFinalResult(id, PackLoadFeedback.FinalResult.APPLIED);
            ci.cancel();
        } else {
            ServerPackCache.onUncachedPush(id, manager);
        }
    }

    @Inject(method = {"popPack", "popAll"}, at = @At("HEAD"), cancellable = true)
    private void alpaka$ignoreServerPop(CallbackInfo ci) {
        if (ServerPackCache.ignorePop()) ci.cancel();
    }

    @WrapOperation(method = "cleanupAfterDisconnect",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/server/ServerPackManager;popAll()V"))
    private void alpaka$keepPacksAfterDisconnect(ServerPackManager instance, Operation<Void> original) {
        if (!ServerPackCache.keepOnDisconnect()) original.call(instance);
    }

    /** Runs on the connector thread; the unload goes to the main thread, like every other pack change. */
    @Inject(method = "configureForServerControl", at = @At("HEAD"))
    private void alpaka$onConnect(Connection connection, ServerPackManager.PackPromptStatus status, CallbackInfo ci) {
        if (ServerPackCache.onConnect(status)) Minecraft.getInstance().execute(manager::popAll);
    }

    @Inject(method = "configureForLocalWorld", at = @At("HEAD"))
    private void alpaka$onLocalWorld(CallbackInfo ci) {
        if (ServerPackCache.onLocalWorld()) manager.popAll();
    }

    @Inject(method = "onReloadSuccess", at = @At("HEAD"))
    private void alpaka$onPacksApplied(CallbackInfo ci) {
        if (pendingReload != null) ServerPackCache.onPacksApplied(pendingReload.packsToLoad());
    }

    @WrapOperation(method = "startReload",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> alpaka$noReloadDuringRestore(Minecraft minecraft, Operation<CompletableFuture<Void>> original) {
        return ServerPackCache.suppressReload() ? CompletableFuture.completedFuture(null) : original.call(minecraft);
    }
}
