package net.alpaka.addons.features.guild

import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.contents.PlainTextContents

/**
 * Puts the player's own guild tag in front of guild chat instead of Hypixel's "Guild >".
 *
 * Hypixel sends the line as `§2Guild > §6[MVP++] Name §e[Rank]§f: text`, captured from a log. Only
 * the channel marker is touched; everything after it is left exactly as it arrived.
 *
 * ### Why the message is edited rather than rebuilt
 *
 * The obvious approach - read the whole line as a string, swap the prefix, hand back a fresh literal -
 * would throw away every hover and click the message carries, which on Hypixel is how a player's
 * rank card and the clickable parts of a line work. So the component tree is walked instead and only
 * the one text run holding the marker is replaced, leaving styles, siblings and events intact.
 *
 * ### Order against the bridge bot formatter
 *
 * That formatter recognises a relay by the line starting with "Guild > " and rebuilds it with the
 * same marker. Replacing the marker first would stop it recognising anything, so it runs first and
 * this runs on its output - see ChatComponentMixin.
 *
 * Display only. Nothing is sent, and what other players see is unchanged.
 */
object GuildPrefixFormatter {

    /** Hypixel's own marker, as it appears once colour codes are stripped. */
    private const val TARGET = "Guild >"

    /**
     * Turns `&`-style colour codes into the section sign Minecraft renders.
     *
     * The config field is typed in game, where `§` is not on the keyboard - so the convention every
     * other Hypixel mod uses is followed here. Only real code characters are converted, leaving an
     * ampersand that is simply part of a guild's name alone.
     */
    fun translateColorCodes(input: String): String {
        val out = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            val c = input[i]
            val next = if (i + 1 < input.length) input[i + 1] else ' '
            if (c == '&' && isColorCode(next)) {
                out.append('§').append(next)
                i += 2
            } else {
                out.append(c)
                i++
            }
        }
        return out.toString()
    }

    private fun isColorCode(c: Char): Boolean =
        c in '0'..'9' || c.lowercaseChar() in 'a'..'f' || c.lowercaseChar() in "klmnor"

    private var translatedFrom: String? = null
    private var translated: String = ""

    /**
     * The configured tag with its colour codes translated, built once per value rather than per
     * message. The setting changes when somebody types in the config; chat arrives all day.
     */
    private fun tag(raw: String): String {
        if (raw != translatedFrom) {
            translatedFrom = raw
            translated = translateColorCodes(raw.trim())
        }
        return translated
    }

    /**
     * The rewritten line, or null when this message is not guild chat or the feature is off, in
     * which case the caller keeps the message exactly as it arrived.
     */
    @JvmStatic
    fun rewrite(original: Component): Component? {
        val cfg = AlpakaConfig.instance
        if (!cfg.guildPrefixEnabled) return null

        val replacement = tag(cfg.guildPrefixText)
        if (replacement.isEmpty()) return null

        // The cheap look before the expensive one. replaceFirst has to build its copy of the tree as
        // it walks and only finds out at the end whether anything matched, so every message that is
        // not guild chat - which is most of them - cost a whole discarded copy. Worse on a chat
        // re-wrap, where the stored history is replayed through here in one go.
        if (!containsTarget(original)) return null

        return replaceFirst(original, replacement, Replaced())
    }

    /**
     * Whether the marker is in the tree at all, without building a string or a copy.
     *
     * Indexed rather than iterated on purpose: this runs for every message that reaches the chat,
     * and an iterator per node is exactly the allocation the check exists to avoid.
     *
     * Agrees with [replaceFirst] by construction - both look only at plain text runs, so a marker
     * split across two siblings is invisible to both, and neither touches it.
     */
    private fun containsTarget(component: Component): Boolean {
        val contents = component.contents
        if (contents is PlainTextContents && contents.text().contains(TARGET)) return true

        val siblings = component.siblings
        for (index in siblings.indices) {
            if (containsTarget(siblings[index])) return true
        }
        return false
    }

    /** Carries "already done" down the recursion, so only the first marker in the line is replaced. */
    private class Replaced {
        var done = false
    }

    /**
     * Rebuilds the component with the marker replaced in the first text run that holds it.
     *
     * Returns null when nothing matched anywhere, which lets the caller pass the original through
     * untouched rather than an identical copy.
     */
    private fun replaceFirst(component: Component, replacement: String, state: Replaced): Component? {
        var changed = false

        val contents = component.contents
        val rebuilt: MutableComponent = if (!state.done && contents is PlainTextContents) {
            val text = contents.text()
            val at = text.indexOf(TARGET)
            if (at >= 0) {
                state.done = true
                changed = true
                Component.literal(text.substring(0, at) + replacement + text.substring(at + TARGET.length))
            } else {
                Component.literal(text)
            }
        } else {
            MutableComponent.create(component.contents)
        }
        rebuilt.style = component.style

        for (sibling in component.siblings) {
            val rewrittenSibling = replaceFirst(sibling, replacement, state)
            if (rewrittenSibling != null) changed = true
            rebuilt.append(rewrittenSibling ?: sibling)
        }

        return if (changed) rebuilt else null
    }
}
