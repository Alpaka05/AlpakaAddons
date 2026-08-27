package net.alpaka.addons.features.slayer

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.config.AlpakaStats

/**
 * Lifetime slayer XP per slayer, as far as the client can know it.
 *
 * Hypixel never states this figure during normal play. The sidebar carries the quest's own progress,
 * and the only XP number chat ever prints is the RNG meter's stored total, which is meter progress
 * rather than lifetime XP. The lifetime figure exists only in the Slayer menu, so it is remembered
 * the first time [SlayerMenuXpReader] sees it and kept moving from there.
 *
 * Until it has been seen, [totalXp] returns null and the HUD shows a placeholder rather than an
 * invented number.
 */
object SlayerXpTracker {

    /**
     * Session XP already baked into each stored baseline.
     *
     * The menu figure is itself live, so re-opening the menu after killing a few bosses returns a
     * total that *already* includes this session's gains. Adding the running session total on top of
     * that would count those kills twice, so the session figure at the moment of reading is kept and
     * only the gains beyond it are added.
     *
     * Held in memory rather than saved, because the session it refers to does not survive a restart
     * either - a fresh launch starts from zero gains against a saved baseline, which is correct.
     */
    private val sessionXpAtObservation = HashMap<SlayerType, Long>()

    /** Records a lifetime XP figure read from the Slayer menu. */
    fun observeTotal(type: SlayerType, xp: Long) {
        if (xp < 0L) return

        val data = AlpakaStats.slayerBossMap().getOrPut(type) { AlpakaConfig.SlayerData() }
        val alreadyKnown = data.totalXp == xp
        sessionXpAtObservation[type] = SlayerSessionTracker.session(type).xpGained
        if (alreadyKnown) return

        data.totalXp = xp
        AlpakaStats.save()
    }

    /**
     * Best estimate of lifetime slayer XP: the figure last seen in the Slayer menu plus everything
     * earned since it was read. Null when no figure has ever been observed.
     */
    fun totalXp(type: SlayerType): Long? {
        val baseline = AlpakaStats.slayerBossMap()[type]?.totalXp ?: -1L
        if (baseline < 0L) return null

        val gainedSince = SlayerSessionTracker.session(type).xpGained - (sessionXpAtObservation[type] ?: 0L)
        return baseline + gainedSince.coerceAtLeast(0L)
    }
}
