package net.alpaka.addons.mixin;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.slayer.SlayerQuestDetector;
import net.alpaka.addons.features.slayer.SlayerType;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.alpaka.addons.features.sound.LocalAttackTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.Blaze;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public class SoundEngineMixin {

    /** Namespace of every sound this mod registers; never silenced. */
    private static final String ALPAKA_NAMESPACE = "alpaka";

    /** Which custom sound, if any, stands in for the vanilla sound being played. */
    private static final int REPLACE_NOTHING = 0;
    private static final int REPLACE_BUTTON_CLICK = 1;
    private static final int REPLACE_BLAZE_DEATH = 2;
    private static final int REPLACE_INVENTORY_CLICK = 3;
    private static final int REPLACE_ZOMBIE_REMEDY = 4;
    private static final int REPLACE_HIT = 5;
    private static final int REPLACE_PLAYER_HURT = 6;

    /** Squared distance within which a sound counts as coming from the local player. */
    private static final double OWN_SOUND_RADIUS_SQR = 4.0d;

    private static boolean IS_INTERNAL_PLAY = false;

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void onPlaySound(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (IS_INTERNAL_PLAY) return;
        if (sound == null || sound.getIdentifier() == null) return;

        Identifier id = sound.getIdentifier();

        // Replacements are resolved BEFORE any silencing. Every custom sound in this mod is
        // triggered by the vanilla sound it stands in for, so silencing first threw away the
        // trigger and took the custom hit, click and death sounds down with it.
        if (tryReplace(sound, id, cir)) return;

        if (shouldSilence(id)) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }

    /**
     * Blaze-slayer silence, applied only to sounds no custom replacement claimed.
     *
     * While this is on, the only audio that survives is this mod's own: anything in the "alpaka"
     * namespace. That includes the replacements resolved before this runs, so a vanilla sound the
     * mod stands in for is still heard - as the custom version. A replacement whose own toggle is
     * off claims nothing, and so falls through to here and is silenced along with everything else;
     * turning a custom sound off while this is on means silence rather than the vanilla sound,
     * which is the point of the feature.
     *
     * Deliberately not gated behind customSoundsEnabled: this suppresses the game's own audio
     * rather than adding to it, and it exists because Hypixel layers so much sound onto a blaze
     * fight that the mod's own cues - the boss spawn chime especially - are drowned out.
     *
     * activeType is read straight from the field rather than through currentOrRecent(): the field is
     * already refreshed once per client tick, so re-parsing the scoreboard from whatever thread is
     * starting a sound would be needless work.
     */
    private boolean shouldSilence(Identifier id) {
        if (!AlpakaConfig.instance.muteVanillaSoundsInBlazeSlayer) return false;
        if (ALPAKA_NAMESPACE.equals(id.getNamespace())) return false;
        return SlayerQuestDetector.INSTANCE.getActiveType() == SlayerType.BLAZE;
    }

    /** Plays our stand-in for a vanilla sound and cancels the original; false if nothing matched. */
    private boolean tryReplace(SoundInstance sound, Identifier id, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (!AlpakaConfig.instance.customSoundsEnabled) return false;

        int replacement = classify(sound, id.getPath());
        if (replacement == REPLACE_NOTHING) return false;

        // IS_INTERNAL_PLAY keeps the nested play() call from reaching this injector again.
        IS_INTERNAL_PLAY = true;
        try {
            switch (replacement) {
                case REPLACE_BUTTON_CLICK -> CustomSoundFeature.playButtonClickSound();
                case REPLACE_BLAZE_DEATH -> CustomSoundFeature.playBlazeDeathSound();
                case REPLACE_INVENTORY_CLICK -> CustomSoundFeature.playInventoryClickSound();
                case REPLACE_ZOMBIE_REMEDY -> CustomSoundFeature.playZombieRemedySound();
                case REPLACE_HIT -> CustomSoundFeature.playHitSound();
                case REPLACE_PLAYER_HURT -> CustomSoundFeature.playDamageSound();
                default -> { }
            }
        } finally {
            IS_INTERNAL_PLAY = false;
        }

        cir.setReturnValue(SoundEngine.PlayResult.STARTED);
        return true;
    }

    /**
     * Works out which custom sound replaces this one.
     *
     * Every branch returns REPLACE_NOTHING when its own toggle is off, rather than replacing with
     * silence, so switching a custom sound off normally restores the vanilla one. The exception is
     * while the blaze-slayer silence is running, which then swallows that unclaimed vanilla sound -
     * see shouldSilence.
     */
    private int classify(SoundInstance sound, String path) {
        if (path.contains("button.click") || "gui.button.press".equals(path) || "ui.button.click".equals(path)) {
            return AlpakaConfig.instance.customSoundButtonClick ? REPLACE_BUTTON_CLICK : REPLACE_NOTHING;
        }

        if ("entity.blaze.death".equals(path) || path.contains("blaze/death")) {
            if (!AlpakaConfig.instance.customSoundBlazeDeath) return REPLACE_NOTHING;
            // Only our own kills; the sound is emitted wherever the blaze died, which is just as
            // often somebody else's blaze a few blocks away.
            return LocalAttackTracker.wasAttackedByUsNear(Blaze.class, sound.getX(), sound.getY(), sound.getZ())
                    ? REPLACE_BLAZE_DEATH : REPLACE_NOTHING;
        }

        if ("item.pickup".equals(path) || "container.click".equals(path)) {
            return AlpakaConfig.instance.customSoundInventoryClick ? REPLACE_INVENTORY_CLICK : REPLACE_NOTHING;
        }

        if ("entity.zombie_villager.cure".equals(path) || path.contains("remedy")) {
            return AlpakaConfig.instance.customSoundZombieRemedy ? REPLACE_ZOMBIE_REMEDY : REPLACE_NOTHING;
        }

        if ("entity.arrow.hit_player".equals(path) || "entity.player.attack.crit".equals(path) || path.contains("successful_hit")) {
            return AlpakaConfig.instance.customSoundSuccessfulHit ? REPLACE_HIT : REPLACE_NOTHING;
        }

        // Player hurt sounds the server plays directly. PlayerMixin already swaps the sound that
        // Player.getHurtSound returns, which covers damage the client resolves itself, but Hypixel
        // largely drives damage from the server - so the sound arrives as plain
        // "minecraft:entity.player.hurt" and never passes through getHurtSound at all. Catching it
        // here covers that case too. The two cannot double up: once getHurtSound has done its job
        // the path reads "player_hurt", which no branch here matches.
        if (path.startsWith("entity.player.hurt")) {
            if (!AlpakaConfig.instance.customSoundPlayerHurt) return REPLACE_NOTHING;
            return isFromLocalPlayer(sound) ? REPLACE_PLAYER_HURT : REPLACE_NOTHING;
        }

        return REPLACE_NOTHING;
    }

    /**
     * Whether a sound was emitted at the local player's position.
     *
     * Hurt sounds are positioned on the player they belong to, so this keeps another player's hurt
     * sound from being swapped for ours. The radius has to allow a little slack because a
     * server-sent sound can be rounded to block coordinates, which does mean a player standing
     * right on top of us is indistinguishable - harmless for a hurt sound.
     */
    private boolean isFromLocalPlayer(SoundInstance sound) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        double dx = mc.player.getX() - sound.getX();
        double dy = mc.player.getY() - sound.getY();
        double dz = mc.player.getZ() - sound.getZ();
        return dx * dx + dy * dy + dz * dz <= OWN_SOUND_RADIUS_SQR;
    }
}
