package net.alpaka.addons.features.wheel

import net.alpaka.addons.config.AlpakaConfig

/**
 * The quick command wheel's contents, as pages.
 *
 * A wheel with too many segments gets cramped and its labels collide, so the commands are split
 * into pages of at most [MAX_PER_PAGE]; the wheel shows one page at a time and steps between them
 * with the arrows at the screen edges. The pages live in [AlpakaConfig.commandWheelPages]; the old
 * flat list is read once and folded into pages, so a saved config keeps its commands.
 *
 * Every change here writes the config, and empty pages are dropped so a page number in the wheel
 * always has something on it.
 */
object CommandWheelPages {
    /** How many commands a page holds before the next one is opened. */
    const val MAX_PER_PAGE = 12

    @JvmField
    val DEFAULTS: List<String> = listOf("/hub", "/island", "/warp dh", "/wardrobe", "/pets", "/pv")

    @JvmStatic
    fun pages(): MutableList<MutableList<String>> {
        val cfg = AlpakaConfig.instance
        var pages = cfg.commandWheelPages
        if (pages == null || pages.isEmpty() || pages.all { it.isEmpty() }) {
            val legacy = cfg.commandWheelCommands
            val source = if (legacy != null && legacy.isNotEmpty()) legacy else DEFAULTS
            pages = ArrayList()
            source.chunked(MAX_PER_PAGE).forEach { pages.add(ArrayList(it)) }
            cfg.commandWheelPages = pages
            cfg.commandWheelCommands = null
            AlpakaConfig.save()
        }
        return pages
    }

    @JvmStatic
    fun pageCount(): Int = pages().size

    fun contains(command: String): Boolean = pages().any { it.contains(command) }

    /**
     * Adds a command. [wantedPage] null means the first page with room; a full page hands the
     * command on to the next page with room, opening a new one at the end if none has any.
     * A [wantedPage] at or past the end opens a new page. Returns the page it landed on.
     */
    fun add(command: String, wantedPage: Int?): Int {
        val pages = pages()
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

    fun remove(page: Int, index: Int) {
        val pages = pages()
        if (page !in pages.indices || index !in pages[page].indices) return
        pages[page].removeAt(index)
        pruneEmpty(pages)
        AlpakaConfig.save()
    }

    /**
     * Moves a command to [toPage] at [toIndex]; [toPage] equal to the page count opens a new page.
     * Refused (false) when the destination page is full, so a drag cannot overfill a wheel.
     */
    fun move(fromPage: Int, fromIndex: Int, toPage: Int, toIndex: Int): Boolean {
        val pages = pages()
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
    fun shiftToPage(page: Int, index: Int, delta: Int): Boolean {
        val pages = pages()
        val target = page + delta
        if (target < 0 || target > pages.size) return false
        val destinationSize = if (target < pages.size) pages[target].size else 0
        return move(page, index, target, destinationSize)
    }

    fun reset() {
        val cfg = AlpakaConfig.instance
        cfg.commandWheelPages = arrayListOf(ArrayList(DEFAULTS))
        cfg.commandWheelCommands = null
        AlpakaConfig.save()
    }

    private fun pruneEmpty(pages: MutableList<MutableList<String>>) {
        pages.removeAll { it.isEmpty() }
        if (pages.isEmpty()) pages.add(ArrayList())
    }
}
