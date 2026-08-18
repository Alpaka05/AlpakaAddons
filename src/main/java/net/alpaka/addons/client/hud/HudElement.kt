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
}
