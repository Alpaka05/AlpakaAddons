package net.alpaka.addons.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;

public class AlpakaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("alpakaconfig")
                .executes(context -> {
                    MinecraftClient.getInstance().send(() -> {
                        MinecraftClient.getInstance().setScreen(new AlpakaConfigScreen(null));
                    });
                    return 1;
                })
            );
        });
    }
}
