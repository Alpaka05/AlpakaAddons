package net.alpaka.addons.features.wheel

import net.alpaka.addons.compliance.Outbound
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.client.gui.GuiFont
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.features.sound.CustomSoundFeature
import net.alpaka.addons.features.wheel.WheelMesh.Companion.TAU
import net.alpaka.addons.features.wheel.WheelMesh.Companion.lerpColor
import net.alpaka.addons.features.wheel.WheelMesh.Companion.scaleAlpha
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The quick command wheel: a segmented dial around a small hub, one segment per command on the
 * current page, with the command names sitting just outside their segment.
 *
 * Selection is by direction, not by hitting a box. Anywhere outside the hub the mouse's bearing
 * from the centre picks the segment, so a flick of a few pixels in roughly the right direction is
 * enough - the point of the wheel is that it is faster than typing. A short, wide wedge on the rim
 * of the hub turns smoothly to the centre of the segment being aimed at; that segment fills with the
 * accent color and swells outwards, and its label grows with it. Releasing the wheel key or
 * clicking runs the command; right-click or Escape closes without running anything. In toggle mode
 * ([AlpakaConfig.commandWheelToggleMode]) the key only opens and closes the wheel, and only a click
 * runs a command.
 *
 * The commands come in pages ([CommandWheelPages]), so a long list never crowds the dial. Arrows at
 * the left and right screen edges step through the pages, as does the mouse wheel; the arrows sit
 * far enough out that aiming at them never counts as aiming at a segment. Usually the player wants
 * the next page, not the one after it, so each arrow turns once per visit and both arrows and the
 * mouse wheel turn one page per gesture (see [tryStepPage]).
 *
 * The dial grows until no two labels overlap; if that no longer fits between the arrows, the whole
 * wheel is scaled down instead of letting the names run into each other.
 *
 * Everything moves on eased curves driven by real frame time, so it looks the same at 60 and 240
 * frames per second, and everything rounded is drawn by [WheelMesh] rather than stacked rectangles.
 */
class CommandWheelScreen : Screen(Component.literal("Quick Command Menu")) {

    private var pages: List<List<String>> = emptyList()
    private var page = 0
    private var commands: List<String> = emptyList()

    /**
     * The current page's names in the configured font and their widths, built in showPage(): the
     * dial lays them out and draws them every frame, and measuring every name twice a frame added
     * up. The font only changes through a resize or a resource reload, and both run init() ->
     * showPage() again.
     */
    private var labels: List<Component> = emptyList()
    private var labelWidths = IntArray(0)

    /** Per segment, 0 = idle and 1 = fully highlighted; eased towards its target every frame. */
    private var highlight = FloatArray(0)
    private var selectedIndex = -1

    /**
     * False from the moment a hovered arrow turned the page until the mouse has clearly moved away
     * from the arrows again, [ARROW_REARM] pixels past their hit area, so wobbling on the edge of an
     * arrow cannot turn a second page.
     */
    private var arrowHoverArmed = true

    /** When the page last turned, by arrow or scroll; nothing turns again within [PAGE_COOLDOWN_NANOS]. */
    private var lastPageTurnNanos = 0L

    /** When the last scroll event came in; a scroll only turns the page if it starts a new gesture. */
    private var lastScrollNanos = 0L

    /** The current page's layout for the current screen size; rebuilt by showPage() and init(). */
    private var cachedLayout: Layout? = null

    /** 0 while nothing is aimed at, 1 while a segment is selected; fades the wedge and hub rim. */
    private var aim = 0f

    /** Where the wedge points, radians in screen space. Starts pointing up. */
    private var wedgeAngle = -TAU / 4f

    /** Eased hover glow of the two page arrows, left then right. */
    private val arrowGlow = FloatArray(2)

