package net.alpaka.addons.mixin;

import com.mojang.authlib.GameProfile;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
    
    @Shadow
    public abstract GameProfile getProfile();

    @Inject(method = "getTabListDisplayName", at = @At("RETURN"), cancellable = true)
    private void modifyTabListDisplayName(CallbackInfoReturnable<Component> info) {
        if (!AlpakaConfig.instance.nameHighlightingEnabled) return;

        Component original = info.getReturnValue();
        if (original != null) {
            info.setReturnValue(SlayerDropTracker.highlightName(original));
        } else {
            GameProfile profile = getProfile();
            if (profile != null && "Alpakaa".equals(profile.name())) {
                info.setReturnValue(Component.literal("Alpakaa").withStyle(style -> style.withColor(ChatFormatting.GOLD).withBold(true)));
            }
        }
    }
}
