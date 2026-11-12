package net.alpaka.addons.features.inventorybuttons

import net.alpaka.addons.config.AlpakaConfig.InventoryButton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Where an inventory button sits on the screen, and how a position on the screen is stored.
 *
 * A button is kept against the edge of the container's panel it is nearest to: the left or right
 * edge across, the top or bottom edge down. Container panels differ in height - a chest of six rows
 * is taller than the player's inventory, and both are centred - so a button beside the player's own
 * slots, which are always at the bottom, keeps its place next to them, and one above a chest stays
 * above it. Kept apart from Minecraft so it can be tested on its own.
 */
object InventoryButtonLayout {
    /**
     * A button's width and height, the same in every look: room for vanilla's button frame around
     * a 16 px item, which is drawn at its own size in the middle, never scaled up.
     */
    const val SIZE = InventoryButton.SIZE

    /** The size buttons were laid out for before every look had [SIZE]: a slot's size. */
    const val OLD_SIZE = 18

    /** A container's panel on the screen, in GUI pixels. */
    data class Box(val left: Int, val top: Int, val width: Int, val height: Int) {
        val right: Int get() = left + width
        val bottom: Int get() = top + height
    }

    /** The button's top-left corner on a screen of [screenW] by [screenH], kept fully on it. */
    @JvmStatic
    fun place(button: InventoryButton, box: Box, screenW: Int, screenH: Int): IntArray {
        val x = (if (button.anchorRight) box.right else box.left) + button.x
        val y = (if (button.anchorBottom) box.bottom else box.top) + button.y
        return intArrayOf(
            x.coerceIn(0, maxOf(0, screenW - SIZE)),
            y.coerceIn(0, maxOf(0, screenH - SIZE)),
        )
    }

    /** Stores a top-left corner on the screen in [button], against the panel edges nearest to it. */
    @JvmStatic
    fun store(button: InventoryButton, screenX: Int, screenY: Int, box: Box) {
        store(button, screenX, screenY, box, SIZE)
        button.grid = SIZE
    }

    private fun store(button: InventoryButton, x: Int, y: Int, box: Box, size: Int) {
        button.anchorRight = x + size / 2 > box.left + box.width / 2
        button.anchorBottom = y + size / 2 > box.top + box.height / 2
        button.x = x - if (button.anchorRight) box.right else box.left
        button.y = y - if (button.anchorBottom) box.bottom else box.top
    }

    /** The player's inventory panel, which old layouts are converted against. */
    private val INVENTORY = Box(0, 0, 176, 166)

    /**
     * Moves buttons laid out for [OLD_SIZE] to where they belong at [SIZE], once, when the settings
     * load. Returns whether anything changed.
     *
     * The buttons spread out by the size ratio, so buttons that didn't overlap before don't now,
     * and a row or column spreads as a whole from one panel edge. Spreading each button from its
     * own anchor would pull the left half of a row above the panel to the right and the right half
     * to the left, into each other. A row above or below the panel spreads from the panel's left
     * edge and away from the panel; a column beside it spreads down from the top edge and away
     * from its side.
     */
    @JvmStatic
    fun migrate(buttons: List<InventoryButton>?): Boolean {
        if (buttons == null || buttons.none { it.grid != SIZE }) return false
        val box = INVENTORY
        val k = SIZE.toFloat() / OLD_SIZE
        val moved = buttons.map { button ->
            if (button.grid == SIZE) return@map null
            val b = oldPlace(button, box)
            val o = origin(button, b, box, buttons)
            intArrayOf(
                (o[0] + (b[0] - o[0]) * k).roundToInt(),
                (o[1] + (b[1] - o[1]) * k).roundToInt(),
            )
        }
        for ((i, button) in buttons.withIndex()) {
            val p = moved[i] ?: continue
            store(button, p[0], p[1], box, SIZE)
            button.grid = SIZE
        }
        return true
    }

    private fun oldPlace(button: InventoryButton, box: Box): IntArray = intArrayOf(
        (if (button.anchorRight) box.right else box.left) + button.x,
        (if (button.anchorBottom) box.bottom else box.top) + button.y,
    )

