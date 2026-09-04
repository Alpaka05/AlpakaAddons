package net.alpaka.addons.features.wheel

import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.wheel.WheelMesh.Companion.TAU
import net.alpaka.addons.features.wheel.WheelMesh.Companion.lerpColor
import net.alpaka.addons.features.wheel.WheelMesh.Companion.scaleAlpha
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The quick command wheel: a segmented dial around a small hub, one segment per configured command,
 * with the command names sitting just outside their segment.
 *
 * Selection is by direction, not by hitting a box. Anywhere outside the hub the mouse's bearing
 * from the centre picks the segment, so a flick of a few pixels in roughly the right direction is
 * enough - the point of the wheel is that it is faster than typing. A short, wide wedge on the rim
 * of the hub turns smoothly towards the mouse and points at the segment being aimed at; that
 * segment fills with the accent colour and swells outwards, and its label grows with it. Releasing
 * the wheel key or clicking runs the command; right-click or Escape closes without running anything.
 *
 * Everything moves on eased curves driven by real frame time, so it looks the same at 60 and 240
 * frames per second, and everything rounded is drawn by [WheelMesh] rather than stacked rectangles.
 */
class CommandWheelScreen : Screen(Component.literal("Quick Command Menu")) {

    private var commands: List<String> = emptyList()

    /** Per segment, 0 = idle and 1 = fully highlighted; eased towards its target every frame. */
    private var highlight = FloatArray(0)
    private var selectedIndex = -1

    /** 0 while nothing is aimed at, 1 while a segment is selected; fades the wedge and hub rim. */
    private var aim = 0f

    /** Where the wedge points, radians in screen space. Starts pointing up. */
    private var wedgeAngle = -TAU / 4f

