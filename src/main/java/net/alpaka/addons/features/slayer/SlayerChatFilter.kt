package net.alpaka.addons.features.slayer

import net.alpaka.addons.config.AlpakaConfig

/**
 * Keeps Hypixel's slayer chatter out of the visible chat log.
 *
 * These lines used to be hidden by the clean-blaze option, which meant switching off a set of
 * particle and name-tag effects to get the chat back, or putting up with the chatter to keep the
 * effects. The two have nothing to do with each other, so they are separate settings now: clean
 * blaze deals only with what the blazes look like, this deals only with what they say.
 *
 * ### Hidden, not swallowed
 *
 * Nothing here cancels a chat *event*. The only caller is [net.alpaka.addons.mixin.ChatComponentMixin],
 * which cancels the chat GUI's own addMessage - the very last step, after every listener has already
 * seen the message. So slayer tracking, other mods, and anything else reading chat keep working on
 * lines the player never sees.
 *
 * That distinction is not academic: cancelling from a receive event once took the whole slayer quest
 * and kill tracking down with it, because the tracker reads the same messages this hides.
 */
object SlayerChatFilter {

    /**
     * One group of lines the player can switch off as a whole, in the "Hide Slayer Chat Parts"
     * dropdown. Grouped by what the lines are about rather than one switch per line: nobody wants
     * to keep exactly one of the three radio messages.
     *
     * Exact entries are plain text on purpose. Hypixel sends these wrapped in formatting - the real
     * quest line is `"   §5§l» §7Slay §c33,600 Combat XP §7worth of Blazes§7."` - and comparing that
     * raw form against plain entries never matched, which is why these lines kept appearing.
     *
     * Patterns are for the lines carrying a number or a name that varies. They were previously
     * pinned to one exact wording - one specific XP amount, one slayer at one level - so they only
     * ever matched a single tier of a single slayer, if at all.
     */
    private class Group(
        val enabled: (AlpakaConfig) -> Boolean,
        val messages: Set<String> = emptySet(),
        val patterns: List<Regex> = emptyList()
    ) {
        fun matches(cleanText: String): Boolean =
            cleanText in messages || patterns.any { it.matches(cleanText) }
    }

    private val GROUPS = listOf(
        Group(
            { it.hideSlayerChatQuest },
            messages = setOf(
                "SLAYER QUEST COMPLETE!",
                "SLAYER QUEST STARTED!",
                "NICE! SLAYER BOSS SLAIN!"
            ),
            // "» Slay 33,600 Combat XP worth of Blazes."
            patterns = listOf(Regex("""^»\s*Slay\s+[\d,.]+k?\s+Combat XP\s+worth of\s+.+\.?$""", RegexOption.IGNORE_CASE))
        ),
        Group(
            { it.hideSlayerChatRadio },
            messages = setOf(
                "Your radio is weak. Find another enjoyer to boost it.",
                "Your radio signal is strong!",
                "Your radio lost signal. There's too many enjoyers on this channel."
            )
        ),
        Group(
            { it.hideSlayerChatLevelUp },
            // "Blaze Slayer LVL 9 - LVL MAXED OUT!" and the ordinary level-up form.
            patterns = listOf(Regex("""^.*Slayer LVL\s+\d+\b.*$""", RegexOption.IGNORE_CASE))
        ),
        Group(
            { it.hideSlayerChatHealing },
            // "Your Slayer Kill gave you 160 HP healing for 10 seconds!"
            patterns = listOf(Regex("""^Your Slayer Kill gave you\s+[\d,]+\s+HP healing.*$""", RegexOption.IGNORE_CASE))
        ),
        Group(
            { it.hideSlayerChatSunshade },
            patterns = listOf(Regex("""^RARE DROP! Netherrack-Looking Sunshade.*$""", RegexOption.IGNORE_CASE))
        )
    )

    /**
     * Whether a chat message should be kept out of the visible chat log.
     *
     * Takes the message exactly as Hypixel sent it, formatting and all, and strips that itself -
     * callers must not pre-clean.
     */
    @JvmStatic
    fun shouldCancelChatMessage(rawMessage: String): Boolean {
        val config = AlpakaConfig.instance
        if (!config.hideSlayerChatMessages) return false

        val cleanText = SlayerDropTracker.cleanColor(rawMessage).trim()
        if (cleanText.isEmpty()) return false

        return GROUPS.any { it.enabled(config) && it.matches(cleanText) }
    }
}
