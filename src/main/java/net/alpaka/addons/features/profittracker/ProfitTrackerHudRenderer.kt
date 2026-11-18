package net.alpaka.addons.features.profittracker

import net.alpaka.addons.client.gui.HudPanel
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.market.Coins
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.network.chat.Component
import kotlin.math.ceil

/**
 * Draws the Profit Tracker: the activity's name, its items with what they are worth, the coins,
 * how often its action happened, and the total and the profit per hour. Laid out like the Farming
 * HUD (label left, value right) and rebuilt a few times a second rather than every frame.
 */
object ProfitTrackerHudRenderer {

    private const val LINE_HEIGHT = 10
    private const val LABEL_GAP = 8
    private const val GLYPH_HEIGHT = 9
    private const val GROUP_GAP = 3
    private const val REBUILD_INTERVAL_MS = 250L

    private const val COLOR_TITLE = 0xFFFFAA00.toInt()
    private const val COLOR_SUBTITLE = 0xFF777777.toInt()
    private const val COLOR_LABEL = 0xFFAAAAAA.toInt()
    private const val COLOR_VALUE = 0xFFFFFFFF.toInt()
    private const val COLOR_GOOD = 0xFF55FF55.toInt()
    private const val COLOR_BAD = 0xFFFF5555.toInt()
    private const val COLOR_UNPRICED = 0xFF777777.toInt()

    private data class Line(
        val label: String,
        val value: String,
        val labelColor: Int = COLOR_LABEL,
        val valueColor: Int = COLOR_VALUE,
        val spansColumns: Boolean = false,
        val gapAbove: Boolean = false,
    )

    private class Layout(
        val rows: List<Line>,
        val labels: Array<Component>,
        val values: Array<Component?>,
        val valueXs: IntArray,
        val tops: IntArray,
        val width: Int,
        val height: Int,
    )

    private var cached: Layout? = null
    private var cachedAtMs = 0L
    private var cachedKey: Any? = null

    /** Where the HUD was last drawn in game, for clicks while the chat is open; null when it wasn't. */
    @JvmStatic
    var lastDrawn: net.alpaka.addons.client.hud.HudBounds? = null
        private set

    /** The item rows' keys in the order drawn, for clicking one to hide it. */
    private var drawnItemKeys: List<String?> = emptyList()
    private var drawnScale = 1f

    private fun layout(preview: Boolean): Layout {
        val now = System.currentTimeMillis()
        val cfg = AlpakaConfig.instance
        val smooth = cfg.smoothOn(cfg.profitTrackerSmoothFont)
        val key = listOf(preview, smooth, cfg.profitTrackerSessionOnly, cfg.profitTrackerMaxLines, ProfitTracker.version)
        val existing = cached
        if (existing != null && key == cachedKey && now - cachedAtMs < REBUILD_INTERVAL_MS) return existing

        val (rows, keys) = if (preview) previewRows() else liveRows(now)
        val font = Minecraft.getInstance().font
        val built = if (rows.isEmpty() || font == null) {
            Layout(emptyList(), emptyArray(), emptyArray(), IntArray(0), IntArray(0), 0, 0)
        } else {
            val labels = Array(rows.size) { HudPanel.text(Component.literal(rows[it].label), smooth) }
            val values = Array<Component?>(rows.size) { i ->
                rows[i].value.takeIf { it.isNotEmpty() }?.let { HudPanel.text(Component.literal(it), smooth) }
            }
            val labelWidths = IntArray(rows.size) { font.width(labels[it]) }
            val valueWidths = IntArray(rows.size) { values[it]?.let(font::width) ?: 0 }
            var labelMax = 0
            var valueMax = 0
            var spanMax = 0
            for (i in rows.indices) {
                if (rows[i].spansColumns) {
                    spanMax = maxOf(spanMax, labelWidths[i] + if (valueWidths[i] > 0) LABEL_GAP / 2 + valueWidths[i] else 0)
                } else {
                    labelMax = maxOf(labelMax, labelWidths[i])
                    valueMax = maxOf(valueMax, valueWidths[i])
                }
            }
            val width = maxOf(spanMax, labelMax + LABEL_GAP + valueMax)
            val valueXs = IntArray(rows.size)
            val tops = IntArray(rows.size)
            var top = 0
            for (i in rows.indices) {
                if (rows[i].gapAbove) top += GROUP_GAP
                tops[i] = top
                top += LINE_HEIGHT
                // Values are right-aligned, so the coins line up like a bill.
                valueXs[i] = if (rows[i].spansColumns) labelWidths[i] + LABEL_GAP / 2 else width - valueWidths[i]
            }
            Layout(rows, labels, values, valueXs, tops, width, top - (LINE_HEIGHT - GLYPH_HEIGHT))
        }
        cached = built
        cachedAtMs = now
        cachedKey = key
        drawnItemKeys = keys
        return built
    }

