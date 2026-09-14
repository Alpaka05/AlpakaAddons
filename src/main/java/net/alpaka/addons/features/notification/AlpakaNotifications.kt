package net.alpaka.addons.features.notification

import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FormattedText
import net.minecraft.util.FormattedCharSequence
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Small notices that slide in at a screen edge, stack away from it and slide back out.
 *
 * This is the mod's general notification channel, not one feature's private overlay: anything that
 * needs to tell the player something without writing to chat calls [send] and is done. Nothing here
 * knows what a mention or a party invite is - see [MentionNotifier] for the first thing that uses it.
 *
 *     AlpakaNotifications.send("Slayer", "Personal best: 42.3s")
 *
 * A notice may carry styled lines ([sendLines]) and hands back an id, so a feature that has to act
 * while its notice is up - the party invite's Y and N - can ask [isShowing] and take it away early
 * with [dismiss]. A thin bar along the bottom edge runs down while the notice waits, so how long it
 * has left is visible at a glance.
 *
 * Everything is measured in wall-clock milliseconds rather than ticks, so the animation keeps its
 * timing while the game is paused mid-tick or running below twenty ticks a second.
 *
 * Where notices appear is a setting ([AlpakaConfig.notificationCorner]): one of the four corners,
 * or the top centre. From a corner a notice slides in sideways from the nearer edge and the stack
 * grows away from that corner - up from a bottom corner, down from a top one - with the newest
 * always in the slot nearest the corner. At the top centre it drops in from above and the stack
 * grows downwards, the newest on top.
 */
object AlpakaNotifications {

    const val CORNER_BOTTOM_RIGHT = 0
    const val CORNER_BOTTOM_LEFT = 1
    const val CORNER_TOP_RIGHT = 2
    const val CORNER_TOP_LEFT = 3
    const val TOP_CENTER = 4

    /** Names for the config slider, indexed by the position constants. */
    @JvmField
    val CORNER_NAMES = arrayOf("Bottom Right", "Bottom Left", "Top Right", "Top Left", "Top Center")

    /** How long a notice takes to slide in, and again to slide out. */
    private const val SLIDE_MS = 260L

    /**
     * How long a notice stays put between the two, unless the caller asks for something else.
     *
     * Read at the moment the notice is queued rather than while it is on screen, so moving the
     * slider cannot cut short something already being read.
     */
    @JvmStatic
    fun configuredHoldMs(): Long =
        (AlpakaConfig.instance.notificationHoldSeconds.coerceIn(1.0f, 15.0f) * 1000.0f).toLong()

    /** Beyond this the oldest is retired early, so a burst cannot cover the screen. */
    private const val MAX_VISIBLE = 4

    private const val WIDTH = 196
    private const val PAD = 6
    private const val LINE = 10
    private const val GAP = 4
    private const val MARGIN = 8

    /** Width of the coloured stripe down the left edge, which is what carries the accent. */
    private const val STRIPE = 2

    /** Height of the time-left bar along the bottom edge, and the gap kept above it. */
    private const val BAR = 2
    private const val BAR_GAP = 3

    /** At most this many wrapped lines of body text; the rest is dropped rather than shown cut. */
    private const val MAX_BODY_LINES = 2

    /**
     * The glass the box is made of: a little more solid at the top than the bottom, and solid
     * enough overall to read over bright terrain.
     *
     * Graded rather than flat so the box settles into the screen instead of sitting on it as a
     * slab; the header band under the title carries a whisper of the accent for the same reason.
     */
    private const val GLASS_TOP = 0xD20F1216.toInt()
    private const val GLASS_BOTTOM = 0x960F1216.toInt()
    private const val EDGE = 0x40FFFFFF
    private const val SHEEN = 0x30FFFFFF
    private const val SHADOW = 0x40000000
    private const val BAR_TRACK = 0x24FFFFFF
    private const val BODY_TEXT = 0xFFE4E7EA.toInt()

    /**
     * How quickly a notice slides to the slot it should be in, as a time constant in milliseconds.
     *
     * Vertical movement is eased rather than snapped because a notice above one that just expired
     * would otherwise jump down a whole box in a single frame.
     */
    private const val SETTLE_TAU = 70.0f

    private class Notice(
        val id: Long,
        val title: String,
        val body: List<FormattedCharSequence>,
        val accent: Int,
        val bornAtMs: Long,
        val holdMs: Long,
    ) {
        val height: Int = PAD * 2 + LINE + body.size * LINE + BAR_GAP + BAR
        var settledY: Float = Float.NaN
        var retireAtMs: Long = bornAtMs + SLIDE_MS + holdMs + SLIDE_MS
    }

