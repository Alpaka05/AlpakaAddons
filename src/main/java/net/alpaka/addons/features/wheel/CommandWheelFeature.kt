package net.alpaka.addons.features.wheel

import com.mojang.blaze3d.platform.InputConstants
import net.alpaka.addons.client.AlpakaKeyCategory
import net.alpaka.addons.config.AlpakaConfig
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
            // Drained in both modes, so a press from hold mode can't open the wheel after a switch.
            var pressed = false
            while (key.consumeClick()) pressed = true
            val open = opens(pressed, AlpakaConfig.instance.commandWheelToggleMode, key.isDown)
            if (open && client.gui.screen() == null && client.player != null) {
                client.gui.setScreen(CommandWheelScreen(keyHeld = key.isDown))
            }
        }
    }

    /**
     * Whether a tick opens the wheel: only on a fresh press, in hold mode too, and there only while
     * the key is still down.
     *
     * Not on the key merely being down. Closing the wheel by a click, right-click or Escape while the
     * key is still held grabs the mouse, and outside macOS that sets every held key down again
     * (KeyMapping.setAll): the wheel came straight back, and letting go then ran a command nobody
     * aimed at. setAll counts no click, so [pressed] only ever comes from a real press.
     */
    internal fun opens(pressed: Boolean, toggleMode: Boolean, keyDown: Boolean): Boolean =
        pressed && (toggleMode || keyDown)
}
