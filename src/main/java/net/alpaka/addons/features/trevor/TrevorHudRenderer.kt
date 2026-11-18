package net.alpaka.addons.features.trevor

import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.HudPanel
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.util.Locale
import kotlin.math.ceil

/**
 * Draws the Trevor Helper HUD on the Farming Islands: the task in progress and the wait for the
 * next one, then the pelts, the average hunt and the animals hunted by kind. Laid out like the
 * Farming HUD, label and value per row, and rebuilt a few times a second rather than every frame.
 */
object TrevorHudRenderer {

    private const val LINE_HEIGHT = 10
    private const val LABEL_GAP = 5
    private const val GLYPH_HEIGHT = 9
    private const val GROUP_GAP = 3
    private const val REBUILD_INTERVAL_MS = 250L

    private const val COLOR_TITLE = 0xFF55FF55.toInt()
    private const val COLOR_LABEL = 0xFFAAAAAA.toInt()
    private const val COLOR_VALUE = 0xFFFFFFFF.toInt()
    private const val COLOR_GOOD = 0xFF55FF55.toInt()
    private const val COLOR_WARN = 0xFFFFFF55.toInt()
    private const val COLOR_PELTS = 0xFFAA00AA.toInt()

    private data class Line(
        val label: String,
        val value: String,
        val valueColor: Int,
        val title: Boolean = false,
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
    private var cachedPreview = false
    private var cachedFont: Style? = null

    private fun layout(preview: Boolean): Layout {
        val now = System.currentTimeMillis()
        val smooth = AlpakaConfig.instance.smoothOn(AlpakaConfig.instance.trevorHudSmoothFont)
        val fontStyle = if (smooth) GuiFont.style() else Style.EMPTY
        cached?.let {
            if (cachedPreview == preview && cachedFont == fontStyle && now - cachedAtMs < REBUILD_INTERVAL_MS) return it
        }
        val font = Minecraft.getInstance().font
        val rows = buildRows(preview, now)
        val labels = Array(rows.size) { HudPanel.text(Component.literal(rows[it].label), smooth) }
        val values = Array(rows.size) { i ->
            rows[i].value.takeIf { it.isNotEmpty() }?.let { HudPanel.text(Component.literal(it), smooth) }
        }
        var labelMax = 0
        for (i in rows.indices) if (!rows[i].title) labelMax = maxOf(labelMax, font.width(labels[i]))
        val valueXs = IntArray(rows.size)
        val tops = IntArray(rows.size)
        var top = 0
        var widest = 0
        for (i in rows.indices) {
            if (rows[i].gapAbove) top += GROUP_GAP
            tops[i] = top
            top += LINE_HEIGHT
            val labelEnd = font.width(labels[i])
            valueXs[i] = if (rows[i].title) labelEnd + LABEL_GAP else labelMax + LABEL_GAP
            val end = values[i]?.let { valueXs[i] + font.width(it) } ?: labelEnd
            widest = maxOf(widest, end)
        }
        val height = if (rows.isEmpty()) 0 else top - (LINE_HEIGHT - GLYPH_HEIGHT)
        val built = Layout(rows, labels, values, valueXs, tops, widest, height)
        cached = built
        cachedAtMs = now
        cachedPreview = preview
        cachedFont = fontStyle
        return built
    }

    private fun buildRows(preview: Boolean, now: Long): List<Line> {
        val rows = ArrayList<Line>(12)
        rows.add(Line("§lTrevor", "", COLOR_TITLE, title = true))

        // The task.
        if (preview) {
            rows.add(Line("Hunt", "Cow · Trackable · 34m", COLOR_VALUE))
            rows.add(Line("Time Left", "8m 12s", COLOR_VALUE))
            rows.add(Line("Next Task", "12s", COLOR_WARN))
        } else {
            if (TrevorTracker.huntActive) {
                val distance = TrevorTracker.targetDistance()
                val kind = TrevorTracker.targetKind
                val hunt = when {
                    kind != null && distance != null ->
                        "${kind.title} · ${TrevorTracker.rarity} · ${String.format(Locale.ROOT, "%.0fm", distance)}"
                    kind != null -> "${kind.title} · ${TrevorTracker.rarity}"
                    else -> "${TrevorTracker.rarity} · ${TrevorTracker.location}"
                }
                rows.add(Line("Hunt", hunt, COLOR_VALUE))
                rows.add(Line("Time Left", TrevorParse.formatDuration(TrevorTracker.huntLeftMs(now)), COLOR_VALUE))
            }
            val wait = TrevorTracker.nextTaskInMs(now)
            rows.add(
                when {
                    wait > 0L -> Line("Next Task", TrevorParse.formatDuration(wait), COLOR_WARN)
                    TrevorTracker.huntActive -> Line("Next Task", "After This Hunt", COLOR_LABEL)
                    else -> Line("Next Task", "Ready", COLOR_GOOD)
                },
            )
        }

        // Pelts and pace.
        val pelts = if (preview) 1_284 else TrevorTracker.sidebarPelts()
        val gained = if (preview) 312L else TrevorTracker.stats.pelts
        rows.add(
            if (pelts != null) Line("Pelts", "${formatNumber(pelts.toLong())} (+${formatNumber(gained)})", COLOR_PELTS, gapAbove = true)
            else Line("Pelts Gained", formatNumber(gained), COLOR_PELTS, gapAbove = true),
        )
        val average = if (preview) 83_000L else TrevorTracker.averageHuntMs()
        rows.add(Line("Avg Time", average?.let { TrevorParse.formatDuration(it) } ?: "-", COLOR_VALUE))

        // Animals by kind; only the kinds hunted so far.
        var first = true
        for (kind in TrevorTracker.Animal.entries) {
            val count = if (preview) previewCount(kind) else TrevorTracker.kills(kind)
            if (count <= 0) continue
            rows.add(Line(kind.title, formatNumber(count.toLong()), COLOR_VALUE, gapAbove = first))
            first = false
        }
        val total = if (preview) TrevorTracker.Animal.entries.sumOf { previewCount(it) } else TrevorTracker.totalKills()
        rows.add(Line("Total", formatNumber(total.toLong()), COLOR_GOOD, gapAbove = first))
        return rows
    }

    private fun previewCount(kind: TrevorTracker.Animal): Int = when (kind) {
        TrevorTracker.Animal.COW -> 14
        TrevorTracker.Animal.PIG -> 9
        TrevorTracker.Animal.SHEEP -> 11
        TrevorTracker.Animal.CHICKEN -> 6
        TrevorTracker.Animal.RABBIT -> 8
        TrevorTracker.Animal.HORSE -> 4
        TrevorTracker.Animal.UNKNOWN -> 0
    }

    /** Called every frame from the HUD hook. */
    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val cfg = AlpakaConfig.instance
        if (!cfg.trevorHelperEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.gui.hud.isHidden() || mc.level == null || mc.player == null) return
        if (mc.gui.screen() != null && mc.gui.screen() !is ChatScreen) return
        if (!TrevorTracker.huntActive && !TrevorTracker.onFarmingIslands()) return
        val placement = TrevorHudElement.visiblePlacement(mc.window.guiScaledWidth, mc.window.guiScaledHeight)
        renderHud(graphics, placement.anchorX, placement.anchorY, cfg.trevorHudScale, preview = false)
    }

