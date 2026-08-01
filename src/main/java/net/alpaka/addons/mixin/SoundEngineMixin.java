package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {
    private static boolean IS_INTERNAL_PLAY = false;

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void onPlaySound(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (!AlpakaConfig.instance.customSoundsEnabled) return;
        if (IS_INTERNAL_PLAY) return;

        if (sound == null || sound.getIdentifier() == null) return;
        String path = sound.getIdentifier().getPath();

        if (path.contains("button.click") || "gui.button.press".equals(path) || "ui.button.click".equals(path)) {
            IS_INTERNAL_PLAY = true;
            try {
                CustomSoundFeature.playButtonClickSound();
            } finally {
                IS_INTERNAL_PLAY = false;
            }
            cir.setReturnValue(SoundEngine.PlayResult.STARTED);
        } else if ("entity.blaze.death".equals(path) || path.contains("blaze/death")) {
            IS_INTERNAL_PLAY = true;
            try {
                CustomSoundFeature.playBlazeDeathSound();
            } finally {
                IS_INTERNAL_PLAY = false;
            }
            cir.setReturnValue(SoundEngine.PlayResult.STARTED);
        } else if ("item.pickup".equals(path) || "container.click".equals(path)) {
            IS_INTERNAL_PLAY = true;
            try {
                CustomSoundFeature.playInventoryClickSound();
            } finally {
                IS_INTERNAL_PLAY = false;
            }
            cir.setReturnValue(SoundEngine.PlayResult.STARTED);
        } else if ("entity.experience_orb.pickup".equals(path)) {
            IS_INTERNAL_PLAY = true;
            try {
                CustomSoundFeature.playXpOrbSound();
            } finally {
                IS_INTERNAL_PLAY = false;
            }
            cir.setReturnValue(SoundEngine.PlayResult.STARTED);
        } else if ("entity.generic.explode".equals(path) || "entity.dragon_fireball.explode".equals(path) || path.contains("explode")) {
            IS_INTERNAL_PLAY = true;
            try {
                CustomSoundFeature.playRandomHyperionExplodeSound();
            } finally {
                IS_INTERNAL_PLAY = false;
            }
            cir.setReturnValue(SoundEngine.PlayResult.STARTED);
        } else if ("entity.zombie_villager.cure".equals(path) || path.contains("remedy")) {
            IS_INTERNAL_PLAY = true;
            try {
                CustomSoundFeature.playZombieRemedySound();
            } finally {
                IS_INTERNAL_PLAY = false;
            }
            cir.setReturnValue(SoundEngine.PlayResult.STARTED);
        } else if ("entity.arrow.hit_player".equals(path) || "entity.player.attack.crit".equals(path) || path.contains("successful_hit")) {
            IS_INTERNAL_PLAY = true;
            try {
                CustomSoundFeature.playHitSound();
            } finally {
                IS_INTERNAL_PLAY = false;
            }
            cir.setReturnValue(SoundEngine.PlayResult.STARTED);
        }
    }
}
