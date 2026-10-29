package net.alpaka.addons.features.wheel

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.slayer.SkyblockProfileTracker
import net.minecraft.client.Minecraft

/**
 * The quick command wheel's contents, as pages.
 *
 * A wheel with too many segments gets cramped and its labels collide, so the commands are split
 * into pages of at most [MAX_PER_PAGE]; the wheel shows one page at a time and steps between them
 * with the arrows at the screen edges. The pages live in [AlpakaConfig.commandWheelPages]; the old
 * flat list is read once and folded into pages, so a saved config keeps its commands.
 *
 * There is one shared wheel, and a Skyblock profile can have one of its own
 * ([AlpakaConfig.commandWheelProfilePages]): a second profile that is not as far along has no use
 * for half the warps of the main one. A wheel is named by its key, null for the shared one and
 * "account uuid:profile" for a profile's, since two accounts can both have a profile called Kiwi.
 *
 * Every change here writes the config, and empty pages are dropped so a page number in the wheel
 * always has something on it.
 */
object CommandWheelPages {
    /** How many commands a page holds before the next one is opened. */
    const val MAX_PER_PAGE = 12

    @JvmField
    val DEFAULTS: List<String> = listOf("/hub", "/island", "/warp dh", "/wardrobe", "/pets", "/pv")

    /** The key of the profile in play, or null off Skyblock and before Hypixel has named the profile. */
    @JvmStatic
    fun currentProfileKey(): String? {
        val account = Minecraft.getInstance().user?.profileId?.toString() ?: return null
        val profile = SkyblockProfileTracker.currentFor(account) ?: return null
        return profileKey(account, profile)
    }

    internal fun profileKey(account: String, profile: String): String = "$account:$profile"

    /** The profile's name as Hypixel shows it; the tracker keeps it lowercased. */
    fun profileName(key: String): String = key.substringAfter(':').replaceFirstChar { it.titlecase() }

    /** The wheel the key opens right now: the profile's own if it has one, the shared one otherwise. */
    @JvmStatic
    fun activeWheel(): String? = currentProfileKey()?.takeIf { hasOwnWheel(it) }

    fun hasOwnWheel(profileKey: String): Boolean = AlpakaConfig.instance.commandWheelProfilePages.containsKey(profileKey)

    /** The profiles of the account in play that have a wheel of their own, by name. */
    fun profileWheels(): List<String> {
        val account = Minecraft.getInstance().user?.profileId?.toString()
        return AlpakaConfig.instance.commandWheelProfilePages.keys
            .filter { account == null || it.startsWith("$account:") }
            .sortedBy { profileName(it) }
    }

    /** Gives a profile its own wheel, starting as a copy of the shared one so nothing has to be retyped. */
    fun createProfileWheel(profileKey: String) {
        val cfg = AlpakaConfig.instance
        if (cfg.commandWheelProfilePages.containsKey(profileKey)) return
        cfg.commandWheelProfilePages[profileKey] = pages(null).mapTo(ArrayList()) { ArrayList(it) }
        AlpakaConfig.save()
    }

    /** Drops a profile's own wheel; the profile opens the shared one again. */
    fun removeProfileWheel(profileKey: String) {
        if (AlpakaConfig.instance.commandWheelProfilePages.remove(profileKey) != null) AlpakaConfig.save()
    }

    /**
     * The pages of [wheel]. A profile key without a wheel of its own reads the shared one, so a wheel
     * removed while the editor still showed it cannot leave anything pointing at nothing.
     */
    @JvmStatic
    fun pages(wheel: String?): MutableList<MutableList<String>> {
        val cfg = AlpakaConfig.instance
        if (wheel != null) {
            val own = cfg.commandWheelProfilePages[wheel]
            if (own != null) {
                if (own.isEmpty()) own.add(ArrayList())
                return own
            }
        }
        var pages = cfg.commandWheelPages
        if (pages == null || pages.isEmpty() || pages.all { it.isEmpty() }) {
            val legacy = cfg.commandWheelCommands
            val source = if (legacy != null && legacy.isNotEmpty()) legacy else DEFAULTS
            pages = ArrayList()
            source.chunked(MAX_PER_PAGE).forEach { pages.add(ArrayList(it)) }
            // Only in memory: a getter that wrote the settings file ran from the wheel's own drawing.
            // The next change to the wheel saves it, and until then a start folds the same list again.
            cfg.commandWheelPages = pages
            cfg.commandWheelCommands = null
        }
        return pages
    }

