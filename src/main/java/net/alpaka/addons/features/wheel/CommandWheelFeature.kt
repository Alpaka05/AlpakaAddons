package net.alpaka.addons.features.wheel

import com.mojang.blaze3d.platform.InputConstants
import net.alpaka.addons.client.AlpakaKeyCategory
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW

object CommandWheelFeature {
    @JvmField
    var COMMAND_WHEEL_KEY: KeyMapping? = null

    @JvmStatic
    fun register() {
        COMMAND_WHEEL_KEY = KeyMappingHelper.registerKeyMapping(
            KeyMapping(
                "key.alpaka.command_wheel",
                InputConstants.Type.KEYSYM,
                // Unbound for fresh installs: V is Skyblocker's item protection. A key set in
                // options.txt stays as it is.
                GLFW.GLFW_KEY_UNKNOWN,
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
