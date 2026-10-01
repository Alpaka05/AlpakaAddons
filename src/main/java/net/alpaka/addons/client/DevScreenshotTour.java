package net.alpaka.addons.client;

import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.client.hud.HudEditorScreen;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.mainmenu.CustomMainMenuScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A development check, never active for players: walks through the mod's screens and a throwaway
 * world, saves a screenshot at each stop to {@code run/screenshots/}, and quits.
 *
 * Switched on by the environment variable {@code ALPAKA_SCREENSHOT_TOUR} when starting the dev
 * client, so a layout or rendering change can be looked at without anyone at the keyboard:
 *
 *     ALPAKA_SCREENSHOT_TOUR=1 ./gradlew runClient
 *
 * The dev window opens at 854 by 480, which Auto GUI scale turns into 427 by 240 - tighter than the
 * most common real size (480 by 270), so a screen that fits here fits there. The world is a flat
 * creative one in {@code run/saves/alpaka-tour}, made anew on every tour.
 * Settings the tour turns on to show something are changed in memory only and never saved.
 */
public final class DevScreenshotTour {
    private static final String ENV = "ALPAKA_SCREENSHOT_TOUR";
    private static final String WORLD = "alpaka-tour";

    /**
     * One stop: something to do, a condition to wait for, how many ticks to let it settle, and the
     * name of the screenshot to take then - or null for a stop that only sets up the next one.
     */
    private record Stop(String shot, Runnable action, BooleanSupplier ready, int settleTicks) {}

    private static Stop screen(String shot, java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> screen) {
        return new Stop(shot, () -> Minecraft.getInstance().gui.setScreen(screen.get()), () -> true, 30);
    }

    private static final List<Stop> STOPS = List.of(
            screen("main-menu", CustomMainMenuScreen::new),
            screen("config", () -> new AlpakaConfigScreen(null, "")),
            screen("color-picker", () -> new ColorPickerScreen(null, "Accent Colour", 0xFF29B6B2, color -> {})),
            screen("wheel-editor", () -> new CommandWheelConfigScreen(new CustomMainMenuScreen())),
            screen("hud-editor", () -> new HudEditorScreen(null)),
            new Stop(null, DevScreenshotTour::enterWorld,
                    () -> Minecraft.getInstance().player != null && Minecraft.getInstance().gui.screen() == null, 100),
            new Stop("world-hud", DevScreenshotTour::showHuds, () -> true, 30),
            new Stop("hud-editor-world", () -> Minecraft.getInstance().gui.setScreen(new HudEditorScreen(null)), () -> true, 20),
            new Stop(null, () -> Minecraft.getInstance().gui.setScreen(null), () -> true, 5),
            // Two ticks after a burst: the lines are mid-slide inside the chat's box.
            new Stop("chat-slide", DevScreenshotTour::chatBurst, () -> true, 2),
            new Stop("chat-rest", () -> {}, () -> true, 40),
            new Stop("chat-open", () -> Minecraft.getInstance().gui.setScreen(new ChatScreen("", false)), () -> true, 10),
            new Stop("third-person", DevScreenshotTour::thirdPerson, () -> true, 40)
    );

    private static int ticks = 0;
    private static int stop = -1;
    private static boolean done = false;

    private DevScreenshotTour() {}

    public static void register() {
        if (System.getenv(ENV) == null) return;
        AlpakaAddons.LOGGER.info("Screenshot tour on: {} stops", STOPS.size());
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (stop < 0) {
            if (ticks >= 100) begin(mc, 0);
            return;
        }
        if (stop >= STOPS.size()) {
            // A while after the last picture, so its file has been written.
            if (!done && ticks >= 20) {
                done = true;
                AlpakaAddons.LOGGER.info("Screenshot tour done");
                mc.stop();
            }
            return;
        }

        Stop current = STOPS.get(stop);
        if (!current.ready().getAsBoolean()) {
            ticks = 0;
            return;
        }
        if (ticks < current.settleTicks()) return;

        if (current.shot() != null) {
            String name = "tour-" + current.shot() + ".png";
            Screenshot.grab(mc.gameDirectory, name, mc.gameRenderer.mainRenderTarget(), 1,
                    message -> AlpakaAddons.LOGGER.info("Screenshot tour: {}", message.getString()));
        }
        begin(mc, stop + 1);
    }

    private static void begin(Minecraft mc, int index) {
        stop = index;
        ticks = 0;
        if (index < STOPS.size()) STOPS.get(index).action().run();
    }

    /**
     * Creates a fresh flat creative world for the tour. Made anew each time: reopening one stops at
     * a confirmation screen that nobody is there to click.
     */
    private static void enterWorld() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(null);
        java.nio.file.Path old = mc.getLevelSource().getBaseDir().resolve(WORLD);
        if (java.nio.file.Files.exists(old)) {
            try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(old)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            } catch (java.io.IOException e) {
                AlpakaAddons.LOGGER.warn("Screenshot tour: could not delete the old tour world", e);
            }
        }
        LevelSettings settings = new LevelSettings("Alpaka Tour", GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0L, false, false),
                WorldPresets::createTestWorldDimensions, new CustomMainMenuScreen());
    }

    /** Every HUD back at its default place, and the inventory HUD on and attached to the hotbar. */
    private static void showHuds() {
        for (net.alpaka.addons.client.hud.HudElement element : net.alpaka.addons.client.hud.HudRegistry.ELEMENTS) {
            element.reset();
        }
        AlpakaConfig cfg = AlpakaConfig.instance;
        cfg.inventoryHudEnabled = true;
        cfg.inventoryHudAttachToHotbar = true;
        cfg.inventoryHudAlwaysVisible = true;
        cfg.worldAgeHudEnabled = true;
    }

    /** A burst of lines, as a busy chat delivers them. */
    private static void chatBurst() {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 1; i <= 6; i++) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§7Tour line " + i + " §8- a message arriving in a burst"));
        }
    }

    /** Third person with the custom name tag, so the camera glide's end and the tag are visible. */
    private static void thirdPerson() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(null);
        AlpakaConfig cfg = AlpakaConfig.instance;
        cfg.customNameTagEnabled = true;
        cfg.nameTagBackgroundEnabled = true;
        cfg.nameTagBackgroundOpacity = 60;
        cfg.nameTagChromaBorderEnabled = true;
        cfg.renderHandInThirdPerson = false;
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }
}
