package net.alpaka.addons.features.slayer

import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Draws the slayer session HUD: a stack of one-line statistics for the slayer currently being run.
 *
 * The HUD is deliberately absent unless a slayer quest is on the sidebar, so it never sits on screen
 * during unrelated play, and it only ever describes the one slayer in progress.
 *
 * Every figure shown is rebuilt on a timer rather than per frame - see [REBUILD_INTERVAL_MS]. Laying
 * the rows out means formatting numbers and measuring text, which at a few hundred frames a second is
 * a lot of work to redo for text that changes at most once a second.
 */
object SlayerHudRenderer {

    private const val LINE_HEIGHT = 10

    /** Gap between the label column and the value column, in unscaled pixels. */
    private const val LABEL_GAP = 5

    /** Height of one glyph row, used for the last line's contribution to the total height. */
    private const val GLYPH_HEIGHT = 9

    /**
     * How long a laid-out HUD is reused before being rebuilt.
     *
     * The fastest-moving figure is the session clock, which changes once a second; everything else
     * moves per boss. A quarter second is well inside "instant" for a reader while cutting the work
     * to a handful of rebuilds a second regardless of frame rate. A change to which lines are shown
     * bypasses this entirely and rebuilds at once, so ticking a box in the config is never laggy.
     */
    private const val REBUILD_INTERVAL_MS = 250L

    private const val COLOR_TITLE = 0xFFFFAA00.toInt()
    private const val COLOR_LABEL = 0xFFAAAAAA.toInt()
    private const val COLOR_VALUE = 0xFFFFFFFF.toInt()
    private const val COLOR_GOOD = 0xFF55FF55.toInt()
    private const val COLOR_PAUSED = 0xFFFF5555.toInt()

    /** One rendered row: a label, a value, and the colour the value is drawn in. */
    data class Line(val label: String, val value: String, val valueColor: Int)

    /**
     * A laid-out HUD, cached between rebuilds.
     *
     * Sizes are stored unscaled so a change to the HUD's scale - which the editor does on every
     * scroll notch - needs no rebuild, only a multiply.
     */
    private class Layout(
        val rows: List<Line>,
        val labelWidth: Int,
        val unscaledWidth: Int,
        val unscaledHeight: Int
    )

    private var cached: Layout? = null
    private var cachedAtMs = 0L
    private var cachedType: SlayerType? = null
    private var cachedPreview = false
    private var cachedToggles = 0
    private var cachedPauseReason: SlayerSessionTracker.PauseReason? = null

    /**
     * Which lines are switched on, as a bitmask.
     *
     * Compared against the cache so that toggling a line in the config takes effect on the very next
     * frame instead of up to [REBUILD_INTERVAL_MS] later.
     */
    private fun toggleMask(): Int {
        val cfg = AlpakaConfig.instance
        var mask = 0
        if (cfg.slayerHudShowTitle) mask = mask or (1 shl 0)
        if (cfg.slayerHudShowTotalXp) mask = mask or (1 shl 1)
        if (cfg.slayerHudShowSessionXp) mask = mask or (1 shl 2)
        if (cfg.slayerHudShowXpPerHour) mask = mask or (1 shl 3)
        if (cfg.slayerHudShowBossCount) mask = mask or (1 shl 4)
        if (cfg.slayerHudShowAvgBossTime) mask = mask or (1 shl 5)
        if (cfg.slayerHudShowBossesPerHour) mask = mask or (1 shl 6)
        if (cfg.slayerHudShowSessionTime) mask = mask or (1 shl 7)
        if (cfg.slayerHudShowSinceRngDrop) mask = mask or (1 shl 8)
        return mask
    }

    /** The current layout, rebuilding only when it has gone stale or its inputs changed. */
    private fun layout(type: SlayerType?, preview: Boolean): Layout {
        val now = System.currentTimeMillis()
        val toggles = toggleMask()

        // Pause state is part of the key rather than left to the timer, so clicking the HUD to hold
        // the clock reads back immediately instead of up to a quarter second later.
        val pauseReason = if (preview) null else SlayerSessionTracker.pauseReason()

        val existing = cached
        if (existing != null &&
            cachedType == type &&
            cachedPreview == preview &&
            cachedToggles == toggles &&
            cachedPauseReason == pauseReason &&
            now - cachedAtMs < REBUILD_INTERVAL_MS
        ) {
            return existing
        }

        val rows = buildRows(type, preview)
        val font = Minecraft.getInstance().font

        val labelWidth: Int
        val width: Int
        val height: Int
        if (rows.isEmpty() || font == null) {
            labelWidth = 0
            width = 0
            height = 0
        } else {
            // Measured without concatenating a spacer onto every label, which would allocate a
            // throwaway string per row per rebuild.
            labelWidth = rows.maxOf { font.width(it.label) } + LABEL_GAP
            width = rows.maxOf { labelWidth + font.width(it.value) }
            height = (rows.size - 1) * LINE_HEIGHT + GLYPH_HEIGHT
        }

        val built = Layout(rows, labelWidth, width, height)
        cached = built
        cachedAtMs = now
        cachedType = type
        cachedPreview = preview
        cachedToggles = toggles
        cachedPauseReason = pauseReason
        return built
    }

