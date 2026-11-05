package net.alpaka.addons.features.slayer

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.utils.SkyblockUtils

/**
 * The Message Filter's Slayer Messages: keeps Hypixel's slayer chatter out of the visible chat log,
 * the quest, radio, level-up, healing and Sunshade lines anywhere, and the Blaze boss fight's wrong
 * dagger and damage lines in the Blaze zones.
 *
 * These used to be a feature of their own (Hide Slayer Chat Spam) and two switches of Clean Blaze.
 * They sit with the other filters now, but are not hidden the way MessageFilterFeature hides its
 * lines, for the reason below.
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
     * One group of lines the player can switch off as a whole, in the Message Filter's "Slayer
     * Messages" dropdown. Grouped by what the lines are about rather than one switch per line: nobody wants
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
        val name: String,
        val enabled: (AlpakaConfig) -> Boolean,
        val messages: Set<String> = emptySet(),
        val patterns: List<Regex> = emptyList(),
        /** Only hidden in the Blaze slayer's zones; elsewhere the same words can mean something else. */
        val blazeZoneOnly: Boolean = false
    ) {
        fun matches(cleanText: String): Boolean =
            cleanText in messages || patterns.any { it.matches(cleanText) }
    }

    private val GROUPS = listOf(
        Group(
            "quest",
            { it.messageFilterSlayerQuest },
            messages = setOf(
                "SLAYER QUEST COMPLETE!",
                "SLAYER QUEST STARTED!",
                "NICE! SLAYER BOSS SLAIN!"
            ),
            // "» Slay 33,600 Combat XP worth of Blazes."
            patterns = listOf(Regex("""^»\s*Slay\s+[\d,.]+k?\s+Combat XP\s+worth of\s+.+\.?$""", RegexOption.IGNORE_CASE))
        ),
        Group(
            "radio",
            { it.messageFilterSlayerRadio },
            messages = setOf(
                "Your radio is weak. Find another enjoyer to boost it.",
                "Your radio signal is strong!",
                "Your radio lost signal. There's too many enjoyers on this channel."
            )
        ),
        Group(
            "level up",
            { it.messageFilterSlayerLevelUp },
            // Hypixel's two forms, "Blaze Slayer LVL 9 - LVL MAXED OUT!" (as SkyHanni matches it) and
            // "Wolf Slayer LVL 7 - Next LVL in 50,000 XP!". Anchored at the slayer's name: matched
            // anywhere, "Slayer LVL" also hid players writing about their levels.
            patterns = listOf(Regex(
                """^[A-Za-z]+(?: [A-Za-z]+)? Slayer LVL \d+ - (?:LVL MAXED OUT!|Next LVL in [\d,.]+[kKmM]? XP!)$""",
                RegexOption.IGNORE_CASE,
            ))
        ),
        Group(
            "healing",
            { it.messageFilterSlayerHealing },
            // "Your Slayer Kill gave you 160 HP healing for 10 seconds!"
            patterns = listOf(Regex("""^Your Slayer Kill gave you\s+[\d,]+\s+HP healing.*$""", RegexOption.IGNORE_CASE))
        ),
        Group(
            "sunshade",
            { it.messageFilterSlayerSunshade },
            patterns = listOf(Regex("""^RARE DROP! Netherrack-Looking Sunshade.*$""", RegexOption.IGNORE_CASE))
        ),
        Group(
            "wrong dagger",
            { it.messageFilterBlazeWrongDagger },
            // "Strike using the CRYSTAL attunement on your dagger!", sent on every hit with the wrong one.
            patterns = listOf(Regex("""^Strike using the \w+ attunement on your dagger!$""")),
            blazeZoneOnly = true
        ),
        Group(
            "blaze damage",
            { it.messageFilterBlazeDamage },
            // "Your hit was reduced by Hellion Shield!" and the boss's damage reports, such as
            // "You took 1,500 true damage from Quazii's beam!".
            patterns = listOf(
                Regex("""^Your hit was reduced by Hellion Shield!$"""),
                Regex("""^You took [\d,.]+ (?:true )?damage from .+!$"""),
            ),
            blazeZoneOnly = true
        )
    )

    /**
     * A line somebody wrote rather than one Hypixel sent: a channel, a private message, an NPC, or a
     * name and a colon at its start (ranks and SkyBlock levels in front of the name included). Never
     * hidden, whatever it says: "need slayer lvl 9?" in party chat is a question, not a level-up.
     */
    private val SENDER = Regex("""^(?:(?:Guild|Party|Co-op|Officer) > |(?:From|To) |\[NPC] |[^:]{0,48}?\w{1,16}: )""")

    /**
     * Whether a chat message should be kept out of the visible chat log.
     *
     * Takes the message exactly as Hypixel sent it, formatting and all, and strips that itself -
     * callers must not pre-clean.
     */
    @JvmStatic
    fun shouldCancelChatMessage(rawMessage: String): Boolean {
        val config = AlpakaConfig.instance
        if (!config.messageFilterEnabled) return false

        val cleanText = SlayerDropTracker.cleanColor(rawMessage).trim()
        if (cleanText.isEmpty() || SENDER.containsMatchIn(cleanText)) return false

        return GROUPS.any {
            it.enabled(config) && it.matches(cleanText) && (!it.blazeZoneOnly || inBlazeZone())
        }
    }

    /** On SkyBlock and in a Blaze slayer zone. Asked only for a line that matched a Blaze group. */
    private fun inBlazeZone(): Boolean =
        SkyblockUtils.isOnSkyblock() && SlayerSessionTracker.isInTrackerArea(SlayerType.BLAZE)

    /** For tests: the groups that would hide this color-stripped line, switches aside. */
    internal fun groupsForTest(cleanText: String): List<String> =
        if (SENDER.containsMatchIn(cleanText)) emptyList() else GROUPS.filter { it.matches(cleanText) }.map { it.name }
}
