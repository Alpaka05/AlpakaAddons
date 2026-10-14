package net.alpaka.addons.features.notification

import net.alpaka.addons.client.gui.AlpakaStyle
import net.alpaka.addons.client.gui.HudPanel
import net.alpaka.addons.client.gui.ModernGuiUtils
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
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

    /**
     * Hard limit on the list, notices sliding out included. Only reached while nothing prunes the
     * list, for example with the HUD hidden, and then the oldest are dropped outright.
     */
    private const val MAX_KEPT = MAX_VISIBLE * 2

    private const val WIDTH = 196
    private const val PAD = 6
    private const val LINE = 10
    private const val GAP = 4
    private const val MARGIN = 8

    /**
     * Where top-centre notices start: low enough to clear a MacBook's notch in fullscreen, and
     * below any boss bars. Vanilla stacks those from y 12, nineteen pixels apart, name above bar.
     */
    private const val TOP_CENTER_CLEARANCE = 24
    private const val BOSS_BAR_PITCH = 19

    private fun topCenterTop(mc: Minecraft): Int {
        val bars = (mc.gui.hud.bossOverlay as net.alpaka.addons.mixin.BossHealthOverlayAccessor).`alpaka$getEvents`().size
        // The overlay stops adding bars a third of the way down the screen.
        val shown = minOf(bars, maxOf(0, (mc.window.guiScaledHeight / 3 - 12) / BOSS_BAR_PITCH + 1))
        val belowBars = if (shown > 0) BOSS_BAR_PITCH * shown + 4 else 0
        return maxOf(TOP_CENTER_CLEARANCE, belowBars)
    }

    /** Height of the time-left bar along the bottom edge, and the gap kept above it. */
    private const val BAR = 1
    private const val BAR_GAP = 3

    /** At most this many wrapped lines of body text; the rest is dropped rather than shown cut. */
    private const val MAX_BODY_LINES = 2

    /**
     * The box when the blur is off: the config menu's panel colour, a little translucent so the
     * world still shows through, with the HUD panel's corners and a soft shadow - the same box as
     * the blurred one, only without the blur. Solid enough to read over bright terrain.
     */
    private const val PANEL = (0xE0 shl 24) or (AlpakaStyle.PANEL and 0xFFFFFF)
    private const val BAR_TRACK = 0x1AFFFFFF
    /**
     * The blurred panel's tint: neutral black like the other HUD panels, but stronger than their
     * [HudPanel.TINT], since a notice is read at a glance over whatever is behind it.
     */
    private const val BLUR_TINT = 0x8C000000.toInt()
    /** How far the time bar stays clear of the panel's rounded corners, either kind. */
    private const val BAR_INSET = HudPanel.RADIUS
    private const val BODY_TEXT = AlpakaStyle.TEXT_SOFT

    /** How many pieces the bar's fade towards its tip is drawn in; the game has no horizontal gradient. */
    private const val BAR_STEPS = 6

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
        /** Already sent on its way out early; it no longer counts towards [MAX_VISIBLE]. */
        var retiring = false
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
        val lines = if (body.isEmpty()) emptyList() else wrap(Component.literal(body))
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

    /** Wrapped in the font the notice is drawn in, which is settled when it is queued. */
    private fun wrap(text: Component): List<FormattedCharSequence> {
        val font = Minecraft.getInstance().font ?: return emptyList()
        return font.split(HudPanel.text(text, AlpakaConfig.instance.smoothOn(AlpakaConfig.instance.notificationSmoothFont)), WIDTH - PAD * 2)
    }

    private fun enqueue(title: String, lines: List<FormattedCharSequence>, accent: Int, holdMs: Long): Long {
        val hold = if (holdMs > 0L) holdMs else configuredHoldMs()
        synchronized(active) {
            val now = System.currentTimeMillis()
            active.removeAll { now >= it.retireAtMs }

            val id = nextId++
            active.add(Notice(id, title, lines.take(MAX_BODY_LINES), accent, now, hold))

            // Retire from the top rather than refusing the new one: the newest notice is the one
            // the player is most likely waiting for. Only notices still on screen count, and the
            // loop walks the list once. It used to loop on the list's size, which retiring never
            // changes, so a fifth notice while four were up froze the game.
            var live = active.count { !it.retiring }
            for (notice in active) {
                if (live <= MAX_VISIBLE) break
                if (notice.retiring) continue
                retireEarly(notice)
                live--
            }
            while (active.size > MAX_KEPT) active.removeAt(0)
            return id
        }
    }

    /** For tests: how many notices are kept, sliding-out ones included. */
    internal fun keptCount(): Int = synchronized(active) { active.size }

    /** For tests: how many notices are still on screen and not on their way out. */
    internal fun liveCount(): Int = synchronized(active) { active.count { !it.retiring } }

    /** Brings a notice's slide-out forward, without cutting the animation itself short. */
    private fun retireEarly(notice: Notice) {
        val soonest = System.currentTimeMillis() + SLIDE_MS
        if (notice.retireAtMs > soonest) notice.retireAtMs = soonest
        notice.retiring = true
    }

    /** Called every frame from the HUD hook. */
    @JvmStatic
    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        val mc = Minecraft.getInstance()
        val now = System.currentTimeMillis()
        // Pruned before the early return, so expired notices leave even while nothing is drawn.
        synchronized(active) { active.removeAll { now >= it.retireAtMs } }
        // The config menu draws them over itself instead, so its test notice can be seen.
        if (mc.gui.screen() is net.alpaka.addons.client.AlpakaConfigScreen) return
        if (mc.gui.hud.isHidden() || mc.level == null) return
        draw(graphics, now)
    }

    /**
     * The notices over a screen rather than under it, for the config menu: there the HUD is hidden
     * behind the menu, and Send Test Notification has to show something. Works without a world too.
     */
    @JvmStatic
    fun renderOverScreen(graphics: GuiGraphicsExtractor) {
        val now = System.currentTimeMillis()
        synchronized(active) { active.removeAll { now >= it.retireAtMs } }
        graphics.nextStratum()
        draw(graphics, now)
    }

    private fun draw(graphics: GuiGraphicsExtractor, now: Long) {
        val mc = Minecraft.getInstance()

        val font = mc.font ?: return
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
        var stackEdge = when {
            atBottom -> (screenHeight - MARGIN).toFloat()
            topCenter -> topCenterTop(mc).toFloat()
            else -> MARGIN.toFloat()
        }
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
        val blurred = AlpakaConfig.instance.blurOn(AlpakaConfig.instance.notificationBlur)

        if (blurred) {
            // The panel fades as a whole, blur included, rather than through its tint alone.
            HudPanel.submit(graphics, x, y, x + WIDTH, y + height, 1.0f, BLUR_TINT, shown)
        } else {
            drawPanel(graphics, x, y, height, shown)
        }

        // Flat, as the config menu writes: a notice always has a box under it, blurred or not, and
        // the box gives the contrast a shadow would. Only where the extractor cannot take either
        // box does the text keep its shadow over the bare world.
        val shadow = HudPanel.textShadow(graphics, true)
        val textX = x + PAD
        val title = HudPanel.text(Component.literal(notice.title), AlpakaConfig.instance.smoothOn(AlpakaConfig.instance.notificationSmoothFont))
        graphics.text(font, title, textX, y + PAD, faded(accent, shown), shadow)
        for (line in notice.body.indices) {
            graphics.text(font, notice.body[line], textX, y + PAD + LINE + line * LINE, faded(BODY_TEXT, shown), shadow)
        }

        // The time left, as a hairline along the bottom edge that runs down from full while the
        // notice waits: full while sliding in, empty once the slide out begins. Inset on either side
        // so it ends before the panel's rounded corners, and thinning towards its tip. The track is
        // a rounded rod, so its ends are soft rather than cut off square.
        val barY = y + height - BAR
        val barLeft = x + BAR_INSET
        val barWidth = WIDTH - 2 * BAR_INSET
        ModernGuiUtils.drawRoundedRect(graphics, barLeft, barY, barWidth, BAR, 1, faded(BAR_TRACK, shown))
        val remaining = ((notice.retireAtMs - SLIDE_MS - now).toFloat() / notice.holdMs.toFloat()).coerceIn(0.0f, 1.0f)
        val filled = (barWidth * remaining).roundToInt()
        if (filled > 0) {
            val step = max(1, filled / BAR_STEPS)
            var from = barLeft
            var piece = 0
            while (from < barLeft + filled) {
                val to = min(from + step, barLeft + filled)
                // From full strength at the root to a little over half at the tip.
                val alpha = 0xFF - (0xFF - 0x8C) * piece / (BAR_STEPS - 1).coerceAtLeast(1)
                graphics.fill(from, barY, to, y + height, faded(withAlpha(accent, alpha), shown))
                from = to
                piece = min(piece + 1, BAR_STEPS - 1)
            }
        }
    }

    /**
     * The plain panel, for when the blurred one is off: a dark rounded box with a soft shadow, the
     * same shape as the blurred panel. Both fade with the notice, so the box arrives with its text.
     */
    private fun drawPanel(graphics: GuiGraphicsExtractor, x: Int, y: Int, height: Int, shown: Float) {
        ModernGuiUtils.drawPanelShadow(graphics, x, y, WIDTH, height, HudPanel.RADIUS, shown)
        ModernGuiUtils.drawRoundedRect(graphics, x, y, WIDTH, height, HudPanel.RADIUS, faded(PANEL, shown))
    }
}
