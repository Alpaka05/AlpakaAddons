package net.alpaka.addons.features.slayer

import net.alpaka.addons.AlpakaAddons
import net.alpaka.addons.utils.SkyblockUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents

/**
 * Reads lifetime slayer XP out of the Slayer menu while the player has it open.
 *
 * This figure is the one piece of slayer data Hypixel never sends during play: the sidebar carries
 * only the current quest's progress, and the sole XP number in chat is the RNG meter's stored total,
 * which is meter progress and resets when the meter pays out. The lifetime total exists solely in the
 * Slayer menu - reached through the Maddox NPC or an Abiphone call, never a command - on the "Slayer
 * Leaderboards" item, whose tooltip lists every slayer at once:
 *
 * ```
 * Revenant Horror: 2,423,815 (Top 0.77%)
 * Inferno Demonlord: 8,820,600 (#174)
 * ```
 *
 * Reading it is nothing more than looking at text the server has already sent for the player to read
 * on screen - the same data the tooltip itself draws - so no request, scan or probe is involved, and
 * it happens only while the menu is genuinely open.
 */
object SlayerMenuXpReader {

    /**
     * Container titles worth examining.
     *
     * There is no command that opens this menu - it comes from talking to the Maddox NPC in the hub,
     * or calling him on an Abiphone or Batphone from anywhere. The route does not matter to us, since
     * what is watched for is the menu itself rather than any command, and the menu that carries the
     * leaderboards is titled "Slayer". "Maddox" is accepted as well in case a phone call titles its
     * menu after him instead, which costs nothing to allow: a menu still only yields data if its rows
     * name real slayer bosses.
     */
    private val MENU_TITLE_MARKERS = listOf("slayer", "maddox")

    /**
     * One leaderboard row: a boss name, its lifetime XP, then a bracketed rank.
     *
     * The pattern is intentionally loose about the bracket - it is `(Top 0.77%)` for a percentile and
     * `(#174)` once the player is ranked outright - because what makes a row safe to trust is not its
     * shape but that its name resolves to a real slayer. That check alone rejects every other
     * `Label: 1,234` line in the game, and a row for an unrecognised boss is simply skipped.
     */
    private val LEADERBOARD_ROW = Regex("""^\s*(?<boss>[^:]+):\s*(?<xp>[\d,]+)\s*\(""")

    /** Lore lines to dump per item when a Slayer menu yields nothing recognisable. */
    private const val MAX_LOGGED_LORE_LINES = 12

    /** The screen last examined, so each opened menu is only walked once. */
    private var lastScannedScreen: AbstractContainerScreen<*>? = null

    /** Called once per client tick. */
    fun tick() {
        val screen = Minecraft.getInstance().gui.screen()

        if (screen !is AbstractContainerScreen<*>) {
            lastScannedScreen = null
            return
        }
        if (screen === lastScannedScreen) return

        val title = SkyblockUtils.cleanColor(screen.title.string)
        if (MENU_TITLE_MARKERS.none { title.contains(it, ignoreCase = true) }) return

        // Marked scanned before doing the work, so a menu whose slots never resolve cannot make this
        // run every tick for as long as it stays open.
        lastScannedScreen = screen
        scan(screen, title)
    }

    private fun scan(screen: AbstractContainerScreen<*>, title: String) {
        var found = 0
        val unrecognised = StringBuilder()

        for (slot in screen.menu.slots) {
            val stack = slot.item
            if (stack.isEmpty) continue

            val lore = stack.get(DataComponents.LORE) ?: continue
            val lines = lore.lines().map { SkyblockUtils.cleanColor(it.string) }

            var matchedHere = 0
            for (line in lines) {
                val match = LEADERBOARD_ROW.find(line) ?: continue
                val type = SlayerType.fromScoreboardLine(match.groups["boss"]!!.value.trim()) ?: continue
                val xp = match.groups["xp"]!!.value.replace(",", "").toLongOrNull() ?: continue

                SlayerXpTracker.observeTotal(type, xp)
                matchedHere++
            }

            if (matchedHere > 0) {
                found += matchedHere
            } else if (lines.any { it.contains("Slayer", ignoreCase = true) }) {
                // Only items that at least mention slayers are worth reporting, so a failure dump
                // stays readable instead of listing the player's whole inventory.
                unrecognised.append("\n  item '")
                    .append(SkyblockUtils.cleanColor(stack.hoverName.string)).append("'")
                lines.take(MAX_LOGGED_LORE_LINES).forEach { unrecognised.append("\n    | ").append(it) }
            }
        }

        if (found > 0) {
            AlpakaAddons.LOGGER.info("Read lifetime slayer XP for {} slayer(s) from the Slayer menu", found)
        } else if (unrecognised.isNotEmpty()) {
            // Dumped rather than guessed at: the menu's wording is documented nowhere and no other
            // mod parses it, so a real sample is the only way to correct the pattern.
            AlpakaAddons.LOGGER.info(
                "No slayer XP rows recognised in menu '{}'. Lore seen:{}", title, unrecognised
            )
        }
    }
}
