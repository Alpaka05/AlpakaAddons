package net.alpaka.addons.features.itemvalue;

import com.mojang.blaze3d.platform.InputConstants;
import net.alpaka.addons.client.AlpakaKeyCategory;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.market.MarketScreen;
import net.alpaka.addons.mixin.AbstractContainerScreenAccessor;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/**
 * The Item Value key: pressed over an item in any menu - the inventory, a chest, the Auction House -
 * it opens {@link ItemValuePopup} on that item. Pressed again over the same item, or over nothing,
 * it closes it; over another item it moves to that one.
 *
 * The key is unbound until the player picks one under Options > Controls, in the mod's own section.
 * It is read from the menu's own key presses, since a key mapping hears nothing while a screen is
 * open, so a key bound to a mouse button works through the menu's clicks the same way. Typing into a
 * menu's text field, a search or an anvil, never opens the popup.
 *
 * Nothing here sends anything to the server: the popup only reads the item it was opened on.
 */
public final class ItemValueFeature {
    private ItemValueFeature() {}

    public static KeyMapping KEY;

    public static void register() {
        KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.alpaka.item_value", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, AlpakaKeyCategory.CATEGORY));
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
                if (KEY.matches(event)) return !keyPressed(s);
                // Escape closes the popup, and only the popup.
                if (event.isEscape() && ItemValuePopup.isOpenIn(s)) {
                    ItemValuePopup.close();
                    return false;
                }
                return true;
            });
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> {
                if (KEY.matchesMouse(event)) return !keyPressed(s);
                return !ItemValuePopup.mouseClicked(s, event.x(), event.y(), event.button());
            });
            ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> !ItemValuePopup.mouseReleased(event.button()));
            ScreenMouseEvents.allowMouseDrag(screen).register((s, event, dx, dy) -> !ItemValuePopup.mouseDragged(event.button()));
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, h, v) -> !ItemValuePopup.mouseScrolled(s, mx, my, v));
            ScreenEvents.remove(screen).register(ItemValuePopup::screenRemoved);
        });
    }

    /** Whether a key repeating is the Item Value key held down in a menu, which acts once per press only. */
    public static boolean swallowsRepeat(net.minecraft.client.input.KeyEvent event) {
        if (KEY == null || !AlpakaConfig.instance.itemValueEnabled || !KEY.matches(event)) return false;
        Screen screen = Minecraft.getInstance().gui.screen();
        return screen instanceof AbstractContainerScreen<?> && !(screen.getFocused() instanceof EditBox box && box.canConsumeInput());
    }

    /** The key in a menu. True when it opened, moved or closed the popup, so the menu does not get it too. */
    static boolean keyPressed(Screen screen) {
        if (!AlpakaConfig.instance.itemValueEnabled) return false;
        if (screen.getFocused() instanceof EditBox box && box.canConsumeInput()) return false;
        ItemStack hovered = hovered(screen);
        if (hovered.isEmpty() || ItemValuePopup.shows(screen, hovered)) {
            if (!ItemValuePopup.isOpen()) return false;
            ItemValuePopup.close();
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        ItemValuePopup.open(screen, hovered, mc.mouseHandler.getScaledXPos(mc.getWindow()), mc.mouseHandler.getScaledYPos(mc.getWindow()));
        return true;
    }

    /** For the screenshot tour: the key pressed in the open menu, with the mouse where it is. */
    public static void devPress() {
        Screen screen = Minecraft.getInstance().gui.screen();
        if (screen != null) keyPressed(screen);
    }

    /**
     * The item under the mouse: the card or slot the market screens draw there, or the slot of a
     * vanilla menu. Empty while the mouse is over the popup itself, which hides the slots from the menu.
     */
    static ItemStack hovered(Screen screen) {
        if (screen instanceof MarketScreen market && !market.hoveredItem().isEmpty()) return market.hoveredItem();
        if (screen instanceof AbstractContainerScreen<?> container) {
            Slot slot = ((AbstractContainerScreenAccessor) container).alpaka$hoveredSlot();
            if (slot != null && slot.hasItem()) return slot.getItem();
        }
        return ItemStack.EMPTY;
    }
}