    // The dial's mesh, kept between frames: its buffers grow to fit the shadow ring, some 5,000
    // vertices, and a fresh mesh every frame regrew and copied them each time. Rebuilt when the GUI
    // scale changes, since its feather is about one screen pixel.
    private var wheelMesh: WheelMesh? = null
    private var wheelMeshScale = 0

    private val openedAtNanos = System.nanoTime()
    private var lastFrameNanos = openedAtNanos

    override fun isPauseScreen(): Boolean = false

    override fun init() {
        pages = CommandWheelPages.pages().map { ArrayList(it) }
        showPage(lastPage)
    }

    private fun showPage(index: Int) {
        page = if (pages.isEmpty()) 0 else index.coerceIn(0, pages.size - 1)
        lastPage = page
        commands = if (pages.isEmpty()) emptyList() else pages[page]
        labels = commands.map { GuiFont.text(it) }
        labelWidths = IntArray(labels.size) { this.font.width(labels[it]) }
        highlight = FloatArray(commands.size)
        selectedIndex = -1
        cachedLayout = null
    }

    /**
     * Turns the page unless it turned a moment ago. Arrows and scrolling share the cooldown, so
     * hovering an arrow while a trackpad flick is still running out turns one page, not two.
     */
    private fun tryStepPage(delta: Int): Boolean {
        val now = System.nanoTime()
        if (lastPageTurnNanos != 0L && now - lastPageTurnNanos < PAGE_COOLDOWN_NANOS) return false
        lastPageTurnNanos = now
        stepPage(delta)
        return true
    }

    private fun stepPage(delta: Int) {
        if (pages.size <= 1) return
        showPage(((page + delta) % pages.size + pages.size) % pages.size)
        try { CustomSoundFeature.playButtonClickSound() } catch (_: Throwable) {}
    }

    // ------------------------------------------------------------------ input

    override fun keyPressed(event: KeyEvent): Boolean {
        val key = CommandWheelFeature.COMMAND_WHEEL_KEY
        if (key != null && key.matches(event) && AlpakaConfig.instance.commandWheelToggleMode) {
            close()
            return true
        }
        return super.keyPressed(event)
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        val key = CommandWheelFeature.COMMAND_WHEEL_KEY
        if (key != null && key.matches(event)) {
            // In toggle mode this is just the press that opened the wheel letting go.
            if (!AlpakaConfig.instance.commandWheelToggleMode) runSelectedAndClose()
            return true
        }
        return super.keyReleased(event)
    }

