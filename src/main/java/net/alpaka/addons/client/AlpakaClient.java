package net.alpaka.addons.client;

import net.alpaka.addons.features.darkmode.DarkModeSkyblockFeature;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.alpaka.addons.features.wheel.CommandWheelFeature;
import net.alpaka.addons.features.zoom.ZoomFeature;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;

public class AlpakaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        DarkModeSkyblockFeature.register();
        CustomSoundFeature.register();
        SlayerDropTracker.registerEvents();
        ZoomFeature.register();
        CommandWheelFeature.register();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("alpakaconfig")
                .executes(context -> {
                    Minecraft.getInstance().execute(() -> {
                        Minecraft.getInstance().setScreen(new AlpakaConfigScreen(null));
                    });
                    return 1;
                })
            );

            dispatcher.register(ClientCommands.literal("aa")
                .executes(context -> {
                    Minecraft.getInstance().execute(() -> {
                        Minecraft.getInstance().setScreen(new AlpakaConfigScreen(null));
                    });
                    return 1;
                })
            );

            dispatcher.register(ClientCommands.literal("alpakaslayer")
                .executes(context -> {
                    Minecraft.getInstance().execute(() -> {
                        SlayerDropTracker.printKills(Minecraft.getInstance().player);
                    });
                    return 1;
                })
            );
        });
    }
}
