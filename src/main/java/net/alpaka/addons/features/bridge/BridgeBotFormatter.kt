package net.alpaka.addons.features.bridge

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.slayer.SlayerDropTracker
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

/**
 * Rewrites guild-chat messages relayed by a Discord bridge bot into something readable.
 *
 * A bridge bot is just another account in the guild: it posts what Discord users write, so the raw
 * line arrives with the bot's own name and rank attached and the real author buried in the message
 * body - `Guild > [MVP+] BridgeBot [Member]: SomeUser: hello`. That reads as if the bot said it.
 *
 * This pulls the author out and puts it in front in blue brackets, so a relayed message is
 * recognisable at a glance:
 *
 *     Guild > [SomeUser] hello
 *
 * Verified against a real relay, with the bot name set to `VultureGround`:
 *
 *     in : Guild > [VIP] VultureGround [Helper]: Alpakaa: spammer get banned
 *     out: Guild > [Alpakaa] spammer get banned
 *
 * Note the two colons doing different jobs: the first ends Hypixel's own sender block, the second
 * is the bridge's author separator. That is why the sender block is cut at the *first* `": "` and
 * the author is then parsed out of what remains, rather than splitting the line on colons.
 *
 * Display only. The incoming message is re-rendered on this client and nothing is sent, answered or
 * acted upon, so it changes nothing another player or the server can observe.
 */
object BridgeBotFormatter {

    /** Guild and officer chat are the only channels a bridge bot relays into. */
    private val CHANNEL_PREFIXES = arrayOf("Guild > ", "Officer > ", "G > ", "O > ")

    /**
     * Separators bridge bots put between the Discord author and the message.
     *
     * Different bridges use different ones - plain `name: text` is the most common, but the
     * chevron forms show up too - so all of them are recognised rather than betting on one.
     * Ordered longest-first so " > " cannot match inside " » ".
     */
    private val AUTHOR_SEPARATORS = arrayOf(": ", " » ", " >> ", " > ", " | ")

    /** Longest plausible Discord display name; anything longer means we mis-split the line. */
    private const val MAX_AUTHOR_LENGTH = 40

    /**
     * The reformatted line, or null when this message is not a bridge relay and should be left
     * exactly as it arrived.
     */
    @JvmStatic
    fun reformat(original: Component): Component? {
        val cfg = AlpakaConfig.instance
        if (!cfg.bridgeBotFormatterEnabled) return null

        val botName = cfg.bridgeBotName.trim()
        if (botName.isEmpty()) return null

        // Hypixel formats with legacy section codes embedded in the text, so getString() hands back
        // "§2Guild > §a[VIP] ... §f: §rAuthor: message§r" - matching on it directly never fires.
        // Parsing happens on the stripped text; the output is rebuilt with our own colours anyway.
        val raw = SlayerDropTracker.cleanColor(original.string)
        val prefix = CHANNEL_PREFIXES.firstOrNull { raw.startsWith(it) } ?: return null

        // Sender block runs up to the first colon-space; everything after it is what the bot said.
        val split = raw.indexOf(": ", prefix.length)
        if (split < 0) return null

        val sender = raw.substring(prefix.length, split)
        // Match on the sender block only. Checking the whole line would also fire when somebody
        // merely mentions the bot's name in a message.
        if (!sender.contains(botName, ignoreCase = true)) return null

        val payload = raw.substring(split + 2)
        if (payload.isEmpty()) return null

        val author = extractAuthor(payload) ?: return null
        val message = payload.substring(author.length).removePrefixSeparator()

        return Component.literal(prefix).withStyle(ChatFormatting.DARK_GREEN)
            .append(Component.literal("[$author] ").withStyle(ChatFormatting.BLUE))
            .append(Component.literal(message).withStyle(ChatFormatting.WHITE))
    }

    /**
     * The author name at the start of the payload, or null when no separator is present - in which
     * case the bot said something of its own (a join notice, say) and is left alone.
     */
    private fun extractAuthor(payload: String): String? {
        var best: String? = null
        for (separator in AUTHOR_SEPARATORS) {
            val at = payload.indexOf(separator)
            if (at <= 0 || at > MAX_AUTHOR_LENGTH) continue
            val candidate = payload.substring(0, at)
            // A candidate containing a space is a sentence, not a handle. Without this, a message
            // the bot writes itself - "hey guys: look at this" - would be torn apart and "hey guys"
            // shown as its author. Failing to format is harmless; mangling a real message is not.
            if (candidate.contains(' ')) continue
            // Shortest candidate wins, which for prefixes means the earliest separator.
            if (best == null || candidate.length < best.length) best = candidate
        }
        return best
    }

    /** Drops whichever separator followed the author name. */
    private fun String.removePrefixSeparator(): String {
        for (separator in AUTHOR_SEPARATORS) {
            if (startsWith(separator)) return substring(separator.length)
        }
        return trimStart()
    }
}
