package net.alpaka.addons.features.slayer

import net.alpaka.addons.AlpakaAddons
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.config.AlpakaStats

/**
 * Lifetime slayer XP per slayer, as far as the client can know it.
 *
 * Hypixel never states this figure during normal play. The sidebar carries the quest's own progress,
 * and the only XP number chat ever prints is the RNG meter's stored total, which is meter progress
 * rather than lifetime XP. The lifetime figure exists only in the Slayer menu, so it is taken from
 * there when [SlayerMenuXpReader] sees it, and advanced by every boss kill the mod witnesses between
 * readings.
 *
 * Until it has been seen, [totalXp] returns null and the HUD shows a placeholder rather than an
 * invented number.
 *
 * ### Why the stored figure is advanced directly
 *
 * The first version kept the menu figure as a fixed baseline and added the *session's* XP on top,
 * remembering how much session XP that baseline already contained. It fell over in two ways, both
 * showing up in play as the total suddenly dropping back to an older value:
 *
 *  - Session XP lives in memory and is cleared on disconnect, but the remembered offset was not. After
 *    a reconnect everything earned since the menu was last opened vanished from the total until the
 *    menu was opened again.
 *  - The menu's leaderboard rows come from Hypixel's leaderboard cache, which lags live play by
 *    minutes. Opening the menu straight after a run handed back a figure *below* the truth, and it was
 *    accepted as the new baseline without question.
 *
 * Now the persisted figure is the estimate itself: each kill's XP is added to it and saved at once,
 * so nothing is lost to a disconnect, and a menu reading only moves it when it is credible.
 */
object SlayerXpTracker {

    /**
     * How long after the mod last credited XP to a slayer a *lower* menu figure is distrusted.
     *
     * Hypixel's leaderboard rows are cached, so shortly after killing bosses the menu still shows
     * the pre-kill total. A lower figure inside this window is taken as that lag and ignored; one
     * arriving after a long quiet spell is taken as a genuine correction of a total that drifted
     * high - a boss the mod counted that Hypixel did not, say. The moment of the last credit is
     * persisted with the figure, so the window also covers a menu opened right after relaunching.
     */
    private const val STALE_READING_WINDOW_MS = 30 * 60_000L

    /** Adds the XP a boss kill awarded to the lifetime figure, once that figure is known at all. */
    fun credit(type: SlayerType, xp: Long) {
        if (xp <= 0L) return

        val data = AlpakaStats.slayerBossMap().getOrPut(type) { AlpakaConfig.SlayerData() }
        data.lastXpCreditedAtMs = System.currentTimeMillis()
        // Nothing to advance until the menu has given a starting point; adding kills to "unknown"
        // would only invent a number.
        if (data.totalXp >= 0L) data.totalXp += xp
        AlpakaStats.save()
    }

    /** Records a lifetime XP figure read from the Slayer menu. */
    fun observeTotal(type: SlayerType, xp: Long) {
        if (xp < 0L) return

        val data = AlpakaStats.slayerBossMap().getOrPut(type) { AlpakaConfig.SlayerData() }
        val known = data.totalXp
        if (xp == known) return

        if (known >= 0L && xp < known) {
            val sinceCredit = System.currentTimeMillis() - data.lastXpCreditedAtMs
            if (sinceCredit < STALE_READING_WINDOW_MS) {
                AlpakaAddons.LOGGER.info(
                    "Ignored a stale {} slayer XP reading of {} from the Slayer menu (tracking {})",
                    type.display, xp, known
                )
                return
            }
        }

        data.totalXp = xp
        AlpakaStats.save()
    }

    /** Lifetime slayer XP as currently known, or null when the menu has never been read. */
    fun totalXp(type: SlayerType): Long? {
        val known = AlpakaStats.slayerBossMap()[type]?.totalXp ?: -1L
        return if (known < 0L) null else known
    }
}
