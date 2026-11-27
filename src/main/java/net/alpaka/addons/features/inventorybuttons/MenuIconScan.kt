package net.alpaka.addons.features.inventorybuttons

import net.alpaka.addons.AlpakaAddons
import net.alpaka.addons.config.AlpakaConfig
import net.alpaka.addons.config.AtomicJsonFile
import net.alpaka.addons.utils.ItemData
import net.alpaka.addons.utils.SkyblockUtils
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.io.IOException
import java.util.IdentityHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Learns the heads of Hypixel's menus for [MenuIcons]: while a menu is open on SkyBlock, every head
 * in it that is no SkyBlock item is remembered under its name. It only reads what the server
 * already sent; nothing is asked for or clicked.
 *
 * What was learned is kept in config/alpaka-menu-icons.json, read and written off the game's threads.
 */
internal object MenuIconScan {
    /** How many ticks lie between two looks at an open menu; its heads can arrive after it opened. */
    private const val EVERY_TICKS = 5
    /** How long a change waits for others before the file is written. */
    private const val SAVE_DELAY_MS = 10_000L

    private var ticks = 0
    private var menu: AbstractContainerMenu? = null
    /** The stacks of [menu] already looked at; Hypixel swaps a stack for a new one when it changes. */
    private val looked = IdentityHashMap<ItemStack, Unit>()

    private val saveQueued = AtomicBoolean()
    private val files = Executors.newSingleThreadExecutor { r ->
        Thread(r, "Alpaka menu icons").apply { isDaemon = true }
    }

    private fun file() = FabricLoader.getInstance().configDir.resolve("alpaka-menu-icons.json").toFile()

    /** Reads what earlier sessions learned, in the background. */
    fun load() {
        files.execute {
            try {
                AtomicJsonFile.read(file())?.let { MenuIcons.read(it) }
            } catch (e: IOException) {
                AlpakaAddons.LOGGER.debug("Could not read the menu icons: {}", e.toString())
            } catch (e: RuntimeException) {
                // A broken file: the heads are learned again and the next write replaces it.
                AlpakaAddons.LOGGER.debug("Could not read the menu icons: {}", e.toString())
            }
        }
    }

    fun tick(client: Minecraft) {
        if (!AlpakaConfig.instance.inventoryButtonsEnabled) return
        val screen = client.gui.screen()
        if (screen !is AbstractContainerScreen<*> || screen is InventoryScreen || screen is CreativeModeInventoryScreen) {
            if (menu != null) {
                menu = null
                looked.clear()
            }
            return
        }
        if (++ticks % EVERY_TICKS != 0) return
        if (!SkyblockUtils.isOnSkyblock()) return
        val open = screen.menu
        if (open !== menu) {
            menu = open
            looked.clear()
        }
        val now = System.currentTimeMillis()
        var news = false
        for (slot in open.slots) {
            if (slot.container is Inventory) continue
            val stack = slot.item
            if (!stack.`is`(Items.PLAYER_HEAD) || looked.put(stack, Unit) != null) continue
            if (see(stack, now)) news = true
        }
        if (news) saveSoon()
    }

    /** Remembers one head of a menu, unless it is an item of the player's or shows a player. */
    private fun see(stack: ItemStack, now: Long): Boolean {
        if (ItemData.id(stack).isNotEmpty()) return false
        val profile = stack.get(DataComponents.PROFILE) ?: return false
        val name = SkyblockUtils.cleanColor(stack.hoverName.string)
        if (MenuIcons.showsPlayer(profile.name().orElse(null), name)) return false
        for (property in profile.partialProfile().properties().get("textures")) {
            val hash = MenuIcons.hashOf(property.value()) ?: continue
            return MenuIcons.see(hash, name, now)
        }
        return false
    }

    private fun saveSoon() {
        if (!saveQueued.compareAndSet(false, true)) return
        CompletableFuture.delayedExecutor(SAVE_DELAY_MS, TimeUnit.MILLISECONDS, files).execute {
            saveQueued.set(false)
            try {
                AtomicJsonFile.write(file(), MenuIcons.json())
            } catch (e: IOException) {
                AlpakaAddons.LOGGER.debug("Could not keep the menu icons: {}", e.toString())
            } catch (e: RuntimeException) {
                AlpakaAddons.LOGGER.debug("Could not keep the menu icons: {}", e.toString())
            }
        }
    }
}
