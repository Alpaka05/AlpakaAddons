package net.alpaka.addons.client;

import it.unimi.dsi.fastutil.ints.IntList;
import net.alpaka.addons.AlpakaAddons;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.utils.AlpakaPerf;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Dev only: measures what Hide Visual Clutter saves. Off unless {@code ALPAKA_CLUTTER_BENCH} is set
 * (to "all" or a comma list of scenario names); then the dev client opens a fresh flat world, and
 * for every scenario keeps one kind of clutter going in front of the player while it measures the
 * frames with {@link AlpakaPerf}, switching that one effect off, on, off, on, so the run's noise
 * shows next to the difference. Each workload goes the way it does on Hypixel: the integrated
 * server explodes, spawns and breaks, and the packets reach the client's packet listener, where the
 * feature's hooks sit. Only the hit sparks are made on the client, as the player's own hits are.
 *
 * The results go to latest.log ("[ClutterBench]") and to {@code clutter-bench.txt} in the game
 * folder, and the client quits when done.
 */
public final class DevClutterBenchmark {

    private static final String ENV = "ALPAKA_CLUTTER_BENCH";
    private static final String WORLD = "alpaka-clutter-bench";
    private static final int WARM_TICKS = 60;
    private static final int MEASURE_TICKS = 160;
    private static final int CLEAR_TICKS = 30;
    private static final boolean[] PHASES = {false, true, false, true};

    private record Scenario(String name, Consumer<Boolean> toggle, Consumer<ServerLevel> serverTick, Runnable clientTick) {}

    private static final List<Scenario> SCENARIOS = new ArrayList<>();
    private static final List<String> RESULTS = new ArrayList<>();

    private static int ticks;
    private static int stage;
    private static int scenario;
    private static int phase;
    private static int phaseTick;
    private static double ox, oy, oz;
    private static final RandomSource RANDOM = RandomSource.create(42L);

    /** Entities the server spawned for the workload, with the server tick they are due to go. */
    private record Spawned(Entity entity, long dueTick) {}
    private static final Deque<Spawned> SPAWNED = new ArrayDeque<>();
    private static long serverTicks;
    private static final List<UUID> TARGETS = new ArrayList<>();

    private DevClutterBenchmark() {}

    public static void register() {
        String wanted = System.getenv(ENV);
        if (wanted == null) return;
        defineScenarios(wanted);
        AlpakaAddons.LOGGER.info("[ClutterBench] on: {}", SCENARIOS.stream().map(Scenario::name).toList());
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private static void defineScenarios(String wanted) {
        List<Scenario> all = List.of(
                new Scenario("explosions", on -> AlpakaConfig.instance.clutterExplosions = on, DevClutterBenchmark::explosions, null),
                new Scenario("falling_blocks", on -> AlpakaConfig.instance.clutterFallingBlocks = on, DevClutterBenchmark::fallingBlocks, null),
                new Scenario("block_break", on -> AlpakaConfig.instance.clutterBlockBreak = on, DevClutterBenchmark::blockBreak, null),
                new Scenario("hit_particles", on -> AlpakaConfig.instance.clutterHitParticles = on, DevClutterBenchmark::critArrows, DevClutterBenchmark::hitSparks),
                new Scenario("fireworks", on -> AlpakaConfig.instance.clutterFireworks = on, DevClutterBenchmark::fireworks, null),
                new Scenario("experience_orbs", on -> AlpakaConfig.instance.clutterExperienceOrbs = on, DevClutterBenchmark::experienceOrbs, null),
                new Scenario("dragon_death", on -> AlpakaConfig.instance.clutterDragonDeath = on, DevClutterBenchmark::dragonDeath, null));
        for (Scenario s : all) {
            if (wanted.equals("all") || wanted.equals("1") || List.of(wanted.split(",")).contains(s.name())) SCENARIOS.add(s);
        }
    }

    // Driver -------------------------------------------------------------------------------------

    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        switch (stage) {
            case 0 -> {
                if (ticks >= 100 && mc.level == null && mc.gui.screen() != null) {
                    mc.options.pauseOnLostFocus = false;
                    mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
                    // Frames as fast as they come, so the frame time is the work and not a cap.
                    mc.options.enableVsync().set(false);
                    mc.options.framerateLimit().set(net.minecraft.client.Options.UNLIMITED_FRAMERATE_CUTOFF);
                    // Nobody touches the keyboard here, so the AFK limit would cap it at 30.
                    mc.options.inactivityFpsLimit().set(net.minecraft.client.InactivityFpsLimit.MINIMIZED);
                    enterWorld();
                    next();
                }
            }
            case 1 -> {
                if (mc.player != null && mc.level != null && mc.gui.screen() == null && ticks >= 100) {
                    setUpWorld();
                    next();
                }
            }
            case 2 -> {
                if (ticks >= 60) {
                    if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                    scenario = 0;
                    phase = 0;
                    phaseTick = 0;
                    startPhase();
                    next();
                }
            }
            case 3 -> runPhases(mc);
            default -> {
            }
        }
    }

    private static void next() {
        stage++;
        ticks = 0;
    }