    /** The panel corner an old [button], at [b], spreads out from; see [migrate]. */
    private fun origin(button: InventoryButton, b: IntArray, box: Box, buttons: List<InventoryButton>): IntArray {
        val nearX = if (b[0] + OLD_SIZE / 2 < box.left + box.width / 2) box.left else box.right
        val nearY = if (b[1] + OLD_SIZE / 2 < box.top + box.height / 2) box.top else box.bottom
        val beside = besidePanel(b, box)
        val aboveOrBelow = aboveOrBelowPanel(b, box)
        if (beside || aboveOrBelow) {
            return intArrayOf(if (beside) nearX else box.left, if (aboveOrBelow) nearY else box.top)
        }
        // Off a corner of the panel: part of a column if a button beside the panel is above or
        // below it, part of a row if one above or below the panel is next to it.
        var inColumn = false
        var inRow = false
        for (other in buttons) {
            if (other === button) continue
            val ob = oldPlace(other, box)
            if (abs(ob[0] - b[0]) < OLD_SIZE && besidePanel(ob, box)) inColumn = true
            if (abs(ob[1] - b[1]) < OLD_SIZE && aboveOrBelowPanel(ob, box)) inRow = true
        }
        return intArrayOf(if (inColumn) nearX else box.left, if (inRow) nearY else box.top)
    }

    /** Left or right of the panel, level with some part of it. */
    private fun besidePanel(b: IntArray, box: Box): Boolean = b[1] + OLD_SIZE > box.top && b[1] < box.bottom

    /** Above or below the panel, or over it, in line with some part of it across. */
    private fun aboveOrBelowPanel(b: IntArray, box: Box): Boolean = b[0] + OLD_SIZE > box.left && b[0] < box.right

    /**
     * Snaps a dragged corner to what is near it, each axis on its own: the panel's edges, just
     * outside them, and the other buttons - lined up with one, or right next to it. Returns the
     * corner, then 1 or 0 for whether x and y snapped, for the guides.
     */
    @JvmStatic
    fun snap(x: Int, y: Int, box: Box, others: List<IntArray>, range: Int = SNAP_RANGE): IntArray {
        val xs = ArrayList<Int>()
        val ys = ArrayList<Int>()
        // Flush with an edge inside the panel, or just outside it with a gap.
        xs += listOf(box.left, box.right - SIZE, box.left - SIZE - GAP, box.right + GAP)
        ys += listOf(box.top, box.bottom - SIZE, box.top - SIZE - GAP, box.bottom + GAP)
        for (o in others) {
            xs += listOf(o[0], o[0] - SIZE - GAP, o[0] + SIZE + GAP)
            ys += listOf(o[1], o[1] - SIZE - GAP, o[1] + SIZE + GAP)
        }
        val sx = nearest(x, xs, range)
        val sy = nearest(y, ys, range)
        return intArrayOf(sx ?: x, sy ?: y, if (sx != null) 1 else 0, if (sy != null) 1 else 0)
    }

    private fun nearest(value: Int, candidates: List<Int>, range: Int): Int? {
        var best: Int? = null
        var bestDistance = range + 1
        for (c in candidates) {
            val d = kotlin.math.abs(c - value)
            if (d < bestDistance) {
                best = c
                bestDistance = d
            }
        }
        return best
    }

    /**
     * The buttons, by index into [positions] (top-left corners), that sit in one row with the one at
     * [index]: at the same height, each next to the one before with no more than a small gap.
     * Sorted left to right.
     */
    @JvmStatic
    fun rowOf(positions: List<IntArray>, index: Int): List<Int> {
        val y = positions[index][1]
        val level = positions.indices.filter { positions[it][1] == y }.sortedBy { positions[it][0] }
        val at = level.indexOf(index)
        var first = at
        var last = at
        while (first > 0 && adjacent(positions[level[first - 1]], positions[level[first]])) first--
        while (last < level.size - 1 && adjacent(positions[level[last]], positions[level[last + 1]])) last++
        return level.subList(first, last + 1)
    }

    private fun adjacent(left: IntArray, right: IntArray): Boolean = right[0] - (left[0] + SIZE) <= GAP + 2

    /**
     * How far [row] (indices into [positions], from [rowOf]) has to move across to be centred on
     * the panel: as much room left of it as right of it. 0 for a single button, which may sit
     * anywhere on purpose, and for a row that isn't above or below the panel.
     */
    @JvmStatic
    fun centerShift(positions: List<IntArray>, row: List<Int>, box: Box): Int {
        if (row.size < 2) return 0
        val left = row.minOf { positions[it][0] }
        val right = row.maxOf { positions[it][0] } + SIZE
        val y = positions[row[0]][1]
        val outside = y + SIZE <= box.top || y >= box.bottom
        val across = right > box.left && left < box.right
        if (!outside || !across) return 0
        return box.left + (box.width - (right - left)) / 2 - left
    }

    /** How close, in GUI pixels, a dragged button has to come to a line to snap to it. */
    const val SNAP_RANGE = 4

    /** The space left between two buttons side by side, and between a button and the panel. */
    const val GAP = 2
}
