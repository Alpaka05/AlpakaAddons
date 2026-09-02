package net.alpaka.addons.features.wheel

import com.mojang.blaze3d.platform.InputConstants
import net.alpaka.addons.client.AlpakaKeyCategory
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.lwjgl.glfw.GLFW

object CommandWheelFeature {
    @JvmField
    var COMMAND_WHEEL_KEY: KeyMapping? = null

    data class WheelItem(val displayName: String, val command: String, val iconItem: Item) {
        fun iconStack(): ItemStack = ItemStack(iconItem)
    }

    @JvmField
    val ITEMS: MutableList<WheelItem> = mutableListOf(
        WheelItem("Hub", "/warp hub", Items.EMERALD),
        WheelItem("Island", "/warp is", Items.GRASS_BLOCK),
        WheelItem("Garden", "/warp garden", Items.GOLDEN_HOE),
        WheelItem("Dungeons", "/warp dungeon_hub", Items.WITHER_SKELETON_SKULL),
        WheelItem("Blaze", "/warp smoldering", Items.BLAZE_ROD),
        WheelItem("Crimson Isle", "/warp isle", Items.NETHERRACK),
        WheelItem("The Barn", "/warp barn", Items.HAY_BLOCK),
        WheelItem("Bayou", "/warp bayou", Items.LILY_PAD),
        WheelItem("Dwarven Mines", "/warp mines", Items.DIAMOND_PICKAXE),
        WheelItem("Crystal Nucleus", "/warp nucleus", Items.PRISMARINE_CRYSTALS)
    )

    @JvmStatic
    fun register() {
        COMMAND_WHEEL_KEY = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.alpaka.command_wheel",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                AlpakaKeyCategory.CATEGORY
            )
        )

        ClientTickEvents.END_CLIENT_TICK.register { client ->
            val key = COMMAND_WHEEL_KEY ?: return@register
            if (key.isDown && client.gui.screen() == null && client.player != null) {
                client.gui.setScreen(CommandWheelScreen())
            }
        }
    }
}
