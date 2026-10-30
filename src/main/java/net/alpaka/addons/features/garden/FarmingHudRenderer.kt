package net.alpaka.addons.features.garden

import java.util.Locale
import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.HudPanel
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.utils.SkyblockUtils
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Draws the Farming HUD on the Garden: a label/value row per figure, laid out like the slayer
 * session HUD and, like it, rebuilt a few times a second rather than every frame.
 */
object FarmingHudRenderer {

    private const val LINE_HEIGHT = 10
    private const val LABEL_GAP = 5
    private const val GLYPH_HEIGHT = 9
    private const val REBUILD_INTERVAL_MS = 250L

    private const val COLOR_TITLE = 0xFF55FF55.toInt()
    private const val COLOR_LABEL = 0xFFAAAAAA.toInt()
    private const val COLOR_VALUE = 0xFFFFFFFF.toInt()
    private const val COLOR_GOOD = 0xFF55FF55.toInt()
    private const val COLOR_BAD = 0xFFFF5555.toInt()
    private const val COLOR_WARN = 0xFFFFFF55.toInt()

    /** One row; the title alone spans both columns. */
    private data class Line(val label: String, val value: String, val valueColor: Int, val spansColumns: Boolean = false)

    private class Layout(
        val rows: List<Line>,
        val unscaledWidth: Int,
        val unscaledHeight: Int,
        val valueXs: IntArray,
        val labelTexts: Array<Component>,
        val valueTexts: Array<Component?>,
    )

    private var cached: Layout? = null
    private var cachedAtMs = 0L
    private var cachedPreview = false
    private var cachedToggles = 0
    private var cachedFont: Style? = null

    private fun toggleMask(): Int {
        val cfg = AlpakaConfig.instance
        var mask = 0
        if (cfg.farmingHudShowTitle) mask = mask or (1 shl 0)
        if (cfg.farmingHudShowXpPerHour) mask = mask or (1 shl 1)
        if (cfg.farmingHudShowMilestone) mask = mask or (1 shl 2)
        if (cfg.farmingHudShowMilestoneEta) mask = mask or (1 shl 3)
        if (cfg.farmingHudShowPestCooldown) mask = mask or (1 shl 4)
        if (cfg.farmingHudShowRepellent) mask = mask or (1 shl 5)
        if (cfg.farmingHudShowContest) mask = mask or (1 shl 6)
        return mask
    }

    private fun layout(preview: Boolean): Layout {
        val now = System.currentTimeMillis()
        val toggles = toggleMask()
        val smoothFont = AlpakaConfig.instance.smoothOn(AlpakaConfig.instance.farmingHudSmoothFont)
        val fontStyle = if (smoothFont) GuiFont.style() else Style.EMPTY

        val existing = cached
        if (existing != null && cachedPreview == preview && cachedToggles == toggles && cachedFont == fontStyle &&
            now - cachedAtMs < REBUILD_INTERVAL_MS
        ) {
            return existing
        }

        val rows = buildRows(preview, now)
        val font = Minecraft.getInstance().font
        val built = if (rows.isEmpty() || font == null) {
            Layout(emptyList(), 0, 0, IntArray(0), emptyArray(), emptyArray())
        } else {
            val labelTexts = Array(rows.size) { HudPanel.text(Component.literal(rows[it].label), smoothFont) }
            var labelMax = 0
            for (index in rows.indices) {
                if (rows[index].spansColumns) continue
                labelMax = maxOf(labelMax, font.width(labelTexts[index]))
            }
            val labelWidth = if (labelMax > 0) labelMax + LABEL_GAP else 0
            val valueXs = IntArray(rows.size)
            val valueTexts = arrayOfNulls<Component>(rows.size)
            var widest = 0
            for (index in rows.indices) {
                val row = rows[index]
                val labelEnd = font.width(labelTexts[index])
                val valueX = if (row.spansColumns) labelEnd + LABEL_GAP else labelWidth
                valueXs[index] = valueX
                val valueText = if (row.value.isNotEmpty()) HudPanel.text(Component.literal(row.value), smoothFont) else null
                valueTexts[index] = valueText
                widest = maxOf(widest, if (valueText == null) labelEnd else valueX + font.width(valueText))
            }
            Layout(rows, widest, (rows.size - 1) * LINE_HEIGHT + GLYPH_HEIGHT, valueXs, labelTexts, valueTexts)
        }

        cached = built
        cachedAtMs = now
        cachedPreview = preview
        cachedToggles = toggles
        cachedFont = fontStyle
        return built
    }

