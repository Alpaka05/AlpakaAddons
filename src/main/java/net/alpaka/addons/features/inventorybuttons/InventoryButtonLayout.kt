package net.alpaka.addons.features.inventorybuttons

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.config.AlpakaConfig.InventoryButton
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
    /** A button's width and height in the Alpaka and Icon Only looks, a slot's size. */
    const val BASE_SIZE = 18

    /** A button's width and height in the vanilla looks, room for the button's frame around the icon. */
    const val VANILLA_SIZE = 22

    /** A button's width and height in the look picked in the settings. */
    @JvmStatic
    val SIZE: Int
        get() = when (AlpakaConfig.instance.inventoryButtonsStyle) {
            InventoryButtonsFeature.STYLE_VANILLA, InventoryButtonsFeature.STYLE_VANILLA_DARK -> VANILLA_SIZE
            else -> BASE_SIZE
        }

    /**
     * How far positions are stretched for the current size. Positions are stored for [BASE_SIZE];
     * larger buttons spread out by the same factor, so buttons that didn't overlap at the base size
     * don't overlap at the larger one either.
     */
    private val scale: Float
        get() = SIZE.toFloat() / BASE_SIZE

    /** A container's panel on the screen, in GUI pixels. */
    data class Box(val left: Int, val top: Int, val width: Int, val height: Int) {
        val right: Int get() = left + width
        val bottom: Int get() = top + height
    }

    /** The button's top-left corner on a screen of [screenW] by [screenH], kept fully on it. */
    @JvmStatic
    fun place(button: InventoryButton, box: Box, screenW: Int, screenH: Int): IntArray {
        val p = placeFree(button, box, others())
        return intArrayOf(
            p[0].coerceIn(0, maxOf(0, screenW - SIZE)),
            p[1].coerceIn(0, maxOf(0, screenH - SIZE)),
        )
    }

    /** Stores a top-left corner on the screen in [button], against the panel edges nearest to it. */
    @JvmStatic
    fun store(button: InventoryButton, screenX: Int, screenY: Int, box: Box) {
        val k = scale
        if (k == 1f) {
            storeBase(button, screenX, screenY, box)
            return
        }
        // Undo the stretch from each corner it could have been stretched from, and keep the first
        // guess that lands back where it was put; which corner applies depends on where the
        // button ends up, so it can't be known before.
        val others = others()
        var best: IntArray? = null
        var bestMiss = Int.MAX_VALUE
        for (ox in listOf(box.left, box.right)) for (oy in listOf(box.top, box.bottom)) {
            val bx = (ox + (screenX - ox) / k).roundToInt()
            val by = (oy + (screenY - oy) / k).roundToInt()
            storeBase(button, bx, by, box)
            val p = placeFree(button, box, others)
            val miss = kotlin.math.abs(p[0] - screenX) + kotlin.math.abs(p[1] - screenY)
            if (miss < bestMiss) {
                bestMiss = miss
                best = intArrayOf(bx, by)
            }
            if (miss == 0) return
        }
        best?.let { storeBase(button, it[0], it[1], box) }
    }

    /** The buttons the stretch looks at to tell rows from columns. */
    private fun others(): List<InventoryButton> = AlpakaConfig.instance.inventoryButtons ?: emptyList()

    /** Where [button] sits at [BASE_SIZE], before any stretch. */
    private fun basePlace(button: InventoryButton, box: Box): IntArray = intArrayOf(
        (if (button.anchorRight) box.right else box.left) + button.x,
        (if (button.anchorBottom) box.bottom else box.top) + button.y,
    )

    private fun storeBase(button: InventoryButton, x: Int, y: Int, box: Box) {
        button.anchorRight = x + BASE_SIZE / 2 > box.left + box.width / 2
        button.anchorBottom = y + BASE_SIZE / 2 > box.top + box.height / 2
        button.x = x - if (button.anchorRight) box.right else box.left
        button.y = y - if (button.anchorBottom) box.bottom else box.top
    }

    /**
     * The button's corner at the current size, not yet kept on the screen.
     *
     * Larger buttons spread out from a panel corner, the same corner for every button of a row or
     * a column, so the whole row grows as one. Stretching each button from its own anchor would
     * pull the left half of a row above the panel to the right and the right half to the left,
     * into each other. A row above or below the panel spreads from the panel's left edge and away
     * from the panel; a column beside it spreads down from the top edge and away from its side.
     */
    private fun placeFree(button: InventoryButton, box: Box, others: List<InventoryButton>): IntArray {
        val b = basePlace(button, box)
        val k = scale
        if (k == 1f) return b
        val o = origin(button, b, box, others)
        return intArrayOf(
            (o[0] + (b[0] - o[0]) * k).roundToInt(),
            (o[1] + (b[1] - o[1]) * k).roundToInt(),
        )
    }

    /** The panel corner [button], at [b], spreads out from; see [placeFree]. */
    private fun origin(button: InventoryButton, b: IntArray, box: Box, others: List<InventoryButton>): IntArray {
        val nearX = if (b[0] + BASE_SIZE / 2 < box.left + box.width / 2) box.left else box.right
        val nearY = if (b[1] + BASE_SIZE / 2 < box.top + box.height / 2) box.top else box.bottom
        val beside = besidePanel(b, box)
        val aboveOrBelow = aboveOrBelowPanel(b, box)
        if (beside || aboveOrBelow) {
            return intArrayOf(if (beside) nearX else box.left, if (aboveOrBelow) nearY else box.top)
        }
        // Off a corner of the panel: part of a column if a button beside the panel is above or
        // below it, part of a row if one above or below the panel is next to it.
        var inColumn = false
        var inRow = false
        for (other in others) {
            if (other === button) continue
            val ob = basePlace(other, box)
            if (kotlin.math.abs(ob[0] - b[0]) < BASE_SIZE && besidePanel(ob, box)) inColumn = true
            if (kotlin.math.abs(ob[1] - b[1]) < BASE_SIZE && aboveOrBelowPanel(ob, box)) inRow = true
        }
        return intArrayOf(if (inColumn) nearX else box.left, if (inRow) nearY else box.top)
    }

    /** Left or right of the panel, level with some part of it. */
    private fun besidePanel(b: IntArray, box: Box): Boolean = b[1] + BASE_SIZE > box.top && b[1] < box.bottom

    /** Above or below the panel, or over it, in line with some part of it across. */
    private fun aboveOrBelowPanel(b: IntArray, box: Box): Boolean = b[0] + BASE_SIZE > box.left && b[0] < box.right

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
