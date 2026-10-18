package net.alpaka.addons.mixin;

import net.alpaka.addons.features.resourcepack.ServerPackCache;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ends Cache Resource Pack's startup restore once the game's first resource load is done. */
@Mixin(Minecraft.class)
public class MinecraftResourceLoadMixin {
    @Inject(method = "onResourceLoadFinished", at = @At("HEAD"))
    private void alpaka$finishPackRestore(CallbackInfo ci) {
        ServerPackCache.onInitialLoadFinished();
    }
}
