package net.alpaka.addons.features.bridge

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.slayer.SlayerDropTracker
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component

/**
 * Rewrites guild-chat messages relayed by a Discord bridge bot into something readable.
 *
 * A bridge bot is just another account in the guild: it posts what Discord users write, so the raw
 * line arrives with the bot's own name and rank attached and the real content buried behind them -
 * `Guild > [MVP+] BridgeBot [Member]: SomeUser: hello`. That reads as if the bot said it, and the
 * rank block is noise on every single line.
 *
 * All of that is dropped and replaced by a blue `[Discord]` marker, with whatever the bot actually
 * wrote kept verbatim:
 *
 *     in : Guild > [VIP] VultureGround [Helper]: Alpakaa: spammer get banned
 *     out: [Discord] Alpakaa: spammer get banned
 *
 * ### Why nothing in the message is parsed
 *
 * An earlier version pulled the author name out and put it in front in brackets. The trouble is
 * that a bridge bot answers guild commands as well as relaying people, and an answer has the very
 * same shape as a relay: `!uuid` replies `<uuid>: <name history>`, which came out as
 * `[3b7d7694c40b428bb09e46b6948e6e0e] godlyv1 8/25/2023 - ...`, as though a player by that name had
 * spoken. Announcements like `Reminder: Star Cult is here` went the same way. There is no reliable
 * way to tell a relay from an answer, so the message body is no longer touched at all - which is
 * both simpler and impossible to get wrong.
 *
 * Display only. The incoming message is re-rendered on this client and nothing is sent, answered or
 * acted upon, so it changes nothing another player or the server can observe.
 */
object BridgeBotFormatter {

    /** Guild and officer chat are the only channels a bridge bot relays into. */
    private val CHANNEL_PREFIXES = arrayOf("Guild > ", "Officer > ", "G > ", "O > ")

    /** What replaces the channel marker and the bot's own name and rank. */
    private const val TAG = "[Discord] "

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
        // "§2Guild > §a[VIP] ... §f: Author: message" - matching on it directly never fires.
        // Detection happens on the stripped text; the body is sliced out of the coloured original.
        val coded = original.string
        val raw = SlayerDropTracker.cleanColor(coded)
        val prefix = CHANNEL_PREFIXES.firstOrNull { raw.startsWith(it) } ?: return null

        // Sender block runs up to the first colon-space; everything after it is what the bot said.
        val split = raw.indexOf(": ", prefix.length)
        if (split < 0) return null

        val sender = raw.substring(prefix.length, split)
        // Match on the sender block only. Checking the whole line would also fire when somebody
        // merely mentions the bot's name in a message.
        if (!sender.contains(botName, ignoreCase = true)) return null

        // The same colon again, this time located in the coloured string, so any formatting the bot
        // put in its own message survives instead of being flattened away.
        val codedSplit = coded.indexOf(": ", coded.indexOf(prefix) + prefix.length)
        val body = if (codedSplit < 0) raw.substring(split + 2) else coded.substring(codedSplit + 2)
        if (body.isEmpty()) return null

        // Built as two siblings of an empty root rather than by appending to the tag, because a
        // sibling inherits its parent's style - hung off the tag, the whole line would come out blue.
        return Component.empty()
            .append(Component.literal(TAG).withStyle(ChatFormatting.BLUE))
            .append(Component.literal(body).withStyle(ChatFormatting.WHITE))
    }
}
