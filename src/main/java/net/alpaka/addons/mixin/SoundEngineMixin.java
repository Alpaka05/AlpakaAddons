package net.alpaka.addons.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.etherwarp.EtherwarpOverlayFeature;
import net.alpaka.addons.features.slayer.SlayerQuestDetector;
import net.alpaka.addons.features.slayer.SlayerType;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.alpaka.addons.features.sound.LocalAttackTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.Blaze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Custom sounds and the Blaze slayer mute.
 *
 * Decided at the volume calculation inside {@link SoundEngine#play}, not at its head. By then the
 * sound has been resolved, other mods' hooks have seen it - SkyHanni reads item cooldowns off
 * sounds there - and the subtitle listeners have shown it. A sound the mod mutes or replaces gets a
 * volume of zero, which vanilla answers by not starting it; a replacement is started once
 * {@code play} has returned, so nothing re-enters it halfway through. Cancelling at the head, as
 * this used to, hid those sounds from SkyHanni and broke its Hyperion cooldown.
 */
@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    /** Namespace of every sound this mod registers; never muted or replaced. */
    @Unique
    private static final String ALPAKA_NAMESPACE = "alpaka";
    @Unique
    private static final String MINECRAFT_NAMESPACE = "minecraft";

    /** Squared distance within which a sound counts as coming from the local player. */
    @Unique
    private static final double OWN_SOUND_RADIUS_SQR = 4.0d;
    /** The same for the remedy sound, which a teleport can move a little further away. */
    @Unique
    private static final double OWN_REMEDY_RADIUS_SQR = 9.0d;

    /** One hit can reach the client twice; the second hurt sound within this window is dropped. */
    @Unique
    private static final long HURT_DEBOUNCE_MS = 100L;

    /**
     * Blaze slayer noise muted in the default mode: the blazes themselves, their fire, the lava and
     * ghasts around the Crimson Isle, and its lightning.
     */
    @Unique
    private static final Set<String> BLAZE_NOISE = Set.of(
            "entity.blaze.ambient", "entity.blaze.burn", "entity.blaze.hurt", "entity.blaze.shoot",
            "entity.blaze.death", "entity.ghast.shoot", "entity.ghast.warn", "block.fire.ambient",
            "block.lava.pop", "block.lava.ambient", "entity.generic.burn",
            "entity.lightning_bolt.thunder", "entity.lightning_bolt.impact");

    /** Sources the "all world audio" mode mutes. UI, master, voice and music are never touched. */
    @Unique
    private static final Set<SoundSource> WORLD_SOURCES = Set.of(
            SoundSource.BLOCKS, SoundSource.HOSTILE, SoundSource.NEUTRAL, SoundSource.PLAYERS,
            SoundSource.AMBIENT, SoundSource.WEATHER);

    /** The stand-in to start once the current {@code play} has returned, or null. */
    @Unique
    private static Runnable alpaka$pendingReplacement = null;
    @Unique
    private static long alpaka$lastHurtMs = 0L;

    @ModifyExpressionValue(
            method = "play",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F")
    )
    private float alpaka$muteOrReplace(float volume, @Local(argsOnly = true) SoundInstance sound) {
        Identifier id = sound.getIdentifier();
        if (id == null || ALPAKA_NAMESPACE.equals(id.getNamespace())) return volume;

        // The Etherwarp cue has its own toggle rather than living under Custom Sounds.
        if (sound instanceof AbstractSoundInstanceAccessor raw
                && EtherwarpOverlayFeature.isOwnWarpSound(id, raw.alpaka$rawPitch(), sound.getX(), sound.getY(), sound.getZ())) {
            alpaka$pendingReplacement = EtherwarpOverlayFeature::playWarpSound;
            return 0.0f;
        }

        // Replacements first: every custom sound is triggered by the vanilla sound it stands in for.
        Runnable replacement = alpaka$replacementFor(sound, id);
        if (replacement != null) {
            alpaka$pendingReplacement = replacement;
            return 0.0f;
        }

        return alpaka$shouldMute(sound, id) ? 0.0f : volume;
    }

    /** Starts the stand-in after the original has been dropped. */
    @Inject(method = "play", at = @At("RETURN"))
    private void alpaka$startReplacement(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        Runnable replacement = alpaka$pendingReplacement;
        if (replacement == null) return;
        alpaka$pendingReplacement = null;
        replacement.run();
    }

    /**
     * The custom sound standing in for this one, or null. Only vanilla sounds are matched, by their
     * exact id, and only the player's own: another player's crit, hurt or kill keeps its vanilla
     * sound. Each rule returns null when its own toggle is off, so switching a custom sound off
     * brings the vanilla one back.
     */
    @Unique
    private static Runnable alpaka$replacementFor(SoundInstance sound, Identifier id) {
        AlpakaConfig cfg = AlpakaConfig.instance;
        if (!cfg.customSoundsEnabled || !MINECRAFT_NAMESPACE.equals(id.getNamespace())) return null;

        String path = id.getPath();
        switch (path) {
            case "ui.button.click" -> {
                // Hypixel's menu clicks arrive as this too, so inside a container it is the
                // inventory click; everywhere else the button click.
                if (cfg.customSoundInventoryClick && Minecraft.getInstance().gui.screen() instanceof AbstractContainerScreen<?>) {
                    return CustomSoundFeature::playInventoryClickSound;
                }
                return cfg.customSoundButtonClick ? CustomSoundFeature::playButtonClickSound : null;
            }
            case "entity.blaze.death" -> {
                // Emitted wherever the blaze died, which is just as often somebody else's blaze.
                if (!cfg.customSoundBlazeDeath) return null;
                return LocalAttackTracker.wasAttackedByUsNear(Blaze.class, sound.getX(), sound.getY(), sound.getZ())
                        ? CustomSoundFeature::playBlazeDeathSound : null;
            }
            case "entity.zombie_villager.cure" -> {
                if (!cfg.customSoundZombieRemedy) return null;
                return alpaka$isNearLocalPlayer(sound, OWN_REMEDY_RADIUS_SQR) && LocalAttackTracker.usedItemRecently()
                        ? CustomSoundFeature::playZombieRemedySound : null;
            }
            case "entity.arrow.hit_player" -> {
                // Only ever played for arrows the local player shot.
                return cfg.customSoundSuccessfulHit ? CustomSoundFeature::playHitSound : null;
            }
            case "entity.player.attack.crit" -> {
                if (!cfg.customSoundSuccessfulHit) return null;
                return alpaka$isNearLocalPlayer(sound, OWN_SOUND_RADIUS_SQR) ? CustomSoundFeature::playHitSound : null;
            }
            case "entity.player.hurt", "entity.player.hurt_drown", "entity.player.hurt_on_fire",
                 "entity.player.hurt_freeze", "entity.player.hurt_sweet_berry_bush" -> {
                if (!cfg.customSoundPlayerHurt || !alpaka$isNearLocalPlayer(sound, OWN_SOUND_RADIUS_SQR)) return null;
                long now = System.currentTimeMillis();
                if (now - alpaka$lastHurtMs < HURT_DEBOUNCE_MS) return () -> {};
                alpaka$lastHurtMs = now;
                return CustomSoundFeature::playDamageSound;
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * The Blaze slayer mute, for sounds no replacement claimed, while a Blaze quest runs. By default
     * only the blaze fight's own noise; optionally every world sound. Never the UI, master, voice or
     * music channels, and never this mod's own sounds, so other mods' menu alerts still play.
     */
    @Unique
    private static boolean alpaka$shouldMute(SoundInstance sound, Identifier id) {
        AlpakaConfig cfg = AlpakaConfig.instance;
        if (!cfg.muteVanillaSoundsInBlazeSlayer) return false;
        if (SlayerQuestDetector.INSTANCE.getActiveType() != SlayerType.BLAZE) return false;
        if (cfg.blazeMuteAllWorldAudio) return WORLD_SOURCES.contains(sound.getSource());
        return MINECRAFT_NAMESPACE.equals(id.getNamespace()) && BLAZE_NOISE.contains(id.getPath());
    }

    /**
     * Whether a sound was emitted at the local player's position. A server-sent sound can be
     * rounded to block coordinates, hence the slack.
     */
    @Unique
    private static boolean alpaka$isNearLocalPlayer(SoundInstance sound, double radiusSqr) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        double dx = mc.player.getX() - sound.getX();
        double dy = mc.player.getY() - sound.getY();
        double dz = mc.player.getZ() - sound.getZ();
        return dx * dx + dy * dy + dz * dz <= radiusSqr;
    }
}
