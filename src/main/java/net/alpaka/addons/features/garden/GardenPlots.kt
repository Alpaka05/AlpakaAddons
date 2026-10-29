package net.alpaka.addons.features.garden

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.utils.SkyblockUtils
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import kotlin.math.floor

/**
 * The Garden's plots: a 5 by 5 grid of 96-block plots centered on the origin, the Barn in the
 * middle. Each square has a fixed number, but a plot can be renamed, and /plottp takes the name.
 * The names are read from the desk's "Configure Plots" menu, which shows the same grid, whenever
 * it is opened; until then a plot goes by its number.
 */
object GardenPlots {

    const val SIZE = 96.0
    const val BARN = 12

    private const val GRID = 5
    private const val HALF_WIDTH = SIZE * GRID / 2

    /** The plot numbers, by row from north to south and column from west to east. */
    private val NUMBERS = intArrayOf(
        21, 13, 9, 14, 22,
        15, 5, 1, 6, 16,
        10, 2, 0, 3, 11,
        17, 7, 4, 8, 18,
        23, 19, 12, 20, 24,
    )

    private const val MENU_TITLE = "Configure Plots"
    private const val NAME_PREFIX = "Plot - "

    /** The grid square (row * 5 + column) a position lies in, or null off the grid. */
    @JvmStatic
    fun index(x: Double, z: Double): Int? {
        if (x < -HALF_WIDTH || x > HALF_WIDTH || z < -HALF_WIDTH || z > HALF_WIDTH) return null
        val col = floor((x + HALF_WIDTH) / SIZE).toInt().coerceIn(0, GRID - 1)
        val row = floor((z + HALF_WIDTH) / SIZE).toInt().coerceIn(0, GRID - 1)
        return row * GRID + col
    }

    /** The name /plottp knows the plot in square [index] by. */
    @JvmStatic
    fun name(index: Int): String {
        val names = AlpakaConfig.instance.pestFinderPlotNames
        return names.getOrNull(index)?.takeIf { it.isNotBlank() } ?: NUMBERS[index].toString()
    }

    /** The menu slot of square [index]: the grid starts in the third column of the chest. */
    private fun slot(index: Int): Int = 2 + (index / GRID) * 9 + index % GRID

    /**
     * Reads the plot names off an open "Configure Plots" menu and keeps them when they changed.
     * Called every few ticks while a menu is open; anything else is left alone.
     */
    @JvmStatic
    fun readMenu(screen: AbstractContainerScreen<*>) {
        if (SkyblockUtils.cleanColor(screen.title.string).trim() != MENU_TITLE) return
        val slots = screen.menu.slots
        val names = ArrayList<String>(GRID * GRID)
        for (index in 0 until GRID * GRID) {
            val slot = slot(index)
            // The menu fills in after it opens; wait until every square holds an item.
            val stack = slots.getOrNull(slot)?.item ?: return
            if (stack.isEmpty) return
            val name = SkyblockUtils.cleanColor(stack.hoverName.string).trim()
            names.add(if (name.startsWith(NAME_PREFIX)) name.substring(NAME_PREFIX.length).trim() else "")
        }
        val cfg = AlpakaConfig.instance
        if (names != cfg.pestFinderPlotNames) {
            cfg.pestFinderPlotNames = names
            AlpakaConfig.save()
        }
    }
}
