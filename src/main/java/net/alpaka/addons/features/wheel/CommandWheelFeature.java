package net.alpaka.addons.features.wheel;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class CommandWheelFeature {
    public static KeyMapping COMMAND_WHEEL_KEY;

    public record WheelItem(String displayName, String command) {}

    public static final List<WheelItem> ITEMS = new ArrayList<>();

    static {
        ITEMS.add(new WheelItem("Hub", "/warp hub"));
        ITEMS.add(new WheelItem("Private Island", "/warp is"));
        ITEMS.add(new WheelItem("Dungeon Hub", "/warp dungeon_hub"));
        ITEMS.add(new WheelItem("Smoldering Tomb", "/warp smoldering"));
        ITEMS.add(new WheelItem("Crimson Isle", "/warp isle"));
        ITEMS.add(new WheelItem("The Barn", "/warp barn"));
        ITEMS.add(new WheelItem("Backwoods Bayou", "/warp bayou"));
        ITEMS.add(new WheelItem("Dwarven Mines", "/warp mines"));
        ITEMS.add(new WheelItem("Crystal Nucleus", "/warp nucleus"));
    }

    public static void register() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.parse("alpaka:addons"));
        COMMAND_WHEEL_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.alpaka.command_wheel",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (COMMAND_WHEEL_KEY.isDown() && client.screen == null && client.player != null) {
                client.setScreen(new CommandWheelScreen());
            }
        });
    }
}
