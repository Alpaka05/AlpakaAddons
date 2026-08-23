package net.alpaka.addons.features.inventoryhud

import net.alpaka.addons.client.hud.HudBounds
import net.alpaka.addons.client.hud.HudElement
import net.alpaka.addons.config.AlpakaConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Editor handle for the inventory HUD. See [InventoryHudRenderer] for the drawing itself.
 *
 * While "attach to hotbar" is on the position is derived from the screen rather than the config, so
 * dragging would have nothing to write to. Rather than silently ignoring the drag, dragging turns
 * the attachment off and hands control back to the stored coordinates - which is what someone
 * dragging the box is asking for anyway.
 */
object InventoryHudElement : HudElement {

    /** Scroll travel per 0.1x of scale, matching the feel of the other resizable HUDs. */
    private const val SCALE_PER_NOTCH = 0.1

    override val id: String = "inventory_hud"
    override val name: String = "Inventory HUD"

    override val isFeatureEnabled: Boolean
        get() = AlpakaConfig.instance.inventoryHudEnabled

    override var anchorX: Int
        get() = bounds().x0
        set(value) {
            detachFromHotbar()
            AlpakaConfig.instance.inventoryHudX = value
        }

    override var anchorY: Int
        get() = bounds().y0
        set(value) {
            detachFromHotbar()
            AlpakaConfig.instance.inventoryHudY = value
        }

    /**
     * Moving the box only means something once it is free of the hotbar, so the first drag releases
     * it - seeded from where it currently sits, so it does not jump on the first pixel of movement.
     */
    private fun detachFromHotbar() {
        val cfg = AlpakaConfig.instance
        if (!cfg.inventoryHudAttachToHotbar) return
        val current = InventoryHudRenderer.footprint(cfg, Minecraft.getInstance())
        cfg.inventoryHudX = current.x0
        cfg.inventoryHudY = current.y0
        cfg.inventoryHudAttachToHotbar = false
    }

    override fun bounds(): HudBounds =
        InventoryHudRenderer.footprint(AlpakaConfig.instance, Minecraft.getInstance())

    override fun adjustScale(notches: Double) {
        val cfg = AlpakaConfig.instance
        val next = cfg.inventoryHudScale + (notches * SCALE_PER_NOTCH).toFloat()
        cfg.inventoryHudScale = (Math.round(next * 100.0f) / 100.0f)
            .coerceIn(InventoryHudRenderer.MIN_SCALE, InventoryHudRenderer.MAX_SCALE)
    }

    override fun reset() {
        val cfg = AlpakaConfig.instance
        cfg.inventoryHudX = InventoryHudRenderer.DEFAULT_X
        cfg.inventoryHudY = InventoryHudRenderer.DEFAULT_Y
        cfg.inventoryHudScale = InventoryHudRenderer.DEFAULT_SCALE
        cfg.inventoryHudAttachToHotbar = true
    }

    override fun render(graphics: GuiGraphicsExtractor) {
        // Always drawn fully open in the editor: the slide is a runtime affordance, and a
        // half-open box would be impossible to position against.
        val box = bounds()
        InventoryHudRenderer.drawPanel(graphics, box.x0, box.y0, AlpakaConfig.instance.inventoryHudScale, 1.0f)
    }

    override fun scaleValue(): Float = AlpakaConfig.instance.inventoryHudScale

    override fun scaleLabel(): String = String.format("%.2fx", AlpakaConfig.instance.inventoryHudScale)
}