    /** The rows, each behind its own toggle. [preview] fills them with sample figures for the editor. */
    private fun buildRows(preview: Boolean, now: Long): List<Line> {
        val cfg = AlpakaConfig.instance
        val rows = ArrayList<Line>(7)

        if (cfg.farmingHudShowTitle) rows.add(Line("§lFarming", "", COLOR_TITLE, spansColumns = true))

        if (cfg.farmingHudShowXpPerHour) {
            val perHour = if (preview) 412_800.0 else FarmingTracker.xpPerHour(now)
            rows.add(Line("XP/hr", perHour?.let { formatNumber(it.roundToLong()) } ?: "-", COLOR_VALUE))
        }

        val milestone = if (preview) FarmingParse.Milestone("Wheat", 32, 45.2) else FarmingTracker.milestone
        val noMilestoneWidget = !preview && !FarmingTracker.hasMilestoneWidget
        if (cfg.farmingHudShowMilestone) {
            rows.add(
                when {
                    noMilestoneWidget -> Line("Milestone", "Tab Widget Off", COLOR_WARN)
                    milestone == null -> Line("Milestone", "-", COLOR_VALUE)
                    milestone.maxed -> Line("Milestone", "${milestone.crop} ${milestone.tier} (Max)", COLOR_GOOD)
                    else -> Line(
                        "Milestone",
                        String.format(Locale.ROOT, "%s %d (%.1f%%)", milestone.crop, milestone.tier, milestone.percent),
                        COLOR_VALUE,
                    )
                }
            )
        }

        if (cfg.farmingHudShowMilestoneEta) {
            val eta = if (preview) 2_538_000L else FarmingTracker.milestoneEtaMs()
            rows.add(
                when {
                    noMilestoneWidget -> Line("Next Tier", "Tab Widget Off", COLOR_WARN)
                    milestone?.maxed == true -> Line("Next Tier", "Maxed", COLOR_GOOD)
                    else -> Line("Next Tier", eta?.let(::formatDuration) ?: "-", COLOR_VALUE)
                }
            )
        }

        if (cfg.farmingHudShowPestCooldown) {
            val cooldown = if (preview) "1m 58s" else FarmingTracker.pestCooldown
            rows.add(
                when {
                    !preview && !FarmingTracker.hasPestWidget -> Line("Pest CD", "Tab Widget Off", COLOR_WARN)
                    cooldown == null -> Line("Pest CD", "?", COLOR_VALUE)
                    cooldown.startsWith("READY") -> Line("Pest CD", "Ready", COLOR_GOOD)
                    cooldown.startsWith("MAX") -> Line("Pest CD", "Max Pests", COLOR_BAD)
                    else -> Line("Pest CD", cooldown, COLOR_VALUE)
                }
            )
        }

        if (cfg.farmingHudShowRepellent) {
            val left = if (preview) 2_712_000L else FarmingTracker.repellentLeftMs(now)
            rows.add(
                if (left == null) {
                    Line("Repellent", "None", COLOR_BAD)
                } else {
                    val max = if (preview || cfg.farmingRepellentMax) " (Max)" else ""
                    Line("Repellent", formatDuration(left) + max, COLOR_GOOD)
                }
            )
        }

        if (cfg.farmingHudShowContest) rows.add(contestLine(preview, now))

        return rows
    }

