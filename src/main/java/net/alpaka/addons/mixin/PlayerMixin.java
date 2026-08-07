package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {
    @Inject(method = "getDisplayName", at = @At("RETURN"), cancellable = true)
    private void modifyDisplayName(CallbackInfoReturnable<Component> info) {
        if (!AlpakaConfig.instance.nameHighlightingEnabled) return;

        Component original = info.getReturnValue();
        if (original != null) {
            info.setReturnValue(SlayerDropTracker.highlightName(original));
        }
    }

    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
    private void onGetHurtSound(net.minecraft.world.damagesource.DamageSource damageSource, CallbackInfoReturnable<net.minecraft.sounds.SoundEvent> cir) {
        if (AlpakaConfig.instance.customSoundsEnabled && (Object)this == net.minecraft.client.Minecraft.getInstance().player) {
            cir.setReturnValue(net.alpaka.addons.features.sound.CustomSoundFeature.DAMAGE_SOUND);
        }
    }
}