    override fun mouseClicked(event: MouseButtonEvent, isDoubleClick: Boolean): Boolean {
        when (event.button()) {
            0 -> {
                // Hovering an arrow already turned the page; a click on it must not run a command.
                if (arrowAt(event.x(), event.y()) >= 0) return true
                if (selectedIndex >= 0) {
                    runSelectedAndClose()
                    return true
                }
            }
            1 -> {
                close()
                return true
            }
        }
        return super.mouseClicked(event, isDoubleClick)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (scrollY != 0.0 && pages.size > 1) {
            // One page per scroll gesture: a trackpad swipe or a fast spin of the wheel sends a
            // burst of events, and only the first one after a pause turns the page.
            val now = System.nanoTime()
            val newGesture = lastScrollNanos == 0L || now - lastScrollNanos > SCROLL_GESTURE_GAP_NANOS
            lastScrollNanos = now
            if (newGesture) tryStepPage(if (scrollY < 0) 1 else -1)
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }

    /**
     * Sends the selected command, once, and closes the wheel. The only thing the wheel ever sends:
     * one command per explicit release or click, nothing on hover, timers or events, and never a
     * sequence of commands.
     */
    private fun runSelectedAndClose() {
        if (selectedIndex in commands.indices) {
            var command = commands[selectedIndex]
            if (command.startsWith("/")) command = command.substring(1)
            Outbound.command(command, Outbound.Cause.WHEEL)
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
        val sweep: Float,
        /** The whole dial is drawn at this scale around its centre; below 1 only if it would not fit. */
        val scale: Float
    )

    private fun layout(count: Int): Layout =
        cachedLayout?.takeIf { it.cx == this.width / 2f && it.cy == this.height / 2f }
            ?: buildLayout(count).also { cachedLayout = it }

    private fun buildLayout(count: Int): Layout {
        val cx = this.width / 2f
        val cy = this.height / 2f
        val sweep = TAU / count
        val widths = labelWidths
        val textHeight = (this.font.lineHeight - 1).toFloat()

        // Push the ring out until no two labels touch, with a little air between them and room for
        // the selected one to swell. Long names on a full page can need a much larger dial.
        var ringInner = 30f + max(0, count - 6) * 1.5f
        if (count >= 2) {
            var steps = 0
            while (labelsOverlap(ringInner + RING_WIDTH + LABEL_GAP, sweep, widths, textHeight) && steps < 600) {
                ringInner += 2f
                steps++
            }
        }
        val ringOuter = ringInner + RING_WIDTH
        val labelRadius = ringOuter + LABEL_GAP

        // How far the dial reaches from its centre, labels included, at their selected size.
        var reachX = ringOuter + SELECT_GROWTH
        var reachY = ringOuter + SELECT_GROWTH
        for (i in 0 until count) {
            val angle = -TAU / 4f + i * sweep
            val r = labelRadius + SELECT_GROWTH
            val ax = cos(angle) * r
            val ay = sin(angle) * r
            val w = widths[i] * (1f + LABEL_GROWTH)
            val h = textHeight * (1f + LABEL_GROWTH)
            reachX = max(reachX, max(-(ax + (cos(angle) - 1f) * w / 2f), ax + (cos(angle) + 1f) * w / 2f))
            reachY = max(reachY, max(-(ay + (sin(angle) - 1f) * h / 2f), ay + (sin(angle) + 1f) * h / 2f))
        }
        // It has to fit between the page arrows and above the page counter; if it does not, the
        // whole dial shrinks rather than the labels moving back into each other.
        val roomX = this.width / 2f - (if (pages.size > 1) ARROW_ZONE_WIDTH else 0f) - 6f
        val roomY = this.height / 2f - (if (pages.size > 1) 30f else 8f)
        val scale = min(1f, min(roomX / reachX, roomY / reachY)).coerceAtLeast(0.3f)

        return Layout(cx, cy, HUB_RADIUS, ringInner, ringOuter, labelRadius, sweep, scale)
    }

    /** Whether any two labels' boxes, padded a little, overlap with the label circle at [radius]. */
    private fun labelsOverlap(radius: Float, sweep: Float, widths: IntArray, textHeight: Float): Boolean {
        val count = widths.size
        val left = FloatArray(count)
        val top = FloatArray(count)
        val right = FloatArray(count)
        val bottom = FloatArray(count)
        for (i in 0 until count) {
            val angle = -TAU / 4f + i * sweep
            val cosA = cos(angle)
            val sinA = sin(angle)
            val ax = cosA * radius
            val ay = sinA * radius
            val x = ax + (cosA - 1f) * widths[i] / 2f
            val y = ay + (sinA - 1f) * textHeight / 2f
            left[i] = x - LABEL_PAD_X
            right[i] = x + widths[i] + LABEL_PAD_X
            top[i] = y - LABEL_PAD_Y
            bottom[i] = y + textHeight + LABEL_PAD_Y
        }
        for (i in 0 until count) {
            for (j in i + 1 until count) {
                if (left[i] < right[j] && left[j] < right[i] && top[i] < bottom[j] && top[j] < bottom[i]) return true
            }
        }
        return false
    }

    private fun segmentAngle(layout: Layout, index: Int): Float = -TAU / 4f + index * layout.sweep

    /** The segment the mouse is pointing at, or -1 while it rests inside the hub. */
    private fun pick(layout: Layout, mouseX: Int, mouseY: Int, count: Int): Int {
        val dx = mouseX - layout.cx
        val dy = mouseY - layout.cy
        if (sqrt(dx * dx + dy * dy) < DEAD_ZONE * layout.scale) return -1
        var bearing = atan2(dy, dx) + TAU / 4f + layout.sweep / 2f
        bearing = ((bearing % TAU) + TAU) % TAU
        return (bearing / layout.sweep).toInt().coerceIn(0, count - 1)
    }

    /** Centre x of the page arrow: 0 is the left one, 1 the right one. */
    private fun arrowCenterX(arrow: Int): Float = if (arrow == 0) ARROW_MARGIN else this.width - ARROW_MARGIN

    /**
     * How far the mouse is outside an arrow's hit area, 0 or less while inside. The hit area is a
     * tall strip along the screen edge, much larger than the drawn arrow, so a quick flick sideways
     * finds it without aiming.
     */
    private fun arrowDistance(arrow: Int, mouseX: Double, mouseY: Double): Double {
        val dx = if (arrow == 0) mouseX - ARROW_ZONE_WIDTH else (this.width - ARROW_ZONE_WIDTH) - mouseX
        val dy = abs(mouseY - this.height / 2.0) - ARROW_ZONE_HALF_HEIGHT
        return max(dx, dy)
    }

    /** Which page arrow the mouse is over, 0 left, 1 right, or -1. Only with more than one page. */
    private fun arrowAt(mouseX: Double, mouseY: Double): Int {
        if (pages.size <= 1) return -1
        for (arrow in 0..1) if (arrowDistance(arrow, mouseX, mouseY) <= 0.0) return arrow
        return -1
    }

    /** Whether the mouse is well clear of both arrows, far enough to let them turn the page again. */
    private fun clearOfArrows(mouseX: Double, mouseY: Double): Boolean =
        pages.size <= 1 || (0..1).all { arrowDistance(it, mouseX, mouseY) > ARROW_REARM }

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

        val guiScale = (this.minecraft ?: Minecraft.getInstance()).window.guiScale.coerceAtLeast(1)
        val mesh = wheelMesh?.takeIf { wheelMeshScale == guiScale }
            ?: WheelMesh(1.15f / guiScale).also { wheelMesh = it; wheelMeshScale = guiScale }
        mesh.reset()
        val accent = ModernGuiUtils.getAccentColor()
        val hoveredArrow = arrowAt(mouseX.toDouble(), mouseY.toDouble())

        // Pointing at an arrow turns the page, no click needed. It turns once per visit: the mouse
        // has to move well away from the arrows before they can turn again, so resting on one, or
        // jittering across the edge of its hit area, does not keep flipping.
        if (hoveredArrow >= 0) {
            if (arrowHoverArmed && tryStepPage(if (hoveredArrow == 0) -1 else 1)) arrowHoverArmed = false
        } else if (clearOfArrows(mouseX.toDouble(), mouseY.toDouble())) {
            arrowHoverArmed = true
        }

        val count = commands.size
        if (count == 0) {
            selectedIndex = -1
            drawEmptyState(graphics, open)
            drawPageChrome(graphics, mesh, accent, open, hoveredArrow, dt)
            return
        }
        if (highlight.size != count) highlight = FloatArray(count)

        val layout = layout(count)
        // Aiming at a page arrow is not aiming at the wheel, so nothing lights up behind it.
        selectedIndex = if (hoveredArrow >= 0) -1 else pick(layout, mouseX, mouseY, count)

        if (selectedIndex >= 0) {
            // The wedge points at the selected segment's centre line rather than at the mouse, so it
            // always sits exactly on the axis of the segment that is lit.
            wedgeAngle = approachAngle(wedgeAngle, segmentAngle(layout, selectedIndex), ease(dt, 26f))
        }
        aim = approach(aim, if (selectedIndex >= 0) 1f else 0f, ease(dt, 16f))
        for (i in 0 until count) {
            highlight[i] = approach(highlight[i], if (i == selectedIndex) 1f else 0f, ease(dt, 15f))
        }

        val pose = graphics.pose()
        pose.pushMatrix()
        val openScale = 0.86f + 0.14f * open
        pose.scaleAround(openScale * layout.scale, openScale * layout.scale, layout.cx, layout.cy)

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
        // selected one takes the accent color and swells outwards.
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

        // The wedge: a short, wide triangle growing out of the hub's rim line, pointing outwards
        // along the selected segment's axis. Its base is not a straight chord but follows the rim's
        // arc, from the inner edge of the hairline ring, so the wedge sits exactly on the circle with
        // nothing of it hanging inside the disc. It fades with the selection, so an idle hub is just
        // the plain disc.
        if (aim > 0.02f) {
            val tip = layout.hubRadius + WEDGE_LENGTH * aim
            val base = layout.hubRadius - 1f
            val halfWidth = WEDGE_HALF_WIDTH * (0.6f + 0.4f * aim)
            val spread = asin((halfWidth / base).coerceIn(-1f, 1f))
            val arcPoints = 6
            val points = FloatArray(2 + 2 * (arcPoints + 1))
            points[0] = layout.cx + cos(wedgeAngle) * tip
            points[1] = layout.cy + sin(wedgeAngle) * tip
            for (i in 0..arcPoints) {
                val a = wedgeAngle + spread - 2f * spread * (i / arcPoints.toFloat())
                points[2 + 2 * i] = layout.cx + cos(a) * base
                points[3 + 2 * i] = layout.cy + sin(a) * base
            }
            mesh.convexPolygon(points, scaleAlpha(accent, open * aim))
            mesh.submit(graphics)
        }

        // Labels sit just outside their segment. Each is anchored by the edge nearest the ring, so a
        // long name on the left or right grows away from the wheel instead of into it, and the
        // selected one scales up around that same anchor and takes the accent color, lifted a
        // little towards white so it stays readable even with a dark accent.
        val labelSelected = lerpColor(accent, 0xFFFFFFFF.toInt(), 0.25f)
        for (i in 0 until count) {
            val h = highlight[i]
            val angle = segmentAngle(layout, i)
            val cosA = cos(angle)
            val sinA = sin(angle)
            val radius = layout.labelRadius + SELECT_GROWTH * h
            val anchorX = layout.cx + cosA * radius
            val anchorY = layout.cy + sinA * radius
            val textWidth = labelWidths[i]
            val textHeight = this.font.lineHeight - 1
            val x = (anchorX + (cosA - 1f) * textWidth / 2f).roundToInt()
            val y = (anchorY + (sinA - 1f) * textHeight / 2f).roundToInt()

            val color = scaleAlpha(lerpColor(COLOR_LABEL, labelSelected, h), open)
            if ((color ushr 24) < 8) continue

            val scale = 1f + LABEL_GROWTH * h
            pose.pushMatrix()
            pose.scaleAround(scale, scale, anchorX, anchorY)
            graphics.text(this.font, labels[i], x, y, color, false)
            pose.popMatrix()
        }

        pose.popMatrix()

        drawPageChrome(graphics, mesh, accent, open, hoveredArrow, dt)
    }

    /**
     * The page arrows at the screen edges and the page counter at the bottom. Drawn outside the
     * wheel's open-scale transform: they belong to the screen, not to the dial.
     */
    private fun drawPageChrome(graphics: GuiGraphicsExtractor, mesh: WheelMesh, accent: Int, open: Float, hoveredArrow: Int, dt: Float) {
        if (pages.size <= 1) return

        val ay = this.height / 2f
        for (arrow in 0..1) {
            arrowGlow[arrow] = approach(arrowGlow[arrow], if (hoveredArrow == arrow) 1f else 0f, ease(dt, 16f))
            val glow = arrowGlow[arrow]
            val ax = arrowCenterX(arrow)
            val direction = if (arrow == 0) -1f else 1f
            val lift = 1f + 0.08f * glow

            mesh.disc(ax, ay, ARROW_RADIUS * lift, scaleAlpha(COLOR_HUB, open))
            mesh.ring(
                ax, ay, ARROW_RADIUS * lift - 1.5f, ARROW_RADIUS * lift,
                scaleAlpha(lerpColor(ModernGuiUtils.COLOR_CARD_BORDER, accent, glow), open)
            )
            mesh.submit(graphics)

            // A chevron: two slanted bars meeting at the point, drawn as thin quads.
            val color = scaleAlpha(lerpColor(COLOR_LABEL, accent, glow), open)
            val tipX = ax + direction * 5f * lift
            val backX = ax - direction * 4f * lift
            val arm = 9.5f * lift
            val t = 2.6f
            mesh.quad(
                backX, ay - arm, color, backX + direction * t, ay - arm, color,
                tipX + direction * t, ay, color, tipX, ay, color
            )
            mesh.quad(
                tipX, ay, color, tipX + direction * t, ay, color,
                backX + direction * t, ay + arm, color, backX, ay + arm, color
            )
            mesh.submit(graphics)
        }

        val counter = "${page + 1} / ${pages.size}"
        ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text(counter), this.width / 2,
            this.height - 24, scaleAlpha(ModernGuiUtils.COLOR_TEXT_MUTED, open))
    }

