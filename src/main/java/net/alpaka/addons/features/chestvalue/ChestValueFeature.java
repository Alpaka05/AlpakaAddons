package net.alpaka.addons.features.chestvalue;

import com.mojang.blaze3d.platform.InputConstants;
import net.alpaka.addons.client.AlpakaKeyCategory;
import net.alpaka.addons.config.AlpakaConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * The Chest Value key: pressed in any menu - a chest, a backpack, the Ender Chest, the inventory -
 * it shows {@link ChestValuePanel} beside it, and pressed again hides it. The choice sticks: the
 * panel comes back with every menu opened after, and after a restart, until the key is pressed again.
 * Pressed outside a menu it switches the same way and says so above the hotbar.
 *
 * Like the Item Value key it is unbound until the player picks one under Options > Controls, and is
 * read from the menu's own key presses, so a mouse button works too and typing into a search never
 * switches it. Nothing here sends anything to the server: the panel only reads the menu's slots.
 */
public final class ChestValueFeature {
    private ChestValueFeature() {}

    public static KeyMapping KEY;

    public static void register() {
        KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.alpaka.chest_value", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, AlpakaKeyCategory.CATEGORY));
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> !KEY.matches(event) || !keyPressed(s));
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (KEY.matchesMouse(event)) return !keyPressed(s);
                return !ChestValuePanel.mouseClicked(s, event.x(), event.y(), event.button());
            });
            ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> !ChestValuePanel.mouseReleased(event.button()));
            ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dx, dy) -> !ChestValuePanel.mouseDragged(event.button()));
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, h, v) -> !ChestValuePanel.mouseScrolled(s, mx, my, v));
            ScreenEvents.remove(screen).register(ChestValuePanel::screenRemoved);
        });
        // Outside a menu the key is an ordinary key mapping.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (KEY.consumeClick()) {
                if (!AlpakaConfig.instance.chestValueEnabled || client.gui.screen() != null) continue;
                toggle();
                client.gui.hud.setOverlayMessage(Component.literal("Chest Value: ")
                        .append(AlpakaConfig.instance.chestValueShown
                                ? Component.literal("Shown in menus").withStyle(ChatFormatting.GREEN)
                                : Component.literal("Hidden").withStyle(ChatFormatting.RED)), false);
            }
        });
    }

    /** Whether a key repeating is the Chest Value key held down in a menu, which acts once per press only. */
    public static boolean swallowsRepeat(net.minecraft.client.input.KeyEvent event) {
        if (KEY == null || !AlpakaConfig.instance.chestValueEnabled || !KEY.matches(event)) return false;
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen instanceof AbstractContainerScreen<?> && !(screen.getFocused() instanceof EditBox box && box.canConsumeInput());
    }

    /** The key in a menu. True when it showed or hid the panel, so the menu does not get it too. */
    static boolean keyPressed(Screen screen) {
        if (!AlpakaConfig.instance.chestValueEnabled) return false;
        if (screen.getFocused() instanceof EditBox box && box.canConsumeInput()) return false;
        toggle();
        return true;
    }

    /** Shows or hides the panel, from now on in every menu. */
    static void toggle() {
        AlpakaConfig.instance.chestValueShown = !AlpakaConfig.instance.chestValueShown;
        AlpakaConfig.save();
        ChestValuePanel.toggled(AlpakaConfig.instance.chestValueShown);
    }

    /** For the screenshot tour: shows the panel. */
    public static void devShow() {
        if (!AlpakaConfig.instance.chestValueShown) toggle();
    }

    /** For the screenshot tour: hides it again, so the tour's config is left as it was. */
    public static void devHide() {
        if (AlpakaConfig.instance.chestValueShown) toggle();
    }
}