    private static void runPhases(Minecraft mc) {
        Scenario current = SCENARIOS.get(scenario);
        phaseTick++;
        int workEnd = WARM_TICKS + MEASURE_TICKS;
        if (phaseTick <= workEnd) {
            IntegratedServer server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> {
                serverTicks++;
                ServerLevel level = server.overworld();
                current.serverTick().accept(level);
                expire(false);
            });
            if (current.clientTick() != null) current.clientTick().run();
        }
        if (phaseTick == WARM_TICKS) {
            AlpakaPerf.start();
        } else if (phaseTick == workEnd) {
            AlpakaPerf.stop();
            record(mc, current);
            clearWorkload(mc);
        } else if (phaseTick >= workEnd + CLEAR_TICKS) {
            phase++;
            if (phase >= PHASES.length) {
                phase = 0;
                scenario++;
            }
            if (scenario >= SCENARIOS.size()) {
                finish(mc);
                return;
            }
            phaseTick = 0;
            startPhase();
        }
    }

    private static void startPhase() {
        AlpakaConfig config = AlpakaConfig.instance;
        // In memory only: the benchmark never saves.
        config.hideVisualClutter = true;
        config.clutterExplosions = false;
        config.clutterFallingBlocks = false;
        config.clutterBlockBreak = false;
        config.clutterHitParticles = false;
        config.clutterFireworks = false;
        config.clutterExperienceOrbs = false;
        config.clutterDragonDeath = false;
        SCENARIOS.get(scenario).toggle().accept(PHASES[phase]);
    }

    private static void record(Minecraft mc, Scenario current) {
        String particles = mc.particleEngine.countParticles();
        int entities = mc.level == null ? 0 : mc.level.getEntityCount();
        String head = String.format(Locale.ROOT, "%s #%d hidden=%s particles=%s entities=%d",
                current.name(), phase / 2 + 1, PHASES[phase] ? "yes" : "no", particles, entities);
        RESULTS.add(head);
        AlpakaAddons.LOGGER.info("[ClutterBench] {}", head);
        for (String line : AlpakaPerf.report()) {
            RESULTS.add("  " + line);
            AlpakaAddons.LOGGER.info("[ClutterBench]   {}", line);
        }
    }

    private static void finish(Minecraft mc) {
        stage = 99;
        Path out = mc.gameDirectory.toPath().resolve("clutter-bench.txt");
        try {
            Files.write(out, RESULTS);
        } catch (IOException e) {
            AlpakaAddons.LOGGER.warn("[ClutterBench] could not write {}", out, e);
        }
        AlpakaAddons.LOGGER.info("[ClutterBench] done");
        mc.stop();
    }

    // World --------------------------------------------------------------------------------------

    private static void enterWorld() {
        Minecraft mc = Minecraft.getInstance();
        Path old = mc.getLevelSource().getBaseDir().resolve(WORLD);
        if (Files.exists(old)) {
            try (var files = Files.walk(old)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            } catch (IOException e) {
                AlpakaAddons.LOGGER.warn("[ClutterBench] could not delete the old world", e);
            }
        }
        LevelSettings settings = new LevelSettings("Alpaka Clutter Bench", GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0L, false, false),
                WorldPresets::createTestWorldDimensions, new TitleScreen());
    }

    /** The player still, at noon, looking south over the ground where everything happens. */
    private static void setUpWorld() {
        Minecraft mc = Minecraft.getInstance();
        UUID id = mc.player.getUUID();
        ox = Math.floor(mc.player.getX()) + 0.5;
        oy = Math.floor(mc.player.getY());
        oz = Math.floor(mc.player.getZ()) + 0.5;
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) return;
            player.setInvulnerable(true);
            var source = server.createCommandSourceStack();
            for (String command : List.of("time set 6000", "weather clear", "gamerule advance_time false",
                    "gamerule advance_weather false", "gamerule spawn_mobs false", "gamerule random_tick_speed 0",
                    String.format(Locale.ROOT, "tp @a %.1f %.1f %.1f 0 10", ox, oy, oz))) {
                server.getCommands().performPrefixedCommand(source, command);
            }
            // Three still targets for the hit sparks.
            ServerLevel level = server.overworld();
            for (int i = 0; i < 3; i++) {
                ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
                if (stand == null) continue;
                stand.snapTo(ox - 2 + i * 2, oy, oz + 5, 180, 0);
                stand.setNoGravity(true);
                level.addFreshEntity(stand);
                TARGETS.add(stand.getUUID());
            }
        });
    }

    private static void clearWorkload(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) server.execute(() -> {
            expire(true);
            for (Entity e : server.overworld().getAllEntities()) {
                if (e instanceof ExperienceOrb) e.discard();
            }
        });
        mc.particleEngine.clearParticles();
    }

    /** Discards the workload's entities that are due, or all of them. Server thread. */
    private static void expire(boolean all) {
        while (!SPAWNED.isEmpty() && (all || SPAWNED.peekFirst().dueTick() <= serverTicks)) {
            Entity e = SPAWNED.pollFirst().entity();
            if (!e.isRemoved()) e.discard();
        }
    }

    private static void keep(ServerLevel level, Entity entity, int lifeTicks) {
        level.addFreshEntity(entity);
        SPAWNED.addLast(new Spawned(entity, serverTicks + lifeTicks));
    }

    /** A random spot in the area in front of the player, 7 to 19 blocks out and up to 8 to either side. */
    private static double ax() { return ox - 8 + RANDOM.nextDouble() * 16; }
    private static double az() { return oz + 7 + RANDOM.nextDouble() * 12; }

    // Workloads (server thread, once per client tick) --------------------------------------------

    /** Hyperion spam: three explosions a tick, each with an explosion particle packet like Hypixel's. */
    private static void explosions(ServerLevel level) {
        for (int i = 0; i < 3; i++) {
            double x = ax(), y = oy + 1 + RANDOM.nextDouble() * 2, z = az();
            level.explode(null, x, y, z, 3.0f, Level.ExplosionInteraction.NONE);
            level.sendParticles(ParticleTypes.EXPLOSION, x, y, z, 8, 1.5, 1.0, 1.5, 0.0);
        }
    }

    /** Molten Wave: fifteen gold blocks a tick hanging in the air for two seconds. */
    private static void fallingBlocks(ServerLevel level) {
        BlockState gold = Blocks.GOLD_BLOCK.defaultBlockState();
        for (int i = 0; i < 15; i++) {
            BlockPos pos = BlockPos.containing(ax(), oy + 1 + RANDOM.nextInt(3), az());
            if (!level.getBlockState(pos).isAir()) continue;
            level.setBlock(pos, gold, 3);
            FallingBlockEntity block = FallingBlockEntity.fall(level, pos, gold);
            block.setNoGravity(true);
            block.dropItem = false;
            SPAWNED.addLast(new Spawned(block, serverTicks + 40));
        }
    }

    /** Farming with other players around: five full blocks broken a tick, 100 a second. */
    private static void blockBreak(ServerLevel level) {
        BlockState[] kinds = {Blocks.STONE.defaultBlockState(), Blocks.MELON.defaultBlockState(),
                Blocks.PUMPKIN.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState(), Blocks.DIRT.defaultBlockState()};
        for (int i = 0; i < 5; i++) {
            BlockPos pos = BlockPos.containing(ox - 4 + RANDOM.nextInt(9), oy + RANDOM.nextInt(2), oz + 4 + RANDOM.nextInt(4));
            level.setBlock(pos, kinds[i], 3);
            level.destroyBlock(pos, false);
        }
    }

    /** Terminator-like crit arrows: three a tick flying across in front of the player. */
    private static void critArrows(ServerLevel level) {
        for (int i = 0; i < 3; i++) {
            Arrow arrow = new Arrow(level, ox - 10, oy + 1 + RANDOM.nextDouble() * 2, az(),
                    new ItemStack(Items.ARROW), null);
            arrow.setCritArrow(true);
            arrow.setNoGravity(true);
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
            arrow.setDeltaMovement(1.2, 0, 0);
            keep(level, arrow, 20);
        }
    }

    /** The player's own hits on three mobs: a crit and an enchanted hit emitter on each, every tick. */
    private static void hitSparks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof ArmorStand && TARGETS.contains(e.getUUID())) {
                mc.particleEngine.createTrackingEmitter(e, ParticleTypes.CRIT);
                mc.particleEngine.createTrackingEmitter(e, ParticleTypes.ENCHANTED_HIT);
            }
        }
    }

    /** A firework show: one rocket with three large stars every other tick. */
    private static void fireworks(ServerLevel level) {
        if (serverTicks % 2 != 0) return;
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        FireworkExplosion star = new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL,
                IntList.of(0xFF3030, 0x30FF30, 0x3030FF), IntList.of(0xFFFFFF), true, true);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(0, List.of(star, star, star)));
        FireworkRocketEntity entity = new FireworkRocketEntity(level, ax(), oy + 1, az(), rocket);
        level.addFreshEntity(entity);
    }

    /** Experience Bottles: ten orbs a tick on the ground, gone after three seconds. */
    private static void experienceOrbs(ServerLevel level) {
        for (int i = 0; i < 10; i++) {
            keep(level, new ExperienceOrb(level, ax(), oy + 0.5, az(), 1 + RANDOM.nextInt(7)), 60);
        }
    }

    /** A dragon killed every two seconds, so about five are dying at a time, like M7's last phase. */
    private static void dragonDeath(ServerLevel level) {
        for (Entity e : level.getAllEntities()) {
            if (e instanceof ExperienceOrb) e.discard();
        }
        if (serverTicks % 40 != 1) return;
        EnderDragon dragon = EntityTypes.ENDER_DRAGON.create(level, EntitySpawnReason.COMMAND);
        if (dragon == null) return;
        dragon.snapTo(ax(), oy + 8 + RANDOM.nextInt(6), oz + 30 + RANDOM.nextInt(10), RANDOM.nextFloat() * 360, 0);
        dragon.setNoAi(true);
        level.addFreshEntity(dragon);
        dragon.setHealth(0.0f);
        SPAWNED.addLast(new Spawned(dragon, serverTicks + 400));
    }
}
