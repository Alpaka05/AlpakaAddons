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

    /**
     * Messages hidden by exact text, once colour codes are stripped and the line is trimmed.
     *
     * Stored as plain text on purpose. Hypixel sends these wrapped in formatting - the real quest
     * line is `"   §5§l» §7Slay §c33,600 Combat XP §7worth of Blazes§7."` - and comparing that raw
     * form against plain entries never matched, which is why these lines kept appearing.
     */
    private val CANCELED_CHAT_MESSAGES = setOf(
        "SLAYER QUEST COMPLETE!",
        "SLAYER QUEST STARTED!",
        "NICE! SLAYER BOSS SLAIN!",
        "Your radio is weak. Find another enjoyer to boost it.",
        "Your radio signal is strong!",
        "Your radio lost signal. There's too many enjoyers on this channel."
    )

    /**
     * Messages hidden by shape, for the ones carrying a number or a name that varies.
     *
     * These were previously pinned to one exact wording - one specific XP amount, one slayer at one
     * level - so they only ever matched a single tier of a single slayer, if at all.
     */
    private val CANCELED_CHAT_PATTERNS = listOf(
        // "» Slay 33,600 Combat XP worth of Blazes."
        Regex("""^»\s*Slay\s+[\d,.]+k?\s+Combat XP\s+worth of\s+.+\.?$""", RegexOption.IGNORE_CASE),
        // "Blaze Slayer LVL 9 - LVL MAXED OUT!" and the ordinary level-up form.
        Regex("""^.*Slayer LVL\s+\d+\b.*$""", RegexOption.IGNORE_CASE),
        // "Your Slayer Kill gave you 160 HP healing for 10 seconds!"
        Regex("""^Your Slayer Kill gave you\s+[\d,]+\s+HP healing.*$""", RegexOption.IGNORE_CASE),
        Regex("""^RARE DROP! Netherrack-Looking Sunshade.*$""", RegexOption.IGNORE_CASE)
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

    /**
     * Whether a chat message should be kept out of the visible chat log.
     *
     * Takes the message exactly as Hypixel sent it, formatting and all, and strips that itself -
     * callers must not pre-clean. Note this only hides the line from display: the message still
     * reaches every listener, so slayer tracking keeps working on messages nobody sees.
     */
    @JvmStatic
    fun shouldCancelChatMessage(rawMessage: String): Boolean {
        if (!AlpakaConfig.instance.cleanBlazeEnabled) return false

        val cleanText = SlayerDropTracker.cleanColor(rawMessage).trim()
        if (cleanText.isEmpty()) return false

        if (cleanText in CANCELED_CHAT_MESSAGES) return true
        return CANCELED_CHAT_PATTERNS.any { it.matches(cleanText) }
    }
}
