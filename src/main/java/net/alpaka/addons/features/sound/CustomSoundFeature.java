package net.alpaka.addons.features.sound;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public class CustomSoundFeature {
    public static SoundEvent RARE_DROP_SOUND;
    public static SoundEvent INSANE_DROP_SOUND;

    public static void register() {
        Identifier rareId = Identifier.parse("alpaka:rare_drop");
        RARE_DROP_SOUND = SoundEvent.createVariableRangeEvent(rareId);
        Registry.register(BuiltInRegistries.SOUND_EVENT, rareId, RARE_DROP_SOUND);

        Identifier insaneId = Identifier.parse("alpaka:insane_drop");
        INSANE_DROP_SOUND = SoundEvent.createVariableRangeEvent(insaneId);
        Registry.register(BuiltInRegistries.SOUND_EVENT, insaneId, INSANE_DROP_SOUND);
    }

    public static void playRareDropSound() {
        if (!AlpakaConfig.instance.customSoundsEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null && RARE_DROP_SOUND != null) {
            float vol = AlpakaConfig.instance.customSoundsVolume;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(RARE_DROP_SOUND, 1.0f, vol));
        }
    }

    public static void playInsaneDropSound() {
        if (!AlpakaConfig.instance.customSoundsEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null && INSANE_DROP_SOUND != null) {
            float vol = AlpakaConfig.instance.customSoundsVolume;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(INSANE_DROP_SOUND, 1.0f, vol));
        }
    }
}
