package net.alpaka.addons.features.sound;

import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;

public class CustomSoundFeature {
    public static SoundEvent BUTTON_CLICK_SOUND;
    public static SoundEvent BLAZE_DEATH_SOUND;
    public static SoundEvent INVENTORY_CLICK_SOUND;
    public static SoundEvent XP_ORB_SOUND;
    public static SoundEvent HEARTBEAT_SOUND;
    public static SoundEvent BOSS_SPAWN_SOUND;
    public static SoundEvent EXPLODE_1_SOUND;
    public static SoundEvent EXPLODE_2_SOUND;
    public static SoundEvent EXPLODE_3_SOUND;
    public static SoundEvent EXPLODE_4_SOUND;
    public static SoundEvent SUCCESSFUL_HIT_SOUND;
    public static SoundEvent HOTBAR_EQUIP_SOUND;
    public static SoundEvent RARE_DROP_SOUND;
    public static SoundEvent INSANE_DROP_SOUND;

    private static final RandomSource RANDOM = RandomSource.create();
    private static long lastHeartbeatTime = 0;
    private static int lastSelectedSlot = -1;

    public static void register() {
        BUTTON_CLICK_SOUND = registerSound("alpaka:button_click");
        BLAZE_DEATH_SOUND = registerSound("alpaka:blaze_death");
        INVENTORY_CLICK_SOUND = registerSound("alpaka:inventory_click");
        XP_ORB_SOUND = registerSound("alpaka:xp_orb");
        HEARTBEAT_SOUND = registerSound("alpaka:heartbeat");
        BOSS_SPAWN_SOUND = registerSound("alpaka:boss_spawn");
        EXPLODE_1_SOUND = registerSound("alpaka:explode1");
        EXPLODE_2_SOUND = registerSound("alpaka:explode2");
        EXPLODE_3_SOUND = registerSound("alpaka:explode3");
        EXPLODE_4_SOUND = registerSound("alpaka:explode4");
        SUCCESSFUL_HIT_SOUND = registerSound("alpaka:successful_hit");
        HOTBAR_EQUIP_SOUND = registerSound("alpaka:hotbar_equip");
        RARE_DROP_SOUND = registerSound("alpaka:rare_drop");
        INSANE_DROP_SOUND = registerSound("alpaka:insane_drop");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!AlpakaConfig.instance.customSoundsEnabled) return;
            if (client.player != null && client.level != null) {
                LocalPlayer player = client.player;

                // 1. Low Health Heartbeat
                float healthRatio = player.getHealth() / player.getMaxHealth();
                if (healthRatio > 0 && healthRatio <= 0.25f) {
                    long now = System.currentTimeMillis();
                    if (now - lastHeartbeatTime >= 900) {
                        lastHeartbeatTime = now;
                        playHeartbeatSound();
                    }
                }

                // 2. Hotbar Slot Equip Sound
                int currentSlot = player.getInventory().getSelectedSlot();
                if (lastSelectedSlot != -1 && currentSlot != lastSelectedSlot) {
                    playHotbarEquipSound();
                }
                lastSelectedSlot = currentSlot;
            }
        });
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

    public static void playXpOrbSound() {
        playSound(XP_ORB_SOUND);
    }

    public static void playHeartbeatSound() {
        playSound(HEARTBEAT_SOUND);
    }

    public static void playBossSpawnSound() {
        playSound(BOSS_SPAWN_SOUND);
    }

    public static void playRandomHyperionExplodeSound() {
        int choice = RANDOM.nextInt(4);
        SoundEvent sound = switch (choice) {
            case 0 -> EXPLODE_1_SOUND;
            case 1 -> EXPLODE_2_SOUND;
            case 2 -> EXPLODE_3_SOUND;
            default -> EXPLODE_4_SOUND;
        };
        playSound(sound);
    }

    public static void playHitSound() {
        playSound(SUCCESSFUL_HIT_SOUND);
    }

    public static void playHotbarEquipSound() {
        playSound(HOTBAR_EQUIP_SOUND);
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
