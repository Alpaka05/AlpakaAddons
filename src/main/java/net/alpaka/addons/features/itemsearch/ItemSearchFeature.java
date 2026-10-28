package net.alpaka.addons.features.itemsearch;

import com.mojang.blaze3d.platform.InputConstants;
import net.alpaka.addons.client.AlpakaKeyBinds;
import net.alpaka.addons.client.AlpakaKeyCategory;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.alpaka.addons.mixin.AbstractContainerScreenAccessor;
import net.alpaka.addons.utils.SkyblockUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

/**
 * Item Search: a window with a search bar over everything the player owns - the inventory, the Ender
 * Chest, backpacks, sacks, the wardrobe, the Accessory Bag, the Personal Vault and the chests on
 * their island. Opened with its key, {@code /alpakasearch [query]}, or the button in its settings;
 * the key pressed over an item in a menu searches for that item.
 *
 * Hypixel sends a container's items only while it is open, so each place is known from the last
 * time the player opened it ({@link ItemSearchTracker}). Clicking a result opens where it lies with
 * Hypixel's own command, once per click, and outlines it there ({@link ItemSearchHighlight}).
 */
public final class ItemSearchFeature {
    private ItemSearchFeature() {}

    public static KeyMapping KEY;

    public static void register() {
        KEY = AlpakaKeyBinds.register(new KeyMapping("key.alpaka.item_search", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_UNKNOWN, AlpakaKeyCategory.CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ItemSearchStore.tick();
            while (KEY.consumeClick()) {
                if (client.gui.screen() == null) open(null, null);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ItemSearchStore.flush());

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?> container)) return;
            ScreenEvents.remove(screen).register(s -> ItemSearchTracker.onMenuClosed(s.getTitle(), container.getMenu()));
            ScreenEvents.afterExtract(screen).register((s, graphics, mx, my, delta) -> ItemSearchHighlight.drawSlots(container, graphics));
            // In a menu the key searches for the item under the mouse.
            ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> !KEY.matches(event) || !keyPressed(s));
            ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !KEY.matchesMouse(event) || !keyPressed(s));
        });

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide()) ItemSearchTracker.onUseBlock(hit.getBlockPos(), level.getBlockState(hit.getBlockPos()));
            return InteractionResult.PASS;
        });

        // Cancelled too: chat mods hide the "[Sacks]" line, and the amounts should follow it anyway.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) ItemSearchTracker.onChat(message);
        });
        ClientReceiveMessageEvents.GAME_CANCELED.register((message, overlay) -> {
            if (!overlay) ItemSearchTracker.onChat(message);
        });

        LevelRenderEvents.COLLECT_SUBMITS.register(ItemSearchHighlight::drawChests);
    }

    /** The key in a menu: searches for the hovered item. True when it did, so the menu does not get the key too. */
    private static boolean keyPressed(Screen screen) {
        if (!AlpakaConfig.instance.itemSearchEnabled) return false;
        if (screen.getFocused() instanceof EditBox box && box.canConsumeInput()) return false;
        if (!(screen instanceof AbstractContainerScreen<?> container)) return false;
        Slot slot = ((AbstractContainerScreenAccessor) container).alpaka$hoveredSlot();
        String query = slot == null || !slot.hasItem() ? null : SkyblockUtils.cleanColor(slot.getItem().getHoverName().getString()).trim();
        open(query, screen);
        return true;
    }

    /**
     * Opens the search window, with the query typed in when there is one. Opened from a menu, it
     * goes back to that menu when closed: the menu stays open on the server meanwhile, and closing
     * it is the player's to do.
     */
    public static void open(String query, Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        if (!AlpakaConfig.instance.itemSearchEnabled) {
            SlayerDropTracker.sendModMessage("§7Item Search is switched off in the settings.");
            return;
        }
        if (mc.player == null || mc.level == null) return;
        if (!SkyblockUtils.isOnSkyblock()) {
            SlayerDropTracker.sendModMessage("§cItem Search only works on SkyBlock.");
            return;
        }
        mc.execute(() -> mc.gui.setScreen(new ItemSearchScreen(query, parent)));
    }
}
