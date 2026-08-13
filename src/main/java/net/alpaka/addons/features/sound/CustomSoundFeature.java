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
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackRepository;
import java.util.ArrayList;
import java.util.List;

public class CustomSoundFeature {
    public static SoundEvent BUTTON_CLICK_SOUND;
    public static SoundEvent BLAZE_DEATH_SOUND;
    public static SoundEvent INVENTORY_CLICK_SOUND;
    public static SoundEvent INVENTORY_OPEN_SOUND;
    public static SoundEvent INVENTORY_CLOSE_SOUND;
    public static SoundEvent DAMAGE_SOUND;
    public static SoundEvent HEARTBEAT_SOUND;
    public static SoundEvent BOSS_SPAWN_SOUND;
    public static SoundEvent ZOMBIE_REMEDY_SOUND;
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
        INVENTORY_OPEN_SOUND = registerSound("alpaka:inventory_open");
        INVENTORY_CLOSE_SOUND = registerSound("alpaka:inventory_close");
        DAMAGE_SOUND = registerSound("alpaka:player_hurt");
        HEARTBEAT_SOUND = registerSound("alpaka:heartbeat");
        BOSS_SPAWN_SOUND = registerSound("alpaka:boss_spawn");
        ZOMBIE_REMEDY_SOUND = registerSound("alpaka:zombie_remedy");
        SUCCESSFUL_HIT_SOUND = registerSound("alpaka:successful_hit");
        HOTBAR_EQUIP_SOUND = registerSound("alpaka:hotbar_equip");
        RARE_DROP_SOUND = registerSound("alpaka:rare_drop");
        INSANE_DROP_SOUND = registerSound("alpaka:insane_drop");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!AlpakaConfig.instance.customSoundsEnabled) return;
            if (client.player != null && client.level != null) {
                LocalPlayer player = client.player;

                // 1. Low Health Heartbeat (only in Survival/Adventure mode when low HP and alive)
                if (AlpakaConfig.instance.customSoundLowHpHeartbeat && !player.isCreative() && !player.isSpectator() && player.isAlive()) {
                    float healthRatio = player.getHealth() / player.getMaxHealth();
                    if (healthRatio > 0 && healthRatio <= 0.30f) {
                        long now = System.currentTimeMillis();
                        if (now - lastHeartbeatTime >= 900) {
                            lastHeartbeatTime = now;
                            playHeartbeatSound();
                        }
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
        if (!AlpakaConfig.instance.customSoundButtonClick) return;
        playSound(BUTTON_CLICK_SOUND, 1.0f);
    }

    public static void playBlazeDeathSound() {
        playSound(BLAZE_DEATH_SOUND);
    }

    public static void playInventoryClickSound() {
        playSound(INVENTORY_CLICK_SOUND);
    }

    public static void playHeartbeatSound() {
        if (!AlpakaConfig.instance.customSoundLowHpHeartbeat) return;
        playSound(HEARTBEAT_SOUND);
    }

    public static void playBossSpawnSound() {
        if (!AlpakaConfig.instance.customSoundNotification) return;
        playSound(BOSS_SPAWN_SOUND);
    }

    public static void playInventoryOpenSound() {
        if (!AlpakaConfig.instance.customSoundInventoryOpenClose) return;
        playSound(INVENTORY_OPEN_SOUND);
    }

    public static void playInventoryCloseSound() {
        if (!AlpakaConfig.instance.customSoundInventoryOpenClose) return;
        playSound(INVENTORY_CLOSE_SOUND);
    }

    public static void playZombieRemedySound() {
        playSound(ZOMBIE_REMEDY_SOUND);
    }

    public static void playHitSound() {
        playSound(SUCCESSFUL_HIT_SOUND);
    }

    public static void playHotbarEquipSound() {
        if (!AlpakaConfig.instance.customSoundHotbarScroll) return;
        playSound(HOTBAR_EQUIP_SOUND);
    }

    public static void playRareDropSound() {
        if (!AlpakaConfig.instance.customSoundRareDrop) return;
        playSound(RARE_DROP_SOUND);
    }

    public static void playInsaneDropSound() {
        if (!AlpakaConfig.instance.customSoundRareDrop) return;
        playSound(INSANE_DROP_SOUND);
    }

    private static void playSound(SoundEvent sound) {
        playSound(sound, 1.0f);
    }

    private static void playSound(SoundEvent sound, float pitch) {
        if (!AlpakaConfig.instance.customSoundsEnabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null && sound != null) {
            float vol = AlpakaConfig.instance.customSoundsVolume;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, vol));
        }
    }
}
