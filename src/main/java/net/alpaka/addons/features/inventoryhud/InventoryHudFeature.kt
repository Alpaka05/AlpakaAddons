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
 * Decides when the inventory HUD is on screen, and animates it fading in above the hotbar when it
 * is attached there; a freely placed panel switches on and off without a fade.
 *
 * Three inputs feed one number, [openAmount]:
 *  - the "always visible" setting,
 *  - a brief auto-open when the inventory contents change, if that setting is on,
 *  - and the keybind, which flips the standing state that "always visible" sets.
 *
 * The keybind flipping rather than forcing is what makes one key sensible in both configurations:
 * with "always visible" on it hides the HUD, with it off it reveals the HUD, and either way pressing
 * again returns to normal. While flipped, item changes leave the HUD alone: a manual hide survives
 * an item pickup, and a manual reveal is not hidden by one.
 *
 * The same comparison makes an incoming item pop in its slot, the way the hotbar does it. Vanilla
 * only starts that pop for the hotbar when it is playing on a server, so the HUD keeps its own pop
 * timer per slot. The pop waits until the panel is fully open: with "show on item change" the
 * panel is still fading in when the item lands, and a pop played out during the fade is lost in it.
 *
 * Change detection compares snapshots the client already has. No inventory is read from the server,
 * nothing is opened, and no input is synthesised.
 */
object InventoryHudFeature {

    /** How long an item change keeps the HUD up, when that option is on. */
    private const val PEEK_DURATION_MS = 2_500L

    /**
     * Fade speed, in open-fraction per second: 4.0 is a 250ms fade. The renderer eases it, so it
     * starts and ends gently rather than at full speed.
     */
    private const val FADE_SPEED = 4.0f

    /**
     * Ceiling on how much time one frame may advance the fade.
     *
     * The renderer stops calling in whenever the HUD is not being drawn - behind F1, behind a menu,
     * with the feature off - so the gap since the last call can be arbitrarily long. Without this
     * the HUD would jump straight to fully open on reappearing instead of fading in.
     */
    private const val MAX_FRAME_MS = 100L

    private const val MAIN_SLOTS = 27
    private const val FIRST_SLOT = 9

    /** Length of a pop, in ticks: what vanilla gives a stack picked up into the hotbar. */
    const val POP_TICKS = 5

    /**
     * How far open the panel has to be before a pending pop starts playing: all the way. Even a
     * panel most of the way in is still moving and brightening, and the pop drowns in that.
     */
    private const val POP_OPEN_THRESHOLD = 1.0f

    /**
     * Ticks after a new player entity appears in which inventory changes are not news. Joining a
     * world or switching Hypixel lobby builds a fresh player whose inventory the server fills in a
     * moment later; without this, every item would peek and pop as if it had just been picked up.
     */
    private const val RESYNC_TICKS = 20

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

    /** The player the snapshot belongs to, and how many ticks of resync grace are left for it. */
    private var lastPlayer: Any? = null
    private var resyncTicks = 0

    /**
     * Pop ticks left per main slot. A slot whose item just arrived starts at [POP_TICKS] and counts
     * down only once the panel is fully open.
     */
    private val popTicks = IntArray(MAIN_SLOTS)

