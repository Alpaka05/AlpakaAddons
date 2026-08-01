package net.alpaka.addons.features.sound;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

public class CustomSoundFeature {
    public static SoundEvent BUTTON_CLICK_SOUND;
    public static SoundEvent BLAZE_DEATH_SOUND;
    public static SoundEvent INVENTORY_CLICK_SOUND;
    public static SoundEvent RARE_DROP_SOUND;
    public static SoundEvent INSANE_DROP_SOUND;

    public static void register() {
        BUTTON_CLICK_SOUND = registerSound("alpaka:button_click");
        BLAZE_DEATH_SOUND = registerSound("alpaka:blaze_death");
        INVENTORY_CLICK_SOUND = registerSound("alpaka:inventory_click");
        RARE_DROP_SOUND = registerSound("alpaka:rare_drop");
        INSANE_DROP_SOUND = registerSound("alpaka:insane_drop");
    }

    private static SoundEvent registerSound(String path) {
        Identifier id = Identifier.parse(path);
        SoundEvent event = SoundEvent.createVariableRangeEvent(id);
        Registry.register(BuiltInRegistries.SOUND_EVENT, id, event);
        return event;
    }

    public static void playButtonClickSound() {
        playSound(BUTTON_CLICK_SOUND);
    }

    public static void playBlazeDeathSound() {
        playSound(BLAZE_DEATH_SOUND);
    }

    public static void playInventoryClickSound() {
        playSound(INVENTORY_CLICK_SOUND);
    }

    public static void playRareDropSound() {
        playSound(RARE_DROP_SOUND);
    }

    public static void playInsaneDropSound() {
        playSound(INSANE_DROP_SOUND);
    }

    private static void playSound(SoundEvent sound) {
        if (!AlpakaConfig.instance.customSoundsEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null && sound != null) {
            float vol = AlpakaConfig.instance.customSoundsVolume;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f, vol));
        }
    }
}
