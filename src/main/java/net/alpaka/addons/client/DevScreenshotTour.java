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
 * Any other value runs only the stops whose picture name starts with it, e.g. {@code config}.
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
            // With the mouse in the top-right corner, so the avatar visibly looks up and towards it.
            new Stop("main-menu", () -> {
                Minecraft.getInstance().gui.setScreen(new CustomMainMenuScreen());
                placeMouse(0.95, 0.05);
            }, () -> true, 30),
            // The mouse moved down to the left, over the card: the avatar turns to look at it.
            new Stop("main-menu-look-left", () -> placeMouse(0.2, 0.75), () -> true, 30),
            new Stop("look-far-left", () -> placeMouse(0.0, 0.5), () -> true, 30),
            // A moment into the entrance: the card springing in, the avatar half materialised.
            new Stop("main-menu-opening", () -> Minecraft.getInstance().gui.setScreen(new CustomMainMenuScreen()), () -> true, 9),
            screen("config", () -> new AlpakaConfigScreen(null, "")),
            screen("config-chat", () -> AlpakaConfigScreen.openOn("Chat", "Chat Tabs")),
            screen("config-damage-tags", () -> AlpakaConfigScreen.openOn("Cosmetics", "Custom Damage Tags")),
            screen("config-search", () -> new AlpakaConfigScreen(null, "blaze")),
            screen("config-inventory-hud", () -> AlpakaConfigScreen.openOn("Interface", "Inventory HUD")),
            screen("config-hud-style", () -> AlpakaConfigScreen.openOn("Design", "HUD Style")),
            screen("config-screenshot", () -> AlpakaConfigScreen.openOn("Chat", "Better Screenshots")),
            screen("config-slayer-hud", () -> AlpakaConfigScreen.openOn("Slayer", "Slayer Session HUD")),
            screen("config-slayer", () -> AlpakaConfigScreen.openOn("Slayer", "Share Buttons On Top Drops")),
            screen("config-tooltips", () -> AlpakaConfigScreen.openOn("Interface", "Scrollable Tooltips")),
            screen("config-menu-font", () -> AlpakaConfigScreen.openOn("Design", "Menu Font").withChoiceOpen()),
            screen("config-world", () -> AlpakaConfigScreen.openOn("World", "Block Overlay")),
            screen("config-viewmodel", () -> AlpakaConfigScreen.openOn("Viewmodel", "Viewmodel Modifiers")),
            screen("color-picker", () -> new ColorPickerScreen(null, "Accent Colour", 0xFF29B6B2, 0xFF00E5FF, color -> {})),
            screen("confirm", () -> new net.alpaka.addons.client.AlpakaConfirmScreen(null, "Disable All Features",
                    "Turns off every feature of Alpaka Addons. Each one can be switched back on in the config.",
                    "Disable All", true, () -> {})),
            screen("wheel-editor", () -> new CommandWheelConfigScreen(new CustomMainMenuScreen())),
            screen("hud-editor", () -> new HudEditorScreen(null)),
            new Stop(null, DevScreenshotTour::enterWorld,
                    () -> Minecraft.getInstance().player != null && Minecraft.getInstance().gui.screen() == null, 100),
            new Stop("world-hud", DevScreenshotTour::showHuds, () -> true, 30),
            new Stop("hud-editor-world", () -> Minecraft.getInstance().gui.setScreen(new HudEditorScreen(null)), () -> true, 20),
            // Every HUD on its blurred panel and in the menu font, a notice in the corner.
            new Stop("hud-style", DevScreenshotTour::hudStyle, () -> true, 20),
            screen("pause-menu", net.alpaka.addons.features.escapemenu.CustomPauseScreen::new),
            new Stop(null, () -> Minecraft.getInstance().gui.setScreen(null), () -> true, 5),
            // Two ticks after a burst: the lines are mid-slide inside the chat's box.
            new Stop("chat-slide", DevScreenshotTour::chatBurst, () -> true, 2),
            new Stop("chat-rest", () -> {}, () -> true, 40),
            new Stop("chat-open", () -> Minecraft.getInstance().gui.setScreen(new ChatScreen("", false)), () -> true, 10),
            // A screenshot's preview mid-shrink, then resting in the corner.
            new Stop(null, () -> {
                Minecraft mc = Minecraft.getInstance();
                Screenshot.grab(mc.gameDirectory, "preview-source.png", mc.gameRenderer.mainRenderTarget(), 1,
                        message -> mc.execute(() -> mc.gui.hud.getChat().addClientSystemMessage(message)));
            }, () -> true, 3),
            new Stop(null, () -> {}, () -> net.alpaka.addons.features.chat.ScreenshotPreview.isOnScreen(), 2),
            new Stop("shot-preview-flash", () -> net.alpaka.addons.features.chat.ScreenshotPreview.devFreezeAt(60), () -> true, 40),
            new Stop("shot-preview-shrink", () -> net.alpaka.addons.features.chat.ScreenshotPreview.devFreezeAt(200), () -> true, 40),
            new Stop("shot-preview-settle", () -> net.alpaka.addons.features.chat.ScreenshotPreview.devFreezeAt(420), () -> true, 40),
            new Stop("shot-preview-exit", () -> net.alpaka.addons.features.chat.ScreenshotPreview.devFreezeAt(650 + 3000 + 250), () -> true, 40),
            new Stop("shot-preview-corner", () -> net.alpaka.addons.features.chat.ScreenshotPreview.devFreezeAt(-1), () -> true, 40),
            new Stop("chat-share", () -> net.alpaka.addons.features.slayer.SlayerDropTracker.devShareSample(), () -> true, 10),
            new Stop("font-chat-scoreboard", DevScreenshotTour::fontInChatAndScoreboard, () -> true, 20),
            new Stop("third-person", DevScreenshotTour::thirdPerson, () -> true, 40),
            // Survival with armour and an action bar: what the attached inventory HUD has to clear.
            new Stop(null, DevScreenshotTour::survival,
                    () -> Minecraft.getInstance().gameMode != null && Minecraft.getInstance().gameMode.canHurtPlayer(), 20),
            new Stop("survival-hud", DevScreenshotTour::actionBar, () -> true, 10),
            // A sidebar like Hypixel's over its blurred panel, and a sword under starlight; twice, to
            // see the galaxy drift and twinkle.
            new Stop(null, DevScreenshotTour::scoreboardAndStarlight, () -> true, 20),
            new Stop("scoreboard-starlight", () -> {}, () -> true, 5),
            new Stop("scoreboard-starlight-later", () -> {}, () -> true, 15),
            screen("viewmodel-setup", () -> AlpakaConfigScreen.openOn("Viewmodel", "Viewmodel Modifiers").inSetupMode()),
            screen("viewmodel-setup-swing", () -> AlpakaConfigScreen.openOn("Viewmodel", "Swing").inSetupMode()),
            new Stop("viewmodel-setup-swinging", net.alpaka.addons.features.viewmodel.SwingPreview::swing, () -> true, 1),
            new Stop(null, () -> Minecraft.getInstance().gui.setScreen(null), () -> true, 5),
            // Turned right a little: the galaxy hangs in the sky, so it slides left over the item.
            new Stop("starlight-still", () -> AlpakaConfig.instance.itemSwayDisabled = true, () -> true, 3),
            new Stop("starlight-turned", () -> Minecraft.getInstance().player.setYRot(
                    Minecraft.getInstance().player.getYRot() + 3.0f), () -> true, 3),
            new Stop(null, () -> AlpakaConfig.instance.itemSwayDisabled = false, () -> true, 1),
            // Starlight together with Disable Item Lighting, still and mid-swing.
            new Stop("starlight-unlit", () -> AlpakaConfig.instance.itemLightingDisabled = true, () -> true, 5),
            new Stop("starlight-unlit-swing", () -> Minecraft.getInstance().player.swing(
                    net.minecraft.world.InteractionHand.MAIN_HAND), () -> true, 3),
            new Stop(null, () -> AlpakaConfig.instance.itemLightingDisabled = false, () -> true, 1),
            // The fire pit preview has to be on a tick before the clay arrives, as in a real fight.
            new Stop(null, DevScreenshotTour::firePitPreview, () -> true, 5),
            new Stop("fire-pits", DevScreenshotTour::placeFirePits, () -> true, 30)
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
            if (ticks >= 100) {
                // The tour runs while someone works in another window; a game that pauses on losing
                // focus opens the pause menu in the world, and the tour waits for it forever. Set
                // here rather than at registration, when the options do not exist yet. In memory only.
                mc.options.pauseOnLostFocus = false;
                begin(mc, 0);
            }
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
        // Any value other than "1" picks the stops whose picture starts with it, so a change to one
        // screen can be looked at without the whole tour: ALPAKA_SCREENSHOT_TOUR=config.
        String only = System.getenv(ENV);
        if (only != null && !only.equals("1")) {
            // A stop without a picture sets up the ones after it, the world for instance, so it runs
            // whenever a picked one follows.
            while (index < STOPS.size() && !picked(index, only)) index++;
        }
        stop = index;
        ticks = 0;
        if (index < STOPS.size()) STOPS.get(index).action().run();
    }

    private static boolean picked(int index, String only) {
        String shot = STOPS.get(index).shot();
        if (shot != null) return shot.startsWith(only);
        for (int later = index + 1; later < STOPS.size(); later++) {
            String laterShot = STOPS.get(later).shot();
            if (laterShot != null && laterShot.startsWith(only)) return true;
        }
        return false;
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

    /**
     * Puts the mouse at this share of the window's width and height. The tour runs without anyone at
     * the mouse, so the handler's private position is set directly; dev only.
     */
    private static void placeMouse(double x, double y) {
        Minecraft mc = Minecraft.getInstance();
        try {
            java.lang.reflect.Field xpos = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            java.lang.reflect.Field ypos = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            xpos.setAccessible(true);
            ypos.setAccessible(true);
            xpos.setDouble(mc.mouseHandler, mc.getWindow().getScreenWidth() * x);
            ypos.setDouble(mc.mouseHandler, mc.getWindow().getScreenHeight() * y);
        } catch (ReflectiveOperationException e) {
            AlpakaAddons.LOGGER.warn("Screenshot tour: could not place the mouse", e);
        }
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

    /** The HUD editor's previews and a notice, all drawn in the shared HUD style. */
    private static void hudStyle() {
        net.alpaka.addons.features.notification.AlpakaNotifications.send("Boss Spawned!",
                "Inferno Demonlord IV is up. Kill it before the timer runs out.");
        Minecraft.getInstance().gui.setScreen(new HudEditorScreen(null));
    }

    /** A burst of lines, as a busy chat delivers them. */
    private static void chatBurst() {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 1; i <= 6; i++) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§7Tour line " + i + " §8- a message arriving in a burst"));
        }
    }

    /** The menu font in the chat and on a sidebar laid out like SkyBlock's. */
    private static void fontInChatAndScoreboard() {
        Minecraft mc = Minecraft.getInstance();
        mc.gui.setScreen(null);
        mc.gui.toastManager().clear();
        AlpakaConfig.instance.menuFontInChat = true;
        AlpakaConfig.instance.menuFontInScoreboard = true;
        net.alpaka.addons.client.gui.GuiFont.refreshChat();
        mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§9Party §8> §b[MVP§c+§b] Alpaka§f: ready for the boss?"));
        mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§2Guild > §a[VIP] Someone §e[MEMBER]§f: gg"));
        mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§6§lRARE DROP! §9Flawed Opal Gemstone §b(+212% ✯ Magic Find)"));

        net.minecraft.client.server.IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            net.minecraft.world.scores.Scoreboard board = server.getScoreboard();
            net.minecraft.world.scores.Objective objective = board.getObjective("tour");
            if (objective == null) {
                objective = board.addObjective("tour", net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
                        Component.literal("§e§lSKYBLOCK"), net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,
                        false, null);
            }
            board.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR, objective);
            String[] lines = {"§710/12/26 §8m12AB", " ", " Late Summer 12th", " §79:40pm ☀", " §7⏣ §cSmoldering Tomb", " ",
                    "Purse: §638,429,113", "Bits: §b12,450", " ", "Slayer Quest", "§4Inferno Demonlord IV", " §7(§e412§7/§c1,500§7) Combat XP", " ", "§ewww.hypixel.net"};
            for (int i = 0; i < lines.length; i++) {
                // A colour code as the holder's name draws as nothing, as on Hypixel; the line is the prefix.
                String owner = "§" + "0123456789abcdef".charAt(i);
                board.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(owner), objective).set(lines.length - i);
                net.minecraft.world.scores.PlayerTeam team = board.getPlayerTeam("tour" + i);
                if (team == null) team = board.addPlayerTeam("tour" + i);
                board.addPlayerToTeam(owner, team);
                team.setPlayerPrefix(Component.literal(lines[i]));
            }
        });
    }

    /** Back in first person, in survival and wearing a chestplate, so hearts and armour show. */
    private static void survival() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        java.util.UUID id = mc.player.getUUID();
        net.minecraft.client.server.IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) return;
            player.setGameMode(GameType.SURVIVAL);
            player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
        });
    }

    /** A scoreboard sidebar with a few lines, and a diamond sword in hand with starlight on. */
    private static void scoreboardAndStarlight() {
        Minecraft mc = Minecraft.getInstance();
        AlpakaConfig.instance.itemSizeFeatureEnabled = true;
        AlpakaConfig.instance.itemStarlightEnabled = true;
        AlpakaConfig.instance.scoreboardBlurEnabled = true;
        mc.player.setXRot(0.0f);
        java.util.UUID id = mc.player.getUUID();
        net.minecraft.client.server.IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
            }
            String[] commands = {
                    "scoreboard objectives add tour dummy {text:\"SKYBLOCK\",color:\"yellow\",bold:true}",
                    "scoreboard objectives setdisplay sidebar tour",
                    // Score holder names cannot hold spaces; these stand in for Hypixel's lines.
                    "scoreboard players set §710/06/26 tour 6",
                    "scoreboard players set Spring_11th tour 5",
                    "scoreboard players set §c⏣Catacombs§7(M7) tour 4",
                    "scoreboard players set Cleared:§a100% tour 3",
                    "scoreboard players set §bAshFall-Nonu tour 2",
                    "scoreboard players set §ewww.hypixel.net tour 1",
            };
            for (String command : commands) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
            }
        });
    }

    /** An action bar line like Hypixel's, which is up the whole time on SkyBlock. */
    private static void actionBar() {
        Minecraft.getInstance().gui.hud.setOverlayMessage(
                Component.literal("§c100/100❤     §a50❈ Defense     §b100/100✎ Mana"), false);
    }

    /** The fire pit highlight without a Blaze boss, looking at the floor ahead. */
    private static void firePitPreview() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        AlpakaConfig.instance.blazeFirePitsEnabled = true;
        if (!net.alpaka.addons.features.blaze.FirePitFeature.togglePreview()) {
            net.alpaka.addons.features.blaze.FirePitFeature.togglePreview();
        }
        // A clear view: nothing over the floor ahead.
        AlpakaConfig.instance.inventoryHudEnabled = false;
        mc.gui.hud.getChat().clearMessages(false);
        mc.player.setXRot(20.0f);
    }

    /**
     * A three-by-three patch of clay in the floor ahead, and one lone block beside it, placed by the
     * integrated server so they reach the client as block updates - the way Hypixel's arrive.
     */
    private static void placeFirePits() {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.server.IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) return;
        net.minecraft.core.BlockPos floor = mc.player.blockPosition().below();
        net.minecraft.core.Direction ahead = mc.player.getDirection();
        net.minecraft.core.Direction side = ahead.getClockWise();
        java.util.List<net.minecraft.core.BlockPos> pits = new java.util.ArrayList<>();
        for (int forward = 4; forward <= 6; forward++) {
            for (int across = -1; across <= 1; across++) {
                pits.add(floor.relative(ahead, forward).relative(side, across));
            }
        }
        pits.add(floor.relative(ahead, 5).relative(side, 3));
        server.execute(() -> {
            net.minecraft.server.level.ServerLevel level = server.overworld();
            for (net.minecraft.core.BlockPos pos : pits) {
                level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.CLAY.defaultBlockState());
            }
        });
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