    private fun drawEmptyState(graphics: GuiGraphicsExtractor, open: Float) {
        val cx = this.width / 2
        val cy = this.height / 2
        val primary = scaleAlpha(ModernGuiUtils.COLOR_TEXT_PRIMARY, open)
        val muted = scaleAlpha(ModernGuiUtils.COLOR_TEXT_MUTED, open)
        if ((primary ushr 24) < 8) return
        ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text("No quick commands yet"), cx, cy - 10, primary)
        ModernGuiUtils.centeredText(graphics, this.font, GuiFont.text("Add some under Alpaka Config → Quick Command Menu"), cx, cy + 4, muted)
    }

    companion object {
        /** The page shown when the wheel last closed, so reopening lands where the player was. */
        private var lastPage = 0

        private const val OPEN_SECONDS = 0.16
        private const val HUB_RADIUS = 19f
        private const val DEAD_ZONE = 13f
        private const val RING_WIDTH = 14f
        private const val LABEL_GAP = 15f
        private const val SEGMENT_GAP = 2.5f
        private const val SELECT_GROWTH = 5f
        private const val WEDGE_LENGTH = 6f
        private const val WEDGE_HALF_WIDTH = 4.5f
        private const val LABEL_GROWTH = 0.22f

        /** Padding around each label when checking that neighbours do not overlap. */
        private const val LABEL_PAD_X = 5f
        private const val LABEL_PAD_Y = 2f

        /** Distance of the page arrows' centres from the screen edge, and their drawn radius. */
        private const val ARROW_MARGIN = 40f
        private const val ARROW_RADIUS = 21f
        /** The arrows' hit area: a strip this wide along each screen edge, this far above and below the centre. */
        private const val ARROW_ZONE_WIDTH = 76f
        private const val ARROW_ZONE_HALF_HEIGHT = 60f
        /** How far the mouse has to move away from an arrow's hit area before it can turn the page again. */
        private const val ARROW_REARM = 28.0

        /** No second page turn within this long of the first, from either arrows or scrolling. */
        private const val PAGE_COOLDOWN_NANOS = 350_000_000L
        /** A pause this long between scroll events ends one scroll gesture and lets the next turn a page. */
        private const val SCROLL_GESTURE_GAP_NANOS = 250_000_000L

        private const val COLOR_SEGMENT = 0xE8262626.toInt()
        private const val COLOR_SEGMENT_OUTER = 0xE82C2C2C.toInt()
        private const val COLOR_HUB = 0xF6171717.toInt()
        private const val COLOR_LABEL = 0xFFB8B8B8.toInt()
    }
}
