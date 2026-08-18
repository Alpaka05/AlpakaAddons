package net.alpaka.addons.client.hud

import net.alpaka.addons.features.playermodel.PlayerModelHudElement
import net.alpaka.addons.features.worldage.WorldAgeHudElement

/**
 * The single list of HUDs that [HudEditorScreen] can position.
 *
 * To make a new HUD editable, implement [HudElement] next to its renderer and add it here. Order
 * matters only for overlap: later entries are treated as being on top.
 */
object HudRegistry {

    @JvmField
    val ELEMENTS: List<HudElement> = listOf(
        WorldAgeHudElement,
        PlayerModelHudElement
    )

    /**
     * The element under the cursor, or null. Searches back to front so that when two boxes
     * overlap, the one drawn on top is the one you grab.
     */
    fun topmostAt(mouseX: Double, mouseY: Double): HudElement? =
        ELEMENTS.lastOrNull { it.bounds().contains(mouseX, mouseY) }
}