    private val openedAtNanos = System.nanoTime()
    private var lastFrameNanos = openedAtNanos

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        val configured = AlpakaConfig.instance.commandWheelCommands
        commands = if (configured == null) emptyList() else ArrayList(configured)
        if (highlight.size != commands.size) highlight = FloatArray(commands.size)
    }

    // ------------------------------------------------------------------ input

    override fun keyReleased(event: KeyEvent): Boolean {
        val key = CommandWheelFeature.COMMAND_WHEEL_KEY
        if (key != null && key.matches(event)) {
            runSelectedAndClose()
            return true
        }
        return super.keyReleased(event)
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        when (event.button()) {
            0 -> if (selectedIndex >= 0) {
                runSelectedAndClose()
                return true
            }
            1 -> {
                close()
                return true
            }
        }
        return super.mouseClicked(event, isDoubleClick)
    }

    private fun runSelectedAndClose() {
        val player = (this.minecraft ?: Minecraft.getInstance()).player
        if (player != null && selectedIndex in commands.indices) {
            var command = commands[selectedIndex]
            if (command.startsWith("/")) command = command.substring(1)
            if (command.isNotBlank()) player.connection.sendCommand(command)
        }
        close()
    }

    private fun close() {
        (this.minecraft ?: Minecraft.getInstance()).gui.setScreen(null)
    }

    // ----------------------------------------------------------------- layout

    private class Layout(
        val cx: Float, val cy: Float,
        val hubRadius: Float,
        val ringInner: Float, val ringOuter: Float,
        val labelRadius: Float,
        val sweep: Float
    )

    private fun layout(count: Int): Layout {
        val cx = this.width / 2f
        val cy = this.height / 2f
        val sweep = TAU / count

        var maxLabelWidth = 0
        for (command in commands) maxLabelWidth = max(maxLabelWidth, this.font.width(command))

        // Neighbouring labels near the top and bottom of the wheel sit almost side by side, so the
        // label circle has to be wide enough for the longest name to clear its neighbour there.
        var ringInner = 30f + max(0, count - 6) * 1.5f
        if (count >= 3) {
            val neededLabelRadius = (maxLabelWidth + 10f) / (2f * sin(sweep))
            ringInner = max(ringInner, neededLabelRadius - RING_WIDTH - LABEL_GAP)
        }
        // And it all has to fit on screen, label text included.
        val roomInner = this.height / 2f - RING_WIDTH - LABEL_GAP - this.font.lineHeight * 1.3f - 10f
        ringInner = min(ringInner, roomInner).coerceAtLeast(22f)

        val ringOuter = ringInner + RING_WIDTH
        return Layout(cx, cy, HUB_RADIUS, ringInner, ringOuter, ringOuter + LABEL_GAP, sweep)
    }

    private fun segmentAngle(layout: Layout, index: Int): Float = -TAU / 4f + index * layout.sweep

    /** The segment the mouse is pointing at, or -1 while it rests inside the hub. */
    private fun pick(layout: Layout, mouseX: Int, mouseY: Int, count: Int): Int {
        val dx = mouseX - layout.cx
        val dy = mouseY - layout.cy
        if (sqrt(dx * dx + dy * dy) < DEAD_ZONE) return -1
        var bearing = atan2(dy, dx) + TAU / 4f + layout.sweep / 2f
        bearing = ((bearing % TAU) + TAU) % TAU
        return (bearing / layout.sweep).toInt().coerceIn(0, count - 1)
    }

    // -------------------------------------------------------------- animation

    /** Fraction of the remaining distance to cover this frame, for a rate in 1/s. */
    private fun ease(dt: Float, rate: Float): Float = 1f - exp(-dt * rate)

    private fun approach(current: Float, target: Float, k: Float): Float = current + (target - current) * k

    /** Like [approach], but takes the short way round the circle. */
    private fun approachAngle(current: Float, target: Float, k: Float): Float {
        var delta = (target - current) % TAU
        if (delta > TAU / 2f) delta -= TAU
        if (delta < -TAU / 2f) delta += TAU
        return current + delta * k
    }

    // -------------------------------------------------------------- rendering

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick)

        val now = System.nanoTime()
        val dt = ((now - lastFrameNanos) / 1_000_000_000.0).toFloat().coerceIn(0f, 0.1f)
        lastFrameNanos = now
        val openT = ((now - openedAtNanos) / 1_000_000_000.0 / OPEN_SECONDS).toFloat().coerceIn(0f, 1f)
        val open = 1f - (1f - openT) * (1f - openT) * (1f - openT)

        graphics.fill(0, 0, this.width, this.height, scaleAlpha(0x38000000, open))

        val count = commands.size
        if (count == 0) {
            selectedIndex = -1
            drawEmptyState(graphics, open)
            return
        }
        if (highlight.size != count) highlight = FloatArray(count)

        val layout = layout(count)
        selectedIndex = pick(layout, mouseX, mouseY, count)

        if (selectedIndex >= 0) {
            val target = atan2(mouseY - layout.cy, mouseX - layout.cx)
            wedgeAngle = approachAngle(wedgeAngle, target, ease(dt, 26f))
        }
        aim = approach(aim, if (selectedIndex >= 0) 1f else 0f, ease(dt, 16f))
        for (i in 0 until count) {
            highlight[i] = approach(highlight[i], if (i == selectedIndex) 1f else 0f, ease(dt, 15f))
        }

        val guiScale = (this.minecraft ?: Minecraft.getInstance()).window.guiScale.coerceAtLeast(1)
        val mesh = WheelMesh(1.15f / guiScale)
        val accent = ModernGuiUtils.getAccentColor()

        val pose = graphics.pose()
        pose.pushMatrix()
        val openScale = 0.86f + 0.14f * open
        pose.scaleAround(openScale, openScale, layout.cx, layout.cy)

        // A soft pool of shadow behind the whole wheel, so it reads as sitting above the world
        // rather than being stamped onto it.
        val shadeRadius = layout.labelRadius + 26f
        mesh.disc(layout.cx, layout.cy, shadeRadius, scaleAlpha(0x5A000000, open))
        mesh.ringSector(
            layout.cx, layout.cy, shadeRadius, shadeRadius + 64f, 0f, TAU, 0f, TAU,
            scaleAlpha(0x5A000000, open), 0x00000000, fullCircle = true
        )
        mesh.submit(graphics)

        // The dial: one segment per command, with a constant pixel gap between neighbours. The
        // selected one takes the accent colour and swells outwards.
        for (i in 0 until count) {
            val h = highlight[i]
            val center = segmentAngle(layout, i)
            val outer = layout.ringOuter + SELECT_GROWTH * h
            val inner = layout.ringInner
            val fill = lerpColor(COLOR_SEGMENT, accent, h)
            val fillOuter = lerpColor(COLOR_SEGMENT_OUTER, accent, h)
            if (count == 1) {
                mesh.ringSector(
                    layout.cx, layout.cy, inner, outer, 0f, TAU, 0f, TAU,
                    scaleAlpha(fill, open), scaleAlpha(fillOuter, open), fullCircle = true
                )
            } else {
                val halfGapInner = SEGMENT_GAP / 2f / inner
                val halfGapOuter = SEGMENT_GAP / 2f / outer
                mesh.ringSector(
                    layout.cx, layout.cy, inner, outer,
                    center - layout.sweep / 2f + halfGapInner, center + layout.sweep / 2f - halfGapInner,
                    center - layout.sweep / 2f + halfGapOuter, center + layout.sweep / 2f - halfGapOuter,
                    scaleAlpha(fill, open), scaleAlpha(fillOuter, open)
                )
            }
        }
        mesh.submit(graphics)

        // The hub, with a hairline rim that warms up towards the accent while something is aimed at.
        mesh.disc(layout.cx, layout.cy, layout.hubRadius, scaleAlpha(COLOR_HUB, open))
        mesh.ring(
            layout.cx, layout.cy, layout.hubRadius - 1f, layout.hubRadius,
            scaleAlpha(lerpColor(ModernGuiUtils.COLOR_CARD_BORDER, accent, aim * 0.7f), open)
        )
        mesh.submit(graphics)

        // The wedge: a short, wide triangle sitting on the hub rim, pointing outwards at the mouse.
        // It fades with the selection, so an idle hub is just the plain disc.
        if (aim > 0.02f) {
            val dirX = cos(wedgeAngle)
            val dirY = sin(wedgeAngle)
            val sideX = -dirY
            val sideY = dirX
            val tip = layout.hubRadius + WEDGE_LENGTH * aim
            val base = layout.hubRadius - WEDGE_INSET
            val halfWidth = WEDGE_HALF_WIDTH * (0.6f + 0.4f * aim)
            mesh.convexPolygon(
                floatArrayOf(
                    layout.cx + dirX * tip, layout.cy + dirY * tip,
                    layout.cx + dirX * base + sideX * halfWidth, layout.cy + dirY * base + sideY * halfWidth,
                    layout.cx + dirX * base - sideX * halfWidth, layout.cy + dirY * base - sideY * halfWidth
                ),
                scaleAlpha(accent, open * aim)
            )
            mesh.submit(graphics)
        }

        // Labels sit just outside their segment. Each is anchored by the edge nearest the ring, so a
        // long name on the left or right grows away from the wheel instead of into it, and the
        // selected one scales up around that same anchor.
        for (i in 0 until count) {
            val command = commands[i]
            val h = highlight[i]
            val angle = segmentAngle(layout, i)
            val cosA = cos(angle)
            val sinA = sin(angle)
            val radius = layout.labelRadius + SELECT_GROWTH * h
            val anchorX = layout.cx + cosA * radius
            val anchorY = layout.cy + sinA * radius
            val textWidth = this.font.width(command)
            val textHeight = this.font.lineHeight - 1
            val x = (anchorX + (cosA - 1f) * textWidth / 2f).roundToInt()
            val y = (anchorY + (sinA - 1f) * textHeight / 2f).roundToInt()

            val color = scaleAlpha(lerpColor(COLOR_LABEL, COLOR_LABEL_SELECTED, h), open)
            if ((color ushr 24) < 8) continue

            val scale = 1f + LABEL_GROWTH * h
            pose.pushMatrix()
            pose.scaleAround(scale, scale, anchorX, anchorY)
            graphics.text(this.font, command, x, y, color, false)
            pose.popMatrix()
        }

        pose.popMatrix()
    }

    private fun drawEmptyState(graphics: GuiGraphicsExtractor, open: Float) {
        val cx = this.width / 2
        val cy = this.height / 2
        val primary = scaleAlpha(ModernGuiUtils.COLOR_TEXT_PRIMARY, open)
        val muted = scaleAlpha(ModernGuiUtils.COLOR_TEXT_MUTED, open)
        if ((primary ushr 24) < 8) return
        graphics.centeredText(this.font, "No quick commands yet", cx, cy - 10, primary)
        graphics.centeredText(this.font, "Add some under Alpaka Config → Quick Command Menu", cx, cy + 4, muted)
    }

    companion object {
        private const val OPEN_SECONDS = 0.16
        private const val HUB_RADIUS = 19f
        private const val DEAD_ZONE = 13f
        private const val RING_WIDTH = 14f
        private const val LABEL_GAP = 15f
        private const val SEGMENT_GAP = 2.5f
        private const val SELECT_GROWTH = 5f
        private const val WEDGE_LENGTH = 6f
        private const val WEDGE_INSET = 3f
        private const val WEDGE_HALF_WIDTH = 4.5f
        private const val LABEL_GROWTH = 0.22f

        private const val COLOR_SEGMENT = 0xE8262626.toInt()
        private const val COLOR_SEGMENT_OUTER = 0xE82C2C2C.toInt()
        private const val COLOR_HUB = 0xF6171717.toInt()
        private const val COLOR_LABEL = 0xFFB8B8B8.toInt()
        private const val COLOR_LABEL_SELECTED = 0xFFFFFFFF.toInt()
    }
}
