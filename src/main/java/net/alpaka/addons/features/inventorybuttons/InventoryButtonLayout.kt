package net.alpaka.addons.features.inventorybuttons

import net.alpaka.addons.config.AlpakaConfig.InventoryButton

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
    /** A button's width and height, a slot's size. */
    const val SIZE = 18

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
        button.anchorRight = screenX + SIZE / 2 > box.left + box.width / 2
        button.anchorBottom = screenY + SIZE / 2 > box.top + box.height / 2
        button.x = screenX - if (button.anchorRight) box.right else box.left
        button.y = screenY - if (button.anchorBottom) box.bottom else box.top
    }

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

    /** How close, in GUI pixels, a dragged button has to come to a line to snap to it. */
    const val SNAP_RANGE = 4

    /** The space left between two buttons side by side, and between a button and the panel. */
    const val GAP = 2
}