    private val active = ArrayList<Notice>()
    private var nextId = 1L
    private var lastFrameMs = 0L

    /**
     * Queues a notice with a plain-text body, wrapped to the box. Safe to call from any thread the
     * game runs on; drawing happens on its own. Returns the notice's id.
     *
     * [accent] of zero means the menu's own accent colour, which is what a caller with no reason to
     * pick something else should pass. [holdMs] of zero means the configured duration.
     */
    @JvmStatic
    @JvmOverloads
    fun send(title: String, body: String = "", accent: Int = 0, holdMs: Long = 0L): Long {
        val lines = if (body.isEmpty()) emptyList() else wrap(FormattedText.of(body))
        return enqueue(title, lines, accent, holdMs)
    }

    /** Queues a notice whose body lines carry their own styling. Each line is wrapped on its own. */
    @JvmStatic
    @JvmOverloads
    fun sendLines(title: String, lines: List<Component>, accent: Int = 0, holdMs: Long = 0L): Long =
        enqueue(title, lines.flatMap { wrap(it) }, accent, holdMs)

    /** Whether the notice is on screen and not yet on its way out. */
    @JvmStatic
    fun isShowing(id: Long): Boolean {
        if (id == 0L) return false
        synchronized(active) {
            val notice = active.firstOrNull { it.id == id } ?: return false
            return System.currentTimeMillis() < notice.retireAtMs - SLIDE_MS
        }
    }

    /** Slides the notice out now instead of at the end of its hold. */
    @JvmStatic
    fun dismiss(id: Long) {
        if (id == 0L) return
        synchronized(active) {
            active.firstOrNull { it.id == id }?.let { retireEarly(it) }
        }
    }

    private fun wrap(text: FormattedText): List<FormattedCharSequence> {
        val font = Minecraft.getInstance().font ?: return emptyList()
        return font.split(text, WIDTH - PAD * 2 - STRIPE)
    }

    private fun enqueue(title: String, lines: List<FormattedCharSequence>, accent: Int, holdMs: Long): Long {
        val hold = if (holdMs > 0L) holdMs else configuredHoldMs()
        synchronized(active) {
            val id = nextId++
            active.add(Notice(id, title, lines.take(MAX_BODY_LINES), accent, System.currentTimeMillis(), hold))
            // Retire from the top rather than refusing the new one: the newest notice is the one
            // the player is most likely waiting for.
            while (active.size > MAX_VISIBLE) retireEarly(active[0])
            return id
        }
    }

    /** Brings a notice's slide-out forward, without cutting the animation itself short. */
    private fun retireEarly(notice: Notice) {
        val soonest = System.currentTimeMillis() + SLIDE_MS
        if (notice.retireAtMs > soonest) notice.retireAtMs = soonest
    }

    /** Called every frame from the HUD hook. */
    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val mc = Minecraft.getInstance()
        if (mc.gui.hud.isHidden() || mc.level == null) return

        val font = mc.font ?: return
        val now = System.currentTimeMillis()
        val deltaMs = if (lastFrameMs == 0L) 16L else (now - lastFrameMs).coerceIn(0L, 250L)
        lastFrameMs = now

        val snapshot: List<Notice>
        synchronized(active) {
            active.removeAll { now >= it.retireAtMs }
            if (active.isEmpty()) return
            snapshot = ArrayList(active)
        }

        val screenWidth = mc.window.guiScaledWidth
        val screenHeight = mc.window.guiScaledHeight

        val corner = AlpakaConfig.instance.notificationCorner
        val topCenter = corner == TOP_CENTER
        val atBottom = corner == CORNER_BOTTOM_RIGHT || corner == CORNER_BOTTOM_LEFT
        val atRight = corner == CORNER_BOTTOM_RIGHT || corner == CORNER_TOP_RIGHT

