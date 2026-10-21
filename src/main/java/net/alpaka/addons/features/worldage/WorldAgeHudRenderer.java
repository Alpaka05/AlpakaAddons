package net.alpaka.addons.features.worldage;

import net.alpaka.addons.client.gui.GuiFont;
import net.alpaka.addons.client.gui.HudPanel;
import net.alpaka.addons.client.hud.VisiblePlacement;
import net.alpaka.addons.config.AlpakaConfig;
import net.alpaka.addons.features.slayer.SlayerDropTracker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.alpaka.addons.utils.SkyblockUtils;

public class WorldAgeHudRenderer {

    private static String currentServerKey = "";
    private static long currentServerJoinTime = 0L;
    private static final Map<String, Long> SERVER_LEAVE_MAP = new HashMap<>();
    /**
     * Client ticks to wait after joining before reading the clock and the sidebar, so the overworld
     * clock and the scoreboard packets have arrived. 50 ticks is the 2.5 seconds this used to sleep.
     */
    private static final int JOIN_DELAY_TICKS = 50;
    private static int joinCountdownTicks = 0;
    private static final Pattern HYPIXEL_DATE_SERVER_PATTERN = Pattern.compile("\\d{1,2}/\\d{1,2}/\\d{2,4}\\s+([a-zA-Z0-9_-]+)");

    /** How often the presence timestamp is refreshed while the player stays on one server. */
    private static final long PRESENCE_STAMP_INTERVAL_MS = 1000L;
    private static long lastPresenceStampMs = 0L;

    /**
     * The drawn line, kept between frames in the font it is drawn in.
     *
     * The text changes once per in-game day at most, so building {@code "Day " + day} and wrapping
     * it in a Component every frame was wasted work for a line that is almost always the same one as
     * last frame, and a fresh wrapper had its bidi order worked out again each time. Rebuilt only
     * when the day or that font ({@link #cachedDayStyle}) changes. The box is measured from this same
     * Component.
     */
    private static long cachedDay = Long.MIN_VALUE;
    private static Style cachedDayStyle = null;
    private static Component cachedDayText = null;

    /** The rendered text for a day count, built at most once per change of day or font. */
    private static Component dayText(long day) {
        boolean smooth = AlpakaConfig.instance.smoothOn(AlpakaConfig.instance.worldAgeHudSmoothFont);
        Style style = smooth ? GuiFont.style() : Style.EMPTY;
        if (cachedDayText == null || day != cachedDay || !style.equals(cachedDayStyle)) {
            cachedDay = day;
            cachedDayStyle = style;
            cachedDayText = HudPanel.text(Component.literal(dayLabel(day)), smooth);
        }
        return cachedDayText;
    }

    /** "Day N", or "Day ?" while the server's age cannot be read. */
    private static String dayLabel(long day) {
        return day == WorldClockTracker.UNKNOWN_DAY ? "Day ?" : "Day " + day;
    }

    public static void registerEvents() {
        ClientEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity == Minecraft.getInstance().player) {
                onServerJoin();
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (joinCountdownTicks <= 0) return;
            if (--joinCountdownTicks == 0) {
                processServerJoin();
            }
        });
    }

    /**
     * Arms the delayed join read.
     *
     * Counted in client ticks rather than slept off on a thread of its own: the work has to happen
     * on the client thread anyway, so a thread that exists only to sleep and then hand back is pure
     * overhead - and ticks are the better clock here, since they stop while the game is loading
     * instead of running the countdown out before a single packet has arrived.
     */
    private static void onServerJoin() {
        long now = System.currentTimeMillis();
        if (now - currentServerJoinTime < 2000L) {
            return;
        }
        currentServerJoinTime = now;
        joinCountdownTicks = JOIN_DELAY_TICKS;
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

                    String clean = SkyblockUtils.cleanColor(lineText);
                    Matcher m = HYPIXEL_DATE_SERVER_PATTERN.matcher(clean);
                    if (m.find()) {
                        return m.group(1);
                    }
                }
            }
        } catch (Exception ignored) {}
        return "";
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
        String dayText = day == WorldClockTracker.UNKNOWN_DAY
                ? "§7day unknown"
                : (day > 30 ? "§c" : (day >= 25 ? "§6" : "§a")) + "Day " + day;

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
            return s > 0 ? m + " min " + s + " s" : m + " min";
        }
        return seconds + " s";
    }

    public static int getDayColor(long day) {
        if (day == WorldClockTracker.UNKNOWN_DAY) {
            return 0xFFAAAAAA; // Gray (unknown)
        } else if (day > 30) {
            return 0xFFFF5555; // Red (>30)
        } else if (day >= 25) {
            return 0xFFFFAA00; // Orange (25-30)
        } else {
            return 0xFF55FF55; // Green (<25)
        }
    }

    /** The server's day, or {@link WorldClockTracker#UNKNOWN_DAY}; see {@link WorldClockTracker}. */
    public static long getWorldDay() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return WorldClockTracker.UNKNOWN_DAY;
        return WorldClockTracker.getDay(mc.level);
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        // Keeps SERVER_LEAVE_MAP holding when the player was LAST active on the current server.
        // Throttled to once a second: this runs before the enabled check, so an untouched second of
        // map writes is work the feature does even while switched off, and the value it stores is
        // only ever read at second resolution anyway.
        long nowMs = System.currentTimeMillis();
        if (!currentServerKey.isEmpty() && nowMs - lastPresenceStampMs >= PRESENCE_STAMP_INTERVAL_MS) {
            lastPresenceStampMs = nowMs;
            SERVER_LEAVE_MAP.put(currentServerKey, nowMs);
        }

        if (!AlpakaConfig.instance.worldAgeHudEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden() || mc.level == null || mc.player == null) return;

        // Hide in menu screens (always allow in ChatScreen)
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) {
            return;
        }

        // Clamped so a GUI-scale change cannot leave the HUD off screen; the stored position is
        // untouched, so returning to the old scale restores it exactly. The line is measured once
        // through visiblePlacement, the editor's own call, rather than once per axis.
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        VisiblePlacement placement = WorldAgeHudElement.INSTANCE.visiblePlacement(screenWidth, screenHeight);
        renderHud(graphics, placement.getAnchorX(), placement.getAnchorY(), AlpakaConfig.instance.worldAgeHudScale);
    }

    public static void renderHud(GuiGraphicsExtractor graphics, int x, int y, float scale) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) return;

        long day = getWorldDay();
        int color = getDayColor(day);

        graphics.pose().pushMatrix();
        graphics.pose().translate((float) x, (float) y);
        graphics.pose().scale(scale, scale);
        Component text = dayText(day);
        if (AlpakaConfig.instance.blurOn(AlpakaConfig.instance.worldAgeHudBlur)) HudPanel.aroundText(graphics, font.width(text), 9, scale);
        graphics.text(font, text, 0, 0, color, HudPanel.TEXT_SHADOW);
        graphics.pose().popMatrix();
    }

    public static int getWidth(Font font, long day, float scale) {
        return (int) Math.ceil(font.width(dayText(day)) * scale);
    }

    public static int getHeight(Font font, float scale) {
        return (int) Math.ceil(9 * scale);
    }
}
