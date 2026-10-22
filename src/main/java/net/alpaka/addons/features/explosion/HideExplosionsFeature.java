package net.alpaka.addons.features.explosion;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Hides explosions, like SkyHanni's Hide Explosions.
 *
 * Hypixel sends most explosions (Hyperion, Bonzo's Staff, TNT, creepers, ...) either as plain
 * explosion particles or as an explode packet. Both end up in {@code ParticleEngine.createParticle},
 * so the big flash and the explosion cloud are dropped there ({@code ParticleEngineMixin}). The
 * explode packet also throws smoke and dust from the blocks around it, which vanilla spawns later
 * from {@code ClientLevel.trackExplosionEffects} ({@code ClientLevelMixin}); that goes too.
 *
 * Purely visual: the sound and the knockback the server sends stay as they are.
 */
public final class HideExplosionsFeature {

    private HideExplosionsFeature() {
    }

    public static boolean shouldCancelParticle(ParticleOptions options) {
        if (!AlpakaConfig.instance.hideExplosions) return false;
        return options.getType() == ParticleTypes.EXPLOSION || options.getType() == ParticleTypes.EXPLOSION_EMITTER;
    }

    public static boolean shouldHideBlockDebris() {
        return AlpakaConfig.instance.hideExplosions;
    }
}
