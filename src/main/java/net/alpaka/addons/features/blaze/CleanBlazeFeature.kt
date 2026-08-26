package net.alpaka.addons.features.blaze

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.utils.SkyblockUtils
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball

/**
 * Strips the visual noise off Hypixel's blazes: their flame and smoke particles, the burning
 * overlay, their name tags, and the fireballs they throw.
 *
 * Purely about how the blazes look. The slayer chatter that used to be hidden from here now has its
 * own setting in [net.alpaka.addons.features.slayer.SlayerChatFilter], so the effects and the
 * messages can be switched independently.
 */
object CleanBlazeFeature {

    @JvmStatic
    fun shouldCancelParticle(options: ParticleOptions): Boolean {
        if (!AlpakaConfig.instance.cleanBlazeEnabled) return false

        val type = options.type
        return type == ParticleTypes.FLAME ||
               type == ParticleTypes.SMALL_FLAME ||
               type == ParticleTypes.SMOKE ||
               type == ParticleTypes.LARGE_SMOKE
    }

    @JvmStatic
    fun shouldHideEntityFire(state: EntityRenderState) {
        if (AlpakaConfig.instance.cleanBlazeEnabled) {
            state.displayFireAnimation = false
        }
    }

    @JvmStatic
    fun shouldStopBlazeRodSpin(): Boolean {
        return AlpakaConfig.instance.stopBlazeSpinning
    }

    @JvmStatic
    fun shouldHideNameTag(entity: Entity): Boolean {
        if (!AlpakaConfig.instance.cleanBlazeEnabled) return false

        if (entity is Blaze) return true

        if (entity.hasCustomName()) {
            val customName = entity.customName
            if (customName != null) {
                // Matched against the raw name: stripping the codes first built a throwaway String
                // and ran a regex, per named entity per frame, for a plain substring test.
                val raw = customName.string
                if (SkyblockUtils.containsIgnoringFormatting(raw, "Smoldering Blaze") ||
                    SkyblockUtils.containsIgnoringFormatting(raw, "Blaze")
                ) {
                    return true
                }
            }
        }
        return false
    }

    @JvmStatic
    fun shouldHideEntity(entity: Entity): Boolean {
        if (!AlpakaConfig.instance.cleanBlazeEnabled) return false

        if (entity is SmallFireball) return true

        if (entity.hasCustomName()) {
            val customName = entity.customName
            if (customName != null) {
                if (SkyblockUtils.containsIgnoringFormatting(customName.string, "Smoldering Blaze")) {
                    return true
                }
            }
        }
        return false
    }

}
