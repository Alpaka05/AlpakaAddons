package net.alpaka.addons.mixin;

import com.mojang.authlib.GameProfile;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {

    @Shadow
    public abstract GameProfile getProfile();

    /**
     * The name this entry was last rewritten from, and the result.
     *
     * Vanilla's {@code getTabListDisplayName} is a field read, and the tab list calls it for every
     * listed player on every frame it is open. Rewriting unconditionally meant walking the whole
     * component tree and rebuilding it up to eighty times a frame; the tab name itself only changes
     * when a packet replaces it, so comparing by identity gets the same answer for a pointer test.
     *
     * Per-instance rather than static: each PlayerInfo has its own name, and they are queried in
     * turn, so a single shared slot would miss on every single call.
     */
    @Unique
    private Component alpaka$cachedSource;

    @Unique
    private Component alpaka$cachedResult;

    @Unique
    private boolean alpaka$cacheValid;

    @Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
    private void modifyTabListDisplayName(CallbackInfoReturnable<Component> info) {
        if (!AlpakaConfig.instance.nameHighlightingEnabled) return;

        Component original = info.getReturnValue();

        if (alpaka$cacheValid && alpaka$cachedSource == original) {
            if (alpaka$cachedResult != null) info.setReturnValue(alpaka$cachedResult);
            return;
        }

        Component result = null;
        if (original != null) {
            result = SlayerDropTracker.highlightName(original);
        } else {
            GameProfile profile = getProfile();
            if (profile != null && "Alpakaa".equals(profile.name())) {
                result = Component.literal("Alpakaa").withStyle(style -> style.withColor(ChatFormatting.LIGHT_PURPLE).withBold(true));
            }
        }

        alpaka$cachedSource = original;
        alpaka$cachedResult = result;
        alpaka$cacheValid = true;

        if (result != null) info.setReturnValue(result);
    }
}