    @JvmStatic
    fun pageCount(wheel: String?): Int = pages(wheel).size

    fun contains(wheel: String?, command: String): Boolean = pages(wheel).any { it.contains(command) }

    /**
     * Adds a command. [wantedPage] null means the first page with room; a full page hands the
     * command on to the next page with room, opening a new one at the end if none has any.
     * A [wantedPage] at or past the end opens a new page. Returns the page it landed on.
     */
    fun add(wheel: String?, command: String, wantedPage: Int?): Int {
        val pages = pages(wheel)
        var target = wantedPage ?: pages.indexOfFirst { it.size < MAX_PER_PAGE }.let { if (it < 0) pages.size else it }
        if (target >= pages.size) {
            pages.add(ArrayList())
            target = pages.size - 1
        } else if (pages[target].size >= MAX_PER_PAGE) {
            var next = target + 1
            while (next < pages.size && pages[next].size >= MAX_PER_PAGE) next++
            if (next >= pages.size) pages.add(ArrayList())
            target = next
        }
        pages[target].add(command)
        AlpakaConfig.save()
        return target
    }

    fun remove(wheel: String?, page: Int, index: Int) {
        val pages = pages(wheel)
        if (page !in pages.indices || index !in pages[page].indices) return
        pages[page].removeAt(index)
        pruneEmpty(pages)
        AlpakaConfig.save()
    }

    /**
     * Moves a command to [toPage] at [toIndex]; [toPage] equal to the page count opens a new page.
     * Refused (false) when the destination page is full, so a drag cannot overfill a wheel.
     */
    fun move(wheel: String?, fromPage: Int, fromIndex: Int, toPage: Int, toIndex: Int): Boolean {
        val pages = pages(wheel)
        if (fromPage !in pages.indices || fromIndex !in pages[fromPage].indices) return false
        if (toPage < 0 || toPage > pages.size) return false
        if (toPage != fromPage && toPage < pages.size && pages[toPage].size >= MAX_PER_PAGE) return false

        val command = pages[fromPage].removeAt(fromIndex)
        if (toPage == pages.size) pages.add(ArrayList())
        val destination = pages[toPage]
        var index = toIndex
        if (toPage == fromPage && toIndex > fromIndex) index--
        destination.add(index.coerceIn(0, destination.size), command)
        pruneEmpty(pages)
        AlpakaConfig.save()
        return true
    }

    /** Moves a command to the end of the page [delta] pages away; past the last page opens a new one. */
    fun shiftToPage(wheel: String?, page: Int, index: Int, delta: Int): Boolean {
        val pages = pages(wheel)
        val target = page + delta
        if (target < 0 || target > pages.size) return false
        val destinationSize = if (target < pages.size) pages[target].size else 0
        return move(wheel, page, index, target, destinationSize)
    }

    fun reset(wheel: String?) {
        val cfg = AlpakaConfig.instance
        if (wheel != null && cfg.commandWheelProfilePages.containsKey(wheel)) {
            cfg.commandWheelProfilePages[wheel] = arrayListOf(ArrayList(DEFAULTS))
        } else {
            cfg.commandWheelPages = arrayListOf(ArrayList(DEFAULTS))
            cfg.commandWheelCommands = null
        }
        AlpakaConfig.save()
    }

    private fun pruneEmpty(pages: MutableList<MutableList<String>>) {
        pages.removeAll { it.isEmpty() }
        if (pages.isEmpty()) pages.add(ArrayList())
    }
}