    /**
     * Builds the rows for a slayer, honouring each line's own toggle.
     *
     * [preview] fills the rows with representative figures instead of live ones, so the HUD editor
     * has something to size and position against before a single boss has been killed.
     */
    private fun buildRows(type: SlayerType?, preview: Boolean): List<Line> {
        val cfg = AlpakaConfig.instance
        val rows = ArrayList<Line>(9)

        val shown = type ?: SlayerType.BLAZE
        val session = if (preview) null else SlayerSessionTracker.session(shown)

        if (cfg.slayerHudShowTitle) {
            // Naming the reason matters: "away" and "left area" are states the player can fix, and a
            // bare "(paused)" would leave them guessing which one stopped the clock.
            val suffix = if (preview) "" else when (SlayerSessionTracker.pauseReason()) {
                SlayerSessionTracker.PauseReason.MANUAL -> " (held)"
                SlayerSessionTracker.PauseReason.IDLE -> " (away)"
                SlayerSessionTracker.PauseReason.OUTSIDE_AREA -> " (left area)"
                null -> ""
            }
            rows.add(Line("${shown.colorCode}§l${shown.display} Slayer", suffix, if (suffix.isEmpty()) COLOR_TITLE else COLOR_PAUSED))
        }

        if (cfg.slayerHudShowTotalXp) {
            val total = if (preview) 1_284_500L else SlayerXpTracker.totalXp(shown)
            rows.add(Line("Total XP", total?.let(::formatNumber) ?: "?", COLOR_VALUE))
        }

        if (cfg.slayerHudShowSessionXp) {
            val gained = if (preview) 12_650L else session?.xpGained ?: 0L
            rows.add(Line("Session XP", "+" + formatNumber(gained), COLOR_GOOD))
        }

        if (cfg.slayerHudShowXpPerHour) {
            val xpPerHour = if (preview) 41_250.0 else session?.xpPerHour()
            rows.add(Line("XP/hr", xpPerHour?.let { formatNumber(it.roundToLong()) } ?: "-", COLOR_VALUE))
        }

        if (cfg.slayerHudShowBossCount) {
            val count = if (preview) 24 else session?.bossCount ?: 0
            rows.add(Line("Bosses", count.toString(), COLOR_VALUE))
        }

        if (cfg.slayerHudShowAvgBossTime) {
            val avg = if (preview) 31_200L else session?.averageBossMs()
            rows.add(Line("Avg Boss", avg?.let(::formatSeconds) ?: "-", COLOR_VALUE))
        }

        if (cfg.slayerHudShowBossesPerHour) {
            val perHour = if (preview) 78.4 else session?.bossesPerHour()
            rows.add(Line("Bosses/hr", perHour?.let { String.format("%.1f", it) } ?: "-", COLOR_VALUE))
        }

        if (cfg.slayerHudShowSessionTime) {
            val active = if (preview) 1_102_000L else session?.activeMs ?: 0L
            rows.add(Line("Session", formatDuration(active), COLOR_VALUE))
        }

        if (cfg.slayerHudShowSinceRngDrop) {
            val item = shown.rngDropItem
            if (item != null) {
                val since = if (preview) 28 else SlayerRngDropTracker.bossesSince(shown)
                rows.add(Line("Since ${shortItemName(item)}", since?.toString() ?: "-", COLOR_VALUE))
            }
        }

        return rows
    }

    /** Called every frame from the HUD hook. */
    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val cfg = AlpakaConfig.instance
        if (!cfg.slayerHudEnabled) return

        val mc = Minecraft.getInstance()
        if (mc.options.hideGui || mc.level == null || mc.player == null) return
        if (mc.screen != null && mc.screen !is ChatScreen) return

        // No quest on the sidebar means there is no slayer being run, and so nothing to report.
        val type = SlayerQuestDetector.currentOrRecent() ?: return

        renderHud(graphics, cfg.slayerHudX, cfg.slayerHudY, cfg.slayerHudScale, type, preview = false)
    }

    /** Draws the HUD at an explicit position and size. Shared with the editor. */
    fun renderHud(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        scale: Float,
        type: SlayerType?,
        preview: Boolean
    ) {
        val font = Minecraft.getInstance().font ?: return
        val laid = layout(type, preview)
        if (laid.rows.isEmpty()) return

        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), y.toFloat())
        graphics.pose().scale(scale, scale)

        var top = 0
        for (line in laid.rows) {
            graphics.text(font, Component.literal(line.label), 0, top, COLOR_LABEL)
            if (line.value.isNotEmpty()) {
                graphics.text(font, Component.literal(line.value), laid.labelWidth, top, line.valueColor)
            }
            top += LINE_HEIGHT
        }

        graphics.pose().popMatrix()
    }

    /** Width in scaled pixels, for the editor's bounding box. */
    fun width(@Suppress("UNUSED_PARAMETER") font: Font, type: SlayerType?, preview: Boolean, scale: Float): Int =
        ceil(layout(type, preview).unscaledWidth * scale).toInt()

    /** Height in scaled pixels, for the editor's bounding box. */
    fun height(type: SlayerType?, preview: Boolean, scale: Float): Int =
        ceil(layout(type, preview).unscaledHeight * scale).toInt()

    /**
     * Trims an item name down to something that fits a HUD label, e.g. "High Class Archfiend Dice"
     * becomes "Dice". Long RNG drop names would otherwise make the HUD wider than everything else
     * on it put together.
     */
    private fun shortItemName(item: String): String = item.substringAfterLast(' ')

    private fun formatNumber(value: Long): String {
        val digits = value.toString()
        val out = StringBuilder(digits.length + digits.length / 3)
        for ((index, ch) in digits.withIndex()) {
            if (index > 0 && (digits.length - index) % 3 == 0) out.append(',')
            out.append(ch)
        }
        return out.toString()
    }

    /** Boss times read best in seconds with one decimal, matching how the community quotes them. */
    private fun formatSeconds(ms: Long): String = String.format("%.1fs", ms / 1000.0)

    private fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000.0).roundToInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) String.format("%dh %02dm", hours, minutes)
        else String.format("%dm %02ds", minutes, seconds)
    }
}
