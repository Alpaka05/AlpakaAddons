package net.alpaka.addons.client.gui;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Keeps typed characters coming to the mod's own screens.
 *
 * Since 26.3 the window only reports typed characters while text input is switched on. Vanilla
 * switches it on when one of its text fields takes the focus and off again when that field loses
 * it or the screen changes. The mod's text fields ({@link EditableText}) are drawn and focused by
 * their screens rather than being vanilla's, so nothing switched it on for them: the keys still
 * arrived, but no letters. Some of these screens take a character without any field focused, too
 * (typing starts a search, "/" is a hotkey), as they could before 26.3, when characters always
 * came.
 *
 * So text input is on for as long as such a screen ({@link TakesTypedText}) is open. It is
 * switched on when the screen is set up and looked at again each tick, because a vanilla text
 * field on the same screen (an item list mod's search box in the Auction House, say) switches it
 * off when it loses the focus.
 */
public final class TypedTextInput {
    private TypedTextInput() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> keepOn(client, screen));
        ClientTickEvents.END_CLIENT_TICK.register(client -> keepOn(client, client.gui.screen()));
    }

    private static void keepOn(Minecraft client, Screen screen) {
        if (screen instanceof TakesTypedText) {
            // Does nothing while it is on already.
            client.textInputManager().startTextInput(screen);
        }
    }
}
