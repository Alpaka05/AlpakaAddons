package net.alpaka.addons.features.inventorybuttons

import net.alpaka.addons.config.AlpakaConfig.InventoryButton
import kotlin.math.abs

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
     *
     * Picked with [GAP_X] and [GAP_Y] so buttons end flush with the player's inventory (176 by
     * 166) on both sides: six side by side fill its width (6 * 26 + 5 * 4 = 176), six stacked fill
     * its height (6 * 26 + 5 * 2 = 166). No one gap does both for any size from 18 up.
     */
    const val SIZE = InventoryButton.SIZE

    /** The space left between two buttons side by side, and between a button and the panel's side. */
    const val GAP_X = 4

    /** The space left between two buttons one above the other, and between a button and the panel's top or bottom. */
    const val GAP_Y = 2

    /** The size buttons were laid out for before every look had a common size: a slot's size. */
    const val OLD_SIZE = 18

    /** The space old layouts left between two buttons, at every earlier size. */
    private const val OLD_GAP = 2

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
     * Moves buttons laid out for an earlier size ([InventoryButton.grid]: [OLD_SIZE] or 22) to where
     * they belong at [SIZE], once, when the settings load. Returns whether anything changed.
     *
     * Each button keeps its place in the grid: a button that was the third in a row is the third
     * in the row at the new size and gaps, so buttons that didn't overlap before don't now, and a
     * row that sat flush with the panel still does. A row or column spreads as a whole from one
     * panel edge; spreading each button from its own anchor would pull the left half of a row above
     * the panel to the right and the right half to the left, into each other. A row above or below
     * the panel spreads from the panel's left edge and away from the panel; a column beside it
     * spreads down from the top edge and away from its side.
     */
    @JvmStatic
    fun migrate(buttons: List<InventoryButton>?): Boolean {
        if (buttons == null || buttons.none { it.grid != SIZE }) return false
        val box = INVENTORY
        val moved = buttons.map { button ->
            if (button.grid == SIZE) return@map null
            val old = oldSize(button)
            val b = oldPlace(button, box)
            val o = origin(button, b, box, buttons)
            intArrayOf(spread(b[0], o[0], old, SIZE + GAP_X), spread(b[1], o[1], old, SIZE + GAP_Y))
        }
        for ((i, button) in buttons.withIndex()) {
            val p = moved[i] ?: continue
            store(button, p[0], p[1], box, SIZE)
            button.grid = SIZE
        }
        return true
    }

    /** The size [button] was laid out for; anything unknown counts as [OLD_SIZE]. */
    private fun oldSize(button: InventoryButton): Int = if (button.grid in OLD_SIZE until 64) button.grid else OLD_SIZE

    /**
     * One coordinate of an old button at [b], of size [old], moved away from the panel edge [o]
     * by whole steps of the new grid: as many steps of [newStep] as it was steps of the old one
     * away, plus whatever was left over, unscaled. A button before the edge (left of or above it)
     * counts from its far side, so its gap to the panel stays the gap it had.
     */
    private fun spread(b: Int, o: Int, old: Int, newStep: Int): Int {
        val oldStep = old + OLD_GAP
        if (b >= o) {
            val d = b - o
            return o + d / oldStep * newStep + d % oldStep
        }
        val e = o - (b + old)
        if (e < 0) return o - SIZE + (b + old - o)
        return o - (e / oldStep * newStep + e % oldStep) - SIZE
    }

    private fun oldPlace(button: InventoryButton, box: Box): IntArray = intArrayOf(
        (if (button.anchorRight) box.right else box.left) + button.x,
        (if (button.anchorBottom) box.bottom else box.top) + button.y,
    )

    /** The panel corner an old [button], at [b], spreads out from; see [migrate]. */
    private fun origin(button: InventoryButton, b: IntArray, box: Box, buttons: List<InventoryButton>): IntArray {
        val old = oldSize(button)
        val nearX = if (b[0] + old / 2 < box.left + box.width / 2) box.left else box.right
        val nearY = if (b[1] + old / 2 < box.top + box.height / 2) box.top else box.bottom
        val beside = besidePanel(b, old, box)
        val aboveOrBelow = aboveOrBelowPanel(b, old, box)
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
            val os = oldSize(other)
            if (abs(ob[0] - b[0]) < old && besidePanel(ob, os, box)) inColumn = true
            if (abs(ob[1] - b[1]) < old && aboveOrBelowPanel(ob, os, box)) inRow = true
        }
        return intArrayOf(if (inColumn) nearX else box.left, if (inRow) nearY else box.top)
    }

    /** Left or right of the panel, level with some part of it. */
    private fun besidePanel(b: IntArray, size: Int, box: Box): Boolean = b[1] + size > box.top && b[1] < box.bottom

    /** Above or below the panel, or over it, in line with some part of it across. */
    private fun aboveOrBelowPanel(b: IntArray, size: Int, box: Box): Boolean = b[0] + size > box.left && b[0] < box.right

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
        xs += listOf(box.left, box.right - SIZE, box.left - SIZE - GAP_X, box.right + GAP_X)
        ys += listOf(box.top, box.bottom - SIZE, box.top - SIZE - GAP_Y, box.bottom + GAP_Y)
        for (o in others) {
            xs += listOf(o[0], o[0] - SIZE - GAP_X, o[0] + SIZE + GAP_X)
            ys += listOf(o[1], o[1] - SIZE - GAP_Y, o[1] + SIZE + GAP_Y)
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

    /** How close, in GUI pixels, a dragged button has to come to a line to snap to it. */
    const val SNAP_RANGE = 4
}
