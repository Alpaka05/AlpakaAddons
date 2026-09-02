package net.alpaka.addons.features.inventoryhud

import com.mojang.blaze3d.platform.InputConstants
import net.alpaka.addons.client.AlpakaKeyCategory
import net.alpaka.addons.config.AlpakaConfig
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.world.item.Item
import org.lwjgl.glfw.GLFW

/**
 * Decides when the inventory HUD is on screen, and animates it sliding up out of the hotbar.
 *
 * Three inputs feed one number, [openAmount]:
 *  - the "always visible" setting,
 *  - a brief auto-open when the inventory contents change, if that setting is on,
 *  - and the keybind, which flips whichever of those is currently the case.
 *
 * The keybind flipping rather than forcing is what makes one key sensible in both configurations:
 * with "always visible" on it hides the HUD, with it off it reveals the HUD, and either way pressing
 * again returns to normal. A manual hide also survives an item pickup, instead of being undone by it.
 *
 * Change detection compares snapshots the client already has. No inventory is read from the server,
 * nothing is opened, and no input is synthesised.
 */
object InventoryHudFeature {

    /** How long an item change keeps the HUD up, when that option is on. */
    private const val PEEK_DURATION_MS = 2_500L

    /** Slide speed, in open-fraction per second. 6.0 is a brisk ~170ms sweep. */
    private const val SLIDE_SPEED = 6.0f

    /**
     * Ceiling on how much time one frame may advance the slide.
     *
     * The renderer stops calling in whenever the HUD is not being drawn - behind F1, behind a menu,
     * with the feature off - so the gap since the last call can be arbitrarily long. Without this
     * the HUD would jump straight to fully open on reappearing instead of sliding.
     */
    private const val MAX_FRAME_MS = 100L

    private const val MAIN_SLOTS = 27
    private const val FIRST_SLOT = 9

    @JvmField
    var TOGGLE_KEY: KeyMapping? = null

    /** Flipped by the keybind; inverts whatever the settings would otherwise do. */
    private var inverted = false

    private var lastChangeMs = 0L
    private var openAmount = 0.0f
    private var lastFrameMs = System.currentTimeMillis()

    // Last seen contents of the 27 main slots. Item identity plus count is enough to notice a
    // pickup, a drop, or a stack being used up.
    private val lastItems = arrayOfNulls<Item>(MAIN_SLOTS)
    private val lastCounts = IntArray(MAIN_SLOTS)
    private var snapshotValid = false

    @JvmStatic
    fun register() {
        TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.alpaka.inventory_hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                AlpakaKeyCategory.CATEGORY
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val key = TOGGLE_KEY ?: return@register
            // consumeClick drains one press per tick, so holding the key does not strobe the toggle.
            var pressed = false
            while (key.consumeClick()) pressed = true
            if (pressed && client.screen == null) inverted = !inverted

            trackInventoryChanges(client)
        }
    }

    /**
     * Notices a change in the main inventory. Only matters when the peek option is on, but the
     * snapshot is kept current regardless so switching the option on cannot fire on a stale diff.
     */
    private fun trackInventoryChanges(client: Minecraft) {
        val player = client.player
        if (player == null) {
            snapshotValid = false
            return
        }

        val inventory = player.inventory
        var changed = false
        for (i in 0 until MAIN_SLOTS) {
            val slot = FIRST_SLOT + i
            if (slot >= inventory.containerSize) continue
            val stack = inventory.getItem(slot)
            val item = if (stack.isEmpty) null else stack.item
            val count = if (stack.isEmpty) 0 else stack.count
            if (lastItems[i] !== item || lastCounts[i] != count) {
                changed = true
                lastItems[i] = item
                lastCounts[i] = count
            }
        }

        // The very first pass fills the snapshot from empty, which is not a real change.
        if (changed && snapshotValid) lastChangeMs = System.currentTimeMillis()
        snapshotValid = true
    }

    /** True while a recent item change should be holding the HUD open. */
    private fun peeking(): Boolean =
        lastChangeMs != 0L && System.currentTimeMillis() - lastChangeMs < PEEK_DURATION_MS

    /**
     * How far open the HUD is, 0..1, advanced from the wall clock so the slide runs at the same
     * speed regardless of frame rate.
     */
    @JvmStatic
    fun openAmount(): Float {
        val now = System.currentTimeMillis()
        val dt = (now - lastFrameMs).coerceAtMost(MAX_FRAME_MS) / 1000.0f
        lastFrameMs = now

        val cfg = AlpakaConfig.instance
        val base = cfg.inventoryHudEnabled &&
            (cfg.inventoryHudAlwaysVisible || (cfg.inventoryHudShowOnItemChange && peeking()))
        val target = if (base != inverted && cfg.inventoryHudEnabled) 1.0f else 0.0f

        openAmount = if (openAmount < target) {
            (openAmount + dt * SLIDE_SPEED).coerceAtMost(target)
        } else {
            (openAmount - dt * SLIDE_SPEED).coerceAtLeast(target)
        }
        return openAmount
    }

    /** Drops the manual flip and any pending peek. Used when leaving a world. */
    @JvmStatic
    fun reset() {
        inverted = false
        lastChangeMs = 0L
        openAmount = 0.0f
        snapshotValid = false
    }
}