    private fun previewRows(): Pair<List<Line>, List<String?>> {
        val rows = listOf(
            Line("Profit Tracker", "Revenant Horror", COLOR_TITLE, COLOR_SUBTITLE, spansColumns = true),
            Line("1x Scythe Blade", "1.42M", gapAbove = true),
            Line("2x Smite VII", "612k"),
            Line("312x Revenant Flesh", "4.06k"),
            Line("Coins", "+38.2k", valueColor = COLOR_GOOD),
            Line("Slayer Quests", "-200k", valueColor = COLOR_BAD),
            Line("Bosses", "4", gapAbove = true),
            Line("Time", "12m 40s"),
            Line("Total Profit", "1.91M", COLOR_VALUE, COLOR_GOOD, gapAbove = true),
            Line("Per Hour", "9.05M"),
        )
        return rows to rows.map { null }
    }

    private fun liveRows(now: Long): Pair<List<Line>, List<String?>> {
        val cfg = AlpakaConfig.instance
        val shown = ProfitTracker.shown() ?: return emptyList<Line>() to emptyList()
        val bucket = shown.bucket
        val rows = ArrayList<Line>()
        val keys = ArrayList<String?>()
        fun add(line: Line, key: String? = null) {
            rows += line
            keys += key
        }

        add(Line("Profit Tracker", shown.activity.title + if (cfg.profitTrackerSessionOnly) "" else " (All Time)",
            COLOR_TITLE, COLOR_SUBTITLE, spansColumns = true))

        // Items by what they are worth, the unpriced ones last.
        val priced = bucket.items.entries.map { (key, item) ->
            val each = ProfitItems.price(key)
            Triple(key, item, if (each.isNaN()) Double.NaN else each * item.amount)
        }.sortedWith(compareByDescending<Triple<String, ProfitItem, Double>> { if (it.third.isNaN()) -1.0 else it.third })

        var total = 0.0
        var first = true
        var hiddenCount = 0
        var overflow = 0
        var overflowValue = 0.0
        for ((key, item, value) in priced) {
            if (item.hidden) {
                hiddenCount++
                continue
            }
            if (!value.isNaN()) total += value
            if (rows.size - 1 >= cfg.profitTrackerMaxLines) {
                overflow++
                if (!value.isNaN()) overflowValue += value
                continue
            }
            val label = "${Coins.full(item.amount.toDouble())}x ${item.name}"
            add(if (value.isNaN()) Line(label, "?", valueColor = COLOR_UNPRICED, gapAbove = first)
                else Line(label, Coins.compact(value), gapAbove = first), key)
            first = false
        }
        if (overflow > 0) add(Line("$overflow more", Coins.compact(overflowValue), COLOR_UNPRICED, COLOR_UNPRICED))
        if (hiddenCount > 0) add(Line("$hiddenCount hidden", "", COLOR_UNPRICED))

        val income = bucket.coins - shown.costs
        if (income != 0L) {
            add(Line("Coins", signed(income), valueColor = if (income >= 0) COLOR_GOOD else COLOR_BAD, gapAbove = first))
            first = false
        }
        if (shown.costs != 0L) add(Line(shown.costLabel, signed(-shown.costs), valueColor = COLOR_BAD, gapAbove = first))
        total += bucket.coins

        if (bucket.actions > 0 && shown.actionLabel != null) {
            add(Line(shown.actionLabel, Coins.full(bucket.actions.toDouble()), gapAbove = true))
            add(Line("Each", Coins.compact(total / bucket.actions)))
        }
        val activeMs = shown.activeMs
        if (activeMs > 0) add(Line("Time", formatDuration(activeMs), gapAbove = bucket.actions == 0L || shown.actionLabel == null))

        add(Line("Total Profit", Coins.compact(total), COLOR_VALUE, if (total >= 0) COLOR_GOOD else COLOR_BAD, gapAbove = true))
        if (activeMs >= 60_000L) add(Line("Per Hour", Coins.compact(total * 3_600_000.0 / activeMs)))
        return rows to keys
    }

