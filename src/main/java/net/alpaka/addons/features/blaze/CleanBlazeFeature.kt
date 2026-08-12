package net.alpaka.addons.features.blaze

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.slayer.SlayerDropTracker
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.monster.Blaze
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball

object CleanBlazeFeature {

    private val CANCELED_CHAT_MESSAGES = setOf(
        "Your Slayer Kill gave you 160 HP healing for 10 seconds!",
        "  SLAYER QUEST COMPLETE!",
        "   Blaze Slayer LVL 9 - LVL MAXED OUT!",
        "   » Slay 33,600 Combat XP worth of Blazes.",
        "Your radio is weak. Find another enjoyer to boost it.",
        "Your radio signal is strong!",
        "Your radio lost signal. There's too many enjoyers on this channel."
    )

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
    fun shouldHideNameTag(entity: Entity): Boolean {
        if (!AlpakaConfig.instance.cleanBlazeEnabled) return false

        if (entity is Blaze) return true

        if (entity.hasCustomName()) {
            val customName = entity.customName
            if (customName != null) {
                val clean = SlayerDropTracker.cleanColor(customName.string)
                if (clean.contains("Smoldering Blaze") || clean.contains("Blaze")) {
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
                val clean = SlayerDropTracker.cleanColor(customName.string)
                if (clean.contains("Smoldering Blaze")) {
                    return true
                }
            }
        }
        return false
    }

    @JvmStatic
    fun shouldCancelChatMessage(rawMessage: String): Boolean {
        if (!AlpakaConfig.instance.cleanBlazeEnabled) return false

        val cleanText = rawMessage.trim()
        if (cleanText in CANCELED_CHAT_MESSAGES || rawMessage in CANCELED_CHAT_MESSAGES) {
            return true
        }

        return rawMessage.startsWith("RARE DROP! Netherrack-Looking Sunshade")
    }
}
