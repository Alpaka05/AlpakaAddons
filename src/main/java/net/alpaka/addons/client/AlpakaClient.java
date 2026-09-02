package net.alpaka.addons.client;

import net.alpaka.addons.client.hud.HudEditorScreen;
import net.alpaka.addons.features.inventoryhud.InventoryHudFeature;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.alpaka.addons.utils.AlpakaDiagnostics;
import net.alpaka.addons.features.sound.CustomSoundFeature;
import net.alpaka.addons.features.wheel.CommandWheelFeature;
import net.alpaka.addons.features.zoom.ZoomFeature;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.alpaka.addons.config.AlpakaConfig;
import net.minecraft.client.Minecraft;

import net.alpaka.addons.features.worldage.WorldAgeHudRenderer;

public class AlpakaClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // First, so the overlay's own render pipeline is registered before the shader manager
        // precompiles vanilla's; see BlockOverlayRenderTypes.
        net.alpaka.addons.features.blockoverlay.BlockOverlayRenderTypes.init();
        CustomSoundFeature.register();
        SlayerDropTracker.registerEvents();
        net.alpaka.addons.features.slayer.SkyblockProfileTracker.INSTANCE.register();
        ZoomFeature.register();
        CommandWheelFeature.register();
        InventoryHudFeature.register();
        net.alpaka.addons.features.notification.MentionNotifier.register();
        WorldAgeHudRenderer.registerEvents();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            // Every alias takes an optional search term: "/aa" opens the config as before, and
            // "/aa snow" opens it with that already typed into the search box. greedyString is what
            // lets the term contain spaces, since it swallows the rest of the line.
            for (String alias : new String[]{"alpakaconfig", "alpaka", "alpakaddons", "aa"}) {
                dispatcher.register(ClientCommands.literal(alias)
                    .executes(context -> openConfig(""))
                    .then(ClientCommands.argument("search", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(context -> openConfig(
                                com.mojang.brigadier.arguments.StringArgumentType.getString(context, "search"))))
                );
            }

            // Registered on their own rather than folded into the alias loop above: that loop is
            // specifically the aliases that open the config screen with an optional search term.
            // These two open something else, which is how they came to be dropped when the loop was
            // introduced - they simply were not on the alias list.

            /** Opens the HUD editor. Also reachable from the edit buttons in the config. */
            dispatcher.register(ClientCommands.literal("alpakahud")
                .executes(context -> {
                    Minecraft.getInstance().execute(() -> {
                        Minecraft.getInstance().gui.setScreen(new HudEditorScreen(null));
                    });
                    return 1;
                })
            );

            /** Prints what the mod currently detects - Skyblock, island, sidebar - into chat. */
            dispatcher.register(ClientCommands.literal("alpakadebug")
                .executes(context -> {
                    Minecraft.getInstance().execute(AlpakaDiagnostics::print);
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
                // The overview only lists each slayer's headline RNG drop; naming a slayer here
                // prints its full drop history. Declared after the "reset" literal below matters
                // not at all - brigadier tries literals before arguments, so "reset" still lands
                // on the literal rather than being read as a slayer name.
                .then(ClientCommands.argument("slayer", com.mojang.brigadier.arguments.StringArgumentType.word())
                    .executes(context -> {
                        String raw = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "slayer");
                        Minecraft.getInstance().execute(() -> {
                            net.alpaka.addons.features.slayer.SlayerType type =
                                    net.alpaka.addons.features.slayer.SlayerType.fromUserInput(raw);
                            if (type == null) {
                                SlayerDropTracker.sendModMessage("§cUnknown slayer '" + raw + "'. Try: §7"
                                        + net.alpaka.addons.features.slayer.SlayerType.userInputNames());
                            } else {
                                SlayerDropTracker.printDropsFor(Minecraft.getInstance().player, type);
                            }
                        });
                        return 1;
                    })
                )
                // Clears the live session stats behind the slayer HUD. Lifetime kill and drop
                // history is untouched - that is the persisted record, not part of a session.
                .then(ClientCommands.literal("reset")
                    .executes(context -> {
                        Minecraft.getInstance().execute(() -> {
                            net.alpaka.addons.features.slayer.SlayerType cleared =
                                    net.alpaka.addons.features.slayer.SlayerSessionTracker.INSTANCE.resetCurrent();
                            if (cleared != null) {
                                SlayerDropTracker.sendModMessage("§7Reset the §e" + cleared.display + "§7 slayer session.");
                            } else {
                                SlayerDropTracker.sendModMessage("§7Reset all slayer sessions.");
                            }
                        });
                        return 1;
                    })
                    // Personal bests are all-time and survive a session reset, so clearing one needs
                    // its own command. Kills and drop history are deliberately left alone.
                    .then(ClientCommands.literal("pb")
                        .executes(context -> {
                            Minecraft.getInstance().execute(() -> clearPersonalBests(null, "all slayers"));
                            return 1;
                        })
                        .then(ClientCommands.argument("slayer", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .executes(context -> {
                                String raw = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "slayer");
                                Minecraft.getInstance().execute(() -> {
                                    net.alpaka.addons.features.slayer.SlayerType type =
                                            net.alpaka.addons.features.slayer.SlayerType.fromUserInput(raw);
                                    if (type == null) {
                                        SlayerDropTracker.sendModMessage("§cUnknown slayer '" + raw + "'. Try: §7"
                                                + net.alpaka.addons.features.slayer.SlayerType.userInputNames());
                                    } else {
                                        clearPersonalBests(type, type.display);
                                    }
                                });
                                return 1;
                            })
                        )
                    )
                )
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

            // Where the slayer record is kept. A command rather than a field in the config screen
            // because the value is a filesystem path: too long for a card that has to fit a label
            // beside it, and normally pasted rather than typed.
            dispatcher.register(ClientCommands.literal("alpakastats")
                .executes(context -> {
                    Minecraft.getInstance().execute(AlpakaClient::printStatsLocation);
                    return 1;
                })
                .then(ClientCommands.literal("folder")
                    .then(ClientCommands.literal("default")
                        .executes(context -> {
                            Minecraft.getInstance().execute(() -> setStatsDirectory(""));
                            return 1;
                        })
                    )
                    .then(ClientCommands.argument("path", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(context -> {
                            String path = com.mojang.brigadier.arguments.StringArgumentType.getString(context, "path");
                            Minecraft.getInstance().execute(() -> setStatsDirectory(path));
                            return 1;
                        })
                    )
                )
            );
        });
    }

    /** Reports where the slayer record lives and whether it is actually there. */
    private static void printStatsLocation() {
        java.io.File file = net.alpaka.addons.config.AlpakaStats.file();
        boolean custom = !AlpakaConfig.instance.statsDirectory.isBlank();

        SlayerDropTracker.sendModMessage("§7Slayer stats: §f" + file.getAbsolutePath());
        SlayerDropTracker.sendModMessage(custom
                ? "§8  custom folder - /alpakastats folder default to go back to the shared one"
                : "§8  shared default - /alpakastats folder <path> to point it at a synced folder");
        SlayerDropTracker.sendModMessage(file.exists()
                ? "§8  file present (" + file.length() + " bytes)"
                : "§8  no file yet; it is written on the next kill");
    }

    /**
     * Points the record at another folder and moves the current one into it.
     *
     * Saved through rather than only remembered, so the change takes effect without a restart: the
     * record in memory is written to the new location immediately, which is also what creates it.
     */
    private static void setStatsDirectory(String path) {
        AlpakaConfig.instance.statsDirectory = path == null ? "" : path.trim();
        AlpakaConfig.save();

        java.io.File dir = net.alpaka.addons.config.AlpakaStats.directory();
        if (!dir.exists() && !dir.mkdirs()) {
            SlayerDropTracker.sendModMessage("§cCould not create §f" + dir.getAbsolutePath());
            SlayerDropTracker.sendModMessage("§7The setting was kept; check the path and try again.");
            return;
        }

        // Merges with whatever is already there rather than replacing it, so pointing a second PC
        // at a folder that already holds a record adds to it instead of flattening it.
        net.alpaka.addons.config.AlpakaStats.save();
        printStatsLocation();
    }

    /** Clears recorded best boss times and reports what was actually removed. */
    private static void clearPersonalBests(net.alpaka.addons.features.slayer.SlayerType type, String label) {
        int cleared = net.alpaka.addons.features.slayer.SlayerTimer.INSTANCE.clearPersonalBest(type);
        if (cleared > 0) {
            SlayerDropTracker.sendModMessage("§7Cleared the personal best for §e" + label + "§7.");
        } else {
            SlayerDropTracker.sendModMessage("§7No personal best recorded for §e" + label + "§7.");
        }
    }

    /** Opens the config screen, optionally with a search term already applied. */
    private static int openConfig(String search) {
        Minecraft.getInstance().execute(() ->
                Minecraft.getInstance().gui.setScreen(new AlpakaConfigScreen(null, search)));
        return 1;
    }
}
