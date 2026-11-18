package net.alpaka.addons.features.blaze

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.slayer.SlayerSessionTracker
import net.alpaka.addons.features.slayer.SlayerType
import net.alpaka.addons.utils.EntityCache
import net.alpaka.addons.utils.SkyblockUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.network.chat.Component
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LightningBolt
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball

/**
 * Strips the visual noise off Hypixel's blazes while slaying them: their flame, smoke, angry
 * villager and lava drip particles, the potion swirls, enchanting glyphs and ender particles
 * around the boss and its demons (the ones SkyHanni's slayer spawn particle hider lists), the
 * attunement coloured dust around them, the burning overlay, the fireballs they throw and lightning, each with its own toggle, and optionally
 * their name tags.
 *
 * Only on SkyBlock, in the Blaze slayer's zones. It used to apply
 * everywhere: every flame and torch particle in every world, the fire on every entity including the
 * player, every small fireball, and any name containing "Blaze", which also hid the Dungeons blaze
 * puzzle's health. Name tags now stay visible unless the player asks otherwise, since grinders read
 * the health on them.
 *
 * Deliberately not tied to an active Blaze quest: the quest stays active wherever the player goes,
 * so walking into the Magma Chamber with one running hid the Magma Boss's flame rings, which deal
 * real damage and have to be seen to be dodged.
 *
 * The burning overlay also comes off the slayer's minibosses and the boss's demons, which are not
 * all blazes and so are recognised by the name tag Hypixel stacks above them instead.
 *
 * SkyHanni's Blaze Slayer Clear View does much the same; running both is harmless but redundant.
 *
 * The boss fight's chat lines (wrong dagger, damage) are the Message Filter's, in
 * [net.alpaka.addons.features.slayer.SlayerChatFilter].
 */
object CleanBlazeFeature {

    /** How often the zone is re-checked; particles ask many times per frame. */
    private const val SCOPE_REFRESH_MS = 250L

    private var scopeCheckedAtMs = 0L
    private var inScope = false

    /**
     * Name tags of the Blaze slayer's mobs that are not blazes, or not always: the boss, the three
     * minibosses, and the two demons the boss summons from tier III, written in circled letters.
     * Names as SkyHanni lists them.
     */
    private val DEMON_NAMES = arrayOf(
        "Inferno Demonlord",
        "Flare Demon",
        "Kindleheart Demon",
        "Burningsoul Demon",
        "ⓆⓊⒶⓏⒾⒾ",
        "ⓉⓎⓅⒽⓄⒺⓊⓈ",
    )

    /** On SkyBlock and in a Blaze zone. Cached, since every particle asks. */
    private fun active(): Boolean {
        if (!AlpakaConfig.instance.cleanBlazeEnabled) return false
        val now = System.currentTimeMillis()
        if (now - scopeCheckedAtMs >= SCOPE_REFRESH_MS) {
            scopeCheckedAtMs = now
            inScope = SkyblockUtils.isOnSkyblock() && SlayerSessionTracker.isInTrackerArea(SlayerType.BLAZE)
        }
        return inScope
    }

    @JvmStatic
    fun shouldCancelParticle(options: ParticleOptions): Boolean {
        // Every particle the game makes comes through here; with the feature off nothing below can
        // say yes, so skip the type checks.
        if (!AlpakaConfig.instance.cleanBlazeEnabled) return false
        val type = options.type
        if (type === ParticleTypes.FLAME || type === ParticleTypes.SMALL_FLAME ||
            type === ParticleTypes.SMOKE || type === ParticleTypes.LARGE_SMOKE ||
            type === ParticleTypes.ANGRY_VILLAGER ||
            // Lava drips, which some of the minibosses shed: hanging, falling and the splash on landing.
            type === ParticleTypes.DRIPPING_LAVA || type === ParticleTypes.FALLING_LAVA ||
            type === ParticleTypes.LANDING_LAVA
        ) return AlpakaConfig.instance.cleanBlazeParticles && active()
        if (type === ParticleTypes.ENTITY_EFFECT || type === ParticleTypes.EFFECT ||
            type === ParticleTypes.INSTANT_EFFECT || type === ParticleTypes.WITCH ||
            type === ParticleTypes.ENCHANT || type === ParticleTypes.PORTAL
        ) return AlpakaConfig.instance.cleanBlazeEffectParticles && active()
        // The coloured dust around the boss and its demons, in the colour of the attunement their
        // Hellion Shield wants (white Spirit, dark grey Ashen, cyan Crystal, yellow Auric). The
        // name tag shows the attunement too, so the dust is only noise.
        if (type === ParticleTypes.DUST) return AlpakaConfig.instance.cleanBlazeAttunementDust && active()
        return false
    }