        // Walked newest first, so the newest takes the slot at the edge and the older ones are
        // pushed further from it as more arrive: up the screen from a bottom corner, down from the
        // top otherwise.
        var stackEdge = if (atBottom) (screenHeight - MARGIN).toFloat() else MARGIN.toFloat()
        for (index in snapshot.indices.reversed()) {
            val notice = snapshot[index]
            val targetY = if (atBottom) stackEdge - notice.height else stackEdge
            stackEdge = if (atBottom) targetY - GAP else targetY + notice.height + GAP

            if (notice.settledY.isNaN()) notice.settledY = targetY
            notice.settledY = ease(notice.settledY, targetY, deltaMs)

            val shown = visibility(notice, now)
            val hidden = 1.0f - shown
            val x: Int
            val y: Int
            if (topCenter) {
                // Drops in from above the screen edge and climbs back out the same way.
                x = (screenWidth - WIDTH) / 2
                y = Math.round(notice.settledY - hidden * (notice.height + MARGIN))
                if (y + notice.height <= 0) continue
            } else {
                // Slides in from the side edge it sits against, so it never crosses the screen.
                val slide = Math.round(hidden * (WIDTH + MARGIN))
                x = if (atRight) screenWidth - MARGIN - WIDTH + slide else MARGIN - slide
                y = Math.round(notice.settledY)
                if (x >= screenWidth || x + WIDTH <= 0) continue
            }

            draw(graphics, font, notice, x, y, shown, now)
        }
    }

    /**
     * How far out the notice is, from 0 fully offscreen to 1 fully in place.
     *
     * Smoothstepped rather than linear so it arrives and leaves without the hard stop a straight
     * ramp gives.
     */
    private fun visibility(notice: Notice, now: Long): Float {
        val sinceBorn = now - notice.bornAtMs
        val untilGone = notice.retireAtMs - now

        val raw = min(
            if (sinceBorn >= SLIDE_MS) 1.0f else sinceBorn / SLIDE_MS.toFloat(),
            if (untilGone >= SLIDE_MS) 1.0f else max(0.0f, untilGone / SLIDE_MS.toFloat()),
        )
        return raw * raw * (3.0f - 2.0f * raw)
    }

    /** Frame-rate independent approach to a target: the same speed at 30fps as at 240. */
    private fun ease(current: Float, target: Float, deltaMs: Long): Float {
        val factor = 1.0f - exp(-deltaMs / SETTLE_TAU)
        return current + (target - current) * factor
    }

    /** Replaces the alpha byte of a colour, keeping its rgb. */
    private fun withAlpha(color: Int, alpha: Int): Int = (alpha.coerceIn(0, 255) shl 24) or (color and 0xFFFFFF)

    /**
     * A colour faded by how far in the notice is, so the box and its text arrive together instead
     * of the text popping in over a still-transparent box. Never below the few units the font
     * renderer would otherwise read as "opaque".
     */
    private fun faded(color: Int, shown: Float): Int =
        withAlpha(color, ((color ushr 24) * shown).roundToInt().coerceAtLeast(4))

    private fun draw(graphics: GuiGraphicsExtractor, font: Font, notice: Notice, x: Int, y: Int, shown: Float, now: Long) {
        val accent = if (notice.accent != 0) notice.accent else ModernGuiUtils.getAccentColor()
        val height = notice.height

        // A soft shadow lifts the box off whatever is behind it.
        graphics.fill(x + 1, y + 2, x + WIDTH + 1, y + height + 2, faded(SHADOW, shown))
        // Graded top to bottom, so the box fades out towards its lower edge rather than ending.
        graphics.fillGradient(x, y, x + WIDTH, y + height, faded(GLASS_TOP, shown), faded(GLASS_BOTTOM, shown))
        // The header band: a whisper of the accent behind the title, gone by the first body line.
        graphics.fillGradient(x + STRIPE, y, x + WIDTH, y + PAD + LINE, faded(withAlpha(accent, 0x30), shown), faded(withAlpha(accent, 0x00), shown))
        // A single bright line along the top is what reads as a lit edge on glass.
        ModernGuiUtils.drawRect(graphics, x, y, WIDTH, 1, faded(SHEEN, shown))
        ModernGuiUtils.drawOutline(graphics, x, y, WIDTH, height, faded(EDGE, shown))
        // The accent fades with the glass instead of running full strength to the bottom corner.
        graphics.fillGradient(x, y, x + STRIPE, y + height, faded(accent, shown), faded(withAlpha(accent, 0x50), shown))

        val textX = x + STRIPE + PAD
        graphics.text(font, Component.literal(notice.title), textX, y + PAD, faded(accent, shown))
        for (line in notice.body.indices) {
            graphics.text(font, notice.body[line], textX, y + PAD + LINE + line * LINE, faded(BODY_TEXT, shown))
        }

        // The time left, as a bar along the bottom edge that runs down from full while the notice
        // waits; it is full while sliding in and empty once the slide out begins.
        val barY = y + height - BAR
        val barLeft = x + STRIPE
        val barWidth = WIDTH - STRIPE
        graphics.fill(barLeft, barY, x + WIDTH, y + height, faded(BAR_TRACK, shown))
        val remaining = ((notice.retireAtMs - SLIDE_MS - now).toFloat() / notice.holdMs.toFloat()).coerceIn(0.0f, 1.0f)
        val filled = (barWidth * remaining).roundToInt()
        if (filled > 0) {
            graphics.fillGradient(barLeft, barY, barLeft + filled, y + height, faded(accent, shown), faded(withAlpha(accent, 0xB0), shown))
        }
    }
}
