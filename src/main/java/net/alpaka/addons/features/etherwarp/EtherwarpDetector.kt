package net.alpaka.addons.features.etherwarp

import net.alpaka.addons.utils.SkyblockUtils
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.ItemStack

/**
 * Detects when the player is lining up a Hypixel Skyblock Etherwarp teleport, so features that
 * draw on the targeted block can stay out of the way of the teleport-target indicators other
 * mods render.
 *
 * Etherwarp is granted by the Etherwarp Conduit itself, and by an Aspect of the End or Aspect
 * of the Void that has the Conduit merged into it. All three advertise it in their tooltip as
 * "Ability: Ether Transmission", so that is what we match on - it covers every item carrying
 * the ability without hardcoding Hypixel item ids, and keeps working if the item pool grows.
 *
 * Strictly read-only: this inspects the player's own held item and sneak state, both of which
 * the client already has locally. Nothing is sent, timed, or automated.
 */
object EtherwarpDetector {

    /**
     * Tooltip fragments that identify the ability. "ether transmission" is the current ability
     * name; "etherwarp" also catches the Conduit's own item name and any wording drift.
     */
    private val ABILITY_MARKERS = arrayOf("ether transmission", "etherwarp")

    // Reading a tooltip allocates a String per line, so the verdict is cached against the stack
    // instance it was derived from. The held stack is only swapped out when the inventory
    // actually changes, so in practice this scans once per item switch rather than per frame.
    private var cachedStack: ItemStack? = null
    private var cachedResult = false

    /**
     * True while the player is sneaking and holding an Etherwarp-capable item - the input
     * combination that aims an Etherwarp teleport.
     */
    @JvmStatic
    fun isAimingEtherwarp(): Boolean {
        val player = Minecraft.getInstance().player ?: return false

        // Cheapest gate first: a shared-flag read that is false on the vast majority of frames.
        if (!player.isShiftKeyDown) return false
        if (!holdsEtherwarpItem(player.mainHandItem)) return false

        // Deliberately last - isOnSkyblock() does a scoreboard lookup plus a regex strip, so it
        // only runs once the far cheaper checks have already passed.
        return SkyblockUtils.isOnSkyblock()
    }

    private fun holdsEtherwarpItem(stack: ItemStack): Boolean {
        if (stack === cachedStack) return cachedResult

        val result = advertisesEtherwarp(stack)
        cachedStack = stack
        cachedResult = result
        return result
    }

    private fun advertisesEtherwarp(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false

        if (matchesMarker(stack.hoverName.string)) return true

        val lore = stack.get(DataComponents.LORE) ?: return false
        for (line in lore.lines()) {
            if (matchesMarker(line.string)) return true
        }
        return false
    }

    private fun matchesMarker(text: String): Boolean =
        ABILITY_MARKERS.any { text.contains(it, ignoreCase = true) }
}
