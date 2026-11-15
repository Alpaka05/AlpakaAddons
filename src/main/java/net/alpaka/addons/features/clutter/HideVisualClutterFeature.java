package net.alpaka.addons.features.clutter;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.mixin.AbstractArrowInGroundAccessor;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;

/**
 * Hides effects that cost frames and tell the player nothing: explosions, falling blocks, the
 * terrain particles of broken blocks, the sparks of critical hits, firework bursts, experience
 * orbs, the Ender Dragon's death and arrows stuck in players and blocks. Each has its own switch under the feature's master switch.
 * It started as Hide Explosions, which is why its master field still reads that name from older
 * files.
 *
 * Each effect is stopped where it starts, before vanilla does any work for it: a packet that would
 * create it is dropped, or the client call that would spawn its particles is skipped. Nothing is
 * added to the path every particle goes through, so while the feature is off each hook costs one
 * boolean read, and only when such an effect happens.
 *
 * What stays on purpose, because other features or the game itself read it:
 * <ul>
 *   <li>Particles the server sends by type are only filtered for explosions. Crit, enchanted hit,
 *       firework, lava drip and happy villager particles from the server are how Hypixel shows
 *       Diana burrows, Precision Mining spots, Invisibugs, the Mage beam and pests, so the hit
 *       sparks and firework bursts are hidden at their client-side source instead
 *       (the attack animation packet, the local attack, crit arrows and the firework rocket's
 *       explosion event).</li>
 *   <li>Flame, smoke, lava, potion swirl, enchant, portal and dust particles, fireballs and
 *       lightning belong to Clean Blaze, and the death puff of mobs to Hide Death Smoke.</li>
 *   <li>Sounds, knockback and damage are server side and stay.</li>
 * </ul>
 */
public final class HideVisualClutterFeature {

    private HideVisualClutterFeature() {
    }

    private static boolean on(boolean part) {
        AlpakaConfig config = AlpakaConfig.instance;
        return config.hideVisualClutter && part;
    }

    // Explosions -------------------------------------------------------------------------------

    /**
     * A particle packet with explosion, explosion emitter or poof particles (poof is what the old
     * 1.8 "explode" particle became): how Hypixel shows Hyperion, Bonzo's Staff, TNT, creepers.
     * The whole packet is dropped before vanilla's loop rolls positions and builds one particle per
     * count. Same three types as SkyHanni's Explosions Hider. Only server-sent poofs go: the smoke
     * vanilla makes itself when a mob dies is left to Hide Death Smoke.
     */
    public static boolean shouldHideParticlePacket(ParticleOptions options) {
        if (!on(AlpakaConfig.instance.clutterExplosions)) return false;
        ParticleType<?> type = options.getType();
        return type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER || type == ParticleTypes.POOF;
    }

    /**
     * An explode packet: its flash particle is skipped, and it is never handed to the explosion
     * tracker, which would otherwise look up blocks and spawn smoke and dust around it for the
     * next ticks.
     */
    public static boolean shouldHideExplodePacket() {
        return on(AlpakaConfig.instance.clutterExplosions);
    }

    // Entities that are never added ------------------------------------------------------------

    /**
     * Falling blocks (Molten Wave's gold, Seismic Wave, debris) and experience orbs. The packet that
     * adds one is dropped, so it is never built, ticked or drawn; a falling block is redrawn as a
     * full block model every frame, and a burst of orbs moves and merges every tick. Neither can be
     * collided with, and both are client copies of server entities: what lands, what is picked up
     * and what deals damage is decided by the server either way.
     */
    public static boolean shouldSkipEntity(EntityType<?> type) {
        AlpakaConfig config = AlpakaConfig.instance;
        if (!config.hideVisualClutter) return false;
        if (type == EntityTypes.FALLING_BLOCK) return config.clutterFallingBlocks;
        if (type == EntityTypes.EXPERIENCE_ORB) return config.clutterExperienceOrbs;
        return false;
    }

    // Block particles --------------------------------------------------------------------------

    /**
     * The terrain particles of a broken block (up to 64 per block, from every block anyone breaks
     * nearby, farming included) and the small ones while a block is being mined. The crack overlay
     * on the block being mined stays.
     */
    public static boolean shouldHideBlockParticles() {
        return on(AlpakaConfig.instance.clutterBlockBreak);
    }

    // Hit particles ----------------------------------------------------------------------------

    /**
     * A critical or enchanted hit spark emitter: 16 particles a tick for three ticks per hit, from
     * the attack animation packet and from the player's own attacks. Every enchanted weapon makes
     * one on every hit, and SkyBlock's crits are its own stat, not vanilla's jump crits, so the
     * sparks say nothing. Only these two emitter types, so a totem's emitter stays.
     */
    public static boolean shouldHideTrackingEmitter(ParticleOptions options) {
        if (!on(AlpakaConfig.instance.clutterHitParticles)) return false;
        ParticleType<?> type = options.getType();
        return type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT;
    }

    /** The four crit particles a fully drawn arrow trails every tick, on the client only. */
    public static boolean shouldHideArrowTrail() {
        return on(AlpakaConfig.instance.clutterHitParticles);
    }

    // Fireworks --------------------------------------------------------------------------------

    /**
     * A firework rocket's burst: one particle that spawns the stars and their trails and sparkles,
     * hundreds for a big rocket, and plays the blast and twinkle sounds itself. Firework particles
     * the server sends on their own (Diana's spade trail, Mage beam) are a different path and stay.
     */
    public static boolean shouldHideFireworkBurst() {
        return on(AlpakaConfig.instance.clutterFireworks);
    }

    // Entities that are not drawn ---------------------------------------------------------------

    /**
     * A dying Ender Dragon (the End's, and the Wither King's in Master Mode 7): it rises for ten
     * seconds while light rays grow out of it and explosions go off around it. The whole dying
     * dragon is skipped; the boss is dead by then, its loot and score are server side.
     *
     * An arrow stuck in a block: it lies there for a minute, and a fight with bows leaves dozens.
     * Only drawing is skipped, the entity stays, and an arrow in flight is always drawn. Tridents
     * are left alone, a stuck one is a weapon waiting to be picked up or to return.
     */
    public static boolean shouldHideEntity(Entity entity) {
        AlpakaConfig config = AlpakaConfig.instance;
        if (!config.hideVisualClutter) return false;
        if (entity instanceof AbstractArrow arrow) {
            return config.clutterAttachedArrows && !(arrow instanceof ThrownTrident)
                    && ((AbstractArrowInGroundAccessor) arrow).alpaka$isInGround();
        }
        return entity instanceof EnderDragon dragon && config.clutterDragonDeath && isDying(dragon);
    }

    /**
     * The arrows stuck in a player's body, which vanilla keeps on them for a while after every hit
     * and which pile up into a pincushion in a bow fight. They only ever show on players.
     */
    public static boolean shouldHideStuckArrows() {
        return on(AlpakaConfig.instance.clutterAttachedArrows);
    }

    // Ender Dragon death -----------------------------------------------------------------------

    /** The explosions vanilla spawns around a dying dragon on the client, in its last second. */
    public static boolean shouldHideDragonDeathParticles() {
        return on(AlpakaConfig.instance.clutterDragonDeath);
    }

    private static boolean isDying(EnderDragon dragon) {
        return dragon.dragonDeathTime > 0 || dragon.isDeadOrDying();
    }
}
