package net.alpaka.addons.client.hud

import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Absolute, screen-space box an element occupies. [x1] and [y1] are exclusive, matching the
 * convention of [GuiGraphicsExtractor.fill].
 */
data class HudBounds(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
    val width: Int get() = x1 - x0
    val height: Int get() = y1 - y0

    fun contains(mouseX: Double, mouseY: Double): Boolean =
        mouseX >= x0 && mouseX < x1 && mouseY >= y0 && mouseY < y1
}

/**
 * A HUD overlay that can be repositioned and resized in [HudEditorScreen].
 *
 * Implementations are the bridge between one feature's config fields and the editor, which knows
 * nothing about individual HUDs: it drags whatever [bounds] reports, resizes through
 * [adjustScale], and draws through [render]. Adding a HUD to the editor therefore means writing
 * one of these and listing it in [HudRegistry] - no editor changes.
 *
 * Everything here reads and writes `AlpakaConfig.instance` live. Persisting is the editor's job,
 * which batches a whole drag into a single save instead of rewriting the file every frame.
 */
interface HudElement {

    /** Stable identifier. Not persisted; used for logging and equality in the editor. */
    val id: String

    /** Human-readable name, drawn as the element's label in the editor. */
    val name: String

    /**
     * Whether the feature that owns this HUD is switched on.
     *
     * Disabled elements still appear in the editor - greyed out - so their position can be set up
     * before the feature is ever turned on.
     */
    val isFeatureEnabled: Boolean

    /**
     * The element's reference point, in screen coordinates, as stored in the config. What the
     * point means is up to the implementation - a text HUD's top-left corner, an avatar's feet -
     * so the editor only ever offsets it by a mouse delta and never assumes a corner.
     */
    var anchorX: Int
    var anchorY: Int

    /** The box the element currently covers, derived from its config values. */
    fun bounds(): HudBounds

    /**
     * Resizes the element, clamped to its own limits. [notches] is raw scroll-wheel travel - one
     * click of a wheel is `1.0`, and trackpads report fractions - so implementations decide what a
     * notch is worth and whether a partial one does anything.
     */
    fun adjustScale(notches: Double)

    /** Restores this element's default position and size. */
    fun reset()

    /** Draws the element exactly as it appears in-game, at its configured position and size. */
    fun render(graphics: GuiGraphicsExtractor)

    /** Current size. Used for change detection, so it must reflect what [adjustScale] writes. */
    fun scaleValue(): Float

    /** Current size formatted for the status line, e.g. `30x` or `1.25x`. */
    fun scaleLabel(): String

    /**
     * The box this element actually occupies, nudged so it lies fully on screen.
     *
     * Positions are stored as absolute pixels, so raising Minecraft's GUI scale shrinks the screen
     * out from under them and an element configured near an edge ends up past it - invisible in
     * game and unreachable in the editor, with no way back except lowering the scale again.
     *
     * The nudge is applied when drawing and hit-testing only; the stored position is left alone, so
     * returning to the old GUI scale puts everything exactly back where it was.
     */
    fun visibleBounds(screenWidth: Int, screenHeight: Int): HudBounds {
        val box = bounds()
        val dx = clampShift(box.x0, box.x1, screenWidth)
        val dy = clampShift(box.y0, box.y1, screenHeight)
        if (dx == 0 && dy == 0) return box
        return HudBounds(box.x0 + dx, box.y0 + dy, box.x1 + dx, box.y1 + dy)
    }

    /** Anchor X shifted by the same amount as [visibleBounds]. Never written back to the config. */
    fun visibleAnchorX(screenWidth: Int, screenHeight: Int): Int {
        val box = bounds()
        return anchorX + clampShift(box.x0, box.x1, screenWidth)
    }

    /** Anchor Y shifted by the same amount as [visibleBounds]. Never written back to the config. */
    fun visibleAnchorY(screenWidth: Int, screenHeight: Int): Int {
        val box = bounds()
        return anchorY + clampShift(box.y0, box.y1, screenHeight)
    }
}

/**
 * How far a span must move to sit inside `0..limit`.
 *
 * An element larger than the screen cannot fit either way, so it is pinned to the near edge -
 * showing its start beats showing its middle with both ends cut off.
 */
private fun clampShift(low: Int, high: Int, limit: Int): Int = when {
    high - low >= limit -> -low
    low < 0 -> -low
    high > limit -> limit - high
    else -> 0
}
