package net.alpaka.addons.features.wheel;

import com.mojang.blaze3d.platform.InputConstants;
import net.alpaka.addons.client.AlpakaKeyCategory;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class CommandWheelFeature {
    public static KeyMapping COMMAND_WHEEL_KEY;

    public record WheelItem(String displayName, String command, Item iconItem) {
        public ItemStack iconStack() {
            return new ItemStack(iconItem);
        }
    }

    public static final List<WheelItem> ITEMS = new ArrayList<>();

    static {
        ITEMS.add(new WheelItem("Hub", "/warp hub", Items.EMERALD));
        ITEMS.add(new WheelItem("Island", "/warp is", Items.GRASS_BLOCK));
        ITEMS.add(new WheelItem("Garden", "/warp garden", Items.GOLDEN_HOE));
        ITEMS.add(new WheelItem("Dungeons", "/warp dungeon_hub", Items.WITHER_SKELETON_SKULL));
        ITEMS.add(new WheelItem("Blaze", "/warp smoldering", Items.BLAZE_ROD));
        ITEMS.add(new WheelItem("Crimson Isle", "/warp isle", Items.NETHERRACK));
        ITEMS.add(new WheelItem("The Barn", "/warp barn", Items.HAY_BLOCK));
        ITEMS.add(new WheelItem("Bayou", "/warp bayou", Items.LILY_PAD));
        ITEMS.add(new WheelItem("Dwarven Mines", "/warp mines", Items.DIAMOND_PICKAXE));
        ITEMS.add(new WheelItem("Crystal Nucleus", "/warp nucleus", Items.PRISMARINE_CRYSTALS));
    }

    public static void register() {
        COMMAND_WHEEL_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.alpaka.command_wheel",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                AlpakaKeyCategory.CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (COMMAND_WHEEL_KEY.isDown() && client.screen == null && client.player != null) {
                client.setScreen(new CommandWheelScreen());
            }
        });
    }
}