    /**
     * Only the slayer's mobs lose their fire - blazes, minibosses and demons. The player's own
     * burning, and every other mob's, stays.
     */
    @JvmStatic
    fun shouldHideEntityFire(entity: Entity, state: EntityRenderState) {
        if (!state.displayFireAnimation || !AlpakaConfig.instance.cleanBlazeFire || !active()) return
        if (entity is Blaze || isDemon(entity)) {
            state.displayFireAnimation = false
        }
    }

    /**
     * Whether this mob's name tag names a Blaze slayer miniboss, demon or the boss.
     *
     * Hypixel gives a mob's name tag the entity id straight after the mob's own, the same
     * assumption [net.alpaka.addons.features.slayer.SlayerBossEntityTracker] resolves the boss by.
     */
    private fun isDemon(entity: Entity): Boolean {
        if (entity !is LivingEntity || entity is ArmorStand) return false
        val tag = Minecraft.getInstance().level?.getEntity(entity.id + 1) as? ArmorStand ?: return false
        val name = tag.customName ?: return false
        val cached = demonTags.get(tag.id)
        if (cached != null && cached.first === name) return cached.second
        val raw = name.string
        val demon = DEMON_NAMES.any { SkyblockUtils.containsIgnoringFormatting(raw, it) }
        if (demonTags.size() >= TAG_CACHE_LIMIT) demonTags.clear()
        demonTags.put(tag.id, name to demon)
        return demon
    }

    /**
     * Verdicts on name tags, kept with the name object they were read from. Both checks run per
     * entity and frame in a Blaze fight, and a name only changes when the server sends a new object,
     * so the answer depends on that object alone; the identity check also keeps a reused id from
     * another world from getting an old answer. Render thread only.
     */
    private val demonTags = EntityCache<Pair<Component, Boolean>>()
    private val smolderingTags = EntityCache<Pair<Component, Boolean>>()
    private const val TAG_CACHE_LIMIT = 512

    /** A part of Clean Blaze, but anywhere on SkyBlock: blazes spin outside the slayer zones too. */
    @JvmStatic
    fun shouldStopBlazeRodSpin(): Boolean {
        val cfg = AlpakaConfig.instance
        return cfg.cleanBlazeEnabled && cfg.stopBlazeSpinning && SkyblockUtils.isOnSkyblock()
    }

    @JvmStatic
    fun shouldHideEntity(entity: Entity): Boolean {
        if (entity is SmallFireball) return AlpakaConfig.instance.cleanBlazeFireballs && active()
        if (entity is LightningBolt) return hideLightning()
        // The setting before the entity: this runs for every entity on every frame.
        if (!AlpakaConfig.instance.cleanBlazeNameTags || !entity.hasCustomName() || !active()) return false
        val customName = entity.customName ?: return false
        val cached = smolderingTags.get(entity.id)
        if (cached != null && cached.first === customName) return cached.second
        val hide = SkyblockUtils.containsIgnoringFormatting(customName.string, "Smoldering Blaze")
        if (smolderingTags.size() >= TAG_CACHE_LIMIT) smolderingTags.clear()
        smolderingTags.put(entity.id, customName to hide)
        return hide
    }

    /** Lightning in the Blaze zones: the bolt itself and the sky flash it causes. The thunder stays. */
    @JvmStatic
    fun hideLightning(): Boolean = AlpakaConfig.instance.cleanBlazeLightning && active()
}
