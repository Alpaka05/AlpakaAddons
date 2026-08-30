package net.alpaka.addons.features.notification

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.slayer.SlayerDropTracker
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

/**
 * Raises a notification when somebody says the player's name in chat.
 *
 * The first user of [AlpakaNotifications], and deliberately the only thing in this file that knows
 * anything about chat - the notification channel itself stays general.
 *
 * ### What counts as a mention
 *
 * Only a line with a sender, and only when the name turns up in what was actually said. Hypixel
 * writes chat as `<sender>: <message>`, so the first colon-space splits the two, and the name has
 * to be on the right of it. That single rule covers the three cases worth getting right:
 *
 *  - the player's own messages carry their name on the left, and never notify;
 *  - a line without a sender at all - joins, deaths, the server talking - is not a mention;
 *  - being mentioned mid-sentence is, however the sentence is punctuated.
 */
object MentionNotifier {

    /** Two chat lines this close together with the same text are treated as one. */
    private const val REPEAT_WINDOW_MS = 1500L

    private var lastBody: String = ""
    private var lastAtMs: Long = 0L

    @JvmStatic
    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay ->
            if (!overlay) inspect(message)
        }
        ClientReceiveMessageEvents.CHAT.register { message, _, _, _, _ ->
            inspect(message)
        }
    }

    private fun inspect(message: Component?) {
        if (message == null) return
        if (!AlpakaConfig.instance.mentionNotificationEnabled) return

        val name = Minecraft.getInstance().player?.gameProfile?.name ?: return
        if (name.isEmpty()) return

        val raw = SlayerDropTracker.cleanColor(message.string)
        val split = raw.indexOf(": ")
        if (split < 0) return

        val sender = raw.substring(0, split)
        val body = raw.substring(split + 2).trim()
        if (body.isEmpty()) return

        // Our own line. The name sits in the sender block of everything we say, so without this
        // every message the player sends would notify them about themselves.
        if (sender.contains(name, ignoreCase = true)) return
        if (!body.contains(name, ignoreCase = true)) return

        val now = System.currentTimeMillis()
        // Both chat events can carry the same line, and a mod that cancels one and re-sends it
        // makes that two arrivals a frame apart. One notice is enough.
        if (body == lastBody && now - lastAtMs < REPEAT_WINDOW_MS) return
        lastBody = body
        lastAtMs = now

        AlpakaNotifications.send("Chat Mention", speakerOf(sender) + " mentioned you")
    }

    /**
     * Who did the mentioning, out of a sender block, or a fallback when there is nothing to read.
     *
     * A sender arrives dressed up - `Guild > [MVP+] Someone [ELITE]` - so the bracketed rank and
     * guild tags come off and the last word of what is left is the name. Channel markers like
     * `Guild >` fall away with them, being followed by more words.
     *
     * A relay needs one step more. When somebody on Discord replies to someone, the bridge writes
     * both names joined by an arrow - `LEMAN > [Discord] VultureAir⇾Jiles777: hi` - and it is the
     * first of the two who wrote the message. The second is who they were answering, and naming
     * them would credit the wrong person.
     */
    private fun speakerOf(sender: String): String {
        val bare = sender.replace(BRACKETED, " ").trim()
        val word = bare.split(' ').lastOrNull { it.isNotBlank() && it != ">" } ?: return "Someone"
        return word.substringBefore(REPLY_ARROW).ifBlank { "Someone" }
    }

    /** What the bridge puts between an author and the person they replied to. */
    private const val REPLY_ARROW = '⇾'

    private val BRACKETED = Regex("\\[[^\\]]*\\]")
}
