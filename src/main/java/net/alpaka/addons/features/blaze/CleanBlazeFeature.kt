package net.alpaka.addons.features.blaze

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.slayer.SlayerSessionTracker
import net.alpaka.addons.features.slayer.SlayerType
import net.alpaka.addons.utils.SkyblockUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
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
 * Near the boss and its demons every particle goes, not just flame and smoke. The minibosses and
 * the boss's attunement demons are not all blazes and throw lava, dust and the like, and listing
 * those types one by one would never keep up. SkyHanni's Clear View does the same within 10 blocks
 * of the boss.
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

    /** How close a demon's name tag has to be for every particle to go, in blocks. As SkyHanni. */
    private const val DEMON_RADIUS = 10.0

    /**
     * Name tags that mark a fight: the boss, the three minibosses, and the two demons the boss
     * summons from tier III. The demons' names are written in circled letters on Hypixel.
     */
    private val DEMON_NAMES = arrayOf(
        "Inferno Demonlord",
        "Flare Demon",
        "Kindleheart Demon",
        "Burningsoul Demon",
        "ⓆⓊⒶⓏⒾⒾ",
        "ⓉⓎⓅⒽⓄⒺⓊⓈ",
    )

    private var nearDemon = false

    /** On SkyBlock and in a Blaze zone. Cached, since every particle asks. */
    private fun active(): Boolean {
        if (!AlpakaConfig.instance.cleanBlazeEnabled) return false
        val now = System.currentTimeMillis()
        if (now - scopeCheckedAtMs >= SCOPE_REFRESH_MS) {
            scopeCheckedAtMs = now
            inScope = SkyblockUtils.isOnSkyblock() && SlayerSessionTracker.isInTrackerArea(SlayerType.BLAZE)
            nearDemon = inScope && isNearDemon()
        }
        return inScope
    }

    /** Whether a Blaze slayer boss or demon's name tag is within [DEMON_RADIUS] of the player. */
    private fun isNearDemon(): Boolean {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return false
        val player = mc.player ?: return false
        val stands = level.getEntitiesOfClass(
            ArmorStand::class.java,
            player.boundingBox.inflate(DEMON_RADIUS),
        ) { it.hasCustomName() }
        for (stand in stands) {
            val raw = stand.customName?.string ?: continue
            if (DEMON_NAMES.any { SkyblockUtils.containsIgnoringFormatting(raw, it) }) return true
        }
        return false
    }

    /** Flame and smoke anywhere in the Blaze zones; every particle near the boss or a demon. */
    @JvmStatic
    fun shouldCancelParticle(options: ParticleOptions): Boolean {
        if (!AlpakaConfig.instance.cleanBlazeParticles || !active()) return false
        if (nearDemon) return true
        val type = options.type
        return type === ParticleTypes.FLAME || type === ParticleTypes.SMALL_FLAME ||
            type === ParticleTypes.SMOKE || type === ParticleTypes.LARGE_SMOKE
    }

    /**
     * Blazes lose their fire, and so does every mob near the boss or a demon, since the demons are
     * not all blazes. The player's own burning, and anyone else's, always stays.
     */
    @JvmStatic
    fun shouldHideEntityFire(entity: Entity, state: EntityRenderState) {
        if (entity is Player) return
        if ((entity is Blaze || nearDemon) && AlpakaConfig.instance.cleanBlazeFire && active()) {
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
