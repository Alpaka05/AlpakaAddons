package net.alpaka.addons.features.slayer

import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents

/**
 * Remembers which Skyblock profile the player is on.
 *
 * Hypixel never sends the profile to the client as data, but it does say it in chat, on every join
 * and on every switch. Those two lines are the whole source:
 *
 * ```
 * §aYou are playing on profile: §eKiwi
 * §aYour profile was changed to: §ePeach§b (Co-op)
 * ```
 *
 * Both captured from real logs, which is also where the "(Co-op)" suffix comes from - it is part of
 * the line, not part of the profile name, and has to come off or the same profile would be filed
 * under two different names depending on how it was joined.
 *
 * Registered separately from [SlayerDropTracker.onChat] on purpose: that one returns immediately
 * when the drop tracker is switched off, and the profile has to be known regardless of which
 * features are enabled.
 */
object SkyblockProfileTracker {

    /**
     * Matches both announcements and takes the name off the end.
     *
     * The name is captured lazily so the optional "(Co-op)" is stripped rather than swallowed into
     * it. Case insensitive because only the wording is guaranteed, not its capitalisation.
     */
    private val PROFILE_PATTERN = Regex(
        """^(?:You are playing on profile|Your profile was changed to):\s*(?<profile>.+?)(?:\s*\(Co-op\))?$""",
        RegexOption.IGNORE_CASE,
    )

    /** The profile last announced, lowercased, or null before one has been seen this session. */
    var current: String? = null
        private set

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay) onChat(message.string)
        }
        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ ->
            onChat(message.string)
        }
    }

    private fun onChat(raw: String) {
        val clean = SkyblockUtils.cleanColor(raw)
        val match = PROFILE_PATTERN.find(clean) ?: return
        val name = match.groups["profile"]?.value?.trim()?.lowercase() ?: return
        if (name.isEmpty()) return
        current = name
    }

    /** Forgets the profile, for a disconnect. The next join announces it again. */
    fun clear() {
        current = null
    }
}
