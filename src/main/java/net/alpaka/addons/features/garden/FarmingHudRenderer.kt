package net.alpaka.addons.features.garden

import java.util.Locale
import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.HudPanel
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.market.RancherSpeeds
import net.alpaka.addons.utils.SkyblockUtils
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
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

    /**
     * The smallest a crop icon is drawn, in GUI pixels. The real size is the next one at which each
     * of the item's 16 texture pixels covers a whole number of screen pixels; any size in between
     * gives texture pixels of uneven widths, which is what made the icons look blurry. At GUI
     * scale 3 that is 16, at 4 it is 12.
     */
    private const val ICON_MIN_SIZE = 12f
    private const val ICON_GAP = 1

    private const val COLOR_TITLE = 0xFF55FF55.toInt()
    private const val COLOR_LABEL = 0xFFAAAAAA.toInt()
    private const val COLOR_VALUE = 0xFFFFFFFF.toInt()
    private const val COLOR_GOOD = 0xFF55FF55.toInt()
    private const val COLOR_BAD = 0xFFFF5555.toInt()
    private const val COLOR_WARN = 0xFFFFFF55.toInt()

    /** The gap between a crop icon in front of a value and the value. */
    private const val LEAD_ICON_GAP = 2

    /**
     * One row; the title alone spans both columns. A row with [icons] draws them after its value,
     * in brackets: the Contest line's crops. [leadIcon] is a crop drawn in front of the value, in
     * place of its name: the Milestone line's.
     */
    private data class Line(
        val label: String,
        val value: String,
        val valueColor: Int,
        val spansColumns: Boolean = false,
        val icons: List<String> = emptyList(),
        val leadIcon: String? = null,
    )

    private class Layout(
        val rows: List<Line>,
        val unscaledWidth: Int,
        val unscaledHeight: Int,
        val valueXs: IntArray,
        val labelTexts: Array<Component>,
        val valueTexts: Array<Component?>,
        /** Where each row's icons start, after its value and the opening bracket. */
        val iconXs: IntArray,
        /** Where each row's value text starts: after the lead icon, if it has one. */
        val textXs: IntArray,
        /** Each row's top; a row with icons is taller than a line of text. */
        val tops: IntArray,
        val heights: IntArray,
        /** The icons' size in unscaled pixels. */
        val iconSize: Float,
        val closeText: Component,
    )

    private var cached: Layout? = null
    private var cachedAtMs = 0L
    private var cachedPreview = false
    private var cachedToggles = 0
    private var cachedFont: Style? = null
    private var cachedIconSize = 0f

    /** Each crop's item, built once. */
    private val cropStacks = HashMap<String, ItemStack>()

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

    /** The icon size for a HUD drawn at [scale]: see [ICON_MIN_SIZE]. */
    private fun iconSize(scale: Float): Float {
        val screenPerUnit = Minecraft.getInstance().window.guiScale * scale
        if (screenPerUnit <= 0f) return 16f
        val texel = ceil(ICON_MIN_SIZE * screenPerUnit / 16f).coerceAtLeast(1f)
        return texel * 16f / screenPerUnit
    }

    private fun layout(preview: Boolean, scale: Float): Layout {
        val now = System.currentTimeMillis()
        val iconSize = iconSize(scale)
        val toggles = toggleMask()
        val smoothFont = AlpakaConfig.instance.smoothOn(AlpakaConfig.instance.farmingHudSmoothFont)
        val fontStyle = if (smoothFont) GuiFont.style() else Style.EMPTY

        val existing = cached
        if (existing != null && cachedPreview == preview && cachedToggles == toggles && cachedFont == fontStyle &&
            cachedIconSize == iconSize && now - cachedAtMs < REBUILD_INTERVAL_MS
        ) {
            return existing
        }

        val rows = buildRows(preview, now)
        val font = Minecraft.getInstance().font
        val closeText = HudPanel.text(Component.literal(")"), smoothFont)
        val built = if (rows.isEmpty() || font == null) {
            Layout(emptyList(), 0, 0, IntArray(0), emptyArray(), emptyArray(), IntArray(0), IntArray(0), IntArray(0), IntArray(0), iconSize, closeText)
        } else {
            val labelTexts = Array(rows.size) { HudPanel.text(Component.literal(rows[it].label), smoothFont) }
            var labelMax = 0
            for (index in rows.indices) {
                if (rows[index].spansColumns) continue
                labelMax = maxOf(labelMax, font.width(labelTexts[index]))
            }
            val labelWidth = if (labelMax > 0) labelMax + LABEL_GAP else 0
            val valueXs = IntArray(rows.size)
            val iconXs = IntArray(rows.size)
            val textXs = IntArray(rows.size)
            val tops = IntArray(rows.size)
            val heights = IntArray(rows.size)
            val iconWidth = ceil(iconSize).toInt()
            var top = 0
            val valueTexts = arrayOfNulls<Component>(rows.size)
            var widest = 0
            for (index in rows.indices) {
                val row = rows[index]
                val labelEnd = font.width(labelTexts[index])
                val valueX = if (row.spansColumns) labelEnd + LABEL_GAP else labelWidth
                valueXs[index] = valueX
                val hasIcons = row.leadIcon != null || row.icons.isNotEmpty()
                heights[index] = if (hasIcons) maxOf(LINE_HEIGHT, iconWidth) else LINE_HEIGHT
                tops[index] = top
                top += heights[index]
                val textX = if (row.leadIcon != null) valueX + iconWidth + LEAD_ICON_GAP else valueX
                textXs[index] = textX
                val value = if (row.icons.isEmpty()) row.value else "${row.value} ("
                val valueText = if (value.isNotEmpty()) HudPanel.text(Component.literal(value), smoothFont) else null
                valueTexts[index] = valueText
                var end = if (valueText == null) labelEnd else textX + font.width(valueText)
                if (row.icons.isNotEmpty()) {
                    iconXs[index] = end
                    end += row.icons.size * (iconWidth + ICON_GAP) - ICON_GAP + font.width(closeText)
                }
                widest = maxOf(widest, end)
            }
            // The last row ends with its glyphs (or icons), not with the gap to a next line.
            val height = top - (if (heights.last() == LINE_HEIGHT) LINE_HEIGHT - GLYPH_HEIGHT else 0)
            Layout(rows, widest, height, valueXs, labelTexts, valueTexts, iconXs, textXs, tops, heights, iconSize, closeText)
        }

        cached = built
        cachedAtMs = now
        cachedPreview = preview
        cachedToggles = toggles
        cachedFont = fontStyle
        cachedIconSize = iconSize
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

        val milestone = if (preview) FarmingParse.Milestone("Mushroom", 25, 48.7) else FarmingTracker.milestone
        val noMilestoneWidget = !preview && !FarmingTracker.hasMilestoneWidget
        if (cfg.farmingHudShowMilestone) {
            val crops = if (preview) 389_285L to 800_000L else FarmingTracker.milestoneCrops()
            rows.add(
                when {
                    noMilestoneWidget -> Line("Milestone", "Tab Widget Off", COLOR_WARN)
                    milestone == null -> Line("Milestone", "-", COLOR_VALUE)
                    milestone.maxed -> Line("Milestone", "${milestone.tier} (Max)", COLOR_GOOD, leadIcon = milestone.crop)
                    // In crops once the Crop Milestones menu has been opened, as a percentage before.
                    crops != null -> Line(
                        "Milestone",
                        "${milestone.tier}: ${formatNumber(crops.first)}/${formatShort(crops.second)}",
                        COLOR_VALUE,
                        leadIcon = milestone.crop,
                    )
                    else -> Line(
                        "Milestone",
                        String.format(Locale.ROOT, "%d: %.1f%%", milestone.tier, milestone.percent),
                        COLOR_VALUE,
                        leadIcon = milestone.crop,
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
            val left = if (preview) 118_000L else FarmingTracker.pestCooldownLeftMs(now)
            rows.add(
                when {
                    !preview && FarmingTracker.maxPests -> Line("Pest CD", "Max Pests", COLOR_BAD)
                    left == null && !preview && !FarmingTracker.hasPestWidget -> Line("Pest CD", "Tab Widget Off", COLOR_WARN)
                    // Not known until the widget shows a cooldown or pests have spawned once.
                    left == null -> Line("Pest CD", "-", COLOR_VALUE)
                    left <= 0L -> Line("Pest CD", "Ready", COLOR_GOOD)
                    else -> Line("Pest CD", formatDuration(left), COLOR_VALUE)
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

    /** The running or next Jacob's Contest: when, and its crops as their items. */
    private fun contestLine(preview: Boolean, now: Long): Line {
        if (preview) return Line("Contest", "12m 30s", COLOR_VALUE, icons = listOf("Wheat", "Carrot", "Potato"))

        val contest = JacobContests.current(now)
        if (contest != null) {
            return if (contest.startMs <= now) {
                val left = contest.startMs + JacobContests.LENGTH_MS - now
                Line("Contest", "Now ${formatDuration(left)}", COLOR_GOOD, icons = contest.crops)
            } else {
                Line("Contest", formatDuration(contest.startMs - now), COLOR_VALUE, icons = contest.crops)
            }
        }

        // No calendar (API calls off, or not fetched yet): what the tab list's widget says.
        val time = FarmingTracker.tabContestTime?.takeIf { it.isNotEmpty() }
        return Line("Contest", time ?: "-", COLOR_VALUE, icons = FarmingTracker.tabContestCrops)
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
        val laid = layout(preview, scale)
        if (laid.rows.isEmpty()) return

        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), y.toFloat())
        graphics.pose().scale(scale, scale)
        if (AlpakaConfig.instance.blurOn(AlpakaConfig.instance.farmingHudBlur)) {
            HudPanel.aroundText(graphics, laid.unscaledWidth, laid.unscaledHeight, scale)
        }
        val shadow = HudPanel.TEXT_SHADOW
        val iconStep = ceil(laid.iconSize).toInt() + ICON_GAP
        for (index in laid.rows.indices) {
            val row = laid.rows[index]
            val top = laid.tops[index]
            // Text sits in the middle of a row made taller by its icons.
            val textTop = top + (laid.heights[index] - LINE_HEIGHT + 1) / 2
            val iconTop = top + (laid.heights[index] - laid.iconSize) / 2f
            graphics.text(font, laid.labelTexts[index], 0, textTop, if (row.spansColumns) row.valueColor else COLOR_LABEL, shadow)
            row.leadIcon?.let { drawCrop(graphics, it, laid.valueXs[index].toFloat(), iconTop, laid.iconSize, x, y, scale) }
            laid.valueTexts[index]?.let { graphics.text(font, it, laid.textXs[index], textTop, row.valueColor, shadow) }
            if (row.icons.isNotEmpty()) {
                var iconX = laid.iconXs[index]
                for (crop in row.icons) {
                    drawCrop(graphics, crop, iconX.toFloat(), iconTop, laid.iconSize, x, y, scale)
                    iconX += iconStep
                }
                graphics.text(font, laid.closeText, iconX - ICON_GAP, textTop, row.valueColor, shadow)
            }
        }
        graphics.pose().popMatrix()
    }

    /**
     * A crop's item, [size] pixels square at ([left], [top]) inside the HUD drawn at [hudX], [hudY]
     * and [scale]. The corner is moved onto the nearest screen pixel, so the texture pixels line up
     * with the screen's instead of each being smeared over two.
     */
    private fun drawCrop(
        graphics: GuiGraphicsExtractor, crop: String, left: Float, top: Float, size: Float,
        hudX: Int, hudY: Int, scale: Float,
    ) {
        val stack = cropStack(crop) ?: return
        val guiScale = Minecraft.getInstance().window.guiScale.toFloat()
        fun snap(origin: Int, local: Float): Float =
            (Math.round((origin + local * scale) * guiScale) / guiScale - origin) / scale
        graphics.pose().pushMatrix()
        graphics.pose().translate(snap(hudX, left), snap(hudY, top))
        graphics.pose().scale(size / 16f, size / 16f)
        graphics.item(stack, 0, 0)
        graphics.pose().popMatrix()
    }

    /**
     * Null until a world has been joined: items get their components only then, and building a
     * stack before that throws. The HUD editor can be opened from the title screen.
     */
    private fun cropStack(crop: String): ItemStack? {
        cropStacks[crop]?.let { return it }
        if (!Items.STONE.builtInRegistryHolder().areComponentsBound()) return null
        return ItemStack(RancherSpeeds.icon(crop)).also { cropStacks[crop] = it }
    }

    /** Width in scaled pixels, for the editor's bounding box. */
    fun width(preview: Boolean, scale: Float): Int = ceil(layout(preview, scale).unscaledWidth * scale).toInt()

    /** Height in scaled pixels, for the editor's bounding box. */
    fun height(preview: Boolean, scale: Float): Int = ceil(layout(preview, scale).unscaledHeight * scale).toInt()

    private fun formatNumber(value: Long): String = String.format(Locale.US, "%,d", value)

    /** "800k", "1.6M": what a tier needs, as short as the menu writes it. */
    private fun formatShort(value: Long): String = when {
        value >= 1_000_000L -> String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0).replace(".0M", "M")
        value >= 1_000L -> String.format(Locale.ROOT, "%.1fk", value / 1_000.0).replace(".0k", "k")
        else -> value.toString()
    }

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
