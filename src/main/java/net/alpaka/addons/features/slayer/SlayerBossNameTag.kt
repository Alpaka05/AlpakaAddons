package net.alpaka.addons.features.slayer

/**
 * Tells a slayer boss's name tag from every other mob's, colour codes already stripped.
 *
 * Since Hypixel's name tag change in October 2026 a boss carries a level like every mob does, so
 * the level no longer tells the two apart. Samples from SkyHanni's MobFilter, before and after:
 *
 * ```
 * ☠ Revenant Horror IV 1.5M❤
 * [Lv655] ☠ Voidgloom Seraph IV 143.2M/210M❤
 * [Lv377] ☠ Atoned Horror 4.1M/10M❤
 * ```
 *
 * What still sets a boss apart is the skull in front of its name or the roman tier after it. The
 * mobs farmed to summon one never carry either, even where they share the boss's name.
 */
object SlayerBossNameTag {

    private val TIER_AFTER_NAME = Regex("^ [IV]+\\b")

    @JvmStatic
    fun isBoss(name: String, bossNames: Array<String>): Boolean = bossNames.any { isBoss(name, it) }

    @JvmStatic
    fun isBoss(name: String, bossName: String): Boolean {
        val at = name.indexOf(bossName)
        if (at < 0) return false
        return name.contains('☠') || TIER_AFTER_NAME.containsMatchIn(name.substring(at + bossName.length))
    }
}