    fun renderHud(graphics: GuiGraphicsExtractor, x: Int, y: Int, scale: Float, preview: Boolean) {
        val font = Minecraft.getInstance().font ?: return
        val laid = layout(preview)
        if (laid.rows.isEmpty()) return
        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), y.toFloat())
        graphics.pose().scale(scale, scale)
        if (AlpakaConfig.instance.blurOn(AlpakaConfig.instance.trevorHudBlur)) {
            HudPanel.aroundText(graphics, laid.width, laid.height, scale)
        }
        for (i in laid.rows.indices) {
            val row = laid.rows[i]
            graphics.text(font, laid.labels[i], 0, laid.tops[i], if (row.title) row.valueColor else COLOR_LABEL, HudPanel.TEXT_SHADOW)
            laid.values[i]?.let { graphics.text(font, it, laid.valueXs[i], laid.tops[i], row.valueColor, HudPanel.TEXT_SHADOW) }
        }
        graphics.pose().popMatrix()
    }

    fun width(preview: Boolean, scale: Float): Int = ceil(layout(preview).width * scale).toInt()

    fun height(preview: Boolean, scale: Float): Int = ceil(layout(preview).height * scale).toInt()

    private fun formatNumber(value: Long): String = String.format(Locale.US, "%,d", value)
}
