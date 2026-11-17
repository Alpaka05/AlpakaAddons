package net.alpaka.addons.features.inventorybuttons

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
 *
 * Buttons snap to a grid that ends flush with the player's inventory (176 by 166) on every side.
 * Down, seven buttons 2 px apart fill its height exactly (7 * 22 + 6 * 2 = 166). Across no whole
 * gap does that for a size that isn't too big, so seven columns are spread evenly over its width,
 * 3 or 4 px apart, and the grid carries on past both sides at the same pace.
 */
object InventoryButtonLayout {
    /**
     * A button's width and height, the same in every look: room for vanilla's button frame around
     * a 16 px item, which is drawn at its own size in the middle, never scaled up.
     */
    const val SIZE = InventoryButton.SIZE

    /** The space between a button and the panel's side: where the grid's columns next to it fall. */
    const val GAP_X = 4

    /** The space left between two buttons one above the other, and between a button and the panel's top or bottom. */
    const val GAP_Y = 2

    /** The size buttons were laid out for before every look had a common size: a slot's size. */
    const val OLD_SIZE = 18

    /** The smallest gap two columns may have, for how many fit across a panel. */
    private const val MIN_GAP_X = 3

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
        button.layout = InventoryButton.LAYOUT
    }

    private fun store(button: InventoryButton, x: Int, y: Int, box: Box, size: Int) {
        button.anchorRight = x + size / 2 > box.left + box.width / 2
        button.anchorBottom = y + size / 2 > box.top + box.height / 2
        button.x = x - if (button.anchorRight) box.right else box.left
        button.y = y - if (button.anchorBottom) box.bottom else box.top
    }

    /** How many columns of the grid fit across [box]: seven for the inventory and chests. */
    @JvmStatic
    fun columns(box: Box): Int = maxOf(1, (box.width + MIN_GAP_X) / (SIZE + MIN_GAP_X))

    /**
     * The left edge of column [i] of the grid: 0 is flush with the panel's left edge, the last of
     * [columns] flush with its right edge, the ones between spread evenly, and the grid goes on at
     * the same pace on both sides, so column -1 sits [GAP_X] left of the panel.
     */
    @JvmStatic
    fun columnX(box: Box, i: Int): Int {
        val n = columns(box)
        if (n < 2) return box.left + i * (SIZE + GAP_X)
        return box.left + (i * (box.width - SIZE).toFloat() / (n - 1)).roundToInt()
    }

    /** The top edge of row [i] of the grid, counted from the panel's top edge, [GAP_Y] apart. */
    @JvmStatic
    fun rowY(box: Box, i: Int): Int = box.top + i * (SIZE + GAP_Y)

    /** The player's inventory panel, which old layouts are converted against. */
    private val INVENTORY = Box(0, 0, 176, 166)

    /**
     * Moves buttons from an older grid ([InventoryButton.layout]) onto today's, once, when the
     * settings load. Returns whether anything changed.
     *
     * Each button goes to the cell of the new grid that matches its place in the old one: the third
     * in a row over the panel is the third column, the first beside the panel's left edge is column
     * -1, the second above it row -2. A row that ran a little past the panel's edge in the old grid
     * moves along instead of onto a button that is already in that cell.
     */
    @JvmStatic
    fun migrate(buttons: List<InventoryButton>?): Boolean {
        if (buttons == null || buttons.none { it.layout != InventoryButton.LAYOUT }) return false
        val box = INVENTORY
        val n = columns(box)
        val rows = (box.height + GAP_Y) / (SIZE + GAP_Y)
        class Cell(val button: InventoryButton, val oldX: Int, var col: Int, val row: Int)
        val cells = buttons.filter { it.layout != InventoryButton.LAYOUT }.map { button ->
            val old = oldSize(button)
            val b = oldPlace(button, box)
            val row = cell(b[1], old, box.top, box.bottom, oldStepY(button), rows)
            // A row above or below the panel counts its places from the left edge, also where the
            // bigger old buttons pushed its last one past the right edge.
            val col = if (row !in 0 until rows && b[0] >= box.left) ((b[0] - box.left).toFloat() / oldStepX(button)).roundToInt()
                else cell(b[0], old, box.left, box.right, oldStepX(button), n)
            Cell(button, b[0], col, row)
        }
        // Buttons in one row keep their order, one cell apart at least.
        for (row in cells.groupBy { it.row }.values) {
            var last = Int.MIN_VALUE
            for (c in row.sortedBy { it.oldX }) {
                if (last != Int.MIN_VALUE && c.col <= last) c.col = last + 1
                last = c.col
            }
        }
        for (c in cells) {
            store(c.button, columnX(box, c.col), rowY(box, c.row), box, SIZE)
            c.button.grid = SIZE
            c.button.layout = InventoryButton.LAYOUT
        }
        return true
    }

    /**
     * The cell along one axis an old button at [b], of size [old], falls into: counted from the
     * panel's near edge for one before it ([start]) or after it ([end]), so its gap to the panel
     * stays one gap, and from [start] over the panel itself, where there are [count] cells.
     */
    private fun cell(b: Int, old: Int, start: Int, end: Int, step: Int, count: Int): Int = when {
        b + old <= start -> -1 - maxOf(0, ((start - (b + old) - MIN_GAP_X).toFloat() / step).roundToInt())
        b >= end -> count + maxOf(0, ((b - end - MIN_GAP_X).toFloat() / step).roundToInt())
        else -> ((b - start).toFloat() / step).roundToInt().coerceIn(0, count - 1)
    }

    /** The size [button] was laid out for; anything unknown counts as [OLD_SIZE]. */
    private fun oldSize(button: InventoryButton): Int = if (button.grid in OLD_SIZE until 64) button.grid else OLD_SIZE

    /** How far apart the old grid put two buttons side by side: 2 px gaps, 4 px for the 26 px buttons. */
    private fun oldStepX(button: InventoryButton): Int = oldSize(button) + if (button.grid == 26) 4 else 2

    /** How far apart the old grid put two buttons one above the other: always 2 px gaps. */
    private fun oldStepY(button: InventoryButton): Int = oldSize(button) + 2

    private fun oldPlace(button: InventoryButton, box: Box): IntArray = intArrayOf(
        (if (button.anchorRight) box.right else box.left) + button.x,
        (if (button.anchorBottom) box.bottom else box.top) + button.y,
    )

    /**
     * Snaps a dragged corner to what is near it, each axis on its own. Across: the grid's columns
     * ([columnX]), and lined up with another button. Down: the panel's edges and just outside them,
     * and the other buttons - lined up with one, or right above or below it. Returns the corner,
     * then 1 or 0 for whether x and y snapped, for the guides.
     */
    @JvmStatic
    fun snap(x: Int, y: Int, box: Box, others: List<IntArray>, range: Int = SNAP_RANGE): IntArray {
        val xs = ArrayList<Int>()
        val ys = ArrayList<Int>()
        // The column under the corner and its neighbours, wherever on the screen it is.
        val step = (box.width - SIZE).toFloat() / maxOf(1, columns(box) - 1)
        val near = ((x - box.left) / step).roundToInt()
        for (i in near - 1..near + 1) xs += columnX(box, i)
        ys += listOf(box.top, box.bottom - SIZE, box.top - SIZE - GAP_Y, box.bottom + GAP_Y)
        for (o in others) {
            xs += o[0]
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