    @JvmStatic
    fun register() {
        TOGGLE_KEY = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.alpaka.inventory_hud",
                InputConstants.Type.KEYSYM,
                // Unbound for fresh installs: H is Skyblocker's slot lock. A key set in options.txt
                // stays as it is.
                GLFW.GLFW_KEY_UNKNOWN,
                AlpakaKeyCategory.CATEGORY
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val key = TOGGLE_KEY ?: return@register
            // consumeClick drains one press per tick, so holding the key does not strobe the toggle.
            var pressed = false
            while (key.consumeClick()) pressed = true
            if (pressed && client.gui.screen() == null) inverted = !inverted

            trackInventoryChanges(client)
            advancePops()
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
            lastPlayer = null
            return
        }
        if (player !== lastPlayer) {
            lastPlayer = player
            resyncTicks = RESYNC_TICKS
            popTicks.fill(0)
        }
        // The very first pass fills the snapshot from empty, which is not a real change, and
        // neither is the server filling in a freshly built player.
        val news = snapshotValid && resyncTicks == 0
        if (resyncTicks > 0) resyncTicks--

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
                // Something arrived: a new item in the slot, or more of the one already there.
                // Taking items out does not pop, as in the hotbar.
                if (news && item != null && (lastItems[i] !== item || lastCounts[i] < count)) {
                    popTicks[i] = POP_TICKS
                }
                lastItems[i] = item
                lastCounts[i] = count
            }
        }

        if (changed && news) lastChangeMs = System.currentTimeMillis()
        snapshotValid = true
    }

    /**
     * Counts pending pops down once the panel is fully open. A panel that is closed
     * and staying closed drops them, so they do not all go off the next time it is opened.
     */
    private fun advancePops() {
        if (openAmount >= POP_OPEN_THRESHOLD) {
            for (i in 0 until MAIN_SLOTS) if (popTicks[i] > 0) popTicks[i]--
        } else if (openAmount <= 0.0f && targetOpen() == 0.0f) {
            popTicks.fill(0)
        }
    }

    /**
     * How far through its pop the item in a main-inventory slot is, in ticks still to go, with
     * [partialTick] taken off for smooth motion between ticks: vanilla's popTime minus partial tick.
     * Zero or less means no pop.
     */
    @JvmStatic
    fun popTime(mainSlot: Int, partialTick: Float): Float {
        if (mainSlot !in 0 until MAIN_SLOTS) return 0.0f
        val ticks = popTicks[mainSlot]
        if (ticks <= 0) return 0.0f
        // A pop still waiting for the panel to open holds at its start instead of creeping in.
        return if (openAmount >= POP_OPEN_THRESHOLD) ticks - partialTick else ticks.toFloat()
    }

    /** True while a recent item change should be holding the HUD open. */
    private fun peeking(): Boolean =
        lastChangeMs != 0L && System.currentTimeMillis() - lastChangeMs < PEEK_DURATION_MS

    /**
     * How far open the HUD is, 0..1, advanced from the wall clock so the fade runs at the same
     * speed regardless of frame rate. Linear; the renderer applies the easing.
     */
    @JvmStatic
    fun openAmount(): Float {
        val now = System.currentTimeMillis()
        val dt = (now - lastFrameMs).coerceAtMost(MAX_FRAME_MS) / 1000.0f
        lastFrameMs = now

        val cfg = AlpakaConfig.instance
        val target = targetOpen()

        // The fade belongs to the panel's place above the hotbar. A freely placed panel simply
        // appears and disappears.
        if (!cfg.inventoryHudAttachToHotbar) {
            openAmount = target
            return openAmount
        }

        openAmount = if (openAmount < target) {
            (openAmount + dt * FADE_SPEED).coerceAtMost(target)
        } else {
            (openAmount - dt * FADE_SPEED).coerceAtLeast(target)
        }
        return openAmount
    }

    /** Where the fade is heading: 1 when the settings, a peek or the keybind want the HUD up. */
    private fun targetOpen(): Float {
        val cfg = AlpakaConfig.instance
        if (!cfg.inventoryHudEnabled) return 0.0f
        // The keybind flips the standing state only. Flipping the peek along with it would turn
        // "show on item change" inside out once the key had been pressed: up all the time, and
        // gone exactly when an item arrives.
        val shown = if (inverted) {
            !cfg.inventoryHudAlwaysVisible
        } else {
            cfg.inventoryHudAlwaysVisible || (cfg.inventoryHudShowOnItemChange && peeking())
        }
        return if (shown) 1.0f else 0.0f
    }

    /** Drops the manual flip and any pending peek. Used when leaving a world. */
    @JvmStatic
    fun reset() {
        inverted = false
        lastChangeMs = 0L
        openAmount = 0.0f
        snapshotValid = false
        lastPlayer = null
        popTicks.fill(0)
    }
}
