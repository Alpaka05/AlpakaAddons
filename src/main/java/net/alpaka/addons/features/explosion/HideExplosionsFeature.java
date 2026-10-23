package net.alpaka.addons.features.explosion;

import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Hides explosions, like SkyHanni's Explosions Hider.
 *
 * Hypixel shows an explosion (Hyperion, Bonzo's Staff, TNT, creepers, ...) in one of two ways, and
 * both are stopped where the packet is handled ({@code ClientPacketListenerMixin}), before vanilla
 * does any work for it:
 * <ul>
 *   <li>a particle packet with explosion, explosion emitter or poof particles (poof is what the
 *       old 1.8 "explode" particle became). The whole packet is dropped before vanilla's loop
 *       rolls positions and builds one particle per count, so a hidden explosion costs less than a
 *       shown one. Same three types as SkyHanni. Only server-sent poofs go: the smoke vanilla makes
 *       itself when a mob dies is left to Hide Death Smoke.</li>
 *   <li>an explode packet. Its flash particle is skipped, and it is never handed to the explosion
 *       tracker, which would otherwise look up blocks and spawn smoke and dust around it for the
 *       next ticks.</li>
 * </ul>
 * Nothing is added to the per-particle path every other particle goes through, so the feature costs
 * one boolean read per particle or explode packet while it is off. Sound and knockback stay.
 */
public final class HideExplosionsFeature {

    private HideExplosionsFeature() {
    }

    public static boolean shouldHideParticlePacket(ParticleOptions options) {
        if (!AlpakaConfig.instance.hideExplosions) return false;
        ParticleType<?> type = options.getType();
        return type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER || type == ParticleTypes.POOF;
    }

    public static boolean shouldHideExplodePacket() {
        return AlpakaConfig.instance.hideExplosions;
    }
}