    /** The running or next Jacob's Contest: when, and its crops. */
    private fun contestLine(preview: Boolean, now: Long): Line {
        if (preview) return Line("Contest", "12m 30s: Wheat, Carrot, Potato", COLOR_VALUE)

        val contest = JacobContests.current(now)
        if (contest != null) {
            val crops = contest.crops.joinToString(", ")
            return if (contest.startMs <= now) {
                val left = contest.startMs + JacobContests.LENGTH_MS - now
                Line("Contest", "Now (${formatDuration(left)}): $crops", COLOR_GOOD)
            } else {
                Line("Contest", "${formatDuration(contest.startMs - now)}: $crops", COLOR_VALUE)
            }
        }

        // No calendar (API calls off, or not fetched yet): what the tab list's widget says.
        val time = FarmingTracker.tabContestTime
        val crops = FarmingTracker.tabContestCrops.joinToString(", ")
        return when {
            time == null && crops.isEmpty() -> Line("Contest", "-", COLOR_VALUE)
            crops.isEmpty() -> Line("Contest", time!!, COLOR_VALUE)
            time.isNullOrEmpty() -> Line("Contest", crops, COLOR_VALUE)
            else -> Line("Contest", "$time: $crops", COLOR_VALUE)
        }
    }

    /** Called every frame from the HUD hook. */
    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val cfg = AlpakaConfig.instance
        if (!cfg.farmingHudEnabled) return
        val mc = Minecraft.getInstance()
        if (mc.gui.hud.isHidden() || mc.level == null || mc.player == null) return
        if (mc.gui.screen() != null && mc.gui.screen() !is ChatScreen) return
        if (!SkyblockUtils.isOnGarden()) return

        val placement = FarmingHudElement.visiblePlacement(mc.window.guiScaledWidth, mc.window.guiScaledHeight)
        renderHud(graphics, placement.anchorX, placement.anchorY, cfg.farmingHudScale, preview = false)
    }

    /** Draws the HUD at an explicit position and size. Shared with the editor. */
    fun renderHud(graphics: GuiGraphicsExtractor, x: Int, y: Int, scale: Float, preview: Boolean) {
        val font = Minecraft.getInstance().font ?: return
        val laid = layout(preview)
        if (laid.rows.isEmpty()) return

        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), y.toFloat())
        graphics.pose().scale(scale, scale)
        if (AlpakaConfig.instance.blurOn(AlpakaConfig.instance.farmingHudBlur)) {
            HudPanel.aroundText(graphics, laid.unscaledWidth, laid.unscaledHeight, scale)
        }
        val shadow = HudPanel.TEXT_SHADOW
        var top = 0
        for (index in laid.rows.indices) {
            val row = laid.rows[index]
            graphics.text(font, laid.labelTexts[index], 0, top, if (row.spansColumns) row.valueColor else COLOR_LABEL, shadow)
            laid.valueTexts[index]?.let { graphics.text(font, it, laid.valueXs[index], top, row.valueColor, shadow) }
            top += LINE_HEIGHT
        }
        graphics.pose().popMatrix()
    }

    /** Width in scaled pixels, for the editor's bounding box. */
    fun width(preview: Boolean, scale: Float): Int = ceil(layout(preview).unscaledWidth * scale).toInt()

    /** Height in scaled pixels, for the editor's bounding box. */
    fun height(preview: Boolean, scale: Float): Int = ceil(layout(preview).unscaledHeight * scale).toInt()

    private fun formatNumber(value: Long): String = String.format(Locale.US, "%,d", value)

    /** "1h 12m" above an hour, "4m 05s" below. */
    private fun formatDuration(ms: Long): String {
        val totalSeconds = (ms + 999) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) String.format(Locale.ROOT, "%dh %02dm", hours, minutes)
        else String.format(Locale.ROOT, "%dm %02ds", minutes, seconds)
    }
}
