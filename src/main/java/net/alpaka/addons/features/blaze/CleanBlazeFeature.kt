package net.alpaka.addons.features.blaze

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.slayer.SlayerSessionTracker
import net.alpaka.addons.features.slayer.SlayerType
import net.alpaka.addons.utils.SkyblockUtils
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball

/**
 * Strips the visual noise off Hypixel's blazes while slaying them: their flame and smoke particles,
 * the burning overlay and the fireballs they throw, each with its own toggle, and optionally their
 * name tags.
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
 * SkyHanni's Blaze Slayer Clear View does much the same; running both is harmless but redundant.
 *
 * Purely about how the blazes look. The slayer chatter has its own setting in
 * [net.alpaka.addons.features.slayer.SlayerChatFilter].
 */
object CleanBlazeFeature {

    /** How often the zone is re-checked; particles ask many times per frame. */
    private const val SCOPE_REFRESH_MS = 250L

    private var scopeCheckedAtMs = 0L
    private var inScope = false

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
        val type = options.type
        if (type !== ParticleTypes.FLAME && type !== ParticleTypes.SMALL_FLAME &&
            type !== ParticleTypes.SMOKE && type !== ParticleTypes.LARGE_SMOKE
        ) return false
        return AlpakaConfig.instance.cleanBlazeParticles && active()
    }

    /** Only blazes lose their fire; the player's own burning, and every other mob's, stays. */
    @JvmStatic
    fun shouldHideEntityFire(entity: Entity, state: EntityRenderState) {
        if (entity is Blaze && AlpakaConfig.instance.cleanBlazeFire && active()) {
            state.displayFireAnimation = false
        }
    }

    @JvmStatic
    fun shouldStopBlazeRodSpin(): Boolean {
        return AlpakaConfig.instance.stopBlazeSpinning && SkyblockUtils.isOnSkyblock()
    }

    /** Off unless asked for: the health on these tags is what a grinder watches. */
    @JvmStatic
    fun shouldHideNameTag(entity: Entity): Boolean {
        if (!AlpakaConfig.instance.cleanBlazeNameTags || !active()) return false
        if (entity is Blaze) return true
        val customName = entity.customName ?: return false
        return SkyblockUtils.containsIgnoringFormatting(customName.string, "Smoldering Blaze")
    }

    @JvmStatic
    fun shouldHideEntity(entity: Entity): Boolean {
        if (entity is SmallFireball) return AlpakaConfig.instance.cleanBlazeFireballs && active()
        if (!entity.hasCustomName() || !AlpakaConfig.instance.cleanBlazeNameTags || !active()) return false
        val customName = entity.customName ?: return false
        return SkyblockUtils.containsIgnoringFormatting(customName.string, "Smoldering Blaze")
    }
}