    private fun signed(value: Long): String = (if (value > 0) "+" else "") + Coins.compact(value.toDouble())

    /** "1h 12m" above an hour, "4m 05s" below. */
    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) String.format(java.util.Locale.ROOT, "%dh %02dm", hours, minutes)
        else String.format(java.util.Locale.ROOT, "%dm %02ds", minutes, seconds)
    }

    /** Called every frame from the HUD hook; draws nothing when the tracker only shows in menus. */
    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        lastDrawn = null
        if (!ProfitTracker.isHudVisible() || AlpakaConfig.instance.profitTrackerOnlyInInventory) return
        val mc = Minecraft.getInstance()
        if (mc.gui.hud.isHidden() || mc.level == null || mc.player == null) return
        if (mc.gui.screen() != null && mc.gui.screen() !is ChatScreen) return
        draw(graphics)
    }

    /**
     * Over an open inventory or menu, when the tracker only shows there. Drawn after the menu, so
     * the darkened background doesn't cover it, and before the Chest Value panel and tooltips.
     */
    @JvmStatic
    fun renderOverMenu(graphics: GuiGraphicsExtractor, screen: Screen) {
        if (screen !is AbstractContainerScreen<*> || screen is CreativeModeInventoryScreen) return
        if (!ProfitTracker.isHudVisible() || !AlpakaConfig.instance.profitTrackerOnlyInInventory) return
        draw(graphics)
    }

    private fun draw(graphics: GuiGraphicsExtractor) {
        val mc = Minecraft.getInstance()
        val placement = ProfitTrackerHudElement.visiblePlacement(mc.window.guiScaledWidth, mc.window.guiScaledHeight)
        val scale = AlpakaConfig.instance.profitTrackerScale
        if (renderHud(graphics, placement.anchorX, placement.anchorY, scale, preview = false)) {
            val laid = cached ?: return
            lastDrawn = net.alpaka.addons.client.hud.HudBounds(placement.anchorX, placement.anchorY,
                placement.anchorX + ceil(laid.width * scale).toInt(), placement.anchorY + ceil(laid.height * scale).toInt())
            drawnScale = scale
        }
    }

    /** Draws the HUD at an explicit position and size; shared with the editor. Whether anything was drawn. */
    fun renderHud(graphics: GuiGraphicsExtractor, x: Int, y: Int, scale: Float, preview: Boolean): Boolean {
        val font = Minecraft.getInstance().font ?: return false
        val laid = layout(preview)
        if (laid.rows.isEmpty()) return false
        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), y.toFloat())
        graphics.pose().scale(scale, scale)
        if (AlpakaConfig.instance.blurOn(AlpakaConfig.instance.profitTrackerBlur)) {
            HudPanel.aroundText(graphics, laid.width, laid.height, scale)
        }
        val shadow = HudPanel.TEXT_SHADOW
        for (i in laid.rows.indices) {
            val row = laid.rows[i]
            graphics.text(font, laid.labels[i], 0, laid.tops[i], row.labelColor, shadow)
            laid.values[i]?.let { graphics.text(font, it, laid.valueXs[i], laid.tops[i], row.valueColor, shadow) }
        }
        graphics.pose().popMatrix()
        return true
    }

    /**
     * The item row under the mouse while the chat is open, for clicking it to hide or show it; null
     * when the click is not on one.
     */
    fun itemAt(mouseX: Double, mouseY: Double): String? {
        val drawn = lastDrawn ?: return null
        if (!drawn.contains(mouseX, mouseY)) return null
        val laid = cached ?: return null
        val localY = (mouseY - drawn.y0) / drawnScale
        for (i in laid.rows.indices) {
            if (localY >= laid.tops[i] && localY < laid.tops[i] + LINE_HEIGHT) return drawnItemKeys.getOrNull(i)
        }
        return null
    }

    fun width(preview: Boolean, scale: Float): Int = ceil(layout(preview).width * scale).toInt()

    fun height(preview: Boolean, scale: Float): Int = ceil(layout(preview).height * scale).toInt()

    /** Forces the next frame to rebuild, after a reset or a hidden item. */
    fun invalidate() {
        cached = null
    }
}
