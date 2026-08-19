package net.alpaka.addons.client;

import net.alpaka.addons.client.hud.HudEditorScreen;
import net.alpaka.addons.features.critters.PangolinHighlightFeature;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.alpaka.addons.features.wheel.CommandWheelFeature;
import net.alpaka.addons.features.zoom.ZoomFeature;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;

import net.alpaka.addons.features.worldage.WorldAgeHudRenderer;

public class AlpakaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        CustomSoundFeature.register();
        SlayerDropTracker.registerEvents();
        ZoomFeature.register();
        CommandWheelFeature.register();
        WorldAgeHudRenderer.registerEvents();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("alpakaconfig")
                .executes(context -> {
                    Minecraft.getInstance().execute(() -> {
                        Minecraft.getInstance().setScreen(new AlpakaConfigScreen(null));
                    });
                    return 1;
                })
            );

            dispatcher.register(ClientCommands.literal("alpaka")
                .executes(context -> {
                    Minecraft.getInstance().execute(() -> {
                        Minecraft.getInstance().setScreen(new AlpakaConfigScreen(null));
                    });
                    return 1;
                })
            );

            dispatcher.register(ClientCommands.literal("alpakaddons")
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

            dispatcher.register(ClientCommands.literal("alpakahud")
                .executes(context -> {
                    Minecraft.getInstance().execute(() -> {
                        Minecraft.getInstance().setScreen(new HudEditorScreen(null));
                    });
                    return 1;
                })
            );

            dispatcher.register(ClientCommands.literal("alpakadebug")
                .executes(context -> {
                    Minecraft.getInstance().execute(PangolinHighlightFeature::printDiagnostics);
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

            // --- Viewmodel Preset Command ---
            dispatcher.register(ClientCommands.literal("alpakapreset")
                .then(ClientCommands.argument("number", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 3))
                    .executes(context -> {
                        int num = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "number");
                        Minecraft.getInstance().execute(() -> {
                            net.alpaka.addons.config.AlpakaConfig.instance.loadPreset(num - 1);
                            SlayerDropTracker.sendModMessage("§aSwitched to Viewmodel Preset " + num + ".");
                            try { CustomSoundFeature.playButtonClickSound(); } catch (Throwable ignored) {}
                        });
                        return 1;
                    })
                )
                .executes(context -> {
                    Minecraft.getInstance().execute(() -> {
                        SlayerDropTracker.sendModMessage("§7Current preset: §ePreset " + (net.alpaka.addons.config.AlpakaConfig.instance.activeItemPresetIndex + 1) + "§7. Usage: §6/alpakapreset <1|2|3>");
                    });
                    return 1;
                })
            );
        });
    }
}
