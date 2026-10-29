package net.alpaka.addons.features.leapmenu

import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.features.dungeonmap.DungeonParty
import net.alpaka.addons.utils.DungeonState
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ResolvableProfile

/** The five dungeon classes, in Hypixel's colors. */
enum class DungeonClass(val label: String, val color: Int) {
    ARCHER("Archer", 0xFFFFAA00.toInt()),
    BERSERK("Berserk", 0xFFD83A3A.toInt()),
    HEALER("Healer", 0xFFFF55FF.toInt()),
    MAGE("Mage", 0xFF55FFFF.toInt()),
    TANK("Tank", 0xFF2EB82E.toInt());

    companion object {
        fun of(name: String): DungeonClass? = entries.firstOrNull { it.label.equals(name, ignoreCase = true) }
    }
}

/** One teammate's head in the Spirit Leap menu, and the slot that leaps to them. */
data class LeapTarget(val slot: Int, val name: String, val profile: ResolvableProfile?, val dead: Boolean)

/**
 * The Leap Menu: Hypixel's Spirit Leap chest, drawn by [LeapMenuScreen] as one button per teammate.
 *
 * The chest stays Hypixel's own; a click on a button is the click on that teammate's head, sent
 * through Outbound.menuClick. Classes come from the tab list, remembered for the run, because the
 * tab list shows a dead player as `(DEAD)` instead of their class.
 */
object LeapMenu {
    /** Spirit Leap and Infinileap open the same menu; older versions called it Teleport to Player. */
    private val TITLES = setOf("Spirit Leap", "Teleport to Player")
    // "Alpaka05", "[MVP+] Alpaka05", "§bAlpaka05"
    private val HEAD_NAME = Regex("""^(?:\[[^\]]+] )*([A-Za-z0-9_]{1,16})$""")

    private val classes = HashMap<String, DungeonClass>()
    private var ticks = 0

    @JvmStatic
    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { client -> tick(client) }
    }

    private fun tick(client: Minecraft) {
        if (!DungeonState.inDungeon()) {
            if (classes.isNotEmpty()) classes.clear()
            return
        }
        if (AlpakaConfig.instance.leapMenuEnabled && ++ticks % 20 == 0) remember(client)
    }

    private fun remember(client: Minecraft) {
        for (member in DungeonParty.read(client)) {
            DungeonClass.of(member.dungeonClass)?.let { classes[member.name.lowercase()] = it }
        }
    }

    fun classOf(name: String): DungeonClass? = classes[name.lowercase()]

    /** For the screenshot tour, which has no tab list to read classes from. */
    fun rememberClass(name: String, dungeonClass: DungeonClass) {
        classes[name.lowercase()] = dungeonClass
    }

    fun isLeapTitle(title: String) = SkyblockUtils.cleanColor(title).trim() in TITLES

    /** The player name on a head in the menu, without rank or colors, or null if it isn't one. */
    fun headName(hoverName: String): String? =
        HEAD_NAME.matchEntire(SkyblockUtils.cleanColor(hoverName).trim())?.groupValues?.get(1)

    /** Whether a head's lore says that player can't be leaped to right now. */
    fun unavailable(lore: List<String>): Boolean = lore.any {
        val line = SkyblockUtils.cleanColor(it).lowercase()
        "dead" in line || "offline" in line
    }

    /** The teammates' heads in the chest part of [menu], in slot order. */
    fun targets(menu: ChestMenu): List<LeapTarget> {
        val result = ArrayList<LeapTarget>(4)
        for (slot in 0 until menu.rowCount * 9) {
            val stack = menu.getSlot(slot).item
            if (!stack.`is`(Items.PLAYER_HEAD)) continue
            val name = headName(stack.hoverName.string) ?: continue
            val lore = stack.get(DataComponents.LORE)?.lines()?.map { it.string }.orEmpty()
            result += LeapTarget(slot, name, stack.get(DataComponents.PROFILE), unavailable(lore))
        }
        return result
    }

    /**
     * Opens Hypixel's Spirit Leap chest in the Leap Menu instead of the vanilla chest screen. The
     * chest itself is made exactly as vanilla makes it; only the screen showing it differs.
     */
    @JvmStatic
    fun tryOpen(type: MenuType<*>, mc: Minecraft, containerId: Int, title: Component): Boolean {
        val player = mc.player ?: return false
        if (!AlpakaConfig.instance.leapMenuEnabled || !SkyblockUtils.isOnSkyblock() || !isChest(type)) return false
        if (!isLeapTitle(title.string)) return false
        val menu = type.create(containerId, player.inventory) as? ChestMenu ?: return false
        // The class of anyone who died since the last read is still known from before.
        remember(mc)
        player.containerMenu = menu
        mc.gui.setScreen(LeapMenuScreen(menu, player.inventory, title))
        return true
    }

    private fun isChest(type: MenuType<*>) = type == MenuType.GENERIC_9x1 || type == MenuType.GENERIC_9x2 ||
        type == MenuType.GENERIC_9x3 || type == MenuType.GENERIC_9x4 || type == MenuType.GENERIC_9x5 ||
        type == MenuType.GENERIC_9x6
}
