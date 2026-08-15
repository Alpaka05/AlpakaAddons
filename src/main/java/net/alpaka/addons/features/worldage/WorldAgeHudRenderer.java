package net.alpaka.addons.features.worldage;

import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.alpaka.addons.utils.SkyblockUtils;

public class WorldAgeHudRenderer {

    private static String currentServerKey = "";
    private static long currentServerJoinTime = 0L;
    private static final Map<String, Long> SERVER_LEAVE_MAP = new HashMap<>();
    private static final AtomicBoolean IS_PENDING = new AtomicBoolean(false);
    private static final Pattern HYPIXEL_DATE_SERVER_PATTERN = Pattern.compile("\\d{1,2}/\\d{1,2}/\\d{2,4}\\s+([a-zA-Z0-9_-]+)");

    public static void registerEvents() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity == Minecraft.getInstance().player) {
                onServerJoin();
            }
        });
    }

    private static void onServerJoin() {
        long now = System.currentTimeMillis();
        if (now - currentServerJoinTime < 2000L) {
            return;
        }
        currentServerJoinTime = now;

        if (IS_PENDING.compareAndSet(false, true)) {
            new Thread(() -> {
                try {
                    // Wait 2500ms so level overworld clock and scoreboard packets are fully loaded and synced
                    Thread.sleep(2500);
                } catch (InterruptedException ignored) {}
                Minecraft mc = Minecraft.getInstance();
                if (mc != null) {
                    mc.execute(() -> {
                        try {
                            processServerJoin();
                        } finally {
                            IS_PENDING.set(false);
                        }
                    });
                } else {
                    IS_PENDING.set(false);
                }
            }).start();
        }
    }

    private static String getServerKey() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "";

        String sidebarId = getSidebarServerId();
        if (!sidebarId.isEmpty()) {
            return "hypixel_" + sidebarId;
        }

        var serverData = mc.getCurrentServer();
        String ip = serverData != null && serverData.ip != null ? serverData.ip : "local";
        int levelId = System.identityHashCode(mc.level);

        return ip + "_lvl_" + levelId;
    }

    private static String getSidebarServerId() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return "";
        try {
            Scoreboard scoreboard = mc.level.getScoreboard();
            Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (objective != null) {
                for (PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
                    String lineText = "";
                    PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
                    if (team != null) {
                        String prefix = team.getPlayerPrefix() != null ? team.getPlayerPrefix().getString() : "";
                        String ownerName = entry.ownerName() != null ? entry.ownerName().getString() : "";
                        String suffix = team.getPlayerSuffix() != null ? team.getPlayerSuffix().getString() : "";
                        lineText = prefix + ownerName + suffix;
                    } else if (entry.ownerName() != null) {
                        lineText = entry.ownerName().getString();
                    } else {
                        lineText = entry.owner();
                    }

                    String clean = cleanColor(lineText);
                    Matcher m = HYPIXEL_DATE_SERVER_PATTERN.matcher(clean);
                    if (m.find()) {
                        return m.group(1);
                    }
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String cleanColor(String input) {
        if (input == null) return "";
        return input.replaceAll("(?i)§[0-9A-FK-OR]", "").trim();
    }

    private static void processServerJoin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (!AlpakaConfig.instance.worldAgeJoinMessageEnabled) return;
        if (!SkyblockUtils.isOnSkyblock()) return;

        String newServerKey = getServerKey();
        if (newServerKey.isEmpty()) return;

        long now = System.currentTimeMillis();
        boolean recentlyVisited = false;
        long timeAgoSec = 0L;

        if (SERVER_LEAVE_MAP.containsKey(newServerKey)) {
            long lastLeftMs = SERVER_LEAVE_MAP.get(newServerKey);
            long diffMs = now - lastLeftMs;
            long thresholdMs = (long) AlpakaConfig.instance.worldAgeRecentThresholdSec * 1000L;
            if (diffMs <= thresholdMs && diffMs >= 2000L) {
                recentlyVisited = true;
                timeAgoSec = diffMs / 1000L;
            }
        }

        currentServerKey = newServerKey;
        SERVER_LEAVE_MAP.put(newServerKey, now);

        if (!AlpakaConfig.instance.worldAgeJoinMessageEnabled) return;

        long day = getWorldDay();
        String dayColorStr = day > 30 ? "§c" : (day >= 25 ? "§6" : "§a");
        String dayText = dayColorStr + "Day " + day;

        if (recentlyVisited) {
            String timeAgoStr = formatTimeAgo(timeAgoSec);
            SlayerDropTracker.sendModMessage("§7Joined server: " + dayText + " §7(visited §e" + timeAgoStr + "§7 ago).");
        } else {
            SlayerDropTracker.sendModMessage("§7Joined server: " + dayText + "§7.");
        }
    }

    private static String formatTimeAgo(long seconds) {
        if (seconds >= 60) {
            long m = seconds / 60;
            long s = seconds % 60;
            return s > 0 ? String.format("%dm %ds", m, s) : String.format("%dm", m);
        }
        return seconds + "s";
    }

    public static int getDayColor(long day) {
        if (day > 30) {
            return 0xFFFF5555; // Red (>30)
        } else if (day >= 25) {
            return 0xFFFFAA00; // Orange (25-30)
        } else {
            return 0xFF55FF55; // Green (<25)
        }
    }

    public static long getWorldDay() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0L;

        long totalTicks = mc.level.getOverworldClockTime();
        long totalDays = Math.max(0L, totalTicks / 24000L);

        if (SkyblockUtils.isOnSkyblock() || totalDays > 1000) {
            // Hypixel mini-servers reboot on a 36-day (12-hour) cycle
            return totalDays % 36;
        }
        return totalDays;
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        // Continuously update active presence timestamp so SERVER_LEAVE_MAP holds when player was LAST active on current server
        if (!currentServerKey.isEmpty()) {
            SERVER_LEAVE_MAP.put(currentServerKey, System.currentTimeMillis());
        }

        if (!AlpakaConfig.instance.worldAgeHudEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null || mc.player == null) return;

        // Hide in menu screens (always allow in ChatScreen)
        if (mc.screen != null && !(mc.screen instanceof ChatScreen)) {
            return;
        }

        renderHud(graphics, AlpakaConfig.instance.worldAgeHudX, AlpakaConfig.instance.worldAgeHudY, AlpakaConfig.instance.worldAgeHudScale);
    }

    public static void renderHud(GuiGraphicsExtractor graphics, int x, int y, float scale) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) return;

        long day = getWorldDay();
        String text = "Day: " + day;
        int color = getDayColor(day);

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, Component.literal(text), 0, 0, color);
        graphics.pose().popMatrix();
    }

    public static int getWidth(Font font, long day, float scale) {
        String text = "Day: " + day;
        return (int) Math.ceil(font.width(text) * scale);
    }

    public static int getHeight(Font font, float scale) {
        return (int) Math.ceil(9 * scale);
    }
}
