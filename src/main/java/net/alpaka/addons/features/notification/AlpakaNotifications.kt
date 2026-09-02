package net.alpaka.addons.features.notification

import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Small notices that slide in at a screen corner, stack away from it and slide back out.
 *
 * This is the mod's general notification channel, not one feature's private overlay: anything that
 * needs to tell the player something without writing to chat calls [send] and is done. Nothing here
 * knows what a mention is - see [MentionNotifier] for the first thing that uses it.
 *
 *     AlpakaNotifications.send("Slayer", "Personal best: 42.3s")
 *
 * Everything is measured in wall-clock milliseconds rather than ticks, so the animation keeps its
 * timing while the game is paused mid-tick or running below twenty ticks a second.
 *
 * The corner is a setting ([AlpakaConfig.notificationCorner]). Notices slide in horizontally from
 * the nearer screen edge and stack vertically away from that edge: upwards from a bottom corner,
 * downwards from a top one, with the newest always taking the slot nearest the corner.
 */
object AlpakaNotifications {

    const val CORNER_BOTTOM_RIGHT = 0
    const val CORNER_BOTTOM_LEFT = 1
    const val CORNER_TOP_RIGHT = 2
    const val CORNER_TOP_LEFT = 3

    /** Names for the config slider, indexed by the CORNER_* constants. */
    @JvmField
    val CORNER_NAMES = arrayOf("Bottom Right", "Bottom Left", "Top Right", "Top Left")

    /** How long a notice takes to slide in, and again to slide out. */
    private const val SLIDE_MS = 260L

    /**
     * How long it stays put between the two, unless the caller asks for something else.
     *
     * Read at the moment the notice is queued rather than while it is on screen, so moving the
     * slider cannot cut short something already being read.
     */
    private fun configuredHoldMs(): Long =
        (AlpakaConfig.instance.notificationHoldSeconds.coerceIn(1.0f, 15.0f) * 1000.0f).toLong()

    /** Beyond this the oldest is retired early, so a burst cannot cover the screen. */
    private const val MAX_VISIBLE = 4

    private const val WIDTH = 180
    private const val PAD = 6
    private const val LINE = 10
    private const val GAP = 4
    private const val MARGIN = 8

    /** Width of the coloured stripe down the left edge, which is what carries the accent. */
    private const val STRIPE = 2

    /** At most this many wrapped lines of body text; the rest is dropped rather than shown cut. */
    private const val MAX_BODY_LINES = 2

    /**
     * The glass the box is made of: a little more solid at the top than the bottom.
     *
     * Kept translucent enough to read the world through, and graded rather than flat so the box
     * settles into the screen instead of sitting on it as a slab.
     */
    private const val GLASS_TOP = 0xB2101418.toInt()
    private const val GLASS_BOTTOM = 0x59101418
    private const val EDGE = 0x3CFFFFFF
    private const val SHEEN = 0x26FFFFFF

    /**
     * How quickly a notice slides to the slot it should be in, as a time constant in milliseconds.
     *
     * Vertical movement is eased rather than snapped because a notice above one that just expired
     * would otherwise jump down a whole box in a single frame.
     */
    private const val SETTLE_TAU = 70.0f

    private class Notice(
        val title: String,
        val body: List<String>,
        val accent: Int,
        val bornAtMs: Long,
        val holdMs: Long,
    ) {
        val height: Int = PAD * 2 + LINE + body.size * LINE
        var settledY: Float = Float.NaN
        var retireAtMs: Long = bornAtMs + SLIDE_MS + holdMs + SLIDE_MS
    }

    private val active = ArrayList<Notice>()
    private var lastFrameMs = 0L

    /**
     * Queues a notice. Safe to call from any thread the game runs on; drawing happens on its own.
     *
     * [accent] of zero means the menu's own accent colour, which is what a caller with no reason to
     * pick something else should pass.
     */
    @JvmStatic
    @JvmOverloads
    fun send(title: String, body: String = "", accent: Int = 0, holdMs: Long = 0L) {
        val hold = if (holdMs > 0L) holdMs else configuredHoldMs()
        val font = Minecraft.getInstance().font ?: return
        val room = WIDTH - PAD * 2 - STRIPE

        val lines = if (body.isEmpty()) {
            emptyList()
        } else {
            font.getSplitter().splitLines(body, room, Style.EMPTY)
                .take(MAX_BODY_LINES)
                .map { it.string }
        }

        synchronized(active) {
            active.add(Notice(title, lines, accent, System.currentTimeMillis(), hold))
            // Retire from the top rather than refusing the new one: the newest notice is the one
            // the player is most likely waiting for.
            while (active.size > MAX_VISIBLE) retireEarly(active[0])
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
        if (mc.options.hideGui || mc.level == null) return

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
        val atBottom = corner == CORNER_BOTTOM_RIGHT || corner == CORNER_BOTTOM_LEFT
        val atRight = corner == CORNER_BOTTOM_RIGHT || corner == CORNER_TOP_RIGHT

        // Walked newest first, so the newest takes the slot in the corner and the older ones are
        // pushed further from it as more arrive: up the screen from a bottom corner, down from a top.
        var stackEdge = if (atBottom) (screenHeight - MARGIN).toFloat() else MARGIN.toFloat()
        for (index in snapshot.indices.reversed()) {
            val notice = snapshot[index]
            val targetY = if (atBottom) stackEdge - notice.height else stackEdge
            stackEdge = if (atBottom) targetY - GAP else targetY + notice.height + GAP

            if (notice.settledY.isNaN()) notice.settledY = targetY
            notice.settledY = ease(notice.settledY, targetY, deltaMs)

            // Slides in from the side edge it sits against, so it never crosses the screen.
            val hidden = 1.0f - visibility(notice, now)
            val slide = Math.round(hidden * (WIDTH + MARGIN))
            val x = if (atRight) screenWidth - MARGIN - WIDTH + slide else MARGIN - slide
            if (x >= screenWidth || x + WIDTH <= 0) continue

            draw(graphics, font, notice, x, Math.round(notice.settledY))
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
    private fun withAlpha(color: Int, alpha: Int): Int = (alpha shl 24) or (color and 0xFFFFFF)

    private fun draw(graphics: GuiGraphicsExtractor, font: net.minecraft.client.gui.Font, notice: Notice, x: Int, y: Int) {
        val accent = if (notice.accent != 0) notice.accent else ModernGuiUtils.getAccentColor()

        // Graded top to bottom, so the box fades out towards its lower edge rather than ending.
        graphics.fillGradient(x, y, x + WIDTH, y + notice.height, GLASS_TOP, GLASS_BOTTOM)
        // A single bright line along the top is what reads as a lit edge on glass.
        ModernGuiUtils.drawRect(graphics, x, y, WIDTH, 1, SHEEN)
        ModernGuiUtils.drawOutline(graphics, x, y, WIDTH, notice.height, EDGE)
        // The accent fades with the glass instead of running full strength to the bottom corner.
        graphics.fillGradient(x, y, x + STRIPE, y + notice.height, accent, withAlpha(accent, 0x40))

        val textX = x + STRIPE + PAD
        graphics.text(font, Component.literal(notice.title), textX, y + PAD, accent)
        for (line in notice.body.indices) {
            graphics.text(
                font, Component.literal(notice.body[line]),
                textX, y + PAD + LINE + line * LINE, ModernGuiUtils.COLOR_TEXT_PRIMARY
            )
        }
    }
}
